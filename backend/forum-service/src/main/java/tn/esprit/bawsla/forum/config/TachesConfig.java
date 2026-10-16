package tn.esprit.bawsla.forum.config;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Exécuteur des tâches de fond du forum (vectorisation des discussions, indexation des cours, réponses de La Vigie) :
 * l'utilisateur n'attend jamais l'IA. {@code forum.taches.synchrones=true} (tests) les exécute sur place.
 */
@Configuration
public class TachesConfig {

    public static final String EXECUTEUR = "forumTaches";

    @Bean(EXECUTEUR)
    Executor forumTaches(@Value("${forum.taches.synchrones:false}") boolean synchrones) {
        if (synchrones) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executeur = new ThreadPoolTaskExecutor();
        executeur.setCorePoolSize(2);
        executeur.setMaxPoolSize(4);
        executeur.setQueueCapacity(500);
        executeur.setThreadNamePrefix("forum-ia-");
        executeur.initialize();
        return executeur;
    }
}
