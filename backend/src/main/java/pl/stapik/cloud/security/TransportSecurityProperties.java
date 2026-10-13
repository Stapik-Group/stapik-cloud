package pl.stapik.cloud.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("stapik-cloud.security")
public record TransportSecurityProperties(boolean forceHttps) {
}
