package pl.stapik.cloud.admin;

import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.dto.AccountInfo;
import pl.stapik.cloud.admin.dto.TwoFactorSetup;

import java.util.List;
import java.util.UUID;

public interface AdminTwoFactorService {
    AccountInfo describe(UUID adminUserId);

    TwoFactorSetup beginSetup(UUID adminUserId);

    List<String> enable(UUID adminUserId, String verificationCode);

    void disable(UUID adminUserId, String password, String verificationCode);

    List<String> regenerateBackupCodes(UUID adminUserId, String password, String verificationCode);

    boolean verifyLoginCode(UUID adminUserId, String verificationCode);

    AdminUserData reset(UUID adminUserId);
}
