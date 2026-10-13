package tn.esprit.bawsla.cours.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import jakarta.servlet.http.HttpServletRequest;
import tn.esprit.bawsla.cours.common.ApiError;

/**
 * Erreurs d'envoi de fichier levées par Spring avant notre code (limite de taille de la requête, partie absente).
 * Sans ce handler, elles retomberaient dans « 500 Erreur interne » du handler général du socle.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tropGros(HttpServletRequest req) {
        return reponse(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux.", req);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> partieAbsente(HttpServletRequest req) {
        return reponse(HttpStatus.BAD_REQUEST, "Aucun fichier reçu (champ « fichier »).", req);
    }

    private static ResponseEntity<ApiError> reponse(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.name(), message, req.getRequestURI(), null));
    }
}
