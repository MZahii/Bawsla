package tn.esprit.bawsla.forum.service;

import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import tn.esprit.bawsla.forum.config.TachesConfig;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.evenement.DiscussionCreee;
import tn.esprit.bawsla.forum.evenement.DiscussionModifiee;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.service.IaService.Vecteurs;

/**
 * Vectorise chaque discussion (titre + début du contenu) à la création et à chaque édition (plan §4.1).
 * Après validation de la transaction et en tâche de fond : l'auteur n'attend pas l'IA. Si la vectorisation
 * échoue, la discussion reste trouvable par mots-clés et sera vectorisée à sa prochaine édition.
 */
@Service
public class VectorisationService {

    static final int DEBUT_CONTENU = 500;

    private final DiscussionRepository discussionRepository;
    private final IaService ia;
    private final IdentiteService identites;
    private final Executor executeur;
    private final TransactionTemplate transaction;

    public VectorisationService(DiscussionRepository discussionRepository, IaService ia, IdentiteService identites,
                                @Qualifier(TachesConfig.EXECUTEUR) Executor executeur,
                                PlatformTransactionManager transactions) {
        this.discussionRepository = discussionRepository;
        this.ia = ia;
        this.identites = identites;
        this.executeur = executeur;
        this.transaction = Transactions.nouvelle(transactions);
    }

    @TransactionalEventListener
    public void discussionCreee(DiscussionCreee e) {
        executeur.execute(identites.avecIdentite(() -> vectoriser(e.discussionId())));
    }

    @TransactionalEventListener
    public void discussionModifiee(DiscussionModifiee e) {
        executeur.execute(identites.avecIdentite(() -> vectoriser(e.discussionId())));
    }

    /** Texte vectorisé d'une discussion : titre puis début du contenu (en texte brut). */
    public static String texte(Discussion d) {
        return d.getTitre() + "\n" + Textes.extrait(d.getContenu(), DEBUT_CONTENU);
    }

    void vectoriser(Long discussionId) {
        String texte = transaction.execute(s -> discussionRepository.findById(discussionId)
                .map(VectorisationService::texte).orElse(null));
        if (texte == null) {
            return;
        }
        ia.vectoriserDocuments(List.of(texte)).ifPresent((Vecteurs v) -> transaction.executeWithoutResult(s ->
                discussionRepository.findById(discussionId).ifPresent(d -> {
                    d.setEmbedding(v.valeurs().get(0));
                    d.setModeleEmbedding(v.modele());
                })));
    }
}
