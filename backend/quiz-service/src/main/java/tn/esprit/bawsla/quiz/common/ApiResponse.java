package tn.esprit.bawsla.quiz.common;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Enveloppe de TOUTES les réponses réussies (format commun Bawsla, voir CONTRATS_API.md).
 * <pre>{ "success": true, "data": ..., "message": "...", "timestamp": "..." }</pre>
 * Fichier du socle : identique dans chaque service, ne pas le modifier localement.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String message, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now());
    }
}
