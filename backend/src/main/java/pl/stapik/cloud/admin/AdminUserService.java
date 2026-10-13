package pl.stapik.cloud.admin;

import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.dto.AdminUserChanges;
import pl.stapik.cloud.admin.dto.Credentials;
import pl.stapik.cloud.admin.dto.NewAdminUser;
import pl.stapik.cloud.admin.dto.PasswordReset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminUserService {
    Optional<AdminUserData> authenticate(Credentials credentials);

    Optional<AdminUserData> findById(UUID adminUserId);

    List<AdminUserData> findAll();

    AdminUserData create(NewAdminUser newAdminUser);

    AdminUserData update(UUID actingAdminUserId, UUID adminUserId, AdminUserChanges changes);

    AdminUserData delete(UUID actingAdminUserId, UUID adminUserId);

    PasswordReset resetPassword(UUID adminUserId);

    void changePassword(UUID adminUserId, UUID currentSessionId, String currentPassword, String newPassword);
}
