package tn.esprit.bawsla.forum.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.entity.EvenementReputation;
import tn.esprit.bawsla.forum.entity.Reputation;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.NiveauReputation;
import tn.esprit.bawsla.forum.entity.enums.RaisonReputation;
import tn.esprit.bawsla.forum.evenement.MessageMasque;
import tn.esprit.bawsla.forum.evenement.ReponseAcceptee;
import tn.esprit.bawsla.forum.evenement.ReponseApprouvee;
import tn.esprit.bawsla.forum.evenement.VoteModifie;
import tn.esprit.bawsla.forum.repository.EvenementReputationRepository;
import tn.esprit.bawsla.forum.repository.ReputationRepository;

/**
 * Points de réputation (plan §3.6), attribués en réaction aux événements du forum, dans la même transaction.
 * Barème : question votée +5, réponse votée +10, réponse acceptée +15 (+2 pour l'auteur de la question qui
 * accepte), approuvée par le formateur +20, vote négatif reçu −2 (et −1 pour le votant), message masqué −10.
 * Annuler (vote retiré, acceptation retirée, message rétabli) rejoue exactement l'inverse de ce qui avait été donné,
 * d'après l'historique : aucun point ne dérive. On ne gagne rien en acceptant sa propre réponse.
 */
@Service
@Transactional
public class ReputationService {

    private final ReputationRepository reputationRepository;
    private final EvenementReputationRepository evenementRepository;

    public ReputationService(ReputationRepository reputationRepository,
                             EvenementReputationRepository evenementRepository) {
        this.reputationRepository = reputationRepository;
        this.evenementRepository = evenementRepository;
    }

    public Reputation reputation(Long userId) {
        return reputationRepository.findById(userId).orElseGet(() -> {
            Reputation r = new Reputation();
            r.setUserId(userId);
            return reputationRepository.save(r);
        });
    }

    public void attribuer(Long userId, int delta, RaisonReputation raison, CibleType cibleType, Long cibleId,
                          Long coursId) {
        if (userId == null || delta == 0) {
            return;
        }
        EvenementReputation e = new EvenementReputation();
        e.setUserId(userId);
        e.setDelta(delta);
        e.setRaison(raison);
        e.setCibleType(cibleType);
        e.setCibleId(cibleId);
        e.setCoursId(coursId);
        evenementRepository.save(e);
        Reputation r = reputation(userId);
        r.setPoints(r.getPoints() + delta);
        r.setNiveau(NiveauReputation.depuisPoints(r.getPoints()));
    }

    /** Retire exactement ce qui a été attribué pour (raison, cible), s'il reste quelque chose. */
    public void annuler(Long userId, RaisonReputation raison, CibleType cibleType, Long cibleId, Long coursId) {
        if (userId == null) {
            return;
        }
        int solde = evenementRepository.solde(userId, raison, cibleType, cibleId);
        attribuer(userId, -solde, raison, cibleType, cibleId, coursId);
    }

    // ------------------------------------------------------------------ Événements

    @EventListener
    public void voteModifie(VoteModifie e) {
        effetDuVote(e, e.ancienneValeur(), -1);
        effetDuVote(e, e.nouvelleValeur(), 1);
    }

    private void effetDuVote(VoteModifie e, int valeur, int sens) {
        if (valeur == 1) {
            RaisonReputation raison = e.cibleType() == CibleType.DISCUSSION ? RaisonReputation.QUESTION_VOTEE
                    : RaisonReputation.REPONSE_VOTEE;
            attribuer(e.auteurCibleId(), sens * raison.getPoints(), raison, e.cibleType(), e.cibleId(), e.coursId());
        } else if (valeur == -1) {
            attribuer(e.auteurCibleId(), sens * RaisonReputation.VOTE_NEGATIF_RECU.getPoints(),
                    RaisonReputation.VOTE_NEGATIF_RECU, e.cibleType(), e.cibleId(), e.coursId());
            attribuer(e.votantId(), sens * RaisonReputation.VOTE_NEGATIF_DONNE.getPoints(),
                    RaisonReputation.VOTE_NEGATIF_DONNE, e.cibleType(), e.cibleId(), e.coursId());
        }
    }

    @EventListener
    public void reponseAcceptee(ReponseAcceptee e) {
        if (e.ancienneReponseId() != null) {
            annuler(e.ancienAuteurId(), RaisonReputation.REPONSE_ACCEPTEE, CibleType.REPONSE, e.ancienneReponseId(),
                    e.coursId());
        }
        if (e.reponseId() == null) {
            annuler(e.auteurDiscussionId(), RaisonReputation.ACCEPTATION, CibleType.DISCUSSION, e.discussionId(),
                    e.coursId());
            return;
        }
        if (e.auteurReponseId().equals(e.auteurDiscussionId())) {
            return;
        }
        attribuer(e.auteurReponseId(), RaisonReputation.REPONSE_ACCEPTEE.getPoints(),
                RaisonReputation.REPONSE_ACCEPTEE, CibleType.REPONSE, e.reponseId(), e.coursId());
        boolean dejaRecompense = evenementRepository.solde(e.auteurDiscussionId(), RaisonReputation.ACCEPTATION,
                CibleType.DISCUSSION, e.discussionId()) > 0;
        if (!dejaRecompense && e.accepteurId().equals(e.auteurDiscussionId())) {
            attribuer(e.auteurDiscussionId(), RaisonReputation.ACCEPTATION.getPoints(), RaisonReputation.ACCEPTATION,
                    CibleType.DISCUSSION, e.discussionId(), e.coursId());
        }
    }

    @EventListener
    public void reponseApprouvee(ReponseApprouvee e) {
        if (e.approuvee()) {
            attribuer(e.auteurReponseId(), RaisonReputation.APPROBATION_FORMATEUR.getPoints(),
                    RaisonReputation.APPROBATION_FORMATEUR, CibleType.REPONSE, e.reponseId(), e.coursId());
        } else {
            annuler(e.auteurReponseId(), RaisonReputation.APPROBATION_FORMATEUR, CibleType.REPONSE, e.reponseId(),
                    e.coursId());
        }
    }

    @EventListener
    public void messageMasque(MessageMasque e) {
        if (e.masque()) {
            attribuer(e.auteurId(), RaisonReputation.MESSAGE_MASQUE.getPoints(), RaisonReputation.MESSAGE_MASQUE,
                    e.cibleType(), e.cibleId(), e.coursId());
        } else {
            annuler(e.auteurId(), RaisonReputation.MESSAGE_MASQUE, e.cibleType(), e.cibleId(), e.coursId());
        }
    }
}
