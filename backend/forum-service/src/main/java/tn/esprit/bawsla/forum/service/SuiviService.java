package tn.esprit.bawsla.forum.service;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.entity.Abonnement;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.Favori;
import tn.esprit.bawsla.forum.evenement.DiscussionCreee;
import tn.esprit.bawsla.forum.evenement.ReponseCreee;
import tn.esprit.bawsla.forum.repository.AbonnementRepository;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.FavoriRepository;
import tn.esprit.bawsla.forum.service.FilService.Fil;

/**
 * Suivi des fils (automatique pour l'auteur et chaque répondant), favoris, « Tout marquer comme lu »,
 * et « Ça répond à ma question » depuis les questions similaires (badge Éclaireur).
 */
@Service
@Transactional
public class SuiviService {

    /** État du suivi d'un fil pour l'utilisateur courant. */
    public record EtatSuivi(Long discussionId, boolean suivie, boolean favori, int nbAbonnes) {
    }

    private final AbonnementRepository abonnementRepository;
    private final FavoriRepository favoriRepository;
    private final DiscussionRepository discussionRepository;
    private final FilService fils;
    private final AccesService acces;
    private final DiscussionService discussions;
    private final BadgeService badges;

    public SuiviService(AbonnementRepository abonnementRepository, FavoriRepository favoriRepository,
                        DiscussionRepository discussionRepository, FilService fils, AccesService acces,
                        DiscussionService discussions, BadgeService badges) {
        this.abonnementRepository = abonnementRepository;
        this.favoriRepository = favoriRepository;
        this.discussionRepository = discussionRepository;
        this.fils = fils;
        this.acces = acces;
        this.discussions = discussions;
        this.badges = badges;
    }

    public EtatSuivi suivre(Long discussionId, boolean suivre, CurrentUser user) {
        Fil fil = fils.charger(discussionId, user);
        if (suivre) {
            abonner(user.id(), fil.discussion());
        } else {
            abonnementRepository.findByUserIdAndDiscussionId(user.id(), discussionId).ifPresent(a -> {
                abonnementRepository.delete(a);
                abonnementRepository.flush();
                fil.discussion().setNbAbonnes((int) abonnementRepository.countByDiscussionId(discussionId));
            });
        }
        return etat(fil.discussion(), user);
    }

    public EtatSuivi favori(Long discussionId, boolean ajouter, CurrentUser user) {
        Fil fil = fils.charger(discussionId, user);
        if (ajouter && !favoriRepository.existsByUserIdAndDiscussionId(user.id(), discussionId)) {
            Favori f = new Favori();
            f.setUserId(user.id());
            f.setDiscussionId(discussionId);
            favoriRepository.save(f);
        } else if (!ajouter) {
            favoriRepository.findByUserIdAndDiscussionId(user.id(), discussionId).ifPresent(favoriRepository::delete);
        }
        return etat(fil.discussion(), user);
    }

    /** Marque comme lus tous les fils (d'un cours si coursId est donné) ; renvoie le nombre de fils marqués. */
    public int toutMarquerLu(Long coursId, CurrentUser user) {
        acces.exigerConnecte(user);
        if (coursId != null) {
            acces.contexte(user, coursId);
        }
        List<Long> ids = discussionRepository.nonLues(user.id(), coursId);
        ids.forEach(id -> discussions.marquerLu(user.id(), id));
        return ids.size();
    }

    /** « Ça répond à ma question » : le membre a trouvé sa réponse sans republier (badge Éclaireur). */
    public void reponseTrouvee(Long discussionId, CurrentUser user) {
        Fil fil = fils.charger(discussionId, user);
        Discussion d = fil.discussion();
        if (d.getAuteurId().equals(user.id())) {
            throw new BadRequestException("C'est ta propre discussion");
        }
        if (d.getNbReponses() == 0) {
            throw new BadRequestException("Cette discussion n'a pas encore de réponse");
        }
        badges.eclaireur(user.id());
        abonner(user.id(), d);
    }

    // ------------------------------------------------------------------ Abonnements automatiques

    @EventListener
    public void discussionCreee(DiscussionCreee e) {
        discussionRepository.findById(e.discussionId()).ifPresent(d -> abonner(e.auteurId(), d));
    }

    @EventListener
    public void reponseCreee(ReponseCreee e) {
        discussionRepository.findById(e.discussionId()).ifPresent(d -> abonner(e.auteurId(), d));
    }

    private void abonner(Long userId, Discussion d) {
        if (abonnementRepository.existsByUserIdAndDiscussionId(userId, d.getId())) {
            return;
        }
        Abonnement a = new Abonnement();
        a.setUserId(userId);
        a.setDiscussionId(d.getId());
        abonnementRepository.save(a);
        d.setNbAbonnes(d.getNbAbonnes() + 1);
    }

    private EtatSuivi etat(Discussion d, CurrentUser user) {
        return new EtatSuivi(d.getId(), abonnementRepository.existsByUserIdAndDiscussionId(user.id(), d.getId()),
                favoriRepository.existsByUserIdAndDiscussionId(user.id(), d.getId()), d.getNbAbonnes());
    }
}
