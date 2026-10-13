package pl.stapik.cloud.asset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.asset.storage.AssetStorage;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssetEncryptionRunner implements ApplicationRunner {

    private final AssetRepository assetRepository;
    private final AssetStorage assetStorage;

    @Override
    public void run(ApplicationArguments args) {
        for (AssetData asset : assetRepository.findAll()) {
            try {
                assetStorage.secure(asset.getStoragePath());
            } catch (IOException e) {
                log.warn("Cannot encrypt the file of asset {}: {}", asset.getId(), e.getMessage());
            }
        }
    }
}
