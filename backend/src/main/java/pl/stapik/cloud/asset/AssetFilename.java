package pl.stapik.cloud.asset;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Pattern;

public final class AssetFilename {

    private static final int MAX_LENGTH_BYTES = 255;
    private static final String FORBIDDEN_CHARACTERS = "/\\:*?\"<>|";
    private static final Pattern RESERVED_DEVICE_NAME = Pattern.compile("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]");

    private AssetFilename() {
    }

    public static String normalize(String filename) {
        String normalized = Normalizer.normalize(filename, Normalizer.Form.NFC);
        requireValid(normalized);
        return normalized;
    }

    private static void requireValid(String filename) {
        if (filename.isBlank()) {
            throw new InvalidAssetFilenameException("must not be blank");
        }
        if (filename.getBytes(StandardCharsets.UTF_8).length > MAX_LENGTH_BYTES) {
            throw new InvalidAssetFilenameException("must not exceed %d bytes in UTF-8".formatted(MAX_LENGTH_BYTES));
        }
        filename.codePoints()
                .filter(AssetFilename::isForbiddenCodePoint)
                .findFirst()
                .ifPresent(codePoint -> {
                    throw new InvalidAssetFilenameException("contains forbidden character U+%04X".formatted(codePoint));
                });
        if (!filename.equals(filename.stripLeading()) || filename.endsWith(" ") || filename.endsWith(".")) {
            throw new InvalidAssetFilenameException("must not start with a space or end with a space or dot");
        }
        if (RESERVED_DEVICE_NAME.matcher(baseName(filename)).matches()) {
            throw new InvalidAssetFilenameException("is a reserved system name");
        }
    }

    private static boolean isForbiddenCodePoint(int codePoint) {
        if (FORBIDDEN_CHARACTERS.indexOf(codePoint) >= 0) {
            return true;
        }
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE,
                 Character.UNASSIGNED, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
            default -> false;
        };
    }

    private static String baseName(String filename) {
        int firstDot = filename.indexOf('.');
        String base = firstDot < 0 ? filename : filename.substring(0, firstDot);
        return base.stripTrailing();
    }
}
