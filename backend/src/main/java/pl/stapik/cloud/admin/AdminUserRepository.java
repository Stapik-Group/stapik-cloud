package pl.stapik.cloud.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.stapik.cloud.admin.data.AdminUserData;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUserData, UUID> {
    Optional<AdminUserData> findByUsername(String username);
}
