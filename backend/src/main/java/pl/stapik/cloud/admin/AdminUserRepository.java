package pl.stapik.cloud.admin;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.stapik.cloud.admin.data.AdminUserData;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUserData, UUID> {
    Optional<AdminUserData> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT adminUser FROM AdminUserData adminUser WHERE adminUser.id = :id")
    Optional<AdminUserData> findForUpdateById(@Param("id") UUID id);
}
