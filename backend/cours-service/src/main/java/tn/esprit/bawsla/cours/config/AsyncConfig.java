package tn.esprit.bawsla.cours.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Exécuteur des traitements IA (longs : un appel LLM peut durer plusieurs dizaines de secondes). */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String IA_EXECUTOR = "iaExecutor";

    @Bean(name = IA_EXECUTOR)
    ThreadPoolTaskExecutor iaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ia-");
        // Plein : on refuse (TaskRejectedException) au lieu d'exécuter dans le thread de la requête.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
