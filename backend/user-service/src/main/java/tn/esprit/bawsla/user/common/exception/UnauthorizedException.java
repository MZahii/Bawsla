package tn.esprit.bawsla.user.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends BawslaException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
