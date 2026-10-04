package tn.esprit.bawsla.gateway.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import reactor.core.publisher.Mono;

/**
 * Point UNIQUE de vérification du JWT.
 * <ol>
 *   <li>Supprime toujours les en-têtes X-User-* envoyés par le client (anti-usurpation).</li>
 *   <li>Laisse passer les chemins publics (/api/auth/**, /api/{module}/health).</li>
 *   <li>Sinon exige "Authorization: Bearer &lt;jwt&gt;" valide, puis transmet
 *       X-User-Id et X-User-Role au service cible.</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    private final JwtUtil jwtUtil;
    private final List<String> publicPaths;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   @Value("${bawsla.security.public-paths}") List<String> publicPaths) {
        this.jwtUtil = jwtUtil;
        this.publicPaths = publicPaths;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(HEADER_USER_ID);
                    h.remove(HEADER_USER_ROLE);
                })
                .build();
        String path = request.getURI().getPath();

        if (request.getMethod() == HttpMethod.OPTIONS || isPublic(path)) {
            return chain.filter(exchange.mutate().request(request).build());
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, path, "Token d'authentification manquant");
        }

        Claims claims;
        try {
            claims = jwtUtil.parse(authHeader.substring(7));
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange, path, "Token invalide ou expiré");
        }

        String userId = claims.getSubject();
        String role = claims.get("role", String.class);
        if (userId == null || role == null) {
            return unauthorized(exchange, path, "Token incomplet");
        }

        ServerHttpRequest authenticated = request.mutate()
                .header(HEADER_USER_ID, userId)
                .header(HEADER_USER_ROLE, role)
                .build();
        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    private boolean isPublic(String path) {
        return publicPaths.stream().anyMatch(p -> matcher.match(p, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String path, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"success":false,"status":401,"error":"UNAUTHORIZED","message":"%s","path":"%s","timestamp":"%s"}"""
                .formatted(message, path.replace("\"", ""), Instant.now());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
