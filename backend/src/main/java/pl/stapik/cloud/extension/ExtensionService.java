package pl.stapik.cloud.extension;

import java.util.List;
import java.util.UUID;

public interface ExtensionService {
    List<ExtensionData> listAll();
    ExtensionData create(ExtensionData extension);
    ExtensionData getById(UUID id);
    void delete(UUID id);
}
