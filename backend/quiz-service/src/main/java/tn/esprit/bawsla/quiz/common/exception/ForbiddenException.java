package tn.esprit.bawsla.quiz.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BawslaException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
