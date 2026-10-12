package pl.stapik.cloud.document;

public final class DocumentETag {

    private static final String QUOTE = "\"";

    private DocumentETag() {
    }

    public static String format(String contentHash) {
        return QUOTE + contentHash + QUOTE;
    }

    public static String parse(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        String value = headerValue.strip();
        boolean quoted = value.length() >= 2 && value.startsWith(QUOTE) && value.endsWith(QUOTE);
        return quoted ? value.substring(1, value.length() - 1) : null;
    }
}
