package tn.esprit.bawsla.cours.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.StreamReadConstraints;

/**
 * Jackson refuse par défaut les chaînes de plus de 20 millions de caractères. L'audio d'une explication arrive d'ai-service
 * encodé en base64 DANS le JSON : un MP3 de 5 minutes fait environ 6 Mo, un WAV (si la conversion en MP3 échoue) plus de
 * 15 Mo. On relève la limite à 64 millions de caractères (environ 48 Mo d'audio) pour que la lecture ne soit jamais bloquée
 * par la taille ; la mémoire du service reste la seule borne.
 */
@Configuration
public class JacksonConfig {

    public static final int TAILLE_MAX_CHAINE = 64_000_000;

    @Bean
    Jackson2ObjectMapperBuilderCustomizer lectureDeGrosAudios() {
        return builder -> builder.postConfigurer(mapper -> mapper.getFactory()
                .setStreamReadConstraints(StreamReadConstraints.builder().maxStringLength(TAILLE_MAX_CHAINE).build()));
    }
}
