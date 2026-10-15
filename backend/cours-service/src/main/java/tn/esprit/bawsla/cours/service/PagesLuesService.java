package tn.esprit.bawsla.cours.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import tn.esprit.bawsla.cours.common.exception.BadRequestException;
import tn.esprit.bawsla.cours.common.exception.ConflictException;
import tn.esprit.bawsla.cours.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.cours.common.exception.UnauthorizedException;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.PageLueResponse;
import tn.esprit.bawsla.cours.dto.PagesLuesResponse;
import tn.esprit.bawsla.cours.dto.TraitementResponse;
import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.EtapeIA;
import tn.esprit.bawsla.cours.entity.PageDocument;
import tn.esprit.bawsla.cours.entity.SourcePage;
import tn.esprit.bawsla.cours.entity.StatutTraitement;
import tn.esprit.bawsla.cours.entity.TraitementIA;
import tn.esprit.bawsla.cours.repository.PageDocumentRepository;
import tn.esprit.bawsla.cours.repository.TraitementIARepository;
import tn.esprit.bawsla.cours.service.FichierService.Fichier;
import tn.esprit.bawsla.cours.service.StockageService.Type;

/**
 * Pages sans texte exploitable (PDF scanné, images déposées) lues par l'IA (vision), avec correction par le formateur. Le texte lu est
 * enregistré dans {@link PageDocument} : la fiche, l'explication et le résumé l'utilisent comme celui d'un PDF ordinaire.
 *
 * <p>La lecture ne démarre jamais seule (elle consomme le quota de l'IA) : le formateur la lance. Chaque lancement lit 30 pages au plus ;
 * les suivantes partent au lancement suivant, qui ne reprend que les pages encore vides.</p>
 */
@Service
@Transactional(readOnly = true)
public class PagesLuesService {

    private static final Logger log = LoggerFactory.getLogger(PagesLuesService.class);

    /** En dessous de ce nombre de caractères (après nettoyage), une page est « sans texte exploitable ». */
    public static final int SEUIL_TEXTE = 30;
    /** Pages lues par lancement. */
    public static final int PLAFOND = 30;
    static final long IMAGE_MAX = 5L * 1024 * 1024;
    static final int IMAGES_MAX = 50;
    static final int COTE_MAX = 8000;
    static final long PIXELS_MAX = 40_000_000L;
    private static final long DELAI_MAX_MINUTES = 20;

    private final CoursService coursService;
    private final PageDocumentRepository pageRepository;
    private final TraitementIARepository traitementRepository;
    private final StockageService stockage;
    private final VisionTraitement traitement;
    private final FicheEnregistrement suivi;

    public PagesLuesService(CoursService coursService, PageDocumentRepository pageRepository, TraitementIARepository traitementRepository,
                            StockageService stockage, VisionTraitement traitement, FicheEnregistrement suivi) {
        this.coursService = coursService;
        this.pageRepository = pageRepository;
        this.traitementRepository = traitementRepository;
        this.stockage = stockage;
        this.traitement = traitement;
        this.suivi = suivi;
    }

    public static boolean sansTexte(PageDocument p) {
        return p.getTexte() == null || p.getTexte().strip().length() < SEUIL_TEXTE;
    }

    /**
     * La lecture par vision peut-elle encore apporter du texte ? Page de PDF sans texte jamais lue, ou page-image encore vide.
     * Une page de PDF déjà lue (même « illisible ») n'est pas relue : on ne repaie pas un appel pour rien.
     */
    public static boolean aLire(PageDocument p) {
        if (p.getImageStockage() != null) {
            return p.getTexte() == null || p.getTexte().isBlank();
        }
        return p.getSource() == SourcePage.PDFBOX && sansTexte(p);
    }

    // ------------------------------------------------------------------ lecture

    public PagesLuesResponse lister(Long coursId, CurrentUser user) {
        coursService.chargerPourEcriture(coursId, user);
        List<PageDocument> pages = pageRepository.findByCoursIdOrderByNumeroAsc(coursId);
        List<PageLueResponse> suivies = pages.stream()
                .filter(p -> p.getSource() == SourcePage.VISION || sansTexte(p))
                .map(PageLueResponse::from)
                .toList();
        return new PagesLuesResponse(pages.size(), (int) pages.stream().filter(PagesLuesService::aLire).count(), PLAFOND, suivies);
    }

    public Fichier image(Long coursId, int numero, CurrentUser user) {
        coursService.chargerPourEcriture(coursId, user);
        PageDocument page = page(coursId, numero);
        if (page.getImageStockage() == null) {
            throw new ResourceNotFoundException("Image de la page", (long) numero);
        }
        String nom = page.getImageStockage();
        String mime = nom.endsWith(".png") ? "image/png" : "image/jpeg";
        return new Fichier(stockage.lire(Type.PAGES, nom), mime, nom);
    }

    public TraitementResponse traitement(Long coursId, CurrentUser user) {
        coursService.chargerPourEcriture(coursId, user);
        return traitementRepository.findFirstByCoursIdAndPorteeOrderByIdDesc(coursId, TraitementIA.PORTEE_VISION)
                .map(dernier -> TraitementResponse.from(dernier.getExecution(),
                        traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, dernier.getExecution())))
                .orElseGet(TraitementResponse::aucun);
    }

    // ------------------------------------------------------------------ images déposées

    /** Dépose une image (PNG ou JPEG, 5 Mo) : elle devient la page suivante du cours, vide jusqu'à sa lecture. */
    @Transactional
    public PageLueResponse deposerImage(Long coursId, MultipartFile envoi, CurrentUser user) {
        coursService.chargerPourEcriture(coursId, user);
        byte[] octets = FichierService.lire(envoi, IMAGE_MAX, "L'image dépasse 5 Mo");
        if (!ressembleAUnePngOuJpeg(octets)) {
            throw new BadRequestException("Format d'image non pris en charge : JPG ou PNG uniquement.");
        }
        verifierDimensions(octets);
        String extension = FichierService.extensionImage(octets); // signature, puis décodage réel
        if (pageRepository.findByCoursIdAndImageStockageIsNotNullOrderByNumeroAsc(coursId).size() >= IMAGES_MAX) {
            throw new BadRequestException("Ce cours a déjà " + IMAGES_MAX + " images : supprime-en avant d'en ajouter.");
        }
        String nom = stockage.sauvegarder(Type.PAGES, octets, extension);
        PageDocument page = pageRepository.save(PageDocument.image(coursId, pageRepository.dernierNumero(coursId) + 1, nom));
        log.info("Image déposée pour le cours {} : page {}", coursId, page.getNumero());
        return PageLueResponse.from(page);
    }

    /** Supprime une page-image et renumérote les pages suivantes. Les pages du PDF ne se suppriment pas (remplacer le PDF suffit). */
    @Transactional
    public void supprimerImage(Long coursId, int numero, CurrentUser user) {
        coursService.chargerPourEcriture(coursId, user);
        PageDocument page = page(coursId, numero);
        if (page.getImageStockage() == null) {
            throw new BadRequestException("Seules les images déposées peuvent être supprimées : les pages du PDF suivent le PDF.");
        }
        stockage.supprimerApresValidation(Type.PAGES, page.getImageStockage());
        pageRepository.delete(page);
        for (PageDocument suivante : pageRepository.findByCoursIdOrderByNumeroAsc(coursId)) {
            if (suivante.getNumero() > numero) {
                suivante.setNumero(suivante.getNumero() - 1);
            }
        }
    }

    // ------------------------------------------------------------------ correction

    /** Le formateur corrige (ou saisit) le texte d'une page avant de générer la fiche. */
    @Transactional
    public PageLueResponse corriger(Long coursId, int numero, String texte, CurrentUser user) {
        coursService.chargerPourEcriture(coursId, user);
        PageDocument page = page(coursId, numero);
        page.setTexte(nettoyer(texte));
        page.setModifie(true);
        return PageLueResponse.from(page);
    }

    // ------------------------------------------------------------------ lecture par vision

    /** Lance la lecture des pages vides en arrière-plan (30 au plus). Suivre avec {@link #traitement}. */
    @Transactional
    public TraitementResponse lire(Long coursId, CurrentUser user, String authorization) {
        coursService.chargerPourEcriture(coursId, user);
        if (authorization == null || authorization.isBlank()) {
            throw new UnauthorizedException("Session introuvable : reconnecte-toi puis réessaie.");
        }
        if (traitementActif(coursId)) {
            throw new ConflictException("Une lecture de pages est déjà en cours : attends qu'elle se termine.");
        }
        List<Integer> numeros = pageRepository.findByCoursIdOrderByNumeroAsc(coursId).stream()
                .filter(PagesLuesService::aLire)
                .sorted(Comparator.comparingInt(PageDocument::getNumero))
                .limit(PLAFOND)
                .map(PageDocument::getNumero)
                .toList();
        if (numeros.isEmpty()) {
            throw new BadRequestException("Aucune page à lire : toutes les pages ont déjà du texte.");
        }
        String execution = UUID.randomUUID().toString();
        TraitementIA ligne = TraitementIA.enAttente(coursId, execution, EtapeIA.LECTURE_IMAGES, TraitementIA.PORTEE_VISION);
        ligne.setAvancement(0);
        ligne.setTotal(numeros.size());
        ligne = traitementRepository.save(ligne);
        TraitementIA enregistree = ligne;
        apresValidation(coursId, execution, () -> traitement.lire(execution, coursId, numeros, authorization));
        return TraitementResponse.from(execution, List.of(enregistree));
    }

    // ------------------------------------------------------------------ interne

    private PageDocument page(Long coursId, int numero) {
        return pageRepository.findByCoursIdAndNumero(coursId, numero)
                .orElseThrow(() -> new ResourceNotFoundException("Page", (long) numero));
    }

    private static String nettoyer(String texte) {
        return texte == null ? "" : texte.replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]", "").strip();
    }

    private boolean traitementActif(Long coursId) {
        return traitementRepository.findFirstByCoursIdAndPorteeOrderByIdDesc(coursId, TraitementIA.PORTEE_VISION).map(dernier -> {
            boolean recent = dernier.getDateCreation().isAfter(LocalDateTime.now().minusMinutes(DELAI_MAX_MINUTES));
            return recent && traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, dernier.getExecution())
                    .stream().anyMatch(t -> t.getStatut().actif());
        }).orElse(false);
    }

    /** PNG (signature de 8 octets) ou JPEG (FF D8 FF) : on ne se fie ni au nom ni au type annoncé par le navigateur. */
    static boolean ressembleAUnePngOuJpeg(byte[] o) {
        boolean jpeg = o.length > 3 && (o[0] & 0xFF) == 0xFF && (o[1] & 0xFF) == 0xD8 && (o[2] & 0xFF) == 0xFF;
        boolean png = o.length > 8 && (o[0] & 0xFF) == 0x89 && o[1] == 'P' && o[2] == 'N' && o[3] == 'G'
                && o[4] == 0x0D && o[5] == 0x0A && o[6] == 0x1A && o[7] == 0x0A;
        return jpeg || png;
    }

    /** Lit seulement l'en-tête de l'image : une image piégée (quelques Ko pour des milliards de pixels) est refusée avant d'être décodée. */
    static void verifierDimensions(byte[] octets) {
        try (ImageInputStream flux = ImageIO.createImageInputStream(new ByteArrayInputStream(octets))) {
            var lecteurs = ImageIO.getImageReaders(flux);
            if (!lecteurs.hasNext()) {
                throw new BadRequestException("Image illisible ou corrompue.");
            }
            ImageReader lecteur = lecteurs.next();
            try {
                lecteur.setInput(flux, true, true);
                int largeur = lecteur.getWidth(0);
                int hauteur = lecteur.getHeight(0);
                if (largeur > COTE_MAX || hauteur > COTE_MAX || (long) largeur * hauteur > PIXELS_MAX) {
                    throw new BadRequestException("Image trop grande : " + COTE_MAX + " pixels au plus par côté.");
                }
            } finally {
                lecteur.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BadRequestException bad) {
                throw bad;
            }
            throw new BadRequestException("Image illisible ou corrompue.");
        }
    }

    /** Lance la tâche une fois la transaction validée. Exécuteur saturé : l'étape passe en échec avec un message lisible. */
    private void apresValidation(Long coursId, String execution, Runnable lancement) {
        Runnable securise = () -> {
            try {
                lancement.run();
            } catch (TaskRejectedException e) {
                log.warn("Exécuteur IA saturé, lecture des pages du cours {} refusée", coursId);
                suivi.passer(execution, coursId, EtapeIA.LECTURE_IMAGES, StatutTraitement.ECHEC, "Trop de traitements en cours, réessaie dans un instant.");
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    securise.run();
                }
            });
        } else {
            securise.run();
        }
    }
}
