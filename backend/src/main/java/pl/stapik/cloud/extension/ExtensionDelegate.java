package pl.stapik.cloud.extension;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.admin.api.ExtensionsApiDelegate;
import pl.stapik.cloud.admin.data.*;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExtensionDelegate implements ExtensionsApiDelegate {

    private final ExtensionService extensionService;
    private final ExtensionMapper extensionMapper;

    @Override
    public ResponseEntity<ExtensionListResponse> listExtensions() {
        List<ExtensionResponse> extensions = extensionService.listAll().stream()
                .map(extensionMapper::toResponse)
                .toList();

        return ResponseEntity.ok(new ExtensionListResponse().extensions(extensions));
    }

    @Override
    public ResponseEntity<ExtensionResponse> createExtension(CreateExtensionRequest createExtensionRequest) {
        ExtensionData created = extensionService.create(extensionMapper.toEntity(createExtensionRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(extensionMapper.toResponse(created));
    }

    @Override
    public ResponseEntity<ExtensionResponse> getExtension(UUID extensionId) {
        return ResponseEntity.ok(extensionMapper.toResponse(extensionService.getById(extensionId)));
    }

    @Override
    public ResponseEntity<Void> deleteExtension(UUID extensionId) {
        extensionService.delete(extensionId);
        return ResponseEntity.noContent().build();
    }
}
