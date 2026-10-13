package pl.stapik.cloud.asset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssetUploadCleanupJob {

    private final AssetUploadService assetUploadService;

    @Scheduled(cron = "0 */30 * * * *")
    public void purgeExpiredUploads() {
        int deleted = assetUploadService.deleteExpired();
        if (deleted > 0) {
            log.info("Deleted {} expired asset uploads", deleted);
        }
    }
}
