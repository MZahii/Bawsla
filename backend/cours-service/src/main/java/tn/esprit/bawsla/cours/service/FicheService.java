package tn.esprit.bawsla.cours.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tn.esprit.bawsla.cours.client.FideliteIaDto;
import tn.esprit.bawsla.cours.client.FideliteIaRequestDto;
import tn.esprit.bawsla.cours.client.NotionIaDto;
import tn.esprit.bawsla.cours.common.exception.ConflictException;
import tn.esprit.bawsla.cours.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.cours.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.FicheRequest;
import tn.esprit.bawsla.cours.dto.FicheResponse;
import tn.esprit.bawsla.cours.dto.NotionRequest;
import tn.esprit.bawsla.cours.dto.TraitementResponse;
import tn.esprit.bawsla.cours.entity.AudioRevision;
import tn.esprit.bawsla.cours.entity.EtapeIA;
import tn.esprit.bawsla.cours.entity.FicheRevision;
import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.Niveau;
import tn.esprit.bawsla.cours.entity.Notion;
import tn.esprit.bawsla.cours.entity.StatutFiche;
import tn.esprit.bawsla.cours.entity.StatutTraitement;
import tn.esprit.bawsla.cours.entity.TraitementIA;
import tn.esprit.bawsla.cours.repository.AudioRevisionRepository;
import tn.esprit.bawsla.cours.repository.FicheRevisionRepository;
import tn.esprit.bawsla.cours.repository.TraitementIARepository;
import tn.esprit.bawsla.cours.service.FichierService.Fichier;
import tn.esprit.bawsla.cours.service.StockageService.Type;

/**
 * Fiche de révision d'un cours. Le formateur propriétaire (ou l'ADMIN) la génère, la corrige et la valide ;
 * l'apprenant ne voit que la dernière fiche VALIDEE. Les anciennes fiches restent dans l'historique.
 */
@Service
@Transactional(readOnly = true)
public class FicheService {

    private static final Logger log = LoggerFactory.getLogger(FicheService.class);
    /** Un traitement « actif » plus ancien que cela est considéré comme perdu et ne bloque plus un nouveau lancement. */
    private static final long DELAI_MAX_MINUTES = 15;
    private static final int MOTS_CLES_MAX = 20;

    private final CoursService coursService;
    private final SourceTexteService sourceTexte;
    private final IaGateway ia;
    private final FicheTraitement traitement;
    private final FicheEnregistrement enregistrement;
    private final FicheRevisionRepository ficheRepository;
    private final AudioRevisionRepository audioRepository;
    private final TraitementIARepository traitementRepository;
    private final StockageService stockage;

    public FicheService(CoursService coursService, SourceTexteService sourceTexte, IaGateway ia, FicheTraitement traitement,
                        FicheEnregistrement enregistrement, FicheRevisionRepository ficheRepository,
                        AudioRevisionRepository audioRepository, TraitementIARepository traitementRepository,
                        StockageService stockage) {
        this.coursService = coursService;
        this.sourceTexte = sourceTexte;
        this.ia = ia;
        this.traitement = traitement;
        this.enregistrement = enregistrement;
        this.ficheRepository = ficheRepository;
        this.audioRepository = audioRepository;
        this.traitementRepository = traitementRepository;
        this.stockage = stockage;
    }

    // ------------------------------------------------------------------ génération

    /** Prépare le suivi puis lance le traitement en arrière-plan (une fois la transaction validée). */
    @Transactional
    public TraitementResponse lancerGeneration(Long coursId, CurrentUser user, String authorization) {
        Cours cours = coursService.chargerPourEcriture(coursId, user);
        verifierAucunTraitementActif(coursId);
        String texte = sourceTexte.pour(cours); // 400 s'il n'y a rien à résumer
        Niveau niveau = cours.getNiveau() == null ? Niveau.INTERMEDIAIRE : cours.getNiveau();

        String execution = UUID.randomUUID().toString();
        List<TraitementIA> etapes = creerEtapes(coursId, execution, EtapeIA.TEXTE, EtapeIA.FICHE, EtapeIA.AUDIO);
        apresValidation(execution, coursId,
                () -> traitement.genererFiche(execution, coursId, texte, niveau, authorization));
        return TraitementResponse.from(execution, etapes);
    }

    /** Suivi du dernier lancement (formateur ou ADMIN). */
    public TraitementResponse traitement(Long coursId, CurrentUser user) {
        coursService.chargerPourEcriture(coursId, user);
        return traitementRepository.findFirstByCoursIdAndPorteeOrderByIdDesc(coursId, TraitementIA.PORTEE_FICHE)
                .map(dernier -> TraitementResponse.from(dernier.getExecution(),
                        traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, dernier.getExecution())))
                .orElseGet(TraitementResponse::aucun);
    }

    // ------------------------------------------------------------------ lecture

    /**
     * Formateur et ADMIN : la fiche la plus récente. Apprenant : la plus récente VALIDEE.
     * Renvoie null quand il n'y en a pas : ce n'est pas une erreur, le cours n'a simplement pas (encore) de fiche à montrer.
     */
    public FicheResponse fiche(Long coursId, CurrentUser user) {
        Cours cours = coursService.chargerVisible(coursId, user);
        boolean gestionnaire = estGestionnaire(user, cours);
        return trouver(coursId, gestionnaire).map(fiche -> reponse(fiche, gestionnaire)).orElse(null);
    }

    public Fichier audio(Long coursId, CurrentUser user) {
        Cours cours = coursService.chargerVisible(coursId, user);
        FicheRevision fiche = choisir(coursId, estGestionnaire(user, cours));
        AudioRevision audio = audioRepository.findFirstByFicheIdOrderByIdDesc(fiche.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Audio de la fiche", coursId));
        String mime = audio.getMime() == null ? "audio/mpeg" : audio.getMime();
        String extension = mime.contains("wav") ? ".wav" : ".mp3";
        return new Fichier(stockage.lire(Type.AUDIO, audio.getFichier()), mime, "fiche-cours-" + coursId + extension);
    }

    // ------------------------------------------------------------------ correction et validation

    /**
     * Enregistre la fiche corrigée par le formateur et recalcule son score de fidélité (sans LLM) avec le texte du cours.
     * Une fiche déjà VALIDEE n'est jamais modifiée : la correction crée une nouvelle fiche à valider, et les apprenants
     * continuent de voir l'ancienne.
     */
    @Transactional
    public FicheResponse corriger(Long coursId, FicheRequest requete, CurrentUser user, String authorization) {
        Cours cours = coursService.chargerPourEcriture(coursId, user);
        FicheRevision derniere = ficheRepository.findFirstByCoursIdOrderByIdDesc(coursId)
                .orElseThrow(() -> new ResourceNotFoundException("Fiche du cours", coursId));
        List<String> motsCles = nettoyerMotsCles(requete.motsCles());
        List<NotionIaDto> notionsIa = requete.notions().stream()
                .map(n -> new NotionIaDto(n.titre().strip(), n.definition().strip(), n.extraitSource() == null ? "" : n.extraitSource().strip()))
                .toList();

        // Sans l'IA on ne peut pas recalculer le score : on refuse plutôt que d'enregistrer une fiche au score faux.
        FideliteIaDto controle = ia.fidelite(authorization, new FideliteIaRequestDto(
                new FideliteIaRequestDto.Fiche(requete.resume().strip(), motsCles, notionsIa), sourceTexte.pour(cours)));
        if (controle.notions() == null || controle.notions().size() != notionsIa.size()) {
            throw new ServiceUnavailableException("L'IA a renvoyé un contrôle de fidélité incomplet.");
        }

        boolean nouvelle = derniere.getStatut() == StatutFiche.VALIDEE;
        FicheRevision cible = nouvelle ? copierEnteteDe(derniere) : derniere;
        boolean resumeModifie = nouvelle || !requete.resume().strip().equals(derniere.getResume());

        cible.setResume(requete.resume().strip());
        cible.getMotsCles().clear();
        cible.getMotsCles().addAll(motsCles);
        cible.getNotions().clear();
        int ordre = 1;
        for (int i = 0; i < requete.notions().size(); i++) {
            NotionRequest n = requete.notions().get(i);
            Notion notion = new Notion();
            notion.setFiche(cible);
            notion.setOrdre(ordre++);
            notion.setTitre(n.titre().strip());
            notion.setDefinition(n.definition().strip());
            notion.setExtraitSource(n.extraitSource() == null ? "" : n.extraitSource().strip());
            notion.setJustifiee(controle.notions().get(i).justifiee());
            cible.getNotions().add(notion);
        }
        cible.setScoreFidelite(controle.scoreFidelite());
        cible.getAlertes().clear();
        if (controle.alertes() != null) {
            cible.getAlertes().addAll(controle.alertes());
        }
        cible.setModifieeParFormateur(true);
        cible.setStatut(StatutFiche.GENEREE);
        cible.setDateValidation(null);
        FicheRevision enregistree = ficheRepository.save(cible);

        if (resumeModifie && !nouvelle) {
            supprimerAudios(enregistree.getId()); // l'audio ne correspond plus au résumé corrigé
        }
        return reponse(enregistree, true);
    }

    /** Valide la fiche la plus récente. Si son résumé n'a pas d'audio (fiche corrigée), l'audio est produit en arrière-plan. */
    @Transactional
    public FicheResponse valider(Long coursId, CurrentUser user, String authorization) {
        Cours cours = coursService.chargerPourEcriture(coursId, user);
        FicheRevision fiche = ficheRepository.findFirstByCoursIdOrderByIdDesc(coursId)
                .orElseThrow(() -> new ResourceNotFoundException("Fiche du cours", coursId));
        if (fiche.getStatut() != StatutFiche.VALIDEE) {
            fiche.setStatut(StatutFiche.VALIDEE);
            fiche.setDateValidation(LocalDateTime.now());
        }
        boolean sansAudio = audioRepository.findFirstByFicheIdOrderByIdDesc(fiche.getId()).isEmpty();
        if (sansAudio && !traitementActif(coursId)) {
            String execution = UUID.randomUUID().toString();
            creerEtapes(coursId, execution, EtapeIA.AUDIO);
            String resume = fiche.getResume();
            Long ficheId = fiche.getId();
            apresValidation(execution, coursId, () -> traitement.genererAudio(execution, coursId, ficheId, resume, authorization));
        }
        return reponse(fiche, true);
    }

    // ------------------------------------------------------------------ interne

    private Optional<FicheRevision> trouver(Long coursId, boolean gestionnaire) {
        return gestionnaire
                ? ficheRepository.findFirstByCoursIdOrderByIdDesc(coursId)
                : ficheRepository.findFirstByCoursIdAndStatutOrderByIdDesc(coursId, StatutFiche.VALIDEE);
    }

    private FicheRevision choisir(Long coursId, boolean gestionnaire) {
        return trouver(coursId, gestionnaire).orElseThrow(() -> new ResourceNotFoundException("Fiche du cours", coursId));
    }

    private FicheResponse reponse(FicheRevision fiche, boolean gestionnaire) {
        Optional<AudioRevision> audio = audioRepository.findFirstByFicheIdOrderByIdDesc(fiche.getId());
        boolean valideeDisponible = ficheRepository.findFirstByCoursIdAndStatutOrderByIdDesc(fiche.getCoursId(), StatutFiche.VALIDEE).isPresent();
        return FicheResponse.from(fiche, audio.isPresent(), audio.map(AudioRevision::getFournisseur).orElse(null), gestionnaire, valideeDisponible);
    }

    private static boolean estGestionnaire(CurrentUser user, Cours cours) {
        return user.isAdmin() || user.id().equals(cours.getEnseignantId());
    }

    private FicheRevision copierEnteteDe(FicheRevision source) {
        FicheRevision copie = new FicheRevision();
        copie.setCoursId(source.getCoursId());
        copie.setNiveau(source.getNiveau());
        copie.setVersionPrompt(source.getVersionPrompt());
        copie.setModele(source.getModele());
        return copie;
    }

    private void supprimerAudios(Long ficheId) {
        for (AudioRevision audio : audioRepository.findByFicheId(ficheId)) {
            stockage.supprimerApresValidation(Type.AUDIO, audio.getFichier());
            audioRepository.delete(audio);
        }
    }

    private static List<String> nettoyerMotsCles(List<String> motsCles) {
        LinkedHashMap<String, String> uniques = new LinkedHashMap<>();
        for (String mot : motsCles) {
            String propre = mot.strip();
            if (!propre.isEmpty()) {
                uniques.putIfAbsent(propre.toLowerCase(), propre);
            }
        }
        return new ArrayList<>(uniques.values()).subList(0, Math.min(MOTS_CLES_MAX, uniques.size()));
    }

    private List<TraitementIA> creerEtapes(Long coursId, String execution, EtapeIA... etapes) {
        List<TraitementIA> lignes = new ArrayList<>();
        for (EtapeIA etape : etapes) {
            lignes.add(traitementRepository.save(TraitementIA.enAttente(coursId, execution, etape)));
        }
        return lignes;
    }

    private void verifierAucunTraitementActif(Long coursId) {
        if (traitementActif(coursId)) {
            throw new ConflictException("Une génération est déjà en cours pour ce cours : attends qu'elle se termine.");
        }
    }

    private boolean traitementActif(Long coursId) {
        return traitementRepository.findFirstByCoursIdAndPorteeOrderByIdDesc(coursId, TraitementIA.PORTEE_FICHE).map(dernier -> {
            boolean recent = dernier.getDateCreation().isAfter(LocalDateTime.now().minusMinutes(DELAI_MAX_MINUTES));
            return recent && traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, dernier.getExecution())
                    .stream().anyMatch(t -> t.getStatut().actif());
        }).orElse(false);
    }

    /**
     * Lance le traitement une fois la transaction validée : l'exécuteur relit les lignes de suivi, qui doivent donc
     * être visibles. Si l'exécuteur est saturé, les étapes passent en échec avec un message clair.
     */
    private void apresValidation(String execution, Long coursId, Runnable lancement) {
        Runnable securise = () -> {
            try {
                lancement.run();
            } catch (TaskRejectedException e) {
                log.warn("Exécuteur IA saturé, traitement du cours {} refusé", coursId);
                for (TraitementIA t : traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, execution)) {
                    enregistrement.passer(execution, coursId, t.getEtape(), StatutTraitement.ECHEC,
                            "Trop de générations en cours, réessaie dans un instant.");
                }
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
