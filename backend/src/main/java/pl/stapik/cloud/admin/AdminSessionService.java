package pl.stapik.cloud.admin;

import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.dto.AdminSession;
import pl.stapik.cloud.admin.dto.SessionInfo;
import pl.stapik.cloud.security.admin.ClientInfo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminSessionService {
    AdminSession startSession(AdminUserData adminUser, ClientInfo client);

    Optional<AdminSession> refreshSession(String refreshToken, ClientInfo client);

    void endSession(String refreshToken);

    boolean isActive(UUID sessionId);

    List<SessionInfo> listSessions(UUID adminUserId, UUID currentSessionId);

    void revokeSession(UUID adminUserId, UUID sessionId);

    void revokeOtherSessions(UUID adminUserId, UUID currentSessionId);
}
