package pl.stapik.cloud.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("stapik-cloud.encryption")
public record EncryptionProperties(int activeKeyId, List<String> keys) {
}
