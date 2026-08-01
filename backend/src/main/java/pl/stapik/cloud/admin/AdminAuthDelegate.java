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
import pl.stapik.cloud.admin.dto.Credentials;
import pl.stapik.cloud.security.admin.JwtService;

import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class AdminAuthDelegate implements AuthApiDelegate {

    private final AdminUserService adminUserService;
    private final JwtService jwtService;

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest loginRequest) {
        AdminUserData adminUser = getAdminUserData(loginRequest);
        String token = jwtService.generateToken(adminUser.getId(), adminUser.getUsername(), adminUser.getRole());

        return ResponseEntity.ok(new LoginResponse()
                .token(token)
                .expiresAt(jwtService.expirationOf(token).atOffset(ZoneOffset.UTC)));
    }

    private @NonNull AdminUserData getAdminUserData(LoginRequest loginRequest) {
        return adminUserService.authenticate(Credentials.fromLoginRequest(loginRequest))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
    }
}
