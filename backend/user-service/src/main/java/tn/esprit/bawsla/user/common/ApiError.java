package tn.esprit.bawsla.user.common;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Format commun des erreurs (voir CONTRATS_API.md).
 * <pre>{ "success": false, "status": 404, "error": "NOT_FOUND", "message": "...",
 *   "path": "/api/...", "timestamp": "...", "details": { "champ": "raison" } }</pre>
 * Fichier du socle : identique dans chaque service, ne pas le modifier localement.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(boolean success, int status, String error, String message, String path,
                       Instant timestamp, Map<String, String> details) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(false, status, error, message, path, Instant.now(), null);
    }

    public static ApiError of(int status, String error, String message, String path,
                              Map<String, String> details) {
        return new ApiError(false, status, error, message, path, Instant.now(), details);
    }
}
