package pl.stapik.cloud.extension.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import pl.stapik.cloud.audit.Auditing;
import pl.stapik.cloud.audit.data.AuditAction;
import pl.stapik.cloud.common.error.DuplicateResourceException;
import pl.stapik.cloud.extension.ExtensionData;
import pl.stapik.cloud.extension.ExtensionRepository;
import pl.stapik.cloud.extension.ExtensionService;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExtensionServiceImpl implements ExtensionService {

    private final ExtensionRepository extensionRepository;

    @Override
    public List<ExtensionData> listAll() {
        return extensionRepository.findAll();
    }

    @Override
    @Auditing(action = AuditAction.EXTENSION_CREATED, extensionId = "#result.id")
    public ExtensionData create(ExtensionData extension) {
        extension.setEnabled(true);
        extension.setCreatedAt(Instant.now());
        try {
            return extensionRepository.save(extension);
        } catch (DataIntegrityViolationException ex) {
            throw DuplicateResourceException.mapToDuplicateResourceException(ex);
        }
    }

    @Override
    public ExtensionData getById(UUID id) {
        return extensionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Extension not found: " + id));
    }

    @Override
    @Auditing(action = AuditAction.EXTENSION_DELETED)
    public void delete(UUID id) {
        if (!extensionRepository.existsById(id)) {
            throw new NoSuchElementException("Extension not found: " + id);
        }

        extensionRepository.deleteById(id);
    }
}
