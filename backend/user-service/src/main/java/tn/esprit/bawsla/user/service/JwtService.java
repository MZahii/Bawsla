package tn.esprit.bawsla.user.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import tn.esprit.bawsla.user.entity.User;

/**
 * Émet les JWT (HS256). La validation est faite par l'api-gateway avec le même secret.
 * Claims : sub = id, role. Ne jamais y mettre de donnée sensible (le JWT est lisible côté client).
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    /** Début du secret de développement d'application.yml : public, donc n'importe qui peut signer un jeton ADMIN. */
    static final String PREFIXE_SECRET_DEV = "change-me";

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${bawsla.jwt.secret}") String secret,
                      @Value("${bawsla.jwt.expiration-ms}") long expirationMs) {
        if (secret.startsWith(PREFIXE_SECRET_DEV)) {
            log.warn("JWT_SECRET absent : secret de développement public utilisé. N'importe qui peut fabriquer un jeton valide. "
                    + "Renseigner JWT_SECRET dans .env avant toute démo hors de ce poste.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generate(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuer("bawsla")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
