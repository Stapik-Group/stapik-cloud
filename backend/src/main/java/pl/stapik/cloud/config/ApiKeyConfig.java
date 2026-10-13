package pl.stapik.cloud.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import pl.stapik.cloud.apikey.ApiKeyProperties;

@Configuration
@EnableConfigurationProperties(ApiKeyProperties.class)
public class ApiKeyConfig {
}
