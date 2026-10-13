package pl.stapik.cloud.asset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties("stapik-cloud.asset-upload")
public record AssetUploadProperties(String basePath, Duration sessionTtl, DataSize maxChunkSize) {
}
