package tn.esprit.bawsla.user.common.exception;

import org.springframework.http.HttpStatus;

/** Exception métier de base : le GlobalExceptionHandler la traduit en ApiError. */
public class BawslaException extends RuntimeException {

    private final HttpStatus status;

    public BawslaException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
