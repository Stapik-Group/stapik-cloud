package pl.stapik.cloud.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.admin.api.AccountApiDelegate;
import pl.stapik.cloud.admin.data.AccountResponse;
import pl.stapik.cloud.admin.data.BackupCodesResponse;
import pl.stapik.cloud.admin.data.TwoFactorConfirmationRequest;
import pl.stapik.cloud.admin.data.TwoFactorEnableRequest;
import pl.stapik.cloud.admin.data.TwoFactorSetupResponse;
import pl.stapik.cloud.admin.dto.AccountInfo;
import pl.stapik.cloud.admin.dto.TwoFactorSetup;
import pl.stapik.cloud.security.admin.CurrentAdmin;

@Component
@RequiredArgsConstructor
public class AdminAccountDelegate implements AccountApiDelegate {

    private final AdminTwoFactorService adminTwoFactorService;
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
}
