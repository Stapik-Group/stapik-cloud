package pl.stapik.cloud.asset;

import java.util.Set;

public final class AssetPreviewPolicy {

    private static final Set<String> PREVIEWABLE_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "application/pdf"
    );

    private AssetPreviewPolicy() {
    }

    public static boolean isPreviewable(String mimeType) {
        return PREVIEWABLE_MIME_TYPES.contains(mimeType);
    }
}
