package tn.esprit.bawsla.user.common.security;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import tn.esprit.bawsla.user.common.exception.UnauthorizedException;

/** Injecte un {@link CurrentUser} dans les méthodes de controller qui le déclarent. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String id = webRequest.getHeader(HEADER_USER_ID);
        String role = webRequest.getHeader(HEADER_USER_ROLE);
        if (id == null || role == null) {
            throw new UnauthorizedException("Utilisateur non authentifié (passer par l'api-gateway)");
        }
        try {
            return new CurrentUser(Long.valueOf(id), role);
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("En-tête X-User-Id invalide");
        }
    }
}
