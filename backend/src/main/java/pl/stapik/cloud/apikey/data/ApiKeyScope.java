package pl.stapik.cloud.apikey.data;

public enum ApiKeyScope {
    READ_ONLY,
    READ_WRITE;

    public static ApiKeyScope fromValue(String name) {
        return ("READ_ONLY".equals(name) || "ONLY".equals(name)) ? READ_ONLY : READ_WRITE;
    }
}
