package tn.esprit.bawsla.cours.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.cours.common.exception.BadRequestException;
import tn.esprit.bawsla.cours.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.CoursRequest;
import tn.esprit.bawsla.cours.dto.CoursResponse;
import tn.esprit.bawsla.cours.dto.CoursTexteResponse;
import tn.esprit.bawsla.cours.dto.PageTexte;
import tn.esprit.bawsla.cours.dto.StatutRequest;
import tn.esprit.bawsla.cours.entity.AudioRevision;
import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.ExplicationAudio;
import tn.esprit.bawsla.cours.entity.ResumeLocal;
import tn.esprit.bawsla.cours.entity.Niveau;
import tn.esprit.bawsla.cours.entity.PageDocument;
import tn.esprit.bawsla.cours.entity.StatutCours;
import tn.esprit.bawsla.cours.repository.AudioRevisionRepository;
import tn.esprit.bawsla.cours.repository.ChapitreRepository;
import tn.esprit.bawsla.cours.repository.ChapitreTermineRepository;
import tn.esprit.bawsla.cours.repository.InscriptionRepository;
import tn.esprit.bawsla.cours.repository.CoursRepository;
import tn.esprit.bawsla.cours.repository.ExplicationAudioRepository;
import tn.esprit.bawsla.cours.repository.ResumeLocalRepository;
import tn.esprit.bawsla.cours.repository.FicheRevisionRepository;
import tn.esprit.bawsla.cours.repository.PageDocumentRepository;
import tn.esprit.bawsla.cours.repository.TraitementIARepository;

/**
 * Cours : lecture selon la visibilité (apprenant : cours publiés ; formateur : publiés et les siens ; ADMIN : tout),
 * écriture réservée au propriétaire ou à l'ADMIN.
 */
@Service
@Transactional(readOnly = true)
public class CoursService {

    private final CoursRepository coursRepository;
    private final ChapitreRepository chapitreRepository;
    private final PageDocumentRepository pageRepository;
    private final StockageService stockage;
    private final FicheRevisionRepository ficheRepository;
    private final AudioRevisionRepository audioRepository;
    private final TraitementIARepository traitementRepository;
    private final ExplicationAudioRepository explicationRepository;
    private final ResumeLocalRepository resumeLocalRepository;
    private final InscriptionRepository inscriptionRepository;
    private final ChapitreTermineRepository chapitreTermineRepository;
    private final FormateurVerifier formateurVerifier;

    public CoursService(CoursRepository coursRepository, ChapitreRepository chapitreRepository,
                        PageDocumentRepository pageRepository, StockageService stockage,
                        FicheRevisionRepository ficheRepository, AudioRevisionRepository audioRepository,
                        TraitementIARepository traitementRepository, ExplicationAudioRepository explicationRepository,
                        ResumeLocalRepository resumeLocalRepository, InscriptionRepository inscriptionRepository,
                        ChapitreTermineRepository chapitreTermineRepository, FormateurVerifier formateurVerifier) {
        this.formateurVerifier = formateurVerifier;
        this.inscriptionRepository = inscriptionRepository;
        this.chapitreTermineRepository = chapitreTermineRepository;
        this.coursRepository = coursRepository;
        this.chapitreRepository = chapitreRepository;
        this.pageRepository = pageRepository;
        this.stockage = stockage;
        this.ficheRepository = ficheRepository;
        this.audioRepository = audioRepository;
        this.traitementRepository = traitementRepository;
        this.explicationRepository = explicationRepository;
        this.resumeLocalRepository = resumeLocalRepository;
    }

    /** {@code user} peut être null : visiteur du catalogue public, qui ne voit que les cours publiés. */
    public List<CoursResponse> findAll(String categorie, Niveau niveau, CurrentUser user) {
        // Sans correspondance possible pour un apprenant ou un visiteur : seuls les cours publiés ressortent.
        Long moi = user != null && user.hasAnyRole(CurrentUser.ENSEIGNANT) ? user.id() : -1L;
        boolean admin = user != null && user.isAdmin();
        List<Cours> cours = coursRepository.search(Matieres.nettoyer(categorie), niveau, admin, StatutCours.PUBLIE, moi);
        Map<Long, int[]> stats = statistiques(cours.stream().map(Cours::getId).toList());
        return cours.stream().map(c -> reponse(c, stats)).toList();
    }

    public CoursResponse findById(Long id, CurrentUser user) {
        return reponse(chargerVisible(id, user));
    }

    @Transactional
    public CoursResponse create(CoursRequest request, CurrentUser user) {
        user.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        Cours cours = new Cours();
        apply(cours, request);
        cours.setEnseignantId(user.id());
        return reponse(coursRepository.save(cours));
    }

    @Transactional
    public CoursResponse update(Long id, CoursRequest request, CurrentUser user) {
        Cours cours = chargerPourEcriture(id, user);
        apply(cours, request);
        return reponse(cours);
    }

    @Transactional
    public void delete(Long id, CurrentUser user) {
        Cours cours = chargerPourEcriture(id, user);
        for (PageDocument page : pageRepository.findByCoursIdAndImageStockageIsNotNullOrderByNumeroAsc(id)) {
            stockage.supprimerApresValidation(StockageService.Type.PAGES, page.getImageStockage());
        }
        pageRepository.supprimerDuCours(id);
        for (AudioRevision audio : audioRepository.findByCoursId(id)) {
            stockage.supprimerApresValidation(StockageService.Type.AUDIO, audio.getFichier());
        }
        audioRepository.deleteAll(audioRepository.findByCoursId(id));
        ficheRepository.deleteAll(ficheRepository.findByCoursId(id));
        List<ExplicationAudio> explications = explicationRepository.findByCoursId(id);
        for (ExplicationAudio e : explications) {
            stockage.supprimerApresValidation(StockageService.Type.AUDIO, e.getAudioFichier());
        }
        explicationRepository.deleteAll(explications);
        List<ResumeLocal> resumes = resumeLocalRepository.findByCoursId(id);
        for (ResumeLocal r : resumes) {
            stockage.supprimerApresValidation(StockageService.Type.AUDIO, r.getCheminAudio());
        }
        resumeLocalRepository.deleteAll(resumes);
        traitementRepository.deleteAll(traitementRepository.findByCoursId(id));
        chapitreTermineRepository.supprimerDuCours(id);
        inscriptionRepository.supprimerDuCours(id);
        coursRepository.delete(cours);
        stockage.supprimerApresValidation(StockageService.Type.IMAGES, cours.getImageFichier());
        stockage.supprimerApresValidation(StockageService.Type.PDF, cours.getPdfStockage());
    }

    /** Publier, dépublier ou archiver. Publier exige au moins un chapitre et un formateur validé (contrat 4). */
    @Transactional
    public CoursResponse changerStatut(Long id, StatutRequest request, CurrentUser user) {
        Cours cours = chargerPourEcriture(id, user);
        if (request.statut() == StatutCours.PUBLIE) {
            if (cours.getChapitres().isEmpty()) {
                throw new BadRequestException("Impossible de publier ce cours : ajoute d'abord au moins un chapitre.");
            }
            formateurVerifier.verifierPeutPublier(user);
        }
        cours.setStatut(request.statut());
        return reponse(cours);
    }

    /** Contrat GET /api/cours/{id}/texte : texte du PDF (emails et téléphones masqués). */
    public CoursTexteResponse getTexte(Long id, CurrentUser user) {
        Cours cours = chargerVisible(id, user);
        if (pageRepository.findByCoursIdOrderByNumeroAsc(id).isEmpty()) {
            return CoursTexteResponse.sansPdf(cours.getId());
        }
        List<PageTexte> pages = pageRepository.findByCoursIdOrderByNumeroAsc(id).stream()
                .map((PageDocument p) -> new PageTexte(p.getNumero(), TexteMasker.masquer(p.getTexte())))
                .toList();
        boolean scanne = pages.stream().allMatch(p -> p.texte().isBlank());
        String texte = pages.stream().map(PageTexte::texte).filter(t -> !t.isBlank()).collect(Collectors.joining("\n\n"));
        return new CoursTexteResponse(cours.getId(), texte, false, pages, scanne);
    }

    // ------------------------------------------------------------------ partagé avec les autres services du module

    /**
     * Cours visible par l'appelant, sinon 404 (un cours non publié d'un autre formateur n'existe pas pour lui).
     * {@code user} peut être null : visiteur sans en-têtes, qui ne voit que les cours publiés.
     */
    public Cours chargerVisible(Long id, CurrentUser user) {
        Cours cours = getOrThrow(id);
        if (!visible(cours, user)) {
            throw new ResourceNotFoundException("Cours", id);
        }
        return cours;
    }

    /** Cours que l'appelant a le droit de modifier : son propriétaire ou un ADMIN. */
    public Cours chargerPourEcriture(Long id, CurrentUser user) {
        Cours cours = getOrThrow(id);
        user.requireOwnerOrAdmin(cours.getEnseignantId());
        return cours;
    }

    public CoursResponse reponse(Cours cours) {
        return reponse(cours, statistiques(List.of(cours.getId())));
    }

    // ------------------------------------------------------------------ interne

    private static boolean visible(Cours cours, CurrentUser user) {
        if (cours.getStatut() == StatutCours.PUBLIE) {
            return true;
        }
        if (user == null) {
            return false;
        }
        return user.isAdmin()
                || (user.hasAnyRole(CurrentUser.ENSEIGNANT) && user.id().equals(cours.getEnseignantId()));
    }

    private Cours getOrThrow(Long id) {
        return coursRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cours", id));
    }

    private CoursResponse reponse(Cours cours, Map<Long, int[]> stats) {
        int[] s = stats.getOrDefault(cours.getId(), new int[] {0, 0, 0});
        return CoursResponse.from(cours, s[0], s[1], s[2]);
    }

    /** coursId -> [nombre de chapitres, durée totale en minutes, nombre d'inscrits] (deux requêtes groupées, jamais une par cours). */
    private Map<Long, int[]> statistiques(Collection<Long> ids) {
        Map<Long, int[]> stats = new HashMap<>();
        if (ids.isEmpty()) {
            return stats;
        }
        for (Object[] ligne : chapitreRepository.statistiques(ids)) {
            stats.put((Long) ligne[0], new int[] {((Number) ligne[1]).intValue(), ((Number) ligne[2]).intValue(), 0});
        }
        for (Object[] ligne : inscriptionRepository.compterParCours(ids)) {
            stats.computeIfAbsent((Long) ligne[0], k -> new int[3])[2] = ((Number) ligne[1]).intValue();
        }
        return stats;
    }

    private static void apply(Cours cours, CoursRequest r) {
        cours.setTitre(r.titre());
        cours.setDescription(r.description());
        // Défense en profondeur : la contrainte @MatiereValide a déjà rejeté une matière invalide (400 avec details).
        var erreurs = Matieres.erreurs(r.categorie(), r.categoriePrecision());
        if (!erreurs.isEmpty()) {
            throw new BadRequestException(erreurs.values().iterator().next());
        }
        String matiere = Matieres.canonique(r.categorie()).orElseThrow();
        cours.setCategorie(matiere);
        cours.setCategoriePrecision(Matieres.AUTRE.equals(matiere) ? Matieres.nettoyer(r.categoriePrecision()) : null);
        cours.setNiveau(r.niveau());
        if (r.prix() != null) {
            cours.setPrix(r.prix().setScale(2, RoundingMode.HALF_UP));
        } else if (cours.getPrix() == null) {
            cours.setPrix(BigDecimal.ZERO.setScale(2));
        }
    }
}
