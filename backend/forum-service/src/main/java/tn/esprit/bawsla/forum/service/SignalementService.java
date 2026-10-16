package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.exception.ConflictException;
import tn.esprit.bawsla.forum.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.DecisionRequest;
import tn.esprit.bawsla.forum.dto.SignalementRequest;
import tn.esprit.bawsla.forum.dto.SignalementResponse;
import tn.esprit.bawsla.forum.entity.Signalement;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;
import tn.esprit.bawsla.forum.entity.enums.CategorieModeration;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.StatutSignalement;
import tn.esprit.bawsla.forum.entity.enums.TypeNotification;
import tn.esprit.bawsla.forum.repository.CommentaireRepository;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.ReponseRepository;
import tn.esprit.bawsla.forum.repository.SignalementRepository;
import tn.esprit.bawsla.forum.service.AccesService.Contexte;
import tn.esprit.bawsla.forum.service.AnnuaireService.CoursInfo;
import tn.esprit.bawsla.forum.service.FilService.Fil;
import tn.esprit.bawsla.forum.service.PresentationService.Auteurs;

/**
 * Signalements (plan §3.7) : faits par les membres ou par l'IA (modération automatique), tranchés par le formateur
 * DU cours ou l'admin, qui peut aussi les escalader à l'administration. Une décision s'applique à tous les
 * signalements ouverts du même message ; chaque signalement confirmé compte pour le badge Gardien de son auteur.
 */
@Service
@Transactional(readOnly = true)
public class SignalementService {

    private final SignalementRepository signalementRepository;
    private final DiscussionRepository discussionRepository;
    private final ReponseRepository reponseRepository;
    private final CommentaireRepository commentaireRepository;
    private final FilService fils;
    private final AccesService acces;
    private final ModerationFilService moderationFil;
    private final PresentationService presentation;
    private final NotificationService notifications;
    private final BadgeService badges;
    private final JournalService journal;
    private final Clock horloge;

    public SignalementService(SignalementRepository signalementRepository, DiscussionRepository discussionRepository,
                              ReponseRepository reponseRepository, CommentaireRepository commentaireRepository,
                              FilService fils, AccesService acces, ModerationFilService moderationFil,
                              PresentationService presentation, NotificationService notifications,
                              BadgeService badges, JournalService journal, Clock horloge) {
        this.signalementRepository = signalementRepository;
        this.discussionRepository = discussionRepository;
        this.reponseRepository = reponseRepository;
        this.commentaireRepository = commentaireRepository;
        this.fils = fils;
        this.acces = acces;
        this.moderationFil = moderationFil;
        this.presentation = presentation;
        this.notifications = notifications;
        this.badges = badges;
        this.journal = journal;
        this.horloge = horloge;
    }

    /** Message visé : auteur, fil, cours, extrait, état de masquage. */
    record Cible(Long auteurId, Long discussionId, Contexte ctx, String extrait, boolean masquee) {
    }

    // ------------------------------------------------------------------ Signaler

    @Transactional
    public SignalementResponse signaler(SignalementRequest req, CurrentUser user) {
        Cible cible = cible(req.cibleType(), req.cibleId(), user);
        acces.exigerSansSanction(user);
        if (cible.auteurId().equals(user.id())) {
            throw new BadRequestException("Tu ne peux pas signaler ton propre message");
        }
        if (signalementRepository.existsByAuteurIdAndCibleTypeAndCibleIdAndStatut(user.id(), req.cibleType(),
                req.cibleId(), StatutSignalement.OUVERT)) {
            throw new ConflictException("Tu as déjà signalé ce message ; la modération va l'examiner");
        }
        Signalement s = new Signalement();
        s.setCibleType(req.cibleType());
        s.setCibleId(req.cibleId());
        s.setCoursId(cible.ctx().coursId());
        s.setAuteurCibleId(cible.auteurId());
        s.setAuteurId(user.id());
        s.setMotif(req.motif());
        s.setCommentaire(req.commentaire() == null || req.commentaire().isBlank() ? null : req.commentaire().trim());
        signalementRepository.save(s);
        CoursInfo cours = cible.ctx().cours();
        if (cours.enseignantId() != null) {
            notifications.notifier(cours.enseignantId(), TypeNotification.SIGNALEMENT, cible.discussionId(),
                    req.cibleType() == CibleType.REPONSE ? req.cibleId() : null, null,
                    "Signalement : " + req.motif().name().toLowerCase().replace('_', ' '));
        }
        return vue(s, presentation.auteurs(user), cible);
    }

    // ------------------------------------------------------------------ Files

    /** File d'un cours (formateur du cours ou admin) ; statut null = tous. */
    public List<SignalementResponse> fileDuCours(Long coursId, StatutSignalement statut, CurrentUser user) {
        acces.exigerModerateur(acces.contexte(user, coursId));
        Auteurs auteurs = presentation.auteurs(user);
        return signalementRepository.fileDuCours(coursId, statut).stream()
                .map(s -> vue(s, auteurs, cibleModeration(s, user))).toList();
    }

    /** File de l'admin, toute la plateforme. */
    public List<SignalementResponse> fileAdmin(StatutSignalement statut, CategorieModeration categorie,
                                               boolean escaladeSeule, CurrentUser user) {
        acces.exigerAdmin(user);
        Auteurs auteurs = presentation.auteurs(user);
        return signalementRepository.fileAdmin(statut, categorie, escaladeSeule).stream()
                .map(s -> vue(s, auteurs, cibleModeration(s, user))).toList();
    }

    public long ouverts(CurrentUser user) {
        acces.exigerAdmin(user);
        return signalementRepository.countByStatut(StatutSignalement.OUVERT);
    }

    // ------------------------------------------------------------------ Décider

    /**
     * CONFIRME (avec masquage si demandé), REJETE (rétablit un message masqué automatiquement par l'IA) ou OUVERT
     * avec escalade vers l'administration. Motif obligatoire, décision tracée.
     */
    @Transactional
    public SignalementResponse decider(Long id, DecisionRequest req, CurrentUser user) {
        Signalement s = signalementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Signalement", id));
        acces.exigerModerateur(acces.contexte(user, s.getCoursId()));
        if (s.getStatut() != StatutSignalement.OUVERT) {
            throw new ConflictException("Ce signalement a déjà été tranché");
        }
        if (req.statut() == StatutSignalement.OUVERT) {
            if (!req.escalader()) {
                throw new BadRequestException("Choisis de confirmer, de rejeter ou de transmettre à l'administration");
            }
            if (!s.isEscalade()) {
                s.setEscalade(true);
                journal.tracer(user, ActionModeration.ESCALADER, "SIGNALEMENT", id, s.getCoursId(), req.motif());
            }
            return vue(s, presentation.auteurs(user), cibleModeration(s, user));
        }

        Cible cible = cibleModeration(s, user);
        if (req.statut() == StatutSignalement.CONFIRME && req.masquer() && !cible.masquee()) {
            masquer(s.getCibleType(), s.getCibleId(), true, req.motif(), user);
        } else if (req.statut() == StatutSignalement.REJETE && cible.masquee() && masqueParIa(s)) {
            masquer(s.getCibleType(), s.getCibleId(), false, req.motif(), user);
        }
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        for (Signalement autre : signalementRepository.findByCibleTypeAndCibleIdAndStatut(s.getCibleType(),
                s.getCibleId(), StatutSignalement.OUVERT)) {
            autre.setStatut(req.statut());
            autre.setDecision(req.motif().trim());
            autre.setDecideParId(user.id());
            autre.setDecideLe(maintenant);
            if (req.escalader()) {
                autre.setEscalade(true);
            }
        }
        signalementRepository.flush();
        if (req.statut() == StatutSignalement.CONFIRME) {
            signalementRepository.findByCibleTypeAndCibleIdAndStatut(s.getCibleType(), s.getCibleId(),
                    StatutSignalement.CONFIRME).stream().map(Signalement::getAuteurId).filter(Objects::nonNull)
                    .distinct()
                    .forEach(badges::verifierGardien);
        }
        journal.tracer(user, ActionModeration.DECIDER_SIGNALEMENT, "SIGNALEMENT", id, s.getCoursId(),
                req.statut() + " : " + req.motif().trim());
        return vue(s, presentation.auteurs(user), cibleModeration(s, user));
    }

    // ------------------------------------------------------------------ Aides

    private boolean masqueParIa(Signalement s) {
        return signalementRepository.findByCibleTypeAndCibleIdAndStatut(s.getCibleType(), s.getCibleId(),
                StatutSignalement.OUVERT).stream().anyMatch(x -> x.getAuteurId() == null && x.getScoreIa() != null);
    }

    private void masquer(CibleType type, Long id, boolean masquer, String motif, CurrentUser user) {
        switch (type) {
            case DISCUSSION -> moderationFil.masquerDiscussion(id, masquer, motif, user);
            case REPONSE -> moderationFil.masquerReponse(id, masquer, motif, user);
            case COMMENTAIRE -> moderationFil.masquerCommentaire(id, masquer, motif, user);
            default -> throw new BadRequestException("Cible inconnue");
        }
    }

    /** Cible vue par un membre qui signale : il doit pouvoir la lire. */
    private Cible cible(CibleType type, Long id, CurrentUser user) {
        return switch (type) {
            case DISCUSSION -> {
                Fil f = fils.charger(id, user);
                yield new Cible(f.discussion().getAuteurId(), id, f.ctx(), f.discussion().getTitre(),
                        f.discussion().isMasquee());
            }
            case REPONSE -> {
                FilService.FilReponse f = fils.chargerReponse(id, user);
                yield new Cible(f.reponse().getAuteurId(), f.discussion().getId(), f.ctx(),
                        Textes.extrait(f.reponse().getContenu(), 200), f.reponse().isMasquee());
            }
            case COMMENTAIRE -> {
                FilService.FilCommentaire f = fils.chargerCommentaire(id, user);
                yield new Cible(f.commentaire().getAuteurId(), f.discussion().getId(), f.ctx(),
                        Textes.extrait(f.commentaire().getContenu(), 200), f.commentaire().isMasque());
            }
        };
    }

    /** Cible vue par la modération (y compris masquée ou supprimée depuis). */
    private Cible cibleModeration(Signalement s, CurrentUser user) {
        Contexte ctx = acces.contexte(user, s.getCoursId());
        return switch (s.getCibleType()) {
            case DISCUSSION -> discussionRepository.findById(s.getCibleId())
                    .map(d -> new Cible(d.getAuteurId(), d.getId(), ctx, d.getTitre(), d.isMasquee()))
                    .orElse(new Cible(s.getAuteurCibleId(), null, ctx, "Message supprimé", false));
            case REPONSE -> reponseRepository.findById(s.getCibleId())
                    .map(r -> new Cible(r.getAuteurId(), r.getDiscussionId(), ctx, Textes.extrait(r.getContenu(), 200),
                            r.isMasquee()))
                    .orElse(new Cible(s.getAuteurCibleId(), null, ctx, "Message supprimé", false));
            case COMMENTAIRE -> commentaireRepository.findById(s.getCibleId())
                    .map(c -> new Cible(c.getAuteurId(), c.getDiscussionId(), ctx,
                            Textes.extrait(c.getContenu(), 200), c.isMasque()))
                    .orElse(new Cible(s.getAuteurCibleId(), null, ctx, "Message supprimé", false));
        };
    }

    private SignalementResponse vue(Signalement s, Auteurs auteurs, Cible cible) {
        CoursInfo cours = cible.ctx().cours();
        boolean moderateur = cible.ctx().moderateur();
        return new SignalementResponse(s.getId(), s.getCibleType(), s.getCibleId(), s.getCoursId(), cours.titre(),
                cible.discussionId(), cible.extrait(),
                moderateur ? auteurs.de(s.getAuteurCibleId(), false, cours, true) : null,
                auteurs.de(s.getAuteurId(), false, cours, true), s.getMotif(), s.getCommentaire(), s.getCategorieIa(),
                s.getScoreIa(), s.getRaisonIa(), s.getStatut(), s.isEscalade(), cible.masquee(), s.getDecision(),
                s.getDecideLe(), s.getCreeLe());
    }
}
