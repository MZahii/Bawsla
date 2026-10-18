package tn.esprit.bawsla.cours.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tn.esprit.bawsla.cours.common.exception.BadRequestException;
import tn.esprit.bawsla.cours.common.exception.ConflictException;
import tn.esprit.bawsla.cours.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.cours.common.exception.UnauthorizedException;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.ResumeLocalResponse;
import tn.esprit.bawsla.cours.dto.TraitementResponse;
import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.EtapeIA;
import tn.esprit.bawsla.cours.entity.ResumeLocal;
import tn.esprit.bawsla.cours.entity.StatutResumeLocal;
import tn.esprit.bawsla.cours.entity.StatutTraitement;
import tn.esprit.bawsla.cours.entity.TraitementIA;
import tn.esprit.bawsla.cours.repository.ResumeLocalRepository;
import tn.esprit.bawsla.cours.repository.TraitementIARepository;
import tn.esprit.bawsla.cours.service.FichierService.Fichier;
import tn.esprit.bawsla.cours.service.SourceTexteService.PagesSource;
import tn.esprit.bawsla.cours.service.StockageService.Type;

/**
 * Résumé automatique LOCAL d'un cours (modèle Hugging Face sur CPU ou phrases clés, sans clé API). Le formateur propriétaire (ou l'ADMIN)
 * génère puis valide ; il ne peut ni éditer ni corriger, seulement régénérer. L'apprenant ne voit que la dernière version VALIDE,
 * sans score ni alertes.
 */
@Service
@Transactional(readOnly = true)
public class ResumeLocalService {

    private static final Logger log = LoggerFactory.getLogger(ResumeLocalService.class);
    private static final long DELAI_MAX_MINUTES = 20;
    private static final Set<String> LONGUEURS = Set.of("courte", "moyenne", "longue");
    private static final Set<String> MODES = Set.of("auto", "abstractif", "extractif");

    private final CoursService coursService;
    private final SourceTexteService sourceTexte;
    private final ResumeLocalTraitement traitement;
    private final FicheEnregistrement suivi;
    private final ResumeLocalRepository repository;
    private final TraitementIARepository traitementRepository;
    private final StockageService stockage;

    public ResumeLocalService(CoursService coursService, SourceTexteService sourceTexte, ResumeLocalTraitement traitement,
                              FicheEnregistrement suivi, ResumeLocalRepository repository,
                              TraitementIARepository traitementRepository, StockageService stockage) {
        this.coursService = coursService;
        this.sourceTexte = sourceTexte;
        this.traitement = traitement;
        this.suivi = suivi;
        this.repository = repository;
        this.traitementRepository = traitementRepository;
        this.stockage = stockage;
    }

    // ------------------------------------------------------------------ génération

    /** Prépare le suivi puis lance le résumé en arrière-plan, une fois la transaction validée. */
    @Transactional
    public TraitementResponse generer(Long coursId, String longueur, String mode, CurrentUser user, String authorization) {
        Cours cours = coursService.chargerPourEcriture(coursId, user);
        String longueurChoisie = valeur(longueur, "moyenne", LONGUEURS, "La longueur doit être courte, moyenne ou longue.");
        String modeChoisi = valeur(mode, "auto", MODES, "Le mode doit être auto, abstractif ou extractif.");
        exiger(authorization);
        if (traitementActif(coursId)) {
            throw new ConflictException("Un résumé automatique est déjà en cours pour ce cours : attends qu'il se termine.");
        }
        // Erreur claire AVANT tout suivi et tout appel à l'IA : cours vide, PDF scanné…
        PagesSource source = sourceTexte.pagesPour(cours);

        String execution = UUID.randomUUID().toString();
        List<TraitementIA> etapes = creerEtapes(coursId, execution, EtapeIA.TEXTE, EtapeIA.RESUME, EtapeIA.AUDIO);
        apresValidation(coursId, execution,
                () -> traitement.generer(execution, coursId, source.pages(), longueurChoisie, modeChoisi, authorization));
        return TraitementResponse.from(execution, etapes);
    }

    /** Suivi du dernier lancement (étapes TEXTE, RESUME, AUDIO). */
    public TraitementResponse traitement(Long coursId, CurrentUser user) {
        coursService.chargerPourEcriture(coursId, user);
        return traitementRepository.findFirstByCoursIdAndPorteeOrderByIdDesc(coursId, TraitementIA.PORTEE_RESUME_LOCAL)
                .map(dernier -> TraitementResponse.from(dernier.getExecution(),
                        traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, dernier.getExecution())))
                .orElseGet(TraitementResponse::aucun);
    }

    // ------------------------------------------------------------------ lecture

    /** Formateur et ADMIN : la version la plus récente. Apprenant : la plus récente VALIDE. Null s'il n'y en a pas. */
    public ResumeLocalResponse resume(Long coursId, CurrentUser user) {
        Cours cours = coursService.chargerLisible(coursId, user);
        boolean gestionnaire = estGestionnaire(user, cours);
        return choisir(coursId, gestionnaire).map(r -> reponse(r, gestionnaire)).orElse(null);
    }

    public Fichier audio(Long coursId, CurrentUser user) {
        Cours cours = coursService.chargerLisible(coursId, user);
        ResumeLocal r = choisir(coursId, estGestionnaire(user, cours)).filter(ResumeLocal::aUnAudio)
                .orElseThrow(() -> new ResourceNotFoundException("Audio du résumé local", coursId));
        String mime = r.getMime() == null ? "audio/mpeg" : r.getMime();
        return new Fichier(stockage.lire(Type.AUDIO, r.getCheminAudio()), mime,
                "resume-cours-" + coursId + (mime.contains("wav") ? ".wav" : ".mp3"));
    }

    // ------------------------------------------------------------------ validation

    /** Rend la version la plus récente visible des apprenants. Si elle n'a pas d'audio (échec ou panne), il est produit en arrière-plan. */
    @Transactional
    public ResumeLocalResponse valider(Long coursId, CurrentUser user, String authorization) {
        coursService.chargerPourEcriture(coursId, user);
        exiger(authorization);
        ResumeLocal r = repository.findFirstByCoursIdOrderByIdDesc(coursId)
                .orElseThrow(() -> new ResourceNotFoundException("Résumé local", coursId));
        if (r.getStatut() != StatutResumeLocal.VALIDE) {
            r.setStatut(StatutResumeLocal.VALIDE);
            r.setDateValidation(LocalDateTime.now());
        }
        // Pas d'audio (échec ou panne de la synthèse vocale pendant la génération) : valider le relance en arrière-plan.
        if (!r.aUnAudio() && !traitementActif(coursId)) {
            String execution = UUID.randomUUID().toString();
            creerEtapes(coursId, execution, EtapeIA.AUDIO);
            Long id = r.getId();
            apresValidation(coursId, execution, () -> traitement.produireAudio(execution, coursId, id, authorization));
        }
        return reponse(r, true);
    }

    // ------------------------------------------------------------------ interne

    private Optional<ResumeLocal> choisir(Long coursId, boolean gestionnaire) {
        return gestionnaire
                ? repository.findFirstByCoursIdOrderByIdDesc(coursId)
                : repository.findFirstByCoursIdAndStatutOrderByIdDesc(coursId, StatutResumeLocal.VALIDE);
    }

    private ResumeLocalResponse reponse(ResumeLocal r, boolean gestionnaire) {
        boolean validee = repository.findFirstByCoursIdAndStatutOrderByIdDesc(r.getCoursId(), StatutResumeLocal.VALIDE).isPresent();
        return ResumeLocalResponse.from(r, gestionnaire, validee);
    }

    private static boolean estGestionnaire(CurrentUser user, Cours cours) {
        return user.isAdmin() || user.id().equals(cours.getEnseignantId());
    }

    private static String valeur(String demandee, String defaut, Set<String> permises, String message) {
        String v = demandee == null || demandee.isBlank() ? defaut : demandee.strip().toLowerCase();
        if (!permises.contains(v)) {
            throw new BadRequestException(message);
        }
        return v;
    }

    private List<TraitementIA> creerEtapes(Long coursId, String execution, EtapeIA... etapes) {
        List<TraitementIA> lignes = new ArrayList<>();
        for (EtapeIA etape : etapes) {
            lignes.add(traitementRepository.save(TraitementIA.enAttente(coursId, execution, etape, TraitementIA.PORTEE_RESUME_LOCAL)));
        }
        return lignes;
    }

    private boolean traitementActif(Long coursId) {
        return traitementRepository.findFirstByCoursIdAndPorteeOrderByIdDesc(coursId, TraitementIA.PORTEE_RESUME_LOCAL).map(dernier -> {
            boolean recent = dernier.getDateCreation().isAfter(LocalDateTime.now().minusMinutes(DELAI_MAX_MINUTES));
            return recent && traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, dernier.getExecution())
                    .stream().anyMatch(t -> t.getStatut().actif());
        }).orElse(false);
    }

    private static void exiger(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new UnauthorizedException("Session introuvable : reconnecte-toi puis réessaie.");
        }
    }

    /** Lance la tâche une fois la transaction validée. Exécuteur saturé : les étapes passent en échec avec un message lisible. */
    private void apresValidation(Long coursId, String execution, Runnable lancement) {
        Runnable securise = () -> {
            try {
                lancement.run();
            } catch (TaskRejectedException e) {
                log.warn("Exécuteur IA saturé, résumé local du cours {} refusé", coursId);
                for (TraitementIA t : traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, execution)) {
                    suivi.passer(execution, coursId, t.getEtape(), StatutTraitement.ECHEC, "Trop de traitements en cours, réessaie dans un instant.");
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
