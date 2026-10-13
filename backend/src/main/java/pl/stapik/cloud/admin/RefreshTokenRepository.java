package pl.stapik.cloud.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.stapik.cloud.admin.data.RefreshTokenData;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenData, UUID> {

    Optional<RefreshTokenData> findByTokenHash(String tokenHash);

    List<RefreshTokenData> findByAdminUserIdAndUsedAtIsNullAndExpiresAtAfter(UUID adminUserId, Instant now);

    boolean existsBySessionIdAndExpiresAtAfter(UUID sessionId, Instant now);

    void deleteByTokenHash(String tokenHash);

    long deleteByAdminUserIdAndSessionId(UUID adminUserId, UUID sessionId);

    long deleteByAdminUserIdAndSessionIdNot(UUID adminUserId, UUID sessionId);

    void deleteByAdminUserId(UUID adminUserId);

    @Modifying
    @Query("""
            DELETE FROM RefreshTokenData refreshToken
            WHERE refreshToken.adminUserId = :adminUserId
              AND (refreshToken.expiresAt < :now OR refreshToken.usedAt < :usedBefore)
            """)
    int deleteStaleTokens(@Param("adminUserId") UUID adminUserId,
                          @Param("now") Instant now,
                          @Param("usedBefore") Instant usedBefore);
}
