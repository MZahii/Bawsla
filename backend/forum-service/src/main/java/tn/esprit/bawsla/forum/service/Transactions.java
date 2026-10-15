package tn.esprit.bawsla.forum.service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Transactions des tâches de fond. */
final class Transactions {

    private Transactions() {
    }

    /**
     * Toujours une NOUVELLE transaction : une tâche lancée après la validation d'une requête (phase AFTER_COMMIT,
     * exécuteur synchrone des tests) rejoindrait sinon la transaction déjà validée, et ses écritures seraient perdues.
     */
    static TransactionTemplate nouvelle(PlatformTransactionManager transactions) {
        TransactionTemplate t = new TransactionTemplate(transactions);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return t;
    }
}
