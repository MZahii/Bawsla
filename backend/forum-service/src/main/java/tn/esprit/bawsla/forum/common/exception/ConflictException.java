package tn.esprit.bawsla.forum.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends BawslaException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
