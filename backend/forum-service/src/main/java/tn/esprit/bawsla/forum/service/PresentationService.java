package tn.esprit.bawsla.forum.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.AuteurResponse;
import tn.esprit.bawsla.forum.dto.CommentaireResponse;
import tn.esprit.bawsla.forum.dto.DiscussionDetail;
import tn.esprit.bawsla.forum.dto.DiscussionResume;
import tn.esprit.bawsla.forum.dto.DroitsResponse;
import tn.esprit.bawsla.forum.dto.PieceJointeResponse;
import tn.esprit.bawsla.forum.dto.ReactionsResponse;
import tn.esprit.bawsla.forum.dto.ReponseResponse;
import tn.esprit.bawsla.forum.entity.Commentaire;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.Etiquette;
import tn.esprit.bawsla.forum.entity.Lecture;
import tn.esprit.bawsla.forum.entity.PieceJointe;
import tn.esprit.bawsla.forum.entity.Reaction;
import tn.esprit.bawsla.forum.entity.Reponse;
import tn.esprit.bawsla.forum.entity.Reputation;
import tn.esprit.bawsla.forum.entity.Vote;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.NiveauReputation;
import tn.esprit.bawsla.forum.entity.enums.StatutReponseIa;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;
import tn.esprit.bawsla.forum.entity.enums.TypeReaction;
import tn.esprit.bawsla.forum.repository.AbonnementRepository;
import tn.esprit.bawsla.forum.repository.CommentaireRepository;
import tn.esprit.bawsla.forum.repository.FavoriRepository;
import tn.esprit.bawsla.forum.repository.LectureRepository;
import tn.esprit.bawsla.forum.repository.PieceJointeRepository;
import tn.esprit.bawsla.forum.repository.ReactionRepository;
import tn.esprit.bawsla.forum.repository.ReponseIaRepository;
import tn.esprit.bawsla.forum.repository.ReponseRepository;
import tn.esprit.bawsla.forum.repository.ReputationRepository;
import tn.esprit.bawsla.forum.repository.VoteRepository;
import tn.esprit.bawsla.forum.service.AccesService.Contexte;
import tn.esprit.bawsla.forum.service.AnnuaireService.CoursInfo;
import tn.esprit.bawsla.forum.service.AnnuaireService.MembreInfo;

/**
 * Construit les réponses de l'API à partir des entités, en appliquant les règles d'affichage :
 * <ul>
 *   <li>anonymat : l'auteurId d'un message anonyme n'est montré qu'à son auteur, au formateur du cours et à l'admin ;</li>
 *   <li>masquage : le contenu d'un message masqué n'est montré qu'à son auteur et aux modérateurs ;</li>
 *   <li>droits : calculés pour l'affichage, les services les revérifient à chaque action.</li>
 * </ul>
 * Les données liées (votes, réactions, lectures…) sont chargées par lots pour éviter une requête par élément.
 */
@Service
@Transactional(readOnly = true)
public class PresentationService {

    public static final String URL_PIECES_JOINTES = "/api/forum/pieces-jointes/";
    private static final int LONGUEUR_EXTRAIT = 200;
    private static final Set<StatutReponseIa> VIGIE_VISIBLE = EnumSet.of(StatutReponseIa.GENEREE,
            StatutReponseIa.VALIDEE, StatutReponseIa.CORRIGEE);

    private final AnnuaireService annuaire;
    private final AccesService acces;
    private final ReputationRepository reputationRepository;
    private final ReponseRepository reponseRepository;
    private final CommentaireRepository commentaireRepository;
    private final VoteRepository voteRepository;
    private final ReactionRepository reactionRepository;
    private final LectureRepository lectureRepository;
    private final AbonnementRepository abonnementRepository;
    private final FavoriRepository favoriRepository;
    private final PieceJointeRepository pieceJointeRepository;
    private final ReponseIaRepository reponseIaRepository;

    public PresentationService(AnnuaireService annuaire, AccesService acces,
                               ReputationRepository reputationRepository, ReponseRepository reponseRepository,
                               CommentaireRepository commentaireRepository, VoteRepository voteRepository,
                               ReactionRepository reactionRepository, LectureRepository lectureRepository,
                               AbonnementRepository abonnementRepository, FavoriRepository favoriRepository,
                               PieceJointeRepository pieceJointeRepository, ReponseIaRepository reponseIaRepository) {
        this.annuaire = annuaire;
        this.acces = acces;
        this.reputationRepository = reputationRepository;
        this.reponseRepository = reponseRepository;
        this.commentaireRepository = commentaireRepository;
        this.voteRepository = voteRepository;
        this.reactionRepository = reactionRepository;
        this.lectureRepository = lectureRepository;
        this.abonnementRepository = abonnementRepository;
        this.favoriRepository = favoriRepository;
        this.pieceJointeRepository = pieceJointeRepository;
        this.reponseIaRepository = reponseIaRepository;
    }

    // ------------------------------------------------------------------ Auteurs

    /** Résout les auteurs d'une même réponse API une seule fois chacun (nom, réputation). */
    public final class Auteurs {

        private final CurrentUser lecteur;
        private final Map<Long, MembreInfo> membres = new HashMap<>();
        private final Map<Long, Optional<Reputation>> reputations = new HashMap<>();

        private Auteurs(CurrentUser lecteur) {
            this.lecteur = lecteur;
        }

        /** null si auteurId est null ; masqué si le message est anonyme et le lecteur n'a pas le droit de voir. */
        public AuteurResponse de(Long auteurId, boolean anonyme, CoursInfo cours, boolean lecteurModerateur) {
            if (auteurId == null) {
                return null;
            }
            boolean visible = !anonyme || lecteurModerateur || auteurId.equals(lecteur.id());
            if (!visible) {
                return AuteurResponse.masque();
            }
            MembreInfo membre = membres.computeIfAbsent(auteurId, annuaire::membre);
            Optional<Reputation> reputation = reputations.computeIfAbsent(auteurId, reputationRepository::findById);
            int points = reputation.map(Reputation::getPoints).orElse(0);
            NiveauReputation niveau = reputation.map(Reputation::getNiveau).orElse(NiveauReputation.MOUSSE);
            boolean formateur = cours != null && auteurId.equals(cours.enseignantId());
            return new AuteurResponse(auteurId, membre.nom(), membre.role(), niveau, niveau.getLibelle(), points,
                    anonyme, formateur);
        }
    }

    public Auteurs auteurs(CurrentUser lecteur) {
        return new Auteurs(lecteur);
    }

    // ------------------------------------------------------------------ Liste

    public List<DiscussionResume> resumes(List<Discussion> discussions, CurrentUser user) {
        return resumes(discussions, user, Map.of());
    }

    /** Résumés avec le score de pertinence de chaque fil (recherche), absent s'il n'y en a pas. */
    public List<DiscussionResume> resumes(List<Discussion> discussions, CurrentUser user,
                                          Map<Long, Double> pertinences) {
        if (discussions.isEmpty()) {
            return List.of();
        }
        List<Long> ids = discussions.stream().map(Discussion::getId).toList();
        Map<Long, Lecture> lectures = lectureRepository.findByUserIdAndDiscussionIdIn(user.id(), ids).stream()
                .collect(Collectors.toMap(Lecture::getDiscussionId, l -> l));
        Set<Long> approuvees = new HashSet<>(reponseRepository.discussionsAvecApprobation(ids));
        Set<Long> avecVigie = new HashSet<>(reponseIaRepository.discussionsAvecStatut(ids, VIGIE_VISIBLE));
        Map<Long, CoursInfo> cours = new HashMap<>();
        Map<Long, Boolean> moderation = new HashMap<>();
        Auteurs auteurs = auteurs(user);

        List<DiscussionResume> resultat = new ArrayList<>();
        for (Discussion d : discussions) {
            CoursInfo c = cours.computeIfAbsent(d.getCoursId(), this::coursOuDegrade);
            boolean moderateur = moderation.computeIfAbsent(d.getCoursId(), id -> acces.estModerateur(user, c));
            Lecture lecture = lectures.get(d.getId());
            boolean nonLue = !user.id().equals(d.getDernierActeurId())
                    && (lecture == null || d.getDerniereActiviteLe().isAfter(lecture.getDernierLuLe()));
            resultat.add(new DiscussionResume(d.getId(), d.getCoursId(), c.titre(), d.getChapitreId(), d.getPage(),
                    d.getType(), d.getTitre(), Textes.extrait(d.getContenu(), LONGUEUR_EXTRAIT), d.getStatut(),
                    d.isEpinglee(), d.isVerrouillee(), d.isFaq(), d.getScore(), d.getNbReponses(), d.getNbVues(),
                    libelles(d), auteurs.de(d.getAuteurId(), d.isAnonyme(), c, moderateur),
                    auteurs.de(d.getDernierActeurId(), false, c, moderateur), d.getDerniereActiviteLe(),
                    d.getCreeLe(), nonLue, approuvees.contains(d.getId()), avecVigie.contains(d.getId()),
                    pertinences.get(d.getId())));
        }
        return resultat;
    }

    // ------------------------------------------------------------------ Fil complet

    public DiscussionDetail detail(Discussion d, Contexte ctx) {
        CurrentUser user = ctx.user();
        Auteurs auteurs = auteurs(user);
        List<Reponse> reponses = new ArrayList<>(reponseRepository.findByDiscussionIdAndSupprimeeLeIsNull(d.getId()));
        reponses.sort(ORDRE_REPONSES);
        List<Long> reponseIds = reponses.stream().map(Reponse::getId).toList();

        Map<Long, Integer> votesReponses = mesVotes(user, CibleType.REPONSE, reponseIds);
        int voteDiscussion = mesVotes(user, CibleType.DISCUSSION, List.of(d.getId())).getOrDefault(d.getId(), 0);
        Map<Long, List<Reaction>> reactions = reponseIds.isEmpty() ? Map.of()
                : reactionRepository.findByReponseIdIn(reponseIds).stream()
                        .collect(Collectors.groupingBy(Reaction::getReponseId));
        Map<Long, List<CommentaireResponse>> commentaires = new HashMap<>();
        List<CommentaireResponse> commentairesQuestion = new ArrayList<>();
        for (Commentaire c : commentaireRepository.findByDiscussionIdAndSupprimeLeIsNullOrderByCreeLeAsc(d.getId())) {
            CommentaireResponse r = commentaire(c, ctx, auteurs);
            if (c.getReponseId() == null) {
                commentairesQuestion.add(r);
            } else {
                commentaires.computeIfAbsent(c.getReponseId(), k -> new ArrayList<>()).add(r);
            }
        }
        DroitsResponse droitsFil = droits(d, ctx);
        List<ReponseResponse> reponsesApi = reponses.stream()
                .map(r -> reponse(r, d, ctx, auteurs, votesReponses.getOrDefault(r.getId(), 0),
                        reactions.getOrDefault(r.getId(), List.of()),
                        commentaires.getOrDefault(r.getId(), List.of())))
                .toList();
        List<PieceJointeResponse> pieces = pieceJointeRepository.findByDiscussionId(d.getId()).stream()
                .filter(p -> p.getReponseId() == null).map(PresentationService::pieceJointe).toList();

        return new DiscussionDetail(d.getId(), d.getCoursId(), ctx.cours().titre(), d.getChapitreId(), d.getPage(),
                d.getType(), d.getTitre(), d.getContenu(), d.getStatut(), d.isEpinglee(), d.isVerrouillee(),
                d.isMasquee(), d.isFaq(), d.getReponseAccepteeId(), d.getFusionneeDansId(), d.getScore(),
                voteDiscussion, d.getNbReponses(), d.getNbVues(), d.getNbAbonnes(), libelles(d),
                auteurs.de(d.getAuteurId(), d.isAnonyme(), ctx.cours(), ctx.moderateur()), d.getCreeLe(),
                d.getModifieLe(), d.getDerniereActiviteLe(),
                abonnementRepository.existsByUserIdAndDiscussionId(user.id(), d.getId()),
                favoriRepository.existsByUserIdAndDiscussionId(user.id(), d.getId()), droitsFil, reponsesApi,
                commentairesQuestion, pieces);
    }

    /** Acceptée d'abord, puis approuvées par le formateur, puis votes, puis la plus ancienne. */
    private static final Comparator<Reponse> ORDRE_REPONSES = Comparator
            .comparing(Reponse::isAcceptee).reversed()
            .thenComparing(r -> r.getApprouveeParId() != null, Comparator.reverseOrder())
            .thenComparing(Reponse::getScore, Comparator.reverseOrder())
            .thenComparing(Reponse::getCreeLe);

    public ReponseResponse reponse(Reponse r, Discussion d, Contexte ctx, Auteurs auteurs, int monVote,
                                   List<Reaction> reactions, List<CommentaireResponse> commentaires) {
        boolean auteur = r.getAuteurId().equals(ctx.user().id());
        boolean contenuVisible = !r.isMasquee() || auteur || ctx.moderateur();
        boolean modifiable = !d.isVerrouillee() || ctx.moderateur();
        DroitsResponse droits = new DroitsResponse(auteur && modifiable, auteur || ctx.moderateur(), false,
                peutAccepter(d, ctx), ctx.moderateur());
        return new ReponseResponse(r.getId(), r.getDiscussionId(), contenuVisible ? r.getContenu() : null,
                auteurs.de(r.getAuteurId(), r.isAnonyme(), ctx.cours(), ctx.moderateur()), r.getScore(), monVote,
                r.isAcceptee(), r.getApprouveeParId() != null,
                auteurs.de(r.getApprouveeParId(), false, ctx.cours(), ctx.moderateur()), r.getApprouveeLe(),
                r.isMasquee(), compteurs(reactions), miennes(reactions, ctx.user()), commentaires, r.getCreeLe(),
                r.getModifieLe(), droits);
    }

    public ReponseResponse reponseSeule(Reponse r, Discussion d, Contexte ctx) {
        List<Reaction> reactions = reactionRepository.findByReponseId(r.getId());
        int monVote = mesVotes(ctx.user(), CibleType.REPONSE, List.of(r.getId())).getOrDefault(r.getId(), 0);
        Auteurs auteurs = auteurs(ctx.user());
        List<CommentaireResponse> commentaires = commentaireRepository
                .findByDiscussionIdAndSupprimeLeIsNullOrderByCreeLeAsc(d.getId()).stream()
                .filter(c -> r.getId().equals(c.getReponseId())).map(c -> commentaire(c, ctx, auteurs)).toList();
        return reponse(r, d, ctx, auteurs, monVote, reactions, commentaires);
    }

    public CommentaireResponse commentaire(Commentaire c, Contexte ctx, Auteurs auteurs) {
        boolean auteur = c.getAuteurId().equals(ctx.user().id());
        boolean visible = !c.isMasque() || auteur || ctx.moderateur();
        return new CommentaireResponse(c.getId(), c.getDiscussionId(), c.getReponseId(),
                visible ? c.getContenu() : null, auteurs.de(c.getAuteurId(), false, ctx.cours(), ctx.moderateur()),
                c.isMasque(), c.getCreeLe(), c.getModifieLe(), auteur);
    }

    public ReactionsResponse reactions(Long reponseId, CurrentUser user) {
        List<Reaction> reactions = reactionRepository.findByReponseId(reponseId);
        return new ReactionsResponse(reponseId, compteurs(reactions), miennes(reactions, user));
    }

    public static PieceJointeResponse pieceJointe(PieceJointe p) {
        return new PieceJointeResponse(p.getId(), URL_PIECES_JOINTES + p.getId(), p.getType(), p.getTaille());
    }

    // ------------------------------------------------------------------ Droits

    public DroitsResponse droits(Discussion d, Contexte ctx) {
        boolean auteur = d.getAuteurId().equals(ctx.user().id());
        boolean ouvertAuxReponses = (!d.isVerrouillee() && d.getFusionneeDansId() == null) || ctx.moderateur();
        return new DroitsResponse((auteur || ctx.user().isAdmin()) && (!d.isVerrouillee() || ctx.moderateur()),
                auteur || ctx.moderateur(), ouvertAuxReponses, peutAccepter(d, ctx), ctx.moderateur());
    }

    public static boolean peutAccepter(Discussion d, Contexte ctx) {
        return d.getType() == TypeDiscussion.QUESTION
                && (d.getAuteurId().equals(ctx.user().id()) || ctx.moderateur());
    }

    // ------------------------------------------------------------------ Utilitaires

    public CoursInfo coursOuDegrade(Long coursId) {
        return annuaire.cours(coursId).orElse(new CoursInfo(coursId, "Cours n° " + coursId, null, false));
    }

    private Map<Long, Integer> mesVotes(CurrentUser user, CibleType type, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return voteRepository.findByUserIdAndCibleTypeAndCibleIdIn(user.id(), type, ids).stream()
                .collect(Collectors.toMap(Vote::getCibleId, Vote::getValeur));
    }

    private static Map<TypeReaction, Long> compteurs(List<Reaction> reactions) {
        Map<TypeReaction, Long> compteurs = new EnumMap<>(TypeReaction.class);
        reactions.forEach(r -> compteurs.merge(r.getType(), 1L, Long::sum));
        return compteurs;
    }

    private static Set<TypeReaction> miennes(List<Reaction> reactions, CurrentUser user) {
        Set<TypeReaction> miennes = EnumSet.noneOf(TypeReaction.class);
        reactions.stream().filter(r -> r.getUserId().equals(user.id())).forEach(r -> miennes.add(r.getType()));
        return miennes;
    }

    private static List<String> libelles(Discussion d) {
        return d.getEtiquettes().stream().map(Etiquette::getLibelle).sorted().toList();
    }
}
