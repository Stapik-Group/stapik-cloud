package pl.stapik.cloud.extension;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExtensionRepository extends JpaRepository<ExtensionData, UUID> {
    boolean existsBySlug(String slug);
}
