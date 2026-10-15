package tn.esprit.bawsla.user.client;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.Request;
import feign.Response;
import feign.codec.ErrorDecoder;
import tn.esprit.bawsla.user.common.exception.BadRequestException;
import tn.esprit.bawsla.user.common.exception.ServiceUnavailableException;

/**
 * Réglages du seul client IA (volontairement sans @Configuration). Délai de lecture long : après un redémarrage
 * d'ai-service, le premier appel réentraîne les modèles (30 s à 2 min selon la machine).
 */
public class AiFeignConfig {

    @Bean
    Request.Options aiOptions() {
        return new Request.Options(5, TimeUnit.SECONDS, 180, TimeUnit.SECONDS, true);
    }

    /** Erreur de l'IA → exception du module : 400 reste un 400, tout le reste devient 503. Jamais de 500 brut. */
    @Bean
    ErrorDecoder aiErrorDecoder() {
        ObjectMapper mapper = new ObjectMapper();
        return (methodKey, response) -> {
            String message = message(mapper, response);
            return response.status() == 400
                    ? new BadRequestException(message != null ? message : "Requête refusée par l'IA")
                    : new ServiceUnavailableException(message != null ? message
                            : "Le service d'IA est indisponible (" + response.status() + ")");
        };
    }

    private static String message(ObjectMapper mapper, Response response) {
        if (response.body() == null) {
            return null;
        }
        try (var flux = response.body().asInputStream()) {
            JsonNode message = mapper.readTree(flux).path("message");
            return message.isTextual() && !message.asText().isBlank() ? message.asText() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
