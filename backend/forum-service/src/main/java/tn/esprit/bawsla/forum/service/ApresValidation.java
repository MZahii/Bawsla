package tn.esprit.bawsla.forum.service;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tn.esprit.bawsla.forum.config.TachesConfig;

/**
 * Lance une tâche de fond APRÈS la validation de la transaction en cours (sinon tout de suite), avec l'identité de
 * l'utilisateur copiée maintenant : la tâche voit les données validées et peut appeler l'IA via la gateway.
 */
@Component
public class ApresValidation {

    private final Executor executeur;
    private final IdentiteService identites;

    public ApresValidation(@Qualifier(TachesConfig.EXECUTEUR) Executor executeur, IdentiteService identites) {
        this.executeur = executeur;
        this.identites = identites;
    }

    public void lancer(Runnable tache) {
        Runnable avecIdentite = identites.avecIdentite(tache);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            executeur.execute(avecIdentite);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executeur.execute(avecIdentite);
            }
        });
    }
}
