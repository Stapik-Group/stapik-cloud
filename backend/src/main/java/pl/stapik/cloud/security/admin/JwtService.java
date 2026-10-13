package pl.stapik.cloud.security.admin;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class JwtService {

    private static final String PURPOSE_CLAIM = "purpose";
    private static final String TWO_FACTOR_PURPOSE = "two-factor";
    private static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(5);

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

    public TwoFactorChallenge generateTwoFactorChallenge(UUID adminUserId, String username) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(CHALLENGE_LIFETIME);
        String token = Jwts.builder()
                .subject(username)
                .id(adminUserId.toString())
                .claim(PURPOSE_CLAIM, TWO_FACTOR_PURPOSE)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new TwoFactorChallenge(token, expiresAt);
    }

    public Optional<UUID> validateTwoFactorChallenge(String token) {
        try {
            Claims claims = parse(token);
            if (!TWO_FACTOR_PURPOSE.equals(claims.get(PURPOSE_CLAIM, String.class))) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(claims.getId()));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Two-factor challenge validation failed", e);
            return Optional.empty();
        }
    }

    public Instant expirationOf(String token) {
        return parse(token).getExpiration().toInstant();
    }

    public Optional<JwtPrincipal> validate(String token) {
        try {
            Claims claims = parse(token);
            if (claims.get(PURPOSE_CLAIM) != null) {
                return Optional.empty();
            }
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
