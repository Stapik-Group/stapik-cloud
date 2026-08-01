package pl.stapik.cloud.admin;

import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.dto.Credentials;

import java.util.Optional;

public interface AdminUserService {
    Optional<AdminUserData> authenticate(Credentials credentials);
}
