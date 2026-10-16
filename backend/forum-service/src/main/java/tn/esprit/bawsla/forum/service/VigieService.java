package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import tn.esprit.bawsla.forum.client.ReformulerIaDto;
import tn.esprit.bawsla.forum.client.RepondreIaDto;
import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.exception.ConflictException;
import tn.esprit.bawsla.forum.common.exception.ForbiddenException;
import tn.esprit.bawsla.forum.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.SourceVigie;
import tn.esprit.bawsla.forum.dto.VigieAVerifier;
import tn.esprit.bawsla.forum.dto.VigieFilResponse;
import tn.esprit.bawsla.forum.dto.VigieResponse;
import tn.esprit.bawsla.forum.entity.AvisVigie;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.ParametresForumCours;
import tn.esprit.bawsla.forum.entity.ReponseIa;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.ModeVigie;
import tn.esprit.bawsla.forum.entity.enums.NiveauReponseIa;
import tn.esprit.bawsla.forum.entity.enums.RaisonReputation;
import tn.esprit.bawsla.forum.entity.enums.StatutReponseIa;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;
import tn.esprit.bawsla.forum.entity.enums.TypeNotification;
import tn.esprit.bawsla.forum.evenement.DiscussionCreee;
import tn.esprit.bawsla.forum.repository.AvisVigieRepository;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.ReponseIaRepository;
import tn.esprit.bawsla.forum.service.AccesService.Contexte;
import tn.esprit.bawsla.forum.service.AnnuaireService.CoursInfo;
import tn.esprit.bawsla.forum.service.FilService.Fil;
import tn.esprit.bawsla.forum.service.RagService.PassageRetenu;
import tn.esprit.bawsla.forum.service.RagService.Recuperation;

/**
 * La Vigie dans les fils (plan §4.2) : à la publication d'une QUESTION, réponse asynchrone sourcée, ou abstention
 * (« Le cours ne traite pas ce point ») sans appeler le LLM si rien n'est assez pertinent. Jamais d'invention.
 * <ul>
 *   <li>Le formateur du cours valide, corrige (l'original est gardé) ou rejette ; une réponse validée ou corrigée
 *       enrichit la base de connaissances du forum.</li>
 *   <li>Niveau 3 « réponse générale » : uniquement sur demande de l'apprenant après une abstention, jamais en mode
 *       examen, désactivable par cours et sur toute la plateforme ; le formateur en est prévenu (contenu manquant).</li>
 *   <li>Mode examen : réponse en indices « Guide-moi » ; les apprenants ne voient pas la solution complète.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class VigieService {

    private static final Logger log = LoggerFactory.getLogger(VigieService.class);
    public static final String ABSTENTION = "Le cours ne traite pas ce point. J'ai prévenu le formateur du cours.";
    static final double SEUIL_LEXICAL = 0.5;
    static final int DEBUT_QUESTION = 1200;
    static final double CARACTERES_PAR_JETON = 3.5;

    /** Résultat d'une génération, à recopier dans une ReponseIa (fil ou assistant privé). */
    public record Production(StatutReponseIa statut, NiveauReponseIa niveau, String texte, List<SourceVigie> sources,
                             List<String> indices, Integer confiance, String versionPrompt, String fournisseur,
                             int jetonsEstimes) {
    }

    private final ReponseIaRepository reponseIaRepository;
    private final DiscussionRepository discussionRepository;
    private final AvisVigieRepository avisRepository;
    private final RagService rag;
    private final IndexationService indexation;
    private final IaService ia;
    private final ParametresService parametres;
    private final AccesService acces;
    private final FilService fils;
    private final AnnuaireService annuaire;
    private final NotificationService notifications;
    private final ReputationService reputations;
    private final JournalService journal;
    private final BaseConnaissanceService baseConnaissance;
    private final RechercheService recherche;
    private final VigieMapper mapper;
    private final ApresValidation apresValidation;
    private final TransactionTemplate transaction;
    private final Clock horloge;

    public VigieService(ReponseIaRepository reponseIaRepository, DiscussionRepository discussionRepository,
                        AvisVigieRepository avisRepository, RagService rag, IndexationService indexation, IaService ia,
                        ParametresService parametres, AccesService acces, FilService fils, AnnuaireService annuaire,
                        NotificationService notifications, ReputationService reputations, JournalService journal,
                        BaseConnaissanceService baseConnaissance, RechercheService recherche, VigieMapper mapper,
                        ApresValidation apresValidation, PlatformTransactionManager transactions, Clock horloge) {
        this.reponseIaRepository = reponseIaRepository;
        this.discussionRepository = discussionRepository;
        this.avisRepository = avisRepository;
        this.rag = rag;
        this.indexation = indexation;
        this.ia = ia;
        this.parametres = parametres;
        this.acces = acces;
        this.fils = fils;
        this.annuaire = annuaire;
        this.notifications = notifications;
        this.reputations = reputations;
        this.journal = journal;
        this.baseConnaissance = baseConnaissance;
        this.recherche = recherche;
        this.mapper = mapper;
        this.apresValidation = apresValidation;
        this.transaction = Transactions.nouvelle(transactions);
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ Génération dans un fil

    /** Nouvelle question : réponse « en attente » dans la même transaction, génération après validation. */
    @EventListener
    @Transactional
    public void discussionCreee(DiscussionCreee e) {
        if (e.type() != TypeDiscussion.QUESTION || reponseIaRepository.existsByDiscussionId(e.discussionId())
                || !parametres.cours(e.coursId()).isVigieActive()) {
            return;
        }
        lancerGeneration(e.discussionId(), e.coursId());
    }

    @Transactional
    public VigieFilResponse regenerer(Long discussionId, CurrentUser user) {
        Fil fil = fils.charger(discussionId, user);
        acces.exigerModerateur(fil.ctx());
        if (fil.discussion().getType() != TypeDiscussion.QUESTION) {
            throw new BadRequestException("La Vigie ne répond qu'aux questions");
        }
        lancerGeneration(discussionId, fil.discussion().getCoursId());
        return fil(discussionId, user);
    }

    private void lancerGeneration(Long discussionId, Long coursId) {
        ReponseIa r = new ReponseIa();
        r.setDiscussionId(discussionId);
        r.setCoursId(coursId);
        r.setNiveau(NiveauReponseIa.COURS);
        r.setMode(modeExamen(coursId) ? ModeVigie.GUIDEE : ModeVigie.DIRECTE);
        reponseIaRepository.save(r);
        Long id = r.getId();
        apresValidation.lancer(() -> generer(id));
    }

    /** Génère la réponse d'un fil (tâche de fond) ; ne lève jamais : un échec donne le statut ERREUR. */
    void generer(Long reponseIaId) {
        long debut = System.currentTimeMillis();
        ReponseIa attente = transaction.execute(s -> reponseIaRepository.findById(reponseIaId).orElse(null));
        Discussion d = attente == null ? null
                : transaction.execute(s -> discussionRepository.findById(attente.getDiscussionId()).orElse(null));
        if (d == null) {
            return;
        }
        String question = d.getTitre() + "\n" + Textes.extrait(d.getContenu(), DEBUT_QUESTION);
        try {
            Production p = produire(d.getCoursId(), question, List.of(), attente.getMode());
            transaction.executeWithoutResult(s -> reponseIaRepository.findById(reponseIaId).ifPresent(r -> {
                appliquer(r, p, System.currentTimeMillis() - debut);
                if (p.statut() == StatutReponseIa.HORS_COURS) {
                    prevenirFormateur(d, TypeNotification.HORS_COURS, d.getTitre());
                }
                notifications.notifier(d.getAuteurId(), TypeNotification.VIGIE_PRETE, d.getId(), null, null,
                        p.statut() == StatutReponseIa.HORS_COURS ? ABSTENTION : p.texte());
            }));
        } catch (RuntimeException e) {
            log.warn("La Vigie n'a pas pu répondre au fil {} : {}", d.getId(), e.getMessage());
            transaction.executeWithoutResult(s -> reponseIaRepository.findById(reponseIaId).ifPresent(r -> {
                r.setStatut(StatutReponseIa.ERREUR);
                r.setErreur(Textes.extrait(e.getMessage(), 290));
                r.setDureeMs(System.currentTimeMillis() - debut);
            }));
        }
    }

    /**
     * Récupération, seuil de pertinence, appel à l'ai-service et mise en forme des sources. Partagé avec l'assistant
     * privé. Lève ServiceUnavailableException si l'IA ne répond pas.
     */
    public Production produire(Long coursId, String question,
                               List<ReformulerIaDto.Message> historique, ModeVigie mode) {
        indexation.assurerIndex(coursId);
        Recuperation rec = rag.recuperer(coursId, question, historique);
        double seuil = rec.semantique() ? parametres.decimal(ParametresService.SEUIL_PERTINENCE) : SEUIL_LEXICAL;
        int jetonsQuestion = jetons(question);
        if (rec.passages().isEmpty() || rec.meilleurScore() < seuil) {
            return new Production(StatutReponseIa.HORS_COURS, NiveauReponseIa.COURS, ABSTENTION, List.of(), List.of(),
                    0, null, null, jetonsQuestion);
        }
        CoursInfo cours = annuaire.cours(coursId).orElse(new CoursInfo(coursId, "Cours n° " + coursId, null, false));
        List<RepondreIaDto.Passage> passages = rec.passages().stream()
                .map(p -> new RepondreIaDto.Passage(p.id(), p.origine().name(), cours.titre(), p.titreSection(),
                        p.page(), p.texte(), p.score()))
                .toList();
        RepondreIaDto.Reponse r = ia.repondre(new RepondreIaDto.Requete(rec.requete(),
                mode == ModeVigie.GUIDEE ? "GUIDEE" : "DIRECTE", passages));
        int jetons = jetonsQuestion + passages.stream().mapToInt(p -> jetons(p.texte())).sum()
                + jetons(r.reponse()) + (r.indices() == null ? 0 : r.indices().stream().mapToInt(this::jetons).sum());
        if (r.horsCours()) {
            return new Production(StatutReponseIa.HORS_COURS, NiveauReponseIa.COURS, ABSTENTION, List.of(), List.of(),
                    0, r.versionPrompt(), r.fournisseur(), jetons);
        }
        List<SourceVigie> sources = new ArrayList<>();
        for (RepondreIaDto.Source s : r.sources()) {
            PassageRetenu p = rec.passages().get(s.numero() - 1);
            sources.add(new SourceVigie(s.numero(), p.origine(), coursId, cours.titre(), p.chapitreId(),
                    p.titreSection(), p.page(), s.passage(), s.citation(), p.discussionId(), lien(coursId, p)));
        }
        NiveauReponseIa niveau = sources.stream().anyMatch(s -> s.origine() == NiveauReponseIa.COURS)
                ? NiveauReponseIa.COURS : NiveauReponseIa.FORUM;
        return new Production(StatutReponseIa.GENEREE, niveau, r.reponse(), sources,
                r.indices() == null ? List.of() : r.indices(), r.confiance(), r.versionPrompt(), r.fournisseur(),
                jetons);
    }

    void appliquer(ReponseIa r, Production p, long dureeMs) {
        r.setStatut(p.statut());
        r.setNiveau(p.niveau());
        r.setTexte(p.texte());
        r.setSourcesJson(mapper.ecrire(p.sources()));
        r.setIndicesJson(p.indices().isEmpty() ? null : mapper.ecrire(p.indices()));
        r.setConfiance(p.statut() == StatutReponseIa.GENEREE ? p.confiance() : null);
        r.setVersionPrompt(p.versionPrompt());
        r.setFournisseur(p.fournisseur());
        r.setJetonsEstimes(p.jetonsEstimes());
        r.setDureeMs(dureeMs);
        r.setErreur(null);
    }

    // ------------------------------------------------------------------ Lecture

    public VigieFilResponse fil(Long discussionId, CurrentUser user) {
        Fil fil = fils.charger(discussionId, user);
        Contexte ctx = fil.ctx();
        boolean examen = modeExamen(fil.discussion().getCoursId());
        Optional<ReponseIa> principale = reponseIaRepository.principales(discussionId, NiveauReponseIa.GENERAL)
                .stream().findFirst();
        Optional<ReponseIa> generale = reponseIaRepository
                .findByDiscussionIdAndNiveauOrderByCreeLeDescIdDesc(discussionId, NiveauReponseIa.GENERAL).stream()
                .findFirst();
        VigieResponse p = principale.map(r -> mapper.vers(r, ctx.moderateur(), examen, monAvis(r, user),
                r.getStatut() == StatutReponseIa.GENEREE || r.getStatut() == StatutReponseIa.VALIDEE
                        || r.getStatut() == StatutReponseIa.CORRIGEE ? recherche.liees(discussionId, user) : List.of()))
                .orElse(null);
        VigieResponse g = generale.map(r -> mapper.vers(r, ctx.moderateur(), examen, monAvis(r, user), List.of()))
                .orElse(null);
        boolean disponible = g == null && principale.map(r -> r.getStatut() == StatutReponseIa.HORS_COURS)
                .orElse(false) && generaleAutorisee(fil.discussion().getCoursId()) == null;
        return new VigieFilResponse(p, g, disponible);
    }

    /**
     * Réponses de La Vigie des fils d'un cours, pour son formateur ou l'admin (la plus récente de chaque niveau par
     * fil). filtre : A_VERIFIER (générées non validées, faible confiance d'abord, hors cours, erreurs), VALIDEE,
     * CORRIGEE, REJETEE ou TOUTES.
     */
    public List<VigieAVerifier> duCours(Long coursId, String filtre, CurrentUser user) {
        acces.exigerModerateur(acces.contexte(user, coursId));
        String f = filtre == null || filtre.isBlank() ? "A_VERIFIER" : filtre.toUpperCase();
        java.util.Set<String> vues = new java.util.HashSet<>();
        List<ReponseIa> retenues = reponseIaRepository.findByCoursIdAndDiscussionIdIsNotNullOrderByCreeLeDescIdDesc(
                        coursId).stream()
                .filter(r -> vues.add(r.getDiscussionId() + ":" + (r.getNiveau() == NiveauReponseIa.GENERAL)))
                .filter(r -> switch (f) {
                    case "A_VERIFIER" -> r.getStatut() == StatutReponseIa.GENEREE
                            || r.getStatut() == StatutReponseIa.HORS_COURS || r.getStatut() == StatutReponseIa.ERREUR;
                    case "VALIDEE", "CORRIGEE", "REJETEE" -> r.getStatut().name().equals(f);
                    case "TOUTES" -> true;
                    default -> throw new BadRequestException("Filtre inconnu : " + filtre);
                })
                .sorted(java.util.Comparator.comparingInt((ReponseIa r) -> priorite(r))
                        .thenComparing(ReponseIa::getCreeLe, java.util.Comparator.reverseOrder()))
                .toList();
        Map<Long, String> titres = discussionRepository.findAllById(retenues.stream().map(ReponseIa::getDiscussionId)
                        .distinct().toList()).stream()
                .collect(java.util.stream.Collectors.toMap(Discussion::getId, Discussion::getTitre));
        boolean examen = modeExamen(coursId);
        return retenues.stream().filter(r -> titres.containsKey(r.getDiscussionId()))
                .map(r -> new VigieAVerifier(r.getDiscussionId(), titres.get(r.getDiscussionId()),
                        mapper.vers(r, true, examen, null, List.of())))
                .toList();
    }

    /** Ordre de l'onglet « à vérifier » : erreurs, faible confiance, hors cours, puis le reste. */
    private static int priorite(ReponseIa r) {
        if (r.getStatut() == StatutReponseIa.ERREUR) {
            return 0;
        }
        if (r.getStatut() == StatutReponseIa.GENEREE && r.getConfiance() != null && r.getConfiance() < 50) {
            return 1;
        }
        return r.getStatut() == StatutReponseIa.HORS_COURS ? 2 : 3;
    }

    // ------------------------------------------------------------------ Avis et décisions du formateur

    /** « Utile / Pas utile » : un avis par personne, modifiable ; +1 point à l'auteur de la question qui la juge utile. */
    @Transactional
    public VigieResponse avis(Long reponseIaId, boolean utile, CurrentUser user) {
        ReponseIa r = lisible(reponseIaId, user);
        AvisVigie avis = avisRepository.findByUserIdAndReponseIaId(user.id(), reponseIaId).orElse(null);
        if (avis == null) {
            avis = new AvisVigie();
            avis.setUserId(user.id());
            avis.setReponseIaId(reponseIaId);
        } else if (avis.isUtile() == utile) {
            return vue(r, user);
        } else if (avis.isUtile()) {
            r.setUtileOui(r.getUtileOui() - 1);
        } else {
            r.setUtileNon(r.getUtileNon() - 1);
        }
        avis.setUtile(utile);
        avisRepository.save(avis);
        if (utile) {
            r.setUtileOui(r.getUtileOui() + 1);
        } else {
            r.setUtileNon(r.getUtileNon() + 1);
        }
        if (r.getDiscussionId() != null) {
            discussionRepository.findById(r.getDiscussionId())
                    .filter(d -> d.getAuteurId().equals(user.id()))
                    .ifPresent(d -> {
                        if (utile) {
                            reputations.annuler(user.id(), RaisonReputation.VIGIE_UTILE, CibleType.DISCUSSION,
                                    d.getId(), d.getCoursId());
                            reputations.attribuer(user.id(), RaisonReputation.VIGIE_UTILE.getPoints(),
                                    RaisonReputation.VIGIE_UTILE, CibleType.DISCUSSION, d.getId(), d.getCoursId());
                        } else {
                            reputations.annuler(user.id(), RaisonReputation.VIGIE_UTILE, CibleType.DISCUSSION,
                                    d.getId(), d.getCoursId());
                        }
                    });
        }
        return vue(r, user);
    }

    @Transactional
    public VigieResponse valider(Long reponseIaId, CurrentUser user) {
        ReponseIa r = decidable(reponseIaId, user);
        r.setStatut(StatutReponseIa.VALIDEE);
        r.setCorrigeeParId(user.id());
        r.setCorrigeeLe(LocalDateTime.now(horloge));
        journal.tracer(user, ActionModeration.VALIDER_VIGIE, "REPONSE_IA", reponseIaId, r.getCoursId(), null);
        baseConnaissance.ajouterVigie(r);
        return vue(r, user);
    }

    @Transactional
    public VigieResponse corriger(Long reponseIaId, String texte, String motif, CurrentUser user) {
        ReponseIa r = decidable(reponseIaId, user);
        r.setStatut(StatutReponseIa.CORRIGEE);
        r.setTexteCorrige(texte.trim());
        r.setCorrigeeParId(user.id());
        r.setCorrigeeLe(LocalDateTime.now(horloge));
        journal.tracer(user, ActionModeration.CORRIGER_VIGIE, "REPONSE_IA", reponseIaId, r.getCoursId(), motif);
        baseConnaissance.ajouterVigie(r);
        return vue(r, user);
    }

    @Transactional
    public VigieResponse rejeter(Long reponseIaId, String motif, CurrentUser user) {
        ReponseIa r = decidable(reponseIaId, user);
        r.setStatut(StatutReponseIa.REJETEE);
        r.setCorrigeeParId(user.id());
        r.setCorrigeeLe(LocalDateTime.now(horloge));
        journal.tracer(user, ActionModeration.REJETER_VIGIE, "REPONSE_IA", reponseIaId, r.getCoursId(), motif);
        return vue(r, user);
    }

    // ------------------------------------------------------------------ Niveau 3 : réponse générale

    /** Sur demande de l'apprenant, seulement après une abstention ; renvoie la réponse existante si déjà demandée. */
    @Transactional
    public VigieResponse general(Long discussionId, CurrentUser user) {
        Fil fil = fils.charger(discussionId, user);
        Discussion d = fil.discussion();
        String refus = generaleAutorisee(d.getCoursId());
        if (refus != null) {
            throw new ForbiddenException(refus);
        }
        Optional<ReponseIa> existante = reponseIaRepository
                .findByDiscussionIdAndNiveauOrderByCreeLeDescIdDesc(discussionId, NiveauReponseIa.GENERAL).stream()
                .findFirst();
        if (existante.isPresent()) {
            return vue(existante.get(), user);
        }
        boolean abstention = reponseIaRepository.principales(discussionId, NiveauReponseIa.GENERAL).stream()
                .findFirst().map(r -> r.getStatut() == StatutReponseIa.HORS_COURS).orElse(false);
        if (!abstention) {
            throw new ConflictException("La réponse générale n'est proposée que si le cours ne traite pas ce point");
        }
        ReponseIa r = generer(d.getCoursId(), d.getTitre() + "\n" + Textes.extrait(d.getContenu(), DEBUT_QUESTION));
        r.setDiscussionId(discussionId);
        reponseIaRepository.save(r);
        prevenirFormateur(d, TypeNotification.DEMANDE_GENERALE, d.getTitre());
        return vue(r, user);
    }

    /** Réponse générale (sans source ni confiance) ; l'appelant renseigne le fil ou la conversation. */
    ReponseIa generer(Long coursId, String question) {
        long debut = System.currentTimeMillis();
        RepondreIaDto.ReponseGenerale g = ia.repondreGeneral(question);
        ReponseIa r = new ReponseIa();
        r.setCoursId(coursId);
        r.setNiveau(NiveauReponseIa.GENERAL);
        r.setMode(ModeVigie.GENERAL);
        r.setStatut(StatutReponseIa.GENEREE);
        r.setTexte(g.reponse());
        r.setIndicesJson(mapper.ecrire(Map.of("incertitudes", nonNul(g.incertitudes()), "aVerifier",
                nonNul(g.aVerifier()))));
        r.setVersionPrompt(g.versionPrompt());
        r.setFournisseur(g.fournisseur());
        r.setJetonsEstimes(jetons(question) + jetons(g.reponse()));
        r.setDureeMs(System.currentTimeMillis() - debut);
        return r;
    }

    /** null si la réponse générale est permise dans ce cours, sinon la raison du refus. */
    public String generaleAutorisee(Long coursId) {
        if (!parametres.booleen(ParametresService.REPONSE_GENERALE)) {
            return "La réponse générale est désactivée sur la plateforme";
        }
        ParametresForumCours p = parametres.cours(coursId);
        if (parametres.modeExamenActif(p)) {
            return "Mode examen : la réponse générale est désactivée";
        }
        if (!p.isReponseGeneraleAutorisee()) {
            return "Le formateur a désactivé la réponse générale pour ce cours";
        }
        return null;
    }

    public boolean modeExamen(Long coursId) {
        return parametres.modeExamenActif(parametres.cours(coursId));
    }

    // ------------------------------------------------------------------ Aides

    /** Réponse de La Vigie lisible par l'utilisateur : celle d'un fil qu'il voit, ou de SA conversation privée. */
    ReponseIa lisible(Long reponseIaId, CurrentUser user) {
        acces.exigerConnecte(user);
        ReponseIa r = reponseIaRepository.findById(reponseIaId)
                .orElseThrow(() -> new ResourceNotFoundException("Réponse de La Vigie", reponseIaId));
        if (r.getDiscussionId() != null) {
            Fil fil = fils.charger(r.getDiscussionId(), user);
            if (!fil.ctx().moderateur() && r.getStatut() == StatutReponseIa.REJETEE) {
                throw new ResourceNotFoundException("Réponse de La Vigie", reponseIaId);
            }
        }
        return r;
    }

    private ReponseIa decidable(Long reponseIaId, CurrentUser user) {
        ReponseIa r = lisible(reponseIaId, user);
        if (r.getDiscussionId() == null) {
            throw new BadRequestException("Seules les réponses publiées dans un fil se valident");
        }
        acces.exigerModerateur(acces.contexte(user, r.getCoursId()));
        if (r.getStatut() == StatutReponseIa.EN_ATTENTE || r.getStatut() == StatutReponseIa.ERREUR
                || r.getStatut() == StatutReponseIa.HORS_COURS) {
            throw new BadRequestException("Il n'y a pas de réponse à valider ou corriger");
        }
        return r;
    }

    VigieResponse vue(ReponseIa r, CurrentUser user) {
        boolean moderateur = annuaire.cours(r.getCoursId()).map(c -> acces.estModerateur(user, c)).orElse(false);
        return mapper.vers(r, moderateur, modeExamen(r.getCoursId()), monAvis(r, user), List.of());
    }

    private Boolean monAvis(ReponseIa r, CurrentUser user) {
        return avisRepository.findByUserIdAndReponseIaId(user.id(), r.getId()).map(AvisVigie::isUtile).orElse(null);
    }

    private void prevenirFormateur(Discussion d, TypeNotification type, String extrait) {
        annuaire.cours(d.getCoursId()).map(CoursInfo::enseignantId)
                .ifPresent(formateur -> notifications.notifier(formateur, type, d.getId(), null, null, extrait));
    }

    static String lien(Long coursId, PassageRetenu p) {
        if (p.origine() == NiveauReponseIa.FORUM) {
            return "/app/forum/" + p.discussionId();
        }
        StringBuilder lien = new StringBuilder("/app/cours/").append(coursId).append("/lecture");
        String sep = "?";
        if (p.chapitreId() != null) {
            lien.append(sep).append("chapitre=").append(p.chapitreId());
            sep = "&";
        }
        if (p.page() != null) {
            lien.append(sep).append("page=").append(p.page());
        }
        return lien.toString();
    }

    private int jetons(String texte) {
        return texte == null ? 0 : (int) Math.ceil(texte.length() / CARACTERES_PAR_JETON);
    }

    private static List<String> nonNul(List<String> liste) {
        return liste == null ? List.of() : liste;
    }
}
