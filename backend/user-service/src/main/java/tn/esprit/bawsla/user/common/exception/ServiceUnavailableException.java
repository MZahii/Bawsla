package tn.esprit.bawsla.user.common.exception;

import org.springframework.http.HttpStatus;

/** Un service distant (Feign, ai-service…) est injoignable ou a répondu en erreur. */
public class ServiceUnavailableException extends BawslaException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
