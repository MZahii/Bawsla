package tn.esprit.bawsla.cours.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import tn.esprit.bawsla.cours.client.IaInjoignableException;
import tn.esprit.bawsla.cours.client.IaTimeoutException;
import tn.esprit.bawsla.cours.common.ApiError;

/**
 * Deux pannes de l'IA, deux codes distincts dans {@code ApiError.error} alors que le statut HTTP reste 503 :
 * {@code IA_TIMEOUT} (l'IA met trop de temps à répondre) et {@code IA_INJOIGNABLE} (le service d'IA est injoignable).
 * Le handler général du socle donnerait « SERVICE_UNAVAILABLE » dans les deux cas.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IaExceptionHandler {

    @ExceptionHandler(IaTimeoutException.class)
    public ResponseEntity<ApiError> delai(IaTimeoutException ex, HttpServletRequest req) {
        return reponse(IaTimeoutException.CODE, ex.getMessage(), req);
    }

    @ExceptionHandler(IaInjoignableException.class)
    public ResponseEntity<ApiError> injoignable(IaInjoignableException ex, HttpServletRequest req) {
        return reponse(IaInjoignableException.CODE, ex.getMessage(), req);
    }

    private static ResponseEntity<ApiError> reponse(String code, String message, HttpServletRequest req) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(ApiError.of(status.value(), code, message, req.getRequestURI(), null));
    }
}
