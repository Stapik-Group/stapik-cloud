package pl.stapik.cloud.extension.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.stapik.cloud.extension.ExtensionData;
import pl.stapik.cloud.extension.ExtensionRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtensionServiceImplTest {

    @Mock
    private ExtensionRepository extensionRepository;

    @InjectMocks
    private ExtensionServiceImpl extensionService;

    @Test
    void shouldReturnAllExtensions() {
        // Given
        ExtensionData extension1 = new ExtensionData();
        ExtensionData extension2 = new ExtensionData();
        List<ExtensionData> expectedExtensions = List.of(extension1, extension2);

        when(extensionRepository.findAll()).thenReturn(expectedExtensions);

        // When
        List<ExtensionData> actualExtensions = extensionService.listAll();

        // Then
        assertThat(actualExtensions)
                .isNotNull()
                .hasSize(2)
                .containsExactly(extension1, extension2);
    }

    @Test
    void shouldCreateExtensionWithEnabledFlagAndCurrentTime() {
        // Given
        ExtensionData extensionToSave = new ExtensionData();
        ExtensionData savedExtension = new ExtensionData();
        Instant fixedTime = Instant.parse("2026-07-18T10:00:00Z");

        when(extensionRepository.save(any(ExtensionData.class))).thenReturn(savedExtension);

        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class)) {
            mockedInstant.when(Instant::now).thenReturn(fixedTime);

            // When
            ExtensionData result = extensionService.create(extensionToSave);

            // Then
            assertThat(result).isSameAs(savedExtension);
            assertThat(extensionToSave.isEnabled()).isTrue();
            assertThat(extensionToSave.getCreatedAt()).isEqualTo(fixedTime);
            verify(extensionRepository).save(extensionToSave);
        }
    }

    @Test
    void shouldReturnExtensionWhenFoundById() {
        // Given
        UUID id = UUID.randomUUID();
        ExtensionData expectedExtension = new ExtensionData();
        when(extensionRepository.findById(id)).thenReturn(Optional.of(expectedExtension));

        // When
        ExtensionData result = extensionService.getById(id);

        // Then
        assertThat(result).isNotNull().isSameAs(expectedExtension);
    }

    @Test
    void shouldThrowExceptionWhenExtensionNotFoundById() {
        // Given
        UUID id = UUID.randomUUID();
        when(extensionRepository.findById(id)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> extensionService.getById(id))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Extension not found: " + id);
    }

    @Test
    void shouldDeleteExtensionById() {
        // Given
        UUID id = UUID.randomUUID();
        when(extensionRepository.existsById(id)).thenReturn(true);

        // When
        extensionService.delete(id);

        // Then
        verify(extensionRepository).deleteById(id);
    }
}