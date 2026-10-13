package pl.stapik.cloud.admin.dto;

public record AccountInfo(String username, String role, boolean twoFactorEnabled, long backupCodesRemaining) { }
