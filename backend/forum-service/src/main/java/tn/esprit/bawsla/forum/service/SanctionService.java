package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.exception.ConflictException;
import tn.esprit.bawsla.forum.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.HistoriqueMembreResponse;
import tn.esprit.bawsla.forum.dto.SanctionRequest;
import tn.esprit.bawsla.forum.dto.SanctionResponse;
import tn.esprit.bawsla.forum.dto.SignalementResponse;
import tn.esprit.bawsla.forum.entity.Sanction;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;
import tn.esprit.bawsla.forum.entity.enums.TypeNotification;
import tn.esprit.bawsla.forum.entity.enums.TypeSanction;
import tn.esprit.bawsla.forum.repository.CommentaireRepository;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.ReponseRepository;
import tn.esprit.bawsla.forum.repository.SanctionRepository;
import tn.esprit.bawsla.forum.repository.SignalementRepository;
import tn.esprit.bawsla.forum.service.AnnuaireService.MembreInfo;
import tn.esprit.bawsla.forum.service.PresentationService.Auteurs;

/**
 * Sanctions (ADMIN uniquement, plan §5.4 et §7.7) : avertissement, lecture seule ou suspension, toujours motivées,
 * avec une durée en heures (sans durée = jusqu'à levée). Les sanctions LECTURE_SEULE et SUSPENSION bloquent toute
 * écriture dans le forum (403 explicite, voir AccesService). Le membre est prévenu par une notification.
 */
@Service
@Transactional(readOnly = true)
public class SanctionService {

    private final SanctionRepository sanctionRepository;
    private final SignalementRepository signalementRepository;
    private final DiscussionRepository discussionRepository;
    private final ReponseRepository reponseRepository;
    private final CommentaireRepository commentaireRepository;
    private final SignalementService signalements;
    private final AccesService acces;
    private final AnnuaireService annuaire;
    private final PresentationService presentation;
    private final ProfilService profils;
    private final NotificationService notifications;
    private final JournalService journal;
    private final Clock horloge;

    public SanctionService(SanctionRepository sanctionRepository, SignalementRepository signalementRepository,
                           DiscussionRepository discussionRepository, ReponseRepository reponseRepository,
                           CommentaireRepository commentaireRepository, SignalementService signalements,
                           AccesService acces, AnnuaireService annuaire, PresentationService presentation,
                           ProfilService profils, NotificationService notifications, JournalService journal,
                           Clock horloge) {
        this.sanctionRepository = sanctionRepository;
        this.signalementRepository = signalementRepository;
        this.discussionRepository = discussionRepository;
        this.reponseRepository = reponseRepository;
        this.commentaireRepository = commentaireRepository;
        this.signalements = signalements;
        this.acces = acces;
        this.annuaire = annuaire;
        this.presentation = presentation;
        this.profils = profils;
        this.notifications = notifications;
        this.journal = journal;
        this.horloge = horloge;
    }

    public List<SanctionResponse> lister(boolean activesSeules, CurrentUser user) {
        acces.exigerAdmin(user);
        Auteurs auteurs = presentation.auteurs(user);
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        return sanctionRepository.findAll().stream()
                .filter(s -> !activesSeules || active(s, maintenant))
                .sorted((a, b) -> b.getDateDebut().compareTo(a.getDateDebut()))
                .map(s -> vue(s, auteurs, maintenant)).toList();
    }

    @Transactional
    public SanctionResponse sanctionner(SanctionRequest req, CurrentUser user) {
        acces.exigerAdmin(user);
        if (req.userId().equals(user.id())) {
            throw new BadRequestException("Tu ne peux pas te sanctionner toi-même");
        }
        if (CurrentUser.ADMIN.equals(annuaire.membre(req.userId()).role())) {
            throw new BadRequestException("Un administrateur ne peut pas être sanctionné dans le forum");
        }
        if (req.type() == TypeSanction.AVERTISSEMENT && req.dureeHeures() != null) {
            throw new BadRequestException("Un avertissement n'a pas de durée");
        }
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        Sanction s = new Sanction();
        s.setUserId(req.userId());
        s.setType(req.type());
        s.setMotif(req.motif().trim());
        s.setDateDebut(maintenant);
        s.setDateFin(req.dureeHeures() == null ? null : maintenant.plusHours(req.dureeHeures()));
        s.setDecidePar(user.id());
        s.setSignalementId(req.signalementId());
        sanctionRepository.save(s);
        journal.tracer(user, ActionModeration.SANCTIONNER, "MEMBRE", req.userId(), null,
                req.type() + " : " + req.motif().trim());
        notifications.notifier(req.userId(), TypeNotification.SANCTION, null, null, null, message(s));
        return vue(s, presentation.auteurs(user), maintenant);
    }

    @Transactional
    public SanctionResponse lever(Long id, CurrentUser user) {
        acces.exigerAdmin(user);
        Sanction s = sanctionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sanction", id));
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        if (!active(s, maintenant)) {
            throw new ConflictException("Cette sanction n'est plus active");
        }
        s.setLeveeLe(maintenant);
        s.setLeveeParId(user.id());
        journal.tracer(user, ActionModeration.LEVER_SANCTION, "MEMBRE", s.getUserId(), null, s.getType().name());
        return vue(s, presentation.auteurs(user), maintenant);
    }

    /** Historique forum d'un membre : messages masqués, signalements reçus, sanctions, réputation. */
    public HistoriqueMembreResponse historique(Long userId, CurrentUser user) {
        acces.exigerAdmin(user);
        Auteurs auteurs = presentation.auteurs(user);
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        long masques = discussionRepository.countByAuteurIdAndMasqueeTrue(userId)
                + reponseRepository.countByAuteurIdAndMasqueeTrue(userId)
                + commentaireRepository.countByAuteurIdAndMasqueTrue(userId);
        List<Long> recus = signalementRepository.findByAuteurCibleIdOrderByCreeLeDesc(userId).stream()
                .map(s -> s.getId()).toList();
        List<SignalementResponse> signalementsRecus = signalements.fileAdmin(null, null, false, user).stream()
                .filter(s -> recus.contains(s.id())).toList();
        List<SanctionResponse> sanctions = sanctionRepository.findByUserIdOrderByDateDebutDesc(userId).stream()
                .map(s -> vue(s, auteurs, maintenant)).toList();
        MembreInfo membre = annuaire.membre(userId);
        String role = membre.role() == null ? CurrentUser.ETUDIANT : membre.role();
        return new HistoriqueMembreResponse(auteurs.de(userId, false, null, true), masques, signalementsRecus,
                sanctions, profils.reputation(new CurrentUser(userId, role)));
    }

    private boolean active(Sanction s, LocalDateTime maintenant) {
        return s.getLeveeLe() == null && !s.getDateDebut().isAfter(maintenant)
                && (s.getDateFin() == null || s.getDateFin().isAfter(maintenant));
    }

    private SanctionResponse vue(Sanction s, Auteurs auteurs, LocalDateTime maintenant) {
        return new SanctionResponse(s.getId(), auteurs.de(s.getUserId(), false, null, true), s.getType(),
                s.getMotif(), s.getDateDebut(), s.getDateFin(), active(s, maintenant), s.getDecidePar(),
                s.getSignalementId(), s.getLeveeLe());
    }

    private static String message(Sanction s) {
        return switch (s.getType()) {
            case AVERTISSEMENT -> "Avertissement de la modération : " + Textes.extrait(s.getMotif(), 200);
            case LECTURE_SEULE -> "Ton accès au forum passe en lecture seule : " + Textes.extrait(s.getMotif(), 200);
            case SUSPENSION -> "Ton accès au forum est suspendu : " + Textes.extrait(s.getMotif(), 200);
        };
    }
}
