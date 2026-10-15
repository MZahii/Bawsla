package tn.esprit.bawsla.quiz.client;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.Request;
import feign.Response;
import feign.codec.ErrorDecoder;
import tn.esprit.bawsla.quiz.common.exception.BadRequestException;
import tn.esprit.bawsla.quiz.common.exception.ServiceUnavailableException;

/**
 * Réglages du client IA (volontairement sans @Configuration : appliqués à ce seul client).
 * Délai de lecture long : un LLM peut mettre plusieurs dizaines de secondes à répondre.
 */
public class IaFeignConfig {

    @Bean
    Request.Options iaOptions() {
        return new Request.Options(5, TimeUnit.SECONDS, 240, TimeUnit.SECONDS, true);
    }

    /**
     * Traduit une réponse d'erreur de l'IA (format commun) en exception du module : un 400 est une erreur de requête,
     * tout le reste (quota, délai, fournisseur indisponible, session refusée) devient un 503. Jamais de 500 brut.
     */
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
                        message != null ? message : "Le quota de l'IA est atteint, réessaie dans quelques minutes.");
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
