package pl.stapik.cloud.apikey.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.stapik.cloud.apikey.*;
import pl.stapik.cloud.apikey.data.ApiKeyData;
import pl.stapik.cloud.apikey.data.ApiKeyScope;
import pl.stapik.cloud.apikey.dto.CreateApiKeyInfo;
import pl.stapik.cloud.apikey.dto.CreatedApiKey;
import pl.stapik.cloud.common.crypto.HashingService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceImplTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private ApiKeyGenerator apiKeyGenerator;

    @Mock
    private HashingService hashingService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-07-26T22:45:00Z"), ZoneId.of("UTC"));

    @InjectMocks
    private ApiKeyServiceImpl apiKeyService;

    @Test
    void shouldListApiKeysByExtensionId() {
        // given
        UUID extensionId = UUID.randomUUID();
        List<ApiKeyData> keys = List.of(new ApiKeyData(), new ApiKeyData());
        given(apiKeyRepository.findByExtensionId(extensionId)).willReturn(keys);

        // when
        List<ApiKeyData> result = apiKeyService.listByExtension(extensionId);

        // then
        assertThat(result).hasSize(2);
        verify(apiKeyRepository).findByExtensionId(extensionId);
    }

    @Test
    void shouldCreateNewApiKey() {
        // given
        UUID extensionId = UUID.randomUUID();
        ApiKeyScope scope = ApiKeyScope.READ_ONLY;
        String rawKey = "raw-secret-key";
        String hashedKey = "hashed-secret-key";
        String prefix = "pre";

        given(apiKeyGenerator.generate()).willReturn(new ApiKeyGenerator.GeneratedKey(rawKey, prefix));
        given(hashingService.hash(anyString())).willReturn(hashedKey);

        ApiKeyData savedKey = ApiKeyData.builder().id(UUID.randomUUID()).build();
        given(apiKeyRepository.save(any(ApiKeyData.class))).willReturn(savedKey);

        // when
        CreateApiKeyInfo createApiKeyInfo = CreateApiKeyInfo.builder()
                .extensionId(extensionId)
                .label("my-key")
                .scope(scope)
                .ipAllowlist("127.0.0.1")
                .expiresAt(Instant.now(clock))
                .build();

        CreatedApiKey created = apiKeyService.create(createApiKeyInfo);

        // then
        assertThat(created.apiKey()).isEqualTo(savedKey);
        assertThat(created.rawKey()).isEqualTo(rawKey);

        ArgumentCaptor<ApiKeyData> captor = ArgumentCaptor.forClass(ApiKeyData.class);
        verify(apiKeyRepository).save(captor.capture());
        assertThat(captor.getValue().getHashedKey()).isEqualTo(hashedKey);
        assertThat(captor.getValue().isRevoked()).isFalse();
        assertThat(captor.getValue().getCreatedAt()).isEqualTo("2026-07-26T22:45:00Z");
    }

    @Test
    void shouldRevokeExistingApiKey() {
        // given
        UUID extensionId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        ApiKeyData apiKey = new ApiKeyData();
        apiKey.setExtensionId(extensionId);

        given(apiKeyRepository.findById(keyId)).willReturn(Optional.of(apiKey));

        // when
        apiKeyService.revoke(extensionId, keyId);

        // then
        assertThat(apiKey.isRevoked()).isTrue();
        verify(apiKeyRepository).save(apiKey);
    }

    @Test
    void shouldThrowExceptionWhenRevokingNonExistentKey() {
        // given
        UUID extensionId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        given(apiKeyRepository.findById(keyId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> apiKeyService.revoke(extensionId, keyId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Api key not found");
    }

}