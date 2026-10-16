package tn.esprit.bawsla.forum.client;

import org.springframework.stereotype.Component;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import tn.esprit.bawsla.forum.common.security.CurrentUserArgumentResolver;
import tn.esprit.bawsla.forum.service.IdentiteService;
import tn.esprit.bawsla.forum.service.IdentiteService.Identite;

/**
 * Propage l'identité de l'utilisateur courant (X-User-Id / X-User-Role) sur les appels Feign, pour que le service
 * appelé applique ses propres règles de visibilité. Fonctionne aussi dans les tâches de fond (indexation,
 * vectorisation) grâce à l'identité copiée par {@link IdentiteService}.
 */
@Component
public class FeignHeadersInterceptor implements RequestInterceptor {

    private final IdentiteService identites;

    public FeignHeadersInterceptor(IdentiteService identites) {
        this.identites = identites;
    }

    @Override
    public void apply(RequestTemplate template) {
        Identite identite = identites.courante();
        if (identite == null) {
            return;
        }
        if (identite.userId() != null) {
            template.header(CurrentUserArgumentResolver.HEADER_USER_ID, identite.userId());
        }
        if (identite.role() != null) {
            template.header(CurrentUserArgumentResolver.HEADER_USER_ROLE, identite.role());
        }
    }
}
