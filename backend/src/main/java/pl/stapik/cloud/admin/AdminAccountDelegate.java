package pl.stapik.cloud.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.admin.api.AccountApiDelegate;
import pl.stapik.cloud.admin.data.AccountResponse;
import pl.stapik.cloud.admin.data.BackupCodesResponse;
import pl.stapik.cloud.admin.data.SessionListResponse;
import pl.stapik.cloud.admin.data.SessionResponse;
import pl.stapik.cloud.admin.data.TwoFactorConfirmationRequest;
import pl.stapik.cloud.admin.data.TwoFactorEnableRequest;
import pl.stapik.cloud.admin.data.TwoFactorSetupResponse;
import pl.stapik.cloud.admin.dto.AccountInfo;
import pl.stapik.cloud.admin.dto.SessionInfo;
import pl.stapik.cloud.admin.dto.TwoFactorSetup;
import pl.stapik.cloud.security.admin.CurrentAdmin;

import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AdminAccountDelegate implements AccountApiDelegate {

    private final AdminTwoFactorService adminTwoFactorService;
    private final AdminSessionService adminSessionService;
    private final CurrentAdmin currentAdmin;

    @Override
    public ResponseEntity<AccountResponse> getAccount() {
        AccountInfo info = adminTwoFactorService.describe(currentAdmin.id());
        return ResponseEntity.ok(new AccountResponse()
                .username(info.username())
                .role(info.role())
                .twoFactorEnabled(info.twoFactorEnabled())
                .backupCodesRemaining(info.backupCodesRemaining()));
    }

    @Override
    public ResponseEntity<TwoFactorSetupResponse> beginTwoFactorSetup() {
        TwoFactorSetup setup = adminTwoFactorService.beginSetup(currentAdmin.id());
        return ResponseEntity.ok(new TwoFactorSetupResponse()
                .secret(setup.secret())
                .provisioningUri(setup.provisioningUri())
                .qrCodeSvg(setup.qrCodeSvg()));
    }

    @Override
    public ResponseEntity<BackupCodesResponse> enableTwoFactor(TwoFactorEnableRequest request) {
        return ResponseEntity.ok(new BackupCodesResponse()
                .backupCodes(adminTwoFactorService.enable(currentAdmin.id(), request.getCode())));
    }

    @Override
    public ResponseEntity<Void> disableTwoFactor(TwoFactorConfirmationRequest request) {
        adminTwoFactorService.disable(currentAdmin.id(), request.getPassword(), request.getCode());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<BackupCodesResponse> regenerateBackupCodes(TwoFactorConfirmationRequest request) {
        return ResponseEntity.ok(new BackupCodesResponse()
                .backupCodes(adminTwoFactorService.regenerateBackupCodes(
                        currentAdmin.id(), request.getPassword(), request.getCode())));
    }

    @Override
    public ResponseEntity<SessionListResponse> listSessions() {
        return ResponseEntity.ok(new SessionListResponse()
                .items(adminSessionService.listSessions(currentAdmin.id(), currentAdmin.sessionId()).stream()
                        .map(this::toResponse)
                        .toList()));
    }

    @Override
    public ResponseEntity<Void> revokeSession(UUID sessionId) {
        adminSessionService.revokeSession(currentAdmin.id(), sessionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> revokeOtherSessions() {
        adminSessionService.revokeOtherSessions(currentAdmin.id(), currentAdmin.sessionId());
        return ResponseEntity.noContent().build();
    }

    private SessionResponse toResponse(SessionInfo session) {
        return new SessionResponse()
                .id(session.id())
                .startedAt(session.startedAt().atOffset(ZoneOffset.UTC))
                .lastActiveAt(session.lastActiveAt().atOffset(ZoneOffset.UTC))
                .expiresAt(session.expiresAt().atOffset(ZoneOffset.UTC))
                .ipAddress(session.ipAddress())
                .userAgent(session.userAgent())
                .current(session.current());
    }
}
