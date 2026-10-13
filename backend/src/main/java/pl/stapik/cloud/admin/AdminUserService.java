package pl.stapik.cloud.admin;

import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.dto.Credentials;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminUserService {
    Optional<AdminUserData> authenticate(Credentials credentials);

    Optional<AdminUserData> findById(UUID adminUserId);

    List<AdminUserData> findAll();

    void changePassword(UUID adminUserId, UUID currentSessionId, String currentPassword, String newPassword);
}
