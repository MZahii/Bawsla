package tn.esprit.bawsla.forum.client;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.Request;
import feign.Response;
import feign.codec.ErrorDecoder;
import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.exception.ServiceUnavailableException;

/**
 * Réglages du client IA (volontairement sans @Configuration : appliqués à ce seul client), sur le modèle du module
 * Quiz. Délai de lecture long : indexer un cours ou générer une réponse peut prendre plusieurs dizaines de secondes.
 */
public class IaFeignConfig {

    @Bean
    Request.Options iaOptions() {
        return new Request.Options(5, TimeUnit.SECONDS, 180, TimeUnit.SECONDS, true);
    }

    /** 400 = erreur de requête ; tout le reste (quota, délai, fournisseur absent) devient un 503, jamais un 500. */
    @Bean
    ErrorDecoder iaErrorDecoder() {
        ObjectMapper mapper = new ObjectMapper();
        return (methodKey, response) -> {
            String message = message(mapper, response);
            return switch (response.status()) {
                case 400 -> new BadRequestException(message != null ? message : "Requête refusée par l'IA");
                case 401, 403 -> new ServiceUnavailableException(
                        "L'IA a refusé l'accès (session expirée ?). Reconnecte-toi puis réessaie.");
                case 429 -> new ServiceUnavailableException(
                        "La limite d'utilisation de l'IA est atteinte pour le moment.");
                default -> new ServiceUnavailableException(
                        message != null ? message : "Le service d'IA est indisponible (" + response.status() + ").");
            };
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
