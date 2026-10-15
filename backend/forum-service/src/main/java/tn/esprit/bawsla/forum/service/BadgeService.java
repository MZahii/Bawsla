package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.dto.BadgeResponse;
import tn.esprit.bawsla.forum.entity.BadgeObtenu;
import tn.esprit.bawsla.forum.entity.Reputation;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.CodeBadge;
import tn.esprit.bawsla.forum.entity.enums.StatutSignalement;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;
import tn.esprit.bawsla.forum.evenement.CommentaireCree;
import tn.esprit.bawsla.forum.evenement.DiscussionCreee;
import tn.esprit.bawsla.forum.evenement.ReponseAcceptee;
import tn.esprit.bawsla.forum.evenement.ReponseApprouvee;
import tn.esprit.bawsla.forum.evenement.ReponseCreee;
import tn.esprit.bawsla.forum.evenement.VoteModifie;
import tn.esprit.bawsla.forum.repository.BadgeObtenuRepository;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.ReponseRepository;
import tn.esprit.bawsla.forum.repository.SignalementRepository;

/**
 * Les 10 badges du forum (plan §3.6), attribués une seule fois chacun, avec une notification.
 * La progression vers chaque badge est recalculée à la demande (page « Mon activité »).
 * Un badge obtenu reste acquis, même si la cause disparaît ensuite (vote retiré, réponse supprimée).
 */
@Service
@Transactional
public class BadgeService {

    static final int JOURS_ASSIDU = 7;
    static final long HEURES_SECOURISTE = 24;

    private final BadgeObtenuRepository badgeRepository;
    private final DiscussionRepository discussionRepository;
    private final ReponseRepository reponseRepository;
    private final SignalementRepository signalementRepository;
    private final ReputationService reputations;
    private final NotificationService notifications;
    private final Clock horloge;

    public BadgeService(BadgeObtenuRepository badgeRepository, DiscussionRepository discussionRepository,
                        ReponseRepository reponseRepository, SignalementRepository signalementRepository,
                        ReputationService reputations, NotificationService notifications, Clock horloge) {
        this.badgeRepository = badgeRepository;
        this.discussionRepository = discussionRepository;
        this.reponseRepository = reponseRepository;
        this.signalementRepository = signalementRepository;
        this.reputations = reputations;
        this.notifications = notifications;
        this.horloge = horloge;
    }

    public void attribuer(Long userId, CodeBadge code) {
        if (userId == null || badgeRepository.existsByUserIdAndCode(userId, code)) {
            return;
        }
        BadgeObtenu b = new BadgeObtenu();
        b.setUserId(userId);
        b.setCode(code);
        b.setObtenuLe(LocalDateTime.now(horloge));
        badgeRepository.save(b);
        notifications.badge(userId, code);
    }

    /** Éclaireur : le membre a trouvé sa réponse dans les questions similaires au lieu de republier. */
    public void eclaireur(Long userId) {
        attribuer(userId, CodeBadge.ECLAIREUR);
    }

    /** Gardien : 3 signalements confirmés (appelé par la modération, lot 6). */
    public void verifierGardien(Long userId) {
        if (signalementRepository.countByAuteurIdAndStatut(userId, StatutSignalement.CONFIRME)
                >= CodeBadge.GARDIEN.getObjectif()) {
            attribuer(userId, CodeBadge.GARDIEN);
        }
    }

    /** Jours d'activité consécutifs (publication, réponse ou commentaire) ; 7 d'affilée donnent « Assidu ». */
    public void enregistrerActivite(Long userId) {
        Reputation r = reputations.reputation(userId);
        LocalDate aujourdhui = LocalDate.now(horloge);
        LocalDate dernier = r.getDernierJourActif();
        if (aujourdhui.equals(dernier)) {
            return;
        }
        boolean suite = dernier != null && dernier.plusDays(1).equals(aujourdhui);
        r.setJoursActifsConsecutifs(suite ? r.getJoursActifsConsecutifs() + 1 : 1);
        r.setDernierJourActif(aujourdhui);
        if (r.getJoursActifsConsecutifs() >= JOURS_ASSIDU) {
            attribuer(userId, CodeBadge.ASSIDU);
        }
    }

    // ------------------------------------------------------------------ Événements

    @EventListener
    public void discussionCreee(DiscussionCreee e) {
        enregistrerActivite(e.auteurId());
        if (e.type() == TypeDiscussion.QUESTION) {
            attribuer(e.auteurId(), CodeBadge.PREMIERE_QUESTION);
        }
    }

    @EventListener
    public void reponseCreee(ReponseCreee e) {
        enregistrerActivite(e.auteurId());
        attribuer(e.auteurId(), CodeBadge.PREMIERE_REPONSE);
        LocalDateTime limite = LocalDateTime.now(horloge).minusHours(HEURES_SECOURISTE);
        if (e.questionSansReponseDepuis() != null && e.questionSansReponseDepuis().isBefore(limite)
                && !e.auteurId().equals(e.auteurDiscussionId())) {
            attribuer(e.auteurId(), CodeBadge.SECOURISTE);
        }
    }

    @EventListener
    public void commentaireCree(CommentaireCree e) {
        enregistrerActivite(e.auteurId());
    }

    @EventListener
    public void voteModifie(VoteModifie e) {
        if (e.cibleType() != CibleType.DISCUSSION || e.nouvelleValeur() != 1) {
            return;
        }
        discussionRepository.findById(e.cibleId())
                .filter(d -> d.getType() == TypeDiscussion.QUESTION
                        && d.getScore() >= CodeBadge.BONNE_QUESTION.getObjectif())
                .ifPresent(d -> attribuer(d.getAuteurId(), CodeBadge.BONNE_QUESTION));
    }

    @EventListener
    public void reponseAcceptee(ReponseAcceptee e) {
        if (e.reponseId() == null || e.auteurReponseId().equals(e.auteurDiscussionId())) {
            return;
        }
        attribuer(e.auteurReponseId(), CodeBadge.REPONSE_ACCEPTEE);
        if (reponseRepository.countByAuteurIdAndAccepteeTrueAndSupprimeeLeIsNull(e.auteurReponseId())
                >= CodeBadge.PEDAGOGUE.getObjectif()) {
            attribuer(e.auteurReponseId(), CodeBadge.PEDAGOGUE);
        }
    }

    @EventListener
    public void reponseApprouvee(ReponseApprouvee e) {
        if (e.approuvee()) {
            attribuer(e.auteurReponseId(), CodeBadge.APPROUVE_FORMATEUR);
        }
    }

    // ------------------------------------------------------------------ Progression

    @Transactional(readOnly = true)
    public List<BadgeResponse> progression(Long userId) {
        Map<CodeBadge, BadgeObtenu> obtenus = badgeRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(BadgeObtenu::getCode, Function.identity()));
        Map<CodeBadge, Integer> avancement = new EnumMap<>(CodeBadge.class);
        int acceptees = (int) reponseRepository.countByAuteurIdAndAccepteeTrueAndSupprimeeLeIsNull(userId);
        avancement.put(CodeBadge.PREMIERE_QUESTION,
                (int) discussionRepository.countByAuteurIdAndTypeAndSupprimeeLeIsNull(userId, TypeDiscussion.QUESTION));
        avancement.put(CodeBadge.PREMIERE_REPONSE, (int) reponseRepository.countByAuteurIdAndSupprimeeLeIsNull(userId));
        avancement.put(CodeBadge.BONNE_QUESTION,
                discussionRepository.meilleurScore(userId, TypeDiscussion.QUESTION));
        avancement.put(CodeBadge.REPONSE_ACCEPTEE, acceptees);
        avancement.put(CodeBadge.APPROUVE_FORMATEUR,
                (int) reponseRepository.countByAuteurIdAndApprouveeParIdIsNotNullAndSupprimeeLeIsNull(userId));
        avancement.put(CodeBadge.PEDAGOGUE, acceptees);
        avancement.put(CodeBadge.ASSIDU, reputations.reputation(userId).getJoursActifsConsecutifs());
        avancement.put(CodeBadge.GARDIEN,
                (int) signalementRepository.countByAuteurIdAndStatut(userId, StatutSignalement.CONFIRME));

        return Arrays.stream(CodeBadge.values()).map(code -> {
            BadgeObtenu b = obtenus.get(code);
            int objectif = code.getObjectif();
            int fait = b != null ? objectif : Math.clamp(avancement.getOrDefault(code, 0), 0, objectif);
            return new BadgeResponse(code, code.getLibelle(), code.getDescription(), b != null,
                    b == null ? null : b.getObtenuLe(), fait, objectif);
        }).toList();
    }
}
