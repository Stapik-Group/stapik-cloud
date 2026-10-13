package pl.stapik.cloud.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Converter
@Component
@RequiredArgsConstructor
public class EncryptedContentConverter implements AttributeConverter<String, String> {

    private final ContentEncryptor contentEncryptor;

    @Override
    public String convertToDatabaseColumn(String content) {
        return content == null ? null : contentEncryptor.encrypt(content);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return stored == null ? null : contentEncryptor.decrypt(stored);
    }
}
