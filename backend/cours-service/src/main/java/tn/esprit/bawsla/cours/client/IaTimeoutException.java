package tn.esprit.bawsla.cours.client;

import tn.esprit.bawsla.cours.common.exception.ServiceUnavailableException;

/**
 * L'IA a mis trop de temps à répondre (délai de lecture dépassé, ou 504 renvoyé par l'ai-service ou la gateway). HTTP 503 comme toute
 * indisponibilité de l'IA, mais avec le code {@code IA_TIMEOUT} dans {@code ApiError.error} (voir {@code IaExceptionHandler}).
 */
public class IaTimeoutException extends ServiceUnavailableException {

    public static final String CODE = "IA_TIMEOUT";
    public static final String MESSAGE = "L'IA met trop de temps à répondre, réessaie dans un instant";

    public IaTimeoutException() {
        super(MESSAGE);
    }

    public IaTimeoutException(String message) {
        super(message);
    }
}
