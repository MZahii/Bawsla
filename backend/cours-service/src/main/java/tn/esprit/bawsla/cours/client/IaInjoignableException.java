package tn.esprit.bawsla.cours.client;

import tn.esprit.bawsla.cours.common.exception.ServiceUnavailableException;

/**
 * Le service d'IA est injoignable (connexion refusée, hôte introuvable, aucune instance). HTTP 503, avec le code
 * {@code IA_INJOIGNABLE} dans {@code ApiError.error} (voir {@code IaExceptionHandler}).
 */
public class IaInjoignableException extends ServiceUnavailableException {

    public static final String CODE = "IA_INJOIGNABLE";
    public static final String MESSAGE = "Le service d'IA est injoignable";

    public IaInjoignableException() {
        super(MESSAGE);
    }
}
