package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.ForbiddenException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.CommentaireRequest;
import tn.esprit.bawsla.forum.dto.CommentaireResponse;
import tn.esprit.bawsla.forum.entity.Commentaire;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;
import tn.esprit.bawsla.forum.evenement.CommentaireCree;
import tn.esprit.bawsla.forum.repository.CommentaireRepository;
import tn.esprit.bawsla.forum.service.AccesService.Contexte;
import tn.esprit.bawsla.forum.service.FilService.Fil;
import tn.esprit.bawsla.forum.service.FilService.FilCommentaire;
import tn.esprit.bawsla.forum.service.FilService.FilReponse;

/** Commentaires courts (600 caractères), sous la question ou sous une réponse, un seul niveau. */
@Service
@Transactional(readOnly = true)
public class CommentaireService {

    private final CommentaireRepository repository;
    private final FilService fils;
    private final AccesService acces;
    private final PresentationService presentation;
    private final JournalService journal;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    public CommentaireService(CommentaireRepository repository, FilService fils, AccesService acces,
                              PresentationService presentation, JournalService journal,
                              ApplicationEventPublisher evenements, Clock horloge) {
        this.repository = repository;
        this.fils = fils;
        this.acces = acces;
        this.presentation = presentation;
        this.journal = journal;
        this.evenements = evenements;
        this.horloge = horloge;
    }

    @Transactional
    public CommentaireResponse commenterDiscussion(Long discussionId, CommentaireRequest req, CurrentUser user) {
        Fil fil = fils.charger(discussionId, user);
        return creer(fil.discussion(), null, fil.discussion().getAuteurId(), fil.ctx(), req);
    }

    @Transactional
    public CommentaireResponse commenterReponse(Long reponseId, CommentaireRequest req, CurrentUser user) {
        FilReponse fil = fils.chargerReponse(reponseId, user);
        return creer(fil.discussion(), reponseId, fil.reponse().getAuteurId(), fil.ctx(), req);
    }

    @Transactional
    public CommentaireResponse modifier(Long id, CommentaireRequest req, CurrentUser user) {
        FilCommentaire fil = fils.chargerCommentaire(id, user);
        Commentaire c = fil.commentaire();
        if (!c.getAuteurId().equals(user.id())) {
            throw new ForbiddenException("Seul l'auteur peut modifier ce commentaire");
        }
        ReponseService.exigerFilOuvert(fil.discussion(), fil.ctx());
        acces.exigerSansSanction(user);
        c.setContenu(req.contenu().trim());
        c.setModifieLe(LocalDateTime.now(horloge));
        return presentation.commentaire(c, fil.ctx(), presentation.auteurs(user));
    }

    @Transactional
    public void supprimer(Long id, CurrentUser user) {
        FilCommentaire fil = fils.chargerCommentaire(id, user);
        Commentaire c = fil.commentaire();
        boolean auteur = c.getAuteurId().equals(user.id());
        if (!auteur && !fil.ctx().moderateur()) {
            throw new ForbiddenException("Seuls l'auteur et le formateur du cours peuvent supprimer ce commentaire");
        }
        c.setSupprimeLe(LocalDateTime.now(horloge));
        if (!auteur) {
            journal.tracer(user, ActionModeration.SUPPRIMER, "COMMENTAIRE", id, fil.ctx().coursId(), null);
        }
    }

    private CommentaireResponse creer(Discussion d, Long reponseId, Long auteurCibleId, Contexte ctx,
                                      CommentaireRequest req) {
        acces.exigerEcriture(ctx);
        ReponseService.exigerFilOuvert(d, ctx);
        acces.exigerSousLimites(ctx, false);
        Commentaire c = new Commentaire();
        c.setDiscussionId(d.getId());
        c.setReponseId(reponseId);
        c.setAuteurId(ctx.user().id());
        c.setContenu(req.contenu().trim());
        repository.save(c);
        d.setDerniereActiviteLe(LocalDateTime.now(horloge));
        d.setDernierActeurId(ctx.user().id());
        evenements.publishEvent(new CommentaireCree(c.getId(), d.getId(), reponseId, d.getCoursId(),
                ctx.user().id(), auteurCibleId, c.getContenu()));
        return presentation.commentaire(c, ctx, presentation.auteurs(ctx.user()));
    }
}
