package tn.esprit.bawsla.cours.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tn.esprit.bawsla.cours.client.ExplicationIaRequestDto;
import tn.esprit.bawsla.cours.client.FideliteExplicationIaDto;
import tn.esprit.bawsla.cours.client.FideliteExplicationIaRequestDto;
import tn.esprit.bawsla.cours.client.PageIaDto;
import tn.esprit.bawsla.cours.client.SegmentIaDto;
import tn.esprit.bawsla.cours.client.TraductionIaRequestDto;
import tn.esprit.bawsla.cours.common.exception.BadRequestException;
import tn.esprit.bawsla.cours.common.exception.ConflictException;
import tn.esprit.bawsla.cours.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.cours.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.cours.common.exception.UnauthorizedException;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.CorrectionExplicationRequest;
import tn.esprit.bawsla.cours.dto.CorrectionExplicationRequest.SegmentCorrection;
import tn.esprit.bawsla.cours.dto.DisponibiliteExplicationResponse;
import tn.esprit.bawsla.cours.dto.ExplicationResponse;
import tn.esprit.bawsla.cours.dto.TraitementResponse;
import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.EtapeIA;
import tn.esprit.bawsla.cours.entity.ExplicationAudio;
import tn.esprit.bawsla.cours.entity.FicheRevision;
import tn.esprit.bawsla.cours.entity.LangueExplication;
import tn.esprit.bawsla.cours.entity.ModeExplication;
import tn.esprit.bawsla.cours.entity.Niveau;
import tn.esprit.bawsla.cours.entity.Notion;
import tn.esprit.bawsla.cours.entity.SegmentExplication;
import tn.esprit.bawsla.cours.entity.StatutFiche;
import tn.esprit.bawsla.cours.entity.StatutTraitement;
import tn.esprit.bawsla.cours.entity.TraitementIA;
import tn.esprit.bawsla.cours.repository.ExplicationAudioRepository;
import tn.esprit.bawsla.cours.repository.FicheRevisionRepository;
import tn.esprit.bawsla.cours.repository.TraitementIARepository;
import tn.esprit.bawsla.cours.service.ExplicationTraitement.Tache;
import tn.esprit.bawsla.cours.service.FichierService.Fichier;
import tn.esprit.bawsla.cours.service.SourceTexteService.PagesSource;
import tn.esprit.bawsla.cours.service.StockageService.Type;

/**
 * Explication orale d'un cours : l'IA RÉEXPLIQUE le cours (elle ne lit pas la fiche), en français ; l'anglais traduit le français
 * validé du même mode. Le formateur propriétaire (ou l'ADMIN) génère, corrige, écoute un aperçu, puis valide ; l'apprenant ne voit que
 * la dernière version VALIDEE de chaque langue et mode.
 *
 * <p>Règle d'écoute : on ne peut valider qu'une version dont un aperçu audio a été produit (« le formateur ne valide jamais sans avoir pu
 * écouter »). Si le script a été modifié depuis l'aperçu, l'empreinte du script ne correspond plus à celle de l'audio : à la validation
 * l'audio est alors refait, et l'apprenant ne reçoit l'audio qu'une fois à jour.</p>
 */
@Service
@Transactional(readOnly = true)
public class ExplicationService {

    private static final Logger log = LoggerFactory.getLogger(ExplicationService.class);
    private static final long DELAI_MAX_MINUTES = 15;
    private static final int DUREE_MIN = 2;
    private static final int DUREE_MAX = 6;
    private static final int NOTIONS_MAX = 30;

    private final CoursService coursService;
    private final SourceTexteService sourceTexte;
    private final IaGateway ia;
    private final ExplicationTraitement traitement;
    private final FicheEnregistrement suivi;
    private final ExplicationAudioRepository repository;
    private final FicheRevisionRepository ficheRepository;
    private final TraitementIARepository traitementRepository;
    private final StockageService stockage;

    public ExplicationService(CoursService coursService, SourceTexteService sourceTexte, IaGateway ia, ExplicationTraitement traitement,
                              FicheEnregistrement suivi, ExplicationAudioRepository repository, FicheRevisionRepository ficheRepository,
                              TraitementIARepository traitementRepository, StockageService stockage) {
        this.coursService = coursService;
        this.sourceTexte = sourceTexte;
        this.ia = ia;
        this.traitement = traitement;
        this.suivi = suivi;
        this.repository = repository;
        this.ficheRepository = ficheRepository;
        this.traitementRepository = traitementRepository;
        this.stockage = stockage;
    }

    // ------------------------------------------------------------------ génération

    /** Prépare le suivi puis lance le script (FR) ou la traduction (EN) en arrière-plan, une fois la transaction validée. */
    @Transactional
    public TraitementResponse lancerGeneration(Long coursId, LangueExplication langue, ModeExplication mode, int dureeMinutes,
                                               CurrentUser user, String authorization) {
        Cours cours = coursService.chargerPourEcriture(coursId, user);
        if (dureeMinutes < DUREE_MIN || dureeMinutes > DUREE_MAX) {
            throw new BadRequestException("La durée visée doit être comprise entre " + DUREE_MIN + " et " + DUREE_MAX + " minutes.");
        }
        exiger(authorization);
        String portee = TraitementIA.porteeExplication(langue, mode);
        verifierAucunTraitementActif(coursId, portee);

        // Erreur claire AVANT tout suivi et tout appel à l'IA : cours vide, PDF scanné…
        PagesSource source = sourceTexte.pagesPour(cours);
        List<String> notions = notionsDuPlan(coursId);
        Niveau niveau = cours.getNiveau() == null ? Niveau.INTERMEDIAIRE : cours.getNiveau();

        Tache tache;
        EtapeIA etapeIa;
        if (langue == LangueExplication.FR) {
            etapeIa = EtapeIA.SCRIPT;
            tache = new Tache(coursId, langue, mode,
                    new ExplicationIaRequestDto(coursId, source.pages(), notions, niveau, mode.code(), langue.code(), dureeMinutes),
                    null, null, notions.isEmpty());
        } else {
            ExplicationAudio fr = validee(coursId, LangueExplication.FR, mode).orElseThrow(() -> new BadRequestException(
                    "Valide d'abord l'explication en français (mode " + mode.code() + ") : l'anglais en est la traduction."));
            etapeIa = EtapeIA.TRADUCTION;
            tache = new Tache(coursId, langue, mode, null,
                    new TraductionIaRequestDto(versSegments(fr, true), source.pages(), notions, langue.code(), LangueExplication.FR.code(), mode.code()),
                    fr.getId(), notions.isEmpty());
        }
        String execution = UUID.randomUUID().toString();
        List<TraitementIA> etapes = creerEtapes(coursId, execution, portee, EtapeIA.TEXTE, etapeIa);
        apresValidation(coursId, execution, () -> traitement.genererScript(execution, tache, authorization));
        return TraitementResponse.from(execution, etapes);
    }

    /** Aperçu audio, à la demande du formateur : met en voix la version la plus récente (une étape AUDIO). */
    @Transactional
    public TraitementResponse apercu(Long coursId, LangueExplication langue, ModeExplication mode, CurrentUser user, String authorization) {
        coursService.chargerPourEcriture(coursId, user);
        exiger(authorization);
        ExplicationAudio e = derniere(coursId, langue, mode).orElseThrow(
                () -> new ResourceNotFoundException("Explication à écouter (génère-la d'abord)", coursId));
        String portee = TraitementIA.porteeExplication(langue, mode);
        verifierAucunTraitementActif(coursId, portee);
        return lancerAudio(coursId, portee, e.getId(), authorization);
    }

    public TraitementResponse traitement(Long coursId, LangueExplication langue, ModeExplication mode, CurrentUser user) {
        coursService.chargerPourEcriture(coursId, user);
        return traitementRepository.findFirstByCoursIdAndPorteeOrderByIdDesc(coursId, TraitementIA.porteeExplication(langue, mode))
                .map(dernier -> TraitementResponse.from(dernier.getExecution(),
                        traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, dernier.getExecution())))
                .orElseGet(TraitementResponse::aucun);
    }

    // ------------------------------------------------------------------ lecture

    /** Formateur et ADMIN : la version la plus récente. Apprenant : la plus récente VALIDEE. Null s'il n'y en a pas. */
    public ExplicationResponse explication(Long coursId, LangueExplication langue, ModeExplication mode, CurrentUser user) {
        Cours cours = coursService.chargerLisible(coursId, user);
        boolean gestionnaire = estGestionnaire(user, cours);
        return choisir(coursId, langue, mode, gestionnaire).map(e -> reponse(e, gestionnaire)).orElse(null);
    }

    /** Les combinaisons langue x mode qui existent : validées pour l'apprenant, la plus récente de chaque pour le formateur. */
    public List<DisponibiliteExplicationResponse> disponibles(Long coursId, CurrentUser user) {
        Cours cours = coursService.chargerVisible(coursId, user);
        boolean gestionnaire = estGestionnaire(user, cours);
        List<DisponibiliteExplicationResponse> liste = new ArrayList<>();
        for (LangueExplication langue : LangueExplication.values()) {
            for (ModeExplication mode : ModeExplication.values()) {
                choisir(coursId, langue, mode, gestionnaire).ifPresent(e -> liste.add(DisponibiliteExplicationResponse.from(e, gestionnaire)));
            }
        }
        return liste;
    }

    /**
     * Audio en lecture continue. Le formateur reçoit l'aperçu le plus récent ; l'apprenant, l'audio de la version validée seulement
     * s'il correspond au script validé (jamais un audio d'une ancienne version du texte).
     */
    public Fichier audio(Long coursId, LangueExplication langue, ModeExplication mode, CurrentUser user) {
        Cours cours = coursService.chargerLisible(coursId, user);
        boolean gestionnaire = estGestionnaire(user, cours);
        ExplicationAudio e = choisir(coursId, langue, mode, gestionnaire)
                .filter(x -> gestionnaire ? x.aUnAudio() : x.audioAJour())
                .orElseThrow(() -> new ResourceNotFoundException("Audio de l'explication", coursId));
        String mime = e.getAudioMime() == null ? "audio/mpeg" : e.getAudioMime();
        String nom = "explication-" + coursId + "-" + langue.code() + "-" + mode.code() + (mime.contains("wav") ? ".wav" : ".mp3");
        return new Fichier(stockage.lire(Type.AUDIO, e.getAudioFichier()), mime, nom);
    }

    // ------------------------------------------------------------------ correction et validation

    /**
     * Enregistre le texte corrigé par le formateur et recalcule le score de fidélité (sans LLM) avec le texte du cours. Une version déjà
     * VALIDEE n'est jamais modifiée : la correction crée une nouvelle version à valider, et les apprenants gardent l'ancienne. Une
     * version corrigée n'a plus d'aperçu à jour : il faut le refaire (ou valider, ce qui le refait).
     */
    @Transactional
    public ExplicationResponse corriger(Long coursId, LangueExplication langue, ModeExplication mode, CorrectionExplicationRequest requete,
                                        CurrentUser user, String authorization) {
        Cours cours = coursService.chargerPourEcriture(coursId, user);
        exiger(authorization);
        ExplicationAudio derniere = derniere(coursId, langue, mode)
                .orElseThrow(() -> new ResourceNotFoundException("Explication", coursId));

        Map<Integer, SegmentCorrection> corrections = new HashMap<>();
        for (SegmentCorrection c : requete.segments()) {
            if (corrections.put(c.ordre(), c) != null) {
                throw new BadRequestException("Le segment " + c.ordre() + " est présent deux fois.");
            }
        }
        if (corrections.size() != derniere.getSegments().size()
                || derniere.getSegments().stream().anyMatch(s -> !corrections.containsKey(s.getOrdre()))) {
            throw new BadRequestException("La correction doit contenir exactement les " + derniere.getSegments().size()
                    + " segments du script : on peut modifier leur texte, mais ni en ajouter ni en supprimer.");
        }

        List<SegmentIaDto> modifies = derniere.getSegments().stream().map(s -> {
            SegmentCorrection c = corrections.get(s.getOrdre());
            return new SegmentIaDto(s.getOrdre(), s.getType().code(), s.getLocuteur().code(), c.texte().strip(), s.getNotion(),
                    c.extraitSource() == null ? null : c.extraitSource().strip(), s.getPage(), null, null);
        }).toList();
        PagesSource source = sourceTexte.pagesPour(cours);
        // Sans l'IA on ne peut pas recalculer le score : on refuse plutôt que d'enregistrer un script au score faux.
        FideliteExplicationIaDto controle = ia.fideliteExplication(authorization,
                new FideliteExplicationIaRequestDto(modifies, source.pages(), notionsDuPlan(coursId)));
        if (controle.segments() == null || controle.segments().size() != modifies.size()) {
            throw new ServiceUnavailableException("L'IA a renvoyé un contrôle de fidélité incomplet.");
        }

        boolean nouvelle = derniere.getStatut() == StatutFiche.VALIDEE;
        ExplicationAudio cible = nouvelle ? copierEnteteDe(derniere) : derniere;
        if (nouvelle) {
            for (SegmentExplication s : derniere.getSegments()) {
                cible.getSegments().add(s.copierPour(cible));
            }
        }
        Map<Integer, SegmentIaDto> verdicts = new HashMap<>();
        controle.segments().forEach(s -> verdicts.put(s.ordre(), s));
        for (SegmentExplication s : cible.getSegments()) {
            SegmentCorrection c = corrections.get(s.getOrdre());
            SegmentIaDto verdict = verdicts.get(s.getOrdre());
            s.setTexte(c.texte().strip());
            s.setExtraitSource(c.extraitSource() == null || c.extraitSource().isBlank() ? null : c.extraitSource().strip());
            if (verdict != null) {
                s.setJustifiee(verdict.justifiee() == null || verdict.justifiee());
                s.setPage(verdict.page());
                s.setAnalogie(Boolean.TRUE.equals(verdict.analogie()));
            }
            if (nouvelle) {
                s.setDebutMs(null);
                s.setFinMs(null);
            }
        }
        cible.setScoreFidelite(controle.scoreFidelite());
        cible.getAlertes().clear();
        if (controle.alertes() != null) {
            cible.getAlertes().addAll(controle.alertes());
        }
        cible.setNbMots(controle.nbMots());
        cible.setDureeEstimeeSecondes(controle.dureeEstimeeSecondes());
        cible.setModifieeParFormateur(true);
        cible.setStatut(StatutFiche.GENEREE);
        cible.setDateValidation(null);
        cible.setEmpreinteScript(cible.calculerEmpreinte());
        return reponse(repository.save(cible), true);
    }

    /**
     * Valide la version la plus récente. Il faut qu'un aperçu audio ait été produit (409 sinon). Si le texte a changé depuis cet aperçu
     * (empreinte différente), l'audio est refait en arrière-plan. Valider le français renvoie à GENEREE l'anglais validé du même mode.
     */
    @Transactional
    public ExplicationResponse valider(Long coursId, LangueExplication langue, ModeExplication mode, CurrentUser user, String authorization) {
        coursService.chargerPourEcriture(coursId, user);
        exiger(authorization);
        ExplicationAudio e = derniere(coursId, langue, mode).orElseThrow(() -> new ResourceNotFoundException("Explication", coursId));
        if (e.getStatut() == StatutFiche.VALIDEE) {
            return reponse(e, true);
        }
        if (!e.aUnAudio()) {
            throw new ConflictException("Écoute d'abord un aperçu : clique sur « Écouter un aperçu » avant de valider, "
                    + "pour entendre ce que les apprenants entendront.");
        }
        e.setStatut(StatutFiche.VALIDEE);
        e.setDateValidation(LocalDateTime.now());
        if (langue == LangueExplication.FR) {
            renvoyerLaTraductionAGeneree(coursId, mode);
        }
        String portee = TraitementIA.porteeExplication(langue, mode);
        if (!e.audioAJour() && !traitementActif(coursId, portee)) {
            lancerAudio(coursId, portee, e.getId(), authorization); // texte modifié depuis l'aperçu : l'audio diffusé est refait
        }
        return reponse(e, true);
    }

    // ------------------------------------------------------------------ interne

    private void renvoyerLaTraductionAGeneree(Long coursId, ModeExplication mode) {
        validee(coursId, LangueExplication.EN, mode).ifPresent(en -> {
            en.setStatut(StatutFiche.GENEREE);
            en.setDateValidation(null);
            en.setARetraduire(true);
            en.getAlertes().add(0, "Le français a été validé de nouveau : cette traduction est à refaire (clique sur « Régénérer »).");
        });
    }

    private TraitementResponse lancerAudio(Long coursId, String portee, Long explicationId, String authorization) {
        String execution = UUID.randomUUID().toString();
        List<TraitementIA> etapes = creerEtapes(coursId, execution, portee, EtapeIA.AUDIO);
        apresValidation(coursId, execution, () -> traitement.produireAudio(execution, coursId, explicationId, authorization));
        return TraitementResponse.from(execution, etapes);
    }

    private Optional<ExplicationAudio> derniere(Long coursId, LangueExplication langue, ModeExplication mode) {
        return repository.findFirstByCoursIdAndLangueAndModeOrderByIdDesc(coursId, langue, mode);
    }

    private Optional<ExplicationAudio> validee(Long coursId, LangueExplication langue, ModeExplication mode) {
        return repository.findFirstByCoursIdAndLangueAndModeAndStatutOrderByIdDesc(coursId, langue, mode, StatutFiche.VALIDEE);
    }

    private Optional<ExplicationAudio> choisir(Long coursId, LangueExplication langue, ModeExplication mode, boolean gestionnaire) {
        return gestionnaire ? derniere(coursId, langue, mode) : validee(coursId, langue, mode);
    }

    private ExplicationResponse reponse(ExplicationAudio e, boolean gestionnaire) {
        return ExplicationResponse.from(e, gestionnaire, validee(e.getCoursId(), e.getLangue(), e.getMode()).isPresent());
    }

    private static boolean estGestionnaire(CurrentUser user, Cours cours) {
        return user.isAdmin() || user.id().equals(cours.getEnseignantId());
    }

    /** Titres des notions de la dernière fiche VALIDEE : le plan. Jamais leur contenu. Vide si le cours n'a pas de fiche validée. */
    private List<String> notionsDuPlan(Long coursId) {
        return ficheRepository.findFirstByCoursIdAndStatutOrderByIdDesc(coursId, StatutFiche.VALIDEE)
                .map(FicheRevision::getNotions).orElse(List.of()).stream()
                .map(Notion::getTitre).filter(t -> t != null && !t.isBlank()).limit(NOTIONS_MAX).toList();
    }

    private static List<SegmentIaDto> versSegments(ExplicationAudio e, boolean avecExtrait) {
        return e.getSegments().stream()
                .map(s -> new SegmentIaDto(s.getOrdre(), s.getType().code(), s.getLocuteur().code(), s.getTexte(), s.getNotion(),
                        avecExtrait ? s.getExtraitSource() : null, avecExtrait ? s.getPage() : null, null, null))
                .toList();
    }

    private ExplicationAudio copierEnteteDe(ExplicationAudio source) {
        ExplicationAudio copie = new ExplicationAudio();
        copie.setCoursId(source.getCoursId());
        copie.setLangue(source.getLangue());
        copie.setMode(source.getMode());
        copie.setVersionPrompt(source.getVersionPrompt());
        copie.setModele(source.getModele());
        copie.setSourceId(source.getSourceId());
        copie.setNotionsChoisiesParIa(source.isNotionsChoisiesParIa());
        return copie;
    }

    private List<TraitementIA> creerEtapes(Long coursId, String execution, String portee, EtapeIA... etapes) {
        List<TraitementIA> lignes = new ArrayList<>();
        for (EtapeIA etape : etapes) {
            lignes.add(traitementRepository.save(TraitementIA.enAttente(coursId, execution, etape, portee)));
        }
        return lignes;
    }

    private void verifierAucunTraitementActif(Long coursId, String portee) {
        if (traitementActif(coursId, portee)) {
            throw new ConflictException("Un traitement est déjà en cours pour cette explication : attends qu'il se termine.");
        }
    }

    private boolean traitementActif(Long coursId, String portee) {
        return traitementRepository.findFirstByCoursIdAndPorteeOrderByIdDesc(coursId, portee).map(dernier -> {
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

    /** Lance la tâche une fois la transaction validée (l'exécuteur relit les lignes de suivi). Saturé : les étapes passent en échec. */
    private void apresValidation(Long coursId, String execution, Runnable lancement) {
        Runnable securise = () -> {
            try {
                lancement.run();
            } catch (TaskRejectedException e) {
                log.warn("Exécuteur IA saturé, explication du cours {} refusée", coursId);
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
