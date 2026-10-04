package tn.esprit.bawsla.quiz.common.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends BawslaException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
