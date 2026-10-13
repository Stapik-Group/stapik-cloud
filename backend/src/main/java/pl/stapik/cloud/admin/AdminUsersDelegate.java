package pl.stapik.cloud.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.admin.api.AdminUsersApiDelegate;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.data.AdminUserListResponse;
import pl.stapik.cloud.admin.data.AdminUserResponse;
import pl.stapik.cloud.admin.data.CreateAdminUserRequest;
import pl.stapik.cloud.admin.data.PasswordResetResponse;
import pl.stapik.cloud.admin.data.UpdateAdminUserRequest;
import pl.stapik.cloud.admin.dto.AdminUserChanges;
import pl.stapik.cloud.admin.dto.NewAdminUser;
import pl.stapik.cloud.admin.dto.PasswordReset;
import pl.stapik.cloud.security.admin.CurrentAdmin;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AdminUsersDelegate implements AdminUsersApiDelegate {

    private final AdminUserService adminUserService;
    private final AdminTwoFactorService adminTwoFactorService;
    private final CurrentAdmin currentAdmin;

    @Override
    public ResponseEntity<AdminUserListResponse> listAdminUsers() {
        return ResponseEntity.ok(new AdminUserListResponse()
                .items(adminUserService.findAll().stream().map(this::toResponse).toList()));
    }

    @Override
    public ResponseEntity<AdminUserResponse> createAdminUser(CreateAdminUserRequest request) {
        AdminUserData created = adminUserService.create(new NewAdminUser(
                request.getUsername().strip(), request.getPassword(), request.getRole().getValue()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @Override
    public ResponseEntity<AdminUserResponse> updateAdminUser(UUID adminUserId, UpdateAdminUserRequest request) {
        AdminUserData updated = adminUserService.update(currentAdmin.id(), adminUserId, new AdminUserChanges(
                request.getUsername().strip(), request.getRole().getValue()));
        return ResponseEntity.ok(toResponse(updated));
    }

    @Override
    public ResponseEntity<Void> deleteAdminUser(UUID adminUserId) {
        adminUserService.delete(currentAdmin.id(), adminUserId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PasswordResetResponse> resetAdminUserPassword(UUID adminUserId) {
        PasswordReset reset = adminUserService.resetPassword(adminUserId);
        return ResponseEntity.ok(new PasswordResetResponse().temporaryPassword(reset.temporaryPassword()));
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
