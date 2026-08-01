package pl.stapik.cloud.security.admin;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final long expirationMinutes;

    public JwtService(
            @Value("${stapik-cloud.security.jwt.secret}") String secret,
            @Value("${stapik-cloud.security.jwt.issuer}") String issuer,
            @Value("${stapik-cloud.security.jwt.expiration-minutes}") long expirationMinutes
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.issuer = issuer;
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(UUID adminUserId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .id(adminUserId.toString())
                .claim("role", role)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    public Instant expirationOf(String token) {
        return parse(token).getExpiration().toInstant();
    }

    public Optional<JwtPrincipal> validate(String token) {
        try {
            Claims claims = parse(token);
            return Optional.of(new JwtPrincipal(
                    UUID.fromString(claims.getId()),
                    claims.getSubject(),
                    claims.get("role", String.class)
            ));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed", e);
            return Optional.empty();
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
