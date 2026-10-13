package pl.stapik.cloud.apikey;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("stapik-cloud.api-key")
public record ApiKeyProperties(Duration defaultValidity, Duration expiryWarningPeriod) {
}
