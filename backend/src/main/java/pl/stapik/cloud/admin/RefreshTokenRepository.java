package pl.stapik.cloud.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.stapik.cloud.admin.data.RefreshTokenData;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenData, UUID> {

    Optional<RefreshTokenData> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

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
