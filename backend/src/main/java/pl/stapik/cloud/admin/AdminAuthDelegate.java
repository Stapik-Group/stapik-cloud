package pl.stapik.cloud.admin;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.admin.api.AuthApiDelegate;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.data.LoginRequest;
import pl.stapik.cloud.admin.data.LoginResponse;
import pl.stapik.cloud.admin.data.RefreshTokenRequest;
import pl.stapik.cloud.admin.dto.AdminSession;
import pl.stapik.cloud.admin.dto.Credentials;

import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class AdminAuthDelegate implements AuthApiDelegate {

    private final AdminUserService adminUserService;
    private final AdminSessionService adminSessionService;

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest loginRequest) {
        AdminUserData adminUser = getAdminUserData(loginRequest);
        return ResponseEntity.ok(toLoginResponse(adminSessionService.startSession(adminUser)));
    }

    @Override
    public ResponseEntity<LoginResponse> refreshToken(RefreshTokenRequest refreshTokenRequest) {
        AdminSession session = adminSessionService.refreshSession(refreshTokenRequest.getRefreshToken())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        return ResponseEntity.ok(toLoginResponse(session));
    }

    @Override
    public ResponseEntity<Void> logout(RefreshTokenRequest refreshTokenRequest) {
        adminSessionService.endSession(refreshTokenRequest.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    private LoginResponse toLoginResponse(AdminSession session) {
        return new LoginResponse()
                .token(session.accessToken())
                .expiresAt(session.accessTokenExpiresAt().atOffset(ZoneOffset.UTC))
                .refreshToken(session.refreshToken())
                .refreshExpiresAt(session.refreshTokenExpiresAt().atOffset(ZoneOffset.UTC));
    }

    private @NonNull AdminUserData getAdminUserData(LoginRequest loginRequest) {
        return adminUserService.authenticate(Credentials.fromLoginRequest(loginRequest))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
    }
}
