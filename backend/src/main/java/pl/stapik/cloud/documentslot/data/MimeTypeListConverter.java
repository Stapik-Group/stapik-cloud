package pl.stapik.cloud.documentslot.data;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

@Converter
public class MimeTypeListConverter implements AttributeConverter<List<String>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(List<String> mimeTypes) {
        return mimeTypes == null || mimeTypes.isEmpty() ? null : String.join(SEPARATOR, mimeTypes);
    }

    @Override
    public List<String> convertToEntityAttribute(String column) {
        return column == null || column.isBlank() ? List.of() : Arrays.asList(column.split(SEPARATOR));
    }
}
