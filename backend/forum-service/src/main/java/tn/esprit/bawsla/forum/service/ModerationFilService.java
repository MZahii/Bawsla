package tn.esprit.bawsla.forum.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.DeplacerRequest;
import tn.esprit.bawsla.forum.dto.DiscussionDetail;
import tn.esprit.bawsla.forum.dto.EtiquettesRequest;
import tn.esprit.bawsla.forum.dto.FusionnerRequest;
import tn.esprit.bawsla.forum.entity.Commentaire;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.Reponse;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.evenement.MessageMasque;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.service.AccesService.Contexte;
import tn.esprit.bawsla.forum.service.FilService.Fil;
import tn.esprit.bawsla.forum.service.FilService.FilCommentaire;
import tn.esprit.bawsla.forum.service.FilService.FilReponse;

/**
 * Outils de modération d'un fil, réservés au formateur DU cours et à l'admin : épingler, verrouiller,
 * masquer / rétablir un message, déplacer vers un autre chapitre, fusionner un doublon, modifier les étiquettes.
 * Chaque décision est tracée dans le journal de modération.
 */
@Service
@Transactional(readOnly = true)
public class ModerationFilService {

    private final FilService fils;
    private final AccesService acces;
    private final EtiquetteService etiquettes;
    private final PresentationService presentation;
    private final JournalService journal;
    private final DiscussionRepository discussionRepository;
    private final ApplicationEventPublisher evenements;

    public ModerationFilService(FilService fils, AccesService acces, EtiquetteService etiquettes,
                                PresentationService presentation, JournalService journal,
                                DiscussionRepository discussionRepository, ApplicationEventPublisher evenements) {
        this.fils = fils;
        this.acces = acces;
        this.etiquettes = etiquettes;
        this.presentation = presentation;
        this.journal = journal;
        this.discussionRepository = discussionRepository;
        this.evenements = evenements;
    }

    @Transactional
    public DiscussionDetail epingler(Long id, boolean epingler, CurrentUser user) {
        Fil fil = moderable(id, user);
        fil.discussion().setEpinglee(epingler);
        tracer(fil, epingler ? ActionModeration.EPINGLER : ActionModeration.DESEPINGLER, null);
        return presentation.detail(fil.discussion(), fil.ctx());
    }

    @Transactional
    public DiscussionDetail verrouiller(Long id, boolean verrouiller, String motif, CurrentUser user) {
        Fil fil = moderable(id, user);
        fil.discussion().setVerrouillee(verrouiller);
        FilService.recalculerStatut(fil.discussion());
        tracer(fil, verrouiller ? ActionModeration.VERROUILLER : ActionModeration.DEVERROUILLER, motif);
        return presentation.detail(fil.discussion(), fil.ctx());
    }

    @Transactional
    public DiscussionDetail masquerDiscussion(Long id, boolean masquer, String motif, CurrentUser user) {
        Fil fil = moderable(id, user);
        Discussion d = fil.discussion();
        if (d.isMasquee() != masquer) {
            d.setMasquee(masquer);
            evenements.publishEvent(new MessageMasque(CibleType.DISCUSSION, id, id, d.getCoursId(), d.getAuteurId(),
                    masquer, false));
        }
        tracer(fil, masquer ? ActionModeration.MASQUER : ActionModeration.RETABLIR, motif);
        return presentation.detail(d, fil.ctx());
    }

    @Transactional
    public void masquerReponse(Long reponseId, boolean masquer, String motif, CurrentUser user) {
        FilReponse fil = fils.chargerReponse(reponseId, user);
        acces.exigerModerateur(fil.ctx());
        Reponse r = fil.reponse();
        Discussion d = fil.discussion();
        if (r.isMasquee() != masquer) {
            r.setMasquee(masquer);
            if (masquer && r.isAcceptee()) {
                r.setAcceptee(false);
                d.setReponseAccepteeId(null);
                FilService.recalculerStatut(d);
            }
            evenements.publishEvent(new MessageMasque(CibleType.REPONSE, reponseId, d.getId(), d.getCoursId(),
                    r.getAuteurId(), masquer, false));
        }
        journal.tracer(user, masquer ? ActionModeration.MASQUER : ActionModeration.RETABLIR, "REPONSE", reponseId,
                d.getCoursId(), motif);
    }

    @Transactional
    public void masquerCommentaire(Long commentaireId, boolean masquer, String motif, CurrentUser user) {
        FilCommentaire fil = fils.chargerCommentaire(commentaireId, user);
        acces.exigerModerateur(fil.ctx());
        Commentaire c = fil.commentaire();
        if (c.isMasque() != masquer) {
            c.setMasque(masquer);
            evenements.publishEvent(new MessageMasque(CibleType.COMMENTAIRE, commentaireId, c.getDiscussionId(),
                    fil.ctx().coursId(), c.getAuteurId(), masquer, false));
        }
        journal.tracer(user, masquer ? ActionModeration.MASQUER : ActionModeration.RETABLIR, "COMMENTAIRE",
                commentaireId, fil.ctx().coursId(), motif);
    }

    @Transactional
    public DiscussionDetail deplacer(Long id, DeplacerRequest req, CurrentUser user) {
        Fil fil = moderable(id, user);
        fil.discussion().setChapitreId(req.chapitreId());
        fil.discussion().setPage(req.page());
        tracer(fil, ActionModeration.DEPLACER, req.motif());
        return presentation.detail(fil.discussion(), fil.ctx());
    }

    /** Fusion d'un doublon : la discussion est verrouillée et devient un lien vers l'originale (même cours). */
    @Transactional
    public DiscussionDetail fusionner(Long id, FusionnerRequest req, CurrentUser user) {
        Fil fil = moderable(id, user);
        Discussion d = fil.discussion();
        if (req.versDiscussionId().equals(id)) {
            throw new BadRequestException("Une discussion ne peut pas être fusionnée avec elle-même");
        }
        Discussion originale = discussionRepository.findById(req.versDiscussionId())
                .filter(o -> o.getSupprimeeLe() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Discussion", req.versDiscussionId()));
        if (!originale.getCoursId().equals(d.getCoursId())) {
            throw new BadRequestException("La discussion d'origine doit appartenir au même cours");
        }
        if (originale.getFusionneeDansId() != null) {
            throw new BadRequestException("La discussion d'origine est elle-même fusionnée");
        }
        d.setFusionneeDansId(originale.getId());
        d.setVerrouillee(true);
        FilService.recalculerStatut(d);
        tracer(fil, ActionModeration.FUSIONNER, req.motif());
        return presentation.detail(d, fil.ctx());
    }

    @Transactional
    public DiscussionDetail modifierEtiquettes(Long id, EtiquettesRequest req, CurrentUser user) {
        Fil fil = moderable(id, user);
        etiquettes.affecter(fil.discussion(), req.etiquettes());
        tracer(fil, ActionModeration.MODIFIER_ETIQUETTES, String.join(", ", Textes.etiquettes(req.etiquettes())));
        return presentation.detail(fil.discussion(), fil.ctx());
    }

    private Fil moderable(Long id, CurrentUser user) {
        Fil fil = fils.charger(id, user);
        acces.exigerModerateur(fil.ctx());
        return fil;
    }

    private void tracer(Fil fil, ActionModeration action, String motif) {
        Contexte ctx = fil.ctx();
        journal.tracer(ctx.user(), action, "DISCUSSION", fil.discussion().getId(), ctx.coursId(), motif);
    }
}
