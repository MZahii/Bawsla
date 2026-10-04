package tn.esprit.bawsla.gateway.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

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

    private final SecretKey key;

    public JwtUtil(@Value("${bawsla.jwt.secret}") String secret) {
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
