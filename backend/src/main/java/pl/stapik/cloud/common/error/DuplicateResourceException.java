package pl.stapik.cloud.common.error;

import lombok.Getter;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;

@Getter
public class DuplicateResourceException extends RuntimeException {

    private final String constraintName;

    public DuplicateResourceException(String constraintName, String message) {
        super(message);
        this.constraintName = constraintName;
    }

    public static DuplicateResourceException mapToDuplicateResourceException(DataIntegrityViolationException ex) {
        String constraintName = extractConstraintName(ex);
        String message = switch (constraintName) {
            case "extension_name_key" -> "Extension with this name already exists";
            case "extension_device_id_key" -> "Extension is already registered for this device";
            default -> "Resource violates a uniqueness constraint";
        };
        return new DuplicateResourceException(constraintName, message);
    }

    private static String extractConstraintName(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof PSQLException psqlEx && psqlEx.getServerErrorMessage() != null) {
            return psqlEx.getServerErrorMessage().getConstraint();
        }
        return "unknown";
    }
}