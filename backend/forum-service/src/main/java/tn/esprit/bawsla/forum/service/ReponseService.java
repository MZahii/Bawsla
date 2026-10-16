package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.exception.ForbiddenException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.ReponseRequest;
import tn.esprit.bawsla.forum.dto.ReponseResponse;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.Reponse;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;
import tn.esprit.bawsla.forum.evenement.ReponseAcceptee;
import tn.esprit.bawsla.forum.evenement.ReponseApprouvee;
import tn.esprit.bawsla.forum.evenement.ReponseCreee;
import tn.esprit.bawsla.forum.repository.ReponseRepository;
import tn.esprit.bawsla.forum.service.AccesService.Contexte;
import tn.esprit.bawsla.forum.service.FilService.Fil;
import tn.esprit.bawsla.forum.service.FilService.FilReponse;

/** Réponses humaines : publication, édition, suppression logique, approbation par le formateur du cours. */
@Service
@Transactional(readOnly = true)
public class ReponseService {

    private final ReponseRepository reponseRepository;
    private final FilService fils;
    private final AccesService acces;
    private final DiscussionService discussions;
    private final PresentationService presentation;
    private final JournalService journal;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    public ReponseService(ReponseRepository reponseRepository, FilService fils, AccesService acces,
                          DiscussionService discussions, PresentationService presentation, JournalService journal,
                          ApplicationEventPublisher evenements, Clock horloge) {
        this.reponseRepository = reponseRepository;
        this.fils = fils;
        this.acces = acces;
        this.discussions = discussions;
        this.presentation = presentation;
        this.journal = journal;
        this.evenements = evenements;
        this.horloge = horloge;
    }

    @Transactional
    public ReponseResponse repondre(Long discussionId, ReponseRequest req, CurrentUser user) {
        Fil fil = fils.charger(discussionId, user);
        Discussion d = fil.discussion();
        Contexte ctx = fil.ctx();
        acces.exigerEcriture(ctx);
        exigerFilOuvert(d, ctx);
        if (req.anonyme()) {
            acces.exigerAnonymatPermis(ctx);
        }
        acces.exigerSousLimites(ctx, false);

        LocalDateTime sansReponseDepuis = d.getNbReponses() == 0 ? d.getCreeLe() : null;
        Reponse r = new Reponse();
        r.setDiscussionId(discussionId);
        r.setAuteurId(user.id());
        r.setAnonyme(req.anonyme());
        r.setContenu(req.contenu().trim());
        reponseRepository.save(r);

        d.setNbReponses(d.getNbReponses() + 1);
        d.setDerniereActiviteLe(LocalDateTime.now(horloge));
        d.setDernierActeurId(req.anonyme() ? null : user.id());
        discussions.marquerLu(user.id(), discussionId);
        evenements.publishEvent(new ReponseCreee(r.getId(), discussionId, d.getCoursId(), user.id(), d.getAuteurId(),
                r.isAnonyme(), r.getContenu(), sansReponseDepuis));
        return presentation.reponseSeule(r, d, ctx);
    }

    @Transactional
    public ReponseResponse modifier(Long id, ReponseRequest req, CurrentUser user) {
        FilReponse fil = fils.chargerReponse(id, user);
        Reponse r = fil.reponse();
        if (!r.getAuteurId().equals(user.id())) {
            throw new ForbiddenException("Seul l'auteur peut modifier cette réponse");
        }
        if (fil.discussion().isVerrouillee() && !fil.ctx().moderateur()) {
            throw new ForbiddenException("Cette discussion est verrouillée");
        }
        acces.exigerSansSanction(user);
        r.setContenu(req.contenu().trim());
        r.setModifieLe(LocalDateTime.now(horloge));
        return presentation.reponseSeule(r, fil.discussion(), fil.ctx());
    }

    /** Suppression logique ; une réponse acceptée supprimée rouvre la question. */
    @Transactional
    public void supprimer(Long id, CurrentUser user) {
        FilReponse fil = fils.chargerReponse(id, user);
        Reponse r = fil.reponse();
        Discussion d = fil.discussion();
        boolean auteur = r.getAuteurId().equals(user.id());
        if (!auteur && !fil.ctx().moderateur()) {
            throw new ForbiddenException("Seuls l'auteur et le formateur du cours peuvent supprimer cette réponse");
        }
        r.setSupprimeeLe(LocalDateTime.now(horloge));
        d.setNbReponses(Math.max(0, d.getNbReponses() - 1));
        if (r.isAcceptee()) {
            discussions.retirerAcceptation(d);
            FilService.recalculerStatut(d);
            evenements.publishEvent(new ReponseAcceptee(d.getId(), d.getCoursId(), null, null, r.getId(),
                    r.getAuteurId(), user.id(), d.getAuteurId()));
        }
        if (!auteur) {
            journal.tracer(user, ActionModeration.SUPPRIMER, "REPONSE", id, d.getCoursId(), null);
        }
    }

    /** Approbation (« endorsement ») : formateur du cours ou admin, badge « Approuvée par la formatrice ». */
    @Transactional
    public ReponseResponse approuver(Long id, boolean approuver, CurrentUser user) {
        FilReponse fil = fils.chargerReponse(id, user);
        acces.exigerModerateur(fil.ctx());
        Reponse r = fil.reponse();
        if (approuver && r.isMasquee()) {
            throw new BadRequestException("Une réponse masquée ne peut pas être approuvée");
        }
        boolean change = approuver != (r.getApprouveeParId() != null);
        if (change) {
            r.setApprouveeParId(approuver ? user.id() : null);
            r.setApprouveeLe(approuver ? LocalDateTime.now(horloge) : null);
            evenements.publishEvent(new ReponseApprouvee(id, r.getDiscussionId(), fil.ctx().coursId(),
                    r.getAuteurId(), user.id(), approuver));
        }
        return presentation.reponseSeule(r, fil.discussion(), fil.ctx());
    }

    /** Fil verrouillé ou fusionné : seuls les modérateurs peuvent encore y écrire. */
    static void exigerFilOuvert(Discussion d, Contexte ctx) {
        if (ctx.moderateur()) {
            return;
        }
        if (d.getFusionneeDansId() != null) {
            throw new ForbiddenException("Cette discussion a été fusionnée avec une autre : réponds dans l'originale");
        }
        if (d.isVerrouillee()) {
            throw new ForbiddenException("Cette discussion est verrouillée");
        }
    }
}
