package pl.stapik.cloud.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import pl.stapik.cloud.common.crypto.EncryptionProperties;

@Configuration
@EnableConfigurationProperties(EncryptionProperties.class)
public class EncryptionConfig {
}
