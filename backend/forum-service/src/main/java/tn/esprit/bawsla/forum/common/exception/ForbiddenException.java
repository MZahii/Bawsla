package tn.esprit.bawsla.forum.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BawslaException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
