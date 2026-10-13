package pl.stapik.cloud.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.stapik.cloud.admin.data.AdminBackupCodeData;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminBackupCodeRepository extends JpaRepository<AdminBackupCodeData, UUID> {

    List<AdminBackupCodeData> findByAdminUserIdAndUsedAtIsNull(UUID adminUserId);

    long countByAdminUserIdAndUsedAtIsNull(UUID adminUserId);

    void deleteByAdminUserId(UUID adminUserId);
}
