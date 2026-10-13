package pl.stapik.cloud.admin.dto;

public record TwoFactorSetup(String secret, String provisioningUri, String qrCodeSvg) { }
