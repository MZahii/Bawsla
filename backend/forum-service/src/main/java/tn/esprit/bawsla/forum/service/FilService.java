package tn.esprit.bawsla.forum.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.entity.Commentaire;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.Reponse;
import tn.esprit.bawsla.forum.entity.enums.StatutDiscussion;
import tn.esprit.bawsla.forum.repository.CommentaireRepository;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.ReponseRepository;
import tn.esprit.bawsla.forum.service.AccesService.Contexte;

/**
 * Charge un fil (ou une réponse, un commentaire) avec son contexte de droits, en appliquant la visibilité :
 * supprimé = 404 pour tous ; discussion masquée = 404 sauf pour son auteur et les modérateurs du cours.
 */
@Service
@Transactional(readOnly = true)
public class FilService {

    public record Fil(Discussion discussion, Contexte ctx) {
    }

    public record FilReponse(Reponse reponse, Discussion discussion, Contexte ctx) {
    }

    public record FilCommentaire(Commentaire commentaire, Discussion discussion, Contexte ctx) {
    }

    private final DiscussionRepository discussionRepository;
    private final ReponseRepository reponseRepository;
    private final CommentaireRepository commentaireRepository;
    private final AccesService acces;

    public FilService(DiscussionRepository discussionRepository, ReponseRepository reponseRepository,
                      CommentaireRepository commentaireRepository, AccesService acces) {
        this.discussionRepository = discussionRepository;
        this.reponseRepository = reponseRepository;
        this.commentaireRepository = commentaireRepository;
        this.acces = acces;
    }

    public Fil charger(Long discussionId, CurrentUser user) {
        acces.exigerConnecte(user);
        Discussion d = discussionRepository.findById(discussionId)
                .filter(x -> x.getSupprimeeLe() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Discussion", discussionId));
        Contexte ctx = acces.contexte(user, d.getCoursId());
        if (d.isMasquee() && !ctx.moderateur() && !d.getAuteurId().equals(user.id())) {
            throw new ResourceNotFoundException("Discussion", discussionId);
        }
        return new Fil(d, ctx);
    }

    public FilReponse chargerReponse(Long reponseId, CurrentUser user) {
        acces.exigerConnecte(user);
        Reponse r = reponseRepository.findById(reponseId)
                .filter(x -> x.getSupprimeeLe() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Réponse", reponseId));
        Fil fil = charger(r.getDiscussionId(), user);
        return new FilReponse(r, fil.discussion(), fil.ctx());
    }

    public FilCommentaire chargerCommentaire(Long commentaireId, CurrentUser user) {
        acces.exigerConnecte(user);
        Commentaire c = commentaireRepository.findById(commentaireId)
                .filter(x -> x.getSupprimeLe() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Commentaire", commentaireId));
        Fil fil = charger(c.getDiscussionId(), user);
        return new FilCommentaire(c, fil.discussion(), fil.ctx());
    }

    /** Statut dérivé : verrouillée = FERMEE, sinon RESOLUE si une réponse est acceptée, sinon OUVERTE. */
    public static void recalculerStatut(Discussion d) {
        if (d.isVerrouillee()) {
            d.setStatut(StatutDiscussion.FERMEE);
        } else if (d.getReponseAccepteeId() != null) {
            d.setStatut(StatutDiscussion.RESOLUE);
        } else {
            d.setStatut(StatutDiscussion.OUVERTE);
        }
    }
}
