package tn.esprit.bawsla.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

/**
 * Filtre JWT de la gateway, sans contexte Spring : la requête transmise au service est capturée par une fausse chaîne.
 * Vérifie les règles de sécurité de CLAUDE.md : X-User-* du client toujours supprimés, identité ajoutée à partir du token seul.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "secret-de-test-assez-long-pour-hs256-0123456789";

    private JwtAuthenticationFilter filtre;
    /** Requête reçue par le service cible ; null si la gateway a répondu elle-même (401). */
    private ServerHttpRequest transmise;

    @BeforeEach
    void preparer() {
        filtre = new JwtAuthenticationFilter(new JwtUtil(SECRET),
                List.of("/api/auth/**", "/api/*/health", "/api/quiz/certificats/verifier/**"),
                List.of("/api/cours", "/api/cours/*", ""));
        transmise = null;
    }

    private MockServerWebExchange executer(MockServerHttpRequest.BaseBuilder<?> requete) {
        MockServerWebExchange exchange = MockServerWebExchange.from(requete);
        filtre.filter(exchange, ex -> {
            transmise = ex.getRequest();
            return Mono.empty();
        }).block();
        return exchange;
    }

    private static String jeton(String sujet, String role, String secret, long dureeMs) {
        var builder = Jwts.builder().expiration(new Date(System.currentTimeMillis() + dureeMs));
        if (sujet != null) builder.subject(sujet);
        if (role != null) builder.claim("role", role);
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Test
    void tokenValide_transmetIdentiteDuToken() {
        executer(MockServerHttpRequest.get("/api/quiz/mes-resultats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton("42", "ETUDIANT", SECRET, 60_000)));

        assertThat(transmise).isNotNull();
        assertThat(transmise.getHeaders().get("X-User-Id")).containsExactly("42");
        assertThat(transmise.getHeaders().get("X-User-Role")).containsExactly("ETUDIANT");
    }

    @Test
    void enTetesUsurpes_remplacesParCeuxDuToken() {
        executer(MockServerHttpRequest.post("/api/cours")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton("42", "ETUDIANT", SECRET, 60_000))
                .header("X-User-Id", "1")
                .header("X-User-Role", "ADMIN"));

        assertThat(transmise.getHeaders().get("X-User-Id")).containsExactly("42");
        assertThat(transmise.getHeaders().get("X-User-Role")).containsExactly("ETUDIANT");
    }

    @Test
    void cheminPublic_passeSansTokenEtSansIdentite() {
        executer(MockServerHttpRequest.post("/api/auth/login")
                .header("X-User-Id", "1")
                .header("X-User-Role", "ADMIN"));

        assertThat(transmise).isNotNull();
        assertThat(transmise.getHeaders().containsKey("X-User-Id")).isFalse();
        assertThat(transmise.getHeaders().containsKey("X-User-Role")).isFalse();
    }

    @Test
    void healthEtVerificationDeCertificat_sontPublics() {
        executer(MockServerHttpRequest.get("/api/forum/health"));
        assertThat(transmise).isNotNull();

        transmise = null;
        executer(MockServerHttpRequest.get("/api/quiz/certificats/verifier/ABC-123"));
        assertThat(transmise).isNotNull();
    }

    @Test
    void visiteur_litLeCatalogueSansIdentite() {
        executer(MockServerHttpRequest.get("/api/cours/7").header("X-User-Role", "ADMIN"));

        assertThat(transmise).isNotNull();
        assertThat(transmise.getHeaders().containsKey("X-User-Role")).isFalse();
    }

    @Test
    void visiteur_neModifiePasLeCatalogue() {
        MockServerWebExchange exchange = executer(MockServerHttpRequest.post("/api/cours"));

        assertThat(transmise).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void sansToken_401AuFormatApiError() {
        MockServerWebExchange exchange = executer(MockServerHttpRequest.get("/api/quiz/mes-resultats"));

        assertThat(transmise).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"success\":false", "\"status\":401", "Token d'authentification manquant", "/api/quiz/mes-resultats");
    }

    @Test
    void tokenMalSigne_401() {
        MockServerWebExchange exchange = executer(MockServerHttpRequest.get("/api/quiz/mes-resultats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton("42", "ADMIN", "un-autre-secret-assez-long-pour-hs256-987654321", 60_000)));

        assertThat(transmise).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Token invalide ou expiré");
    }

    @Test
    void tokenExpire_401() {
        MockServerWebExchange exchange = executer(MockServerHttpRequest.get("/api/quiz/mes-resultats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton("42", "ETUDIANT", SECRET, -60_000)));

        assertThat(transmise).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenSansRole_401() {
        MockServerWebExchange exchange = executer(MockServerHttpRequest.get("/api/quiz/mes-resultats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton("42", null, SECRET, 60_000)));

        assertThat(transmise).isNull();
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Token incomplet");
    }

    @Test
    void enTeteAuthorizationSansBearer_401() {
        MockServerWebExchange exchange = executer(MockServerHttpRequest.get("/api/cours/7")
                .header(HttpHeaders.AUTHORIZATION, "Basic YWRtaW46YWRtaW4="));

        assertThat(transmise).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void preflightCors_passeSansToken() {
        executer(MockServerHttpRequest.options("/api/quiz/mes-resultats"));
        assertThat(transmise).isNotNull();
    }
}
