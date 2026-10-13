package pl.stapik.cloud.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import pl.stapik.cloud.asset.AssetUploadProperties;

@Configuration
@EnableConfigurationProperties(AssetUploadProperties.class)
public class AssetUploadConfig {
}
