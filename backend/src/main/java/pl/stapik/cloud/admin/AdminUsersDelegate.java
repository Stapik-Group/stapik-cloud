package pl.stapik.cloud.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.admin.api.AdminUsersApiDelegate;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.data.AdminUserListResponse;
import pl.stapik.cloud.admin.data.AdminUserResponse;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AdminUsersDelegate implements AdminUsersApiDelegate {

    private final AdminUserService adminUserService;
    private final AdminTwoFactorService adminTwoFactorService;

    @Override
    public ResponseEntity<AdminUserListResponse> listAdminUsers() {
        return ResponseEntity.ok(new AdminUserListResponse()
                .items(adminUserService.findAll().stream().map(this::toResponse).toList()));
    }

    @Override
    public ResponseEntity<Void> resetAdminUserTwoFactor(UUID adminUserId) {
        adminTwoFactorService.reset(adminUserId);
        return ResponseEntity.noContent().build();
    }

    private AdminUserResponse toResponse(AdminUserData adminUser) {
        return new AdminUserResponse()
                .id(adminUser.getId())
                .username(adminUser.getUsername())
                .role(adminUser.getRole())
                .twoFactorEnabled(adminUser.isTotpEnabled());
    }
}
