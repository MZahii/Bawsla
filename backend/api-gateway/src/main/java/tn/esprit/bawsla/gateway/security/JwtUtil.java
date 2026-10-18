package tn.esprit.bawsla.gateway.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Valide les JWT émis par user-service (HS256, secret partagé JWT_SECRET).
 * Claims attendus : sub = id utilisateur, role = ADMIN | ENSEIGNANT | ETUDIANT.
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    /** Début du secret de développement d'application.yml : public, donc n'importe qui peut signer un jeton ADMIN. */
    static final String PREFIXE_SECRET_DEV = "change-me";

    private final SecretKey key;

    public JwtUtil(@Value("${bawsla.jwt.secret}") String secret) {
        if (secret.startsWith(PREFIXE_SECRET_DEV)) {
            log.warn("JWT_SECRET absent : secret de développement public utilisé. N'importe qui peut fabriquer un jeton valide. "
                    + "Renseigner JWT_SECRET dans .env avant toute démo hors de ce poste.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws JwtException si le token est invalide, expiré ou mal signé. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
