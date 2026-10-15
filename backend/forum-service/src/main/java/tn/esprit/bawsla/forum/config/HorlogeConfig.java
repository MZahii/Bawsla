package tn.esprit.bawsla.forum.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Horloge injectable : les tests peuvent la remplacer (anti-flood, sanctions, fenêtres de 24 h). */
@Configuration
public class HorlogeConfig {

    @Bean
    Clock horloge() {
        return Clock.systemDefaultZone();
    }
}
