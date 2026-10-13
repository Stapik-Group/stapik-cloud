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
import pl.stapik.cloud.admin.data.LoginResult;
import pl.stapik.cloud.admin.data.RefreshTokenRequest;
import pl.stapik.cloud.admin.data.TwoFactorLoginRequest;
import pl.stapik.cloud.admin.dto.AdminSession;
import pl.stapik.cloud.admin.dto.Credentials;
import pl.stapik.cloud.security.admin.CurrentClient;
import pl.stapik.cloud.security.admin.JwtService;
import pl.stapik.cloud.security.admin.TwoFactorChallenge;
import pl.stapik.cloud.security.ratelimit.LoginAttemptTracker;

import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class AdminAuthDelegate implements AuthApiDelegate {

    private final AdminUserService adminUserService;
    private final AdminSessionService adminSessionService;
    private final AdminTwoFactorService adminTwoFactorService;
    private final LoginAttemptTracker loginAttemptTracker;
    private final JwtService jwtService;
    private final CurrentClient currentClient;

    @Override
    public ResponseEntity<LoginResult> login(LoginRequest loginRequest) {
        AdminUserData adminUser = getAdminUserData(loginRequest);
        if (adminUser.isTotpEnabled()) {
            return ResponseEntity.ok(toChallengeResult(
                    jwtService.generateTwoFactorChallenge(adminUser.getId(), adminUser.getUsername())));
        }

        loginAttemptTracker.reset(adminUser.getUsername());
        return ResponseEntity.ok(new LoginResult()
                .twoFactorRequired(false)
                .session(toLoginResponse(adminSessionService.startSession(adminUser, currentClient.describe()))));
    }

    @Override
    public ResponseEntity<LoginResponse> completeTwoFactorLogin(TwoFactorLoginRequest request) {
        AdminUserData adminUser = jwtService.validateTwoFactorChallenge(request.getChallengeToken())
                .flatMap(adminUserService::findById)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired challenge"));

        String username = adminUser.getUsername();
        loginAttemptTracker.checkNotLocked(username);
        if (!adminTwoFactorService.verifyLoginCode(adminUser.getId(), request.getCode())) {
            loginAttemptTracker.recordFailure(username);
            throw new BadCredentialsException("Invalid verification code");
        }

        loginAttemptTracker.reset(username);
        return ResponseEntity.ok(toLoginResponse(adminSessionService.startSession(adminUser, currentClient.describe())));
    }

    @Override
    public ResponseEntity<LoginResponse> refreshToken(RefreshTokenRequest refreshTokenRequest) {
        AdminSession session = adminSessionService.refreshSession(refreshTokenRequest.getRefreshToken(), currentClient.describe())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        return ResponseEntity.ok(toLoginResponse(session));
    }

    @Override
    public ResponseEntity<Void> logout(RefreshTokenRequest refreshTokenRequest) {
        adminSessionService.endSession(refreshTokenRequest.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    private LoginResult toChallengeResult(TwoFactorChallenge challenge) {
        return new LoginResult()
                .twoFactorRequired(true)
                .challengeToken(challenge.token())
                .challengeExpiresAt(challenge.expiresAt().atOffset(ZoneOffset.UTC));
    }

    private LoginResponse toLoginResponse(AdminSession session) {
        return new LoginResponse()
                .token(session.accessToken())
                .expiresAt(session.accessTokenExpiresAt().atOffset(ZoneOffset.UTC))
                .refreshToken(session.refreshToken())
                .refreshExpiresAt(session.refreshTokenExpiresAt().atOffset(ZoneOffset.UTC));
    }

    private @NonNull AdminUserData getAdminUserData(LoginRequest loginRequest) {
        String username = loginRequest.getUsername();
        loginAttemptTracker.checkNotLocked(username);
        return adminUserService.authenticate(Credentials.fromLoginRequest(loginRequest))
                .orElseThrow(() -> {
                    loginAttemptTracker.recordFailure(username);
                    return new BadCredentialsException("Invalid credentials");
                });
    }
}
