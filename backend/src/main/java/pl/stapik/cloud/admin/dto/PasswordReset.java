package pl.stapik.cloud.admin.dto;

public record PasswordReset(String username, String temporaryPassword) { }
