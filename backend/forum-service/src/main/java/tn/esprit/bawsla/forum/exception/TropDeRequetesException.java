package tn.esprit.bawsla.forum.exception;

import org.springframework.http.HttpStatus;

import tn.esprit.bawsla.forum.common.exception.BawslaException;

/**
 * Limite anti-flood atteinte (429). Propre au module Forum : elle étend l'exception du socle sans la modifier,
 * et le GlobalExceptionHandler la traduit comme les autres en ApiError.
 */
public class TropDeRequetesException extends BawslaException {

    public TropDeRequetesException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
