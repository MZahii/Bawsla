package tn.esprit.bawsla.forum.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BawslaException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public ResourceNotFoundException(String ressource, Object id) {
        this(ressource + " introuvable (id=" + id + ")");
    }
}
