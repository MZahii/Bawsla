package tn.esprit.bawsla.quiz.client;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import tn.esprit.bawsla.quiz.common.security.CurrentUserArgumentResolver;

/**
 * Propage l'identité de l'utilisateur courant (X-User-Id / X-User-Role) sur les appels Feign,
 * pour que le service appelé sache pour qui il travaille.
 */
@Component
public class FeignHeadersInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        copy(request, template, CurrentUserArgumentResolver.HEADER_USER_ID);
        copy(request, template, CurrentUserArgumentResolver.HEADER_USER_ROLE);
    }

    private static void copy(HttpServletRequest request, RequestTemplate template, String header) {
        String value = request.getHeader(header);
        if (value != null) {
            template.header(header, value);
        }
    }
}
