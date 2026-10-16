package tn.esprit.bawsla.user.client;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import tn.esprit.bawsla.user.common.security.CurrentUserArgumentResolver;

/**
 * Propage l'identité de l'utilisateur courant (X-User-Id / X-User-Role) sur les appels Feign, pour que le service
 * appelé applique ses propres règles d'accès (un apprenant ne lit que ses données). Même modèle que quiz-service.
 */
@Component
public class FeignHeadersInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        copier(request, template, CurrentUserArgumentResolver.HEADER_USER_ID);
        copier(request, template, CurrentUserArgumentResolver.HEADER_USER_ROLE);
    }

    private static void copier(HttpServletRequest request, RequestTemplate template, String entete) {
        String valeur = request.getHeader(entete);
        if (valeur != null) {
            template.header(entete, valeur);
        }
    }
}
