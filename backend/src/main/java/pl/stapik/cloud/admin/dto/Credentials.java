package pl.stapik.cloud.admin.dto;

import lombok.Builder;
import lombok.Data;
import pl.stapik.cloud.admin.data.LoginRequest;

@Data
@Builder
public class Credentials {
    private String username;
    private String password;

    public static Credentials fromLoginRequest(LoginRequest request) {
        return Credentials.of(request.getUsername(), request.getPassword());
    }

    public static Credentials of(String username, String password) {
        return Credentials.builder()
                .username(username)
                .password(password)
                .build();
    }
}
