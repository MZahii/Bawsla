package tn.esprit.bawsla.forum.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import tn.esprit.bawsla.forum.common.security.CurrentUserArgumentResolver;

/**
 * Identité de l'utilisateur pour les appels sortants (Feign) : en-têtes X-User-* pour les services, jeton JWT pour
 * l'IA (qui passe par la gateway). Dans une requête HTTP, elle est lue sur la requête ; dans une tâche de fond, elle a
 * été COPIÉE au moment où la tâche a été soumise (on ne garde jamais l'objet requête, recyclé par le serveur).
 */
@Component
public class IdentiteService {

    public static final String AUTHORIZATION = "Authorization";

    /** Valeurs copiées pour une tâche de fond ; null hors requête et hors tâche. */
    public record Identite(String userId, String role, String jeton) {
    }

    private static final ThreadLocal<Identite> TACHE = new ThreadLocal<>();

    /** Identité courante : celle de la requête HTTP, sinon celle de la tâche de fond, sinon null. */
    public Identite courante() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest r = attrs.getRequest();
            return new Identite(r.getHeader(CurrentUserArgumentResolver.HEADER_USER_ID),
                    r.getHeader(CurrentUserArgumentResolver.HEADER_USER_ROLE), r.getHeader(AUTHORIZATION));
        }
        return TACHE.get();
    }

    /** Jeton « Bearer … » à transmettre à la gateway, ou chaîne vide (la gateway répondra 401, traité en repli). */
    public String jeton() {
        Identite identite = courante();
        return identite == null || identite.jeton() == null ? "" : identite.jeton();
    }

    /** Enveloppe une tâche pour qu'elle s'exécute avec l'identité copiée maintenant. */
    public Runnable avecIdentite(Runnable tache) {
        Identite copie = courante();
        return () -> {
            Identite avant = TACHE.get();
            TACHE.set(copie);
            try {
                tache.run();
            } finally {
                if (avant == null) {
                    TACHE.remove();
                } else {
                    TACHE.set(avant);
                }
            }
        };
    }
}
