package pl.stapik.cloud.admin;

import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.dto.AdminSession;

import java.util.Optional;

public interface AdminSessionService {
    AdminSession startSession(AdminUserData adminUser);

    Optional<AdminSession> refreshSession(String refreshToken);

    void endSession(String refreshToken);
}
