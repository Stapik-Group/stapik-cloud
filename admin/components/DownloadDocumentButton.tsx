"use client";

import { useState } from "react";
import { useTranslation } from "@/lib/i18n/LocaleProvider";
import type { components } from "@/lib/api-types";

type AdminDocumentResponse = components["schemas"]["AdminDocumentResponse"];

export function DownloadDocumentButton({
                                           extensionId,
                                           slotKey,
                                           mimeType,
                                       }: {
    extensionId: string;
    slotKey: string;
    mimeType: string;
}) {
    const { t } = useTranslation();
    const [isDownloading, setIsDownloading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    async function handleDownload() {
        setError(null);
        setIsDownloading(true);

        try {
            const response = await fetch(`/api/extensions/${extensionId}/documents/${slotKey}`);
            if (!response.ok) {
                setError(t("documents.downloadError", { status: response.status }));
                return;
            }

            const currentDocument: AdminDocumentResponse = await response.json();
            const fileBlob = new Blob([currentDocument.content], { type: `${mimeType};charset=utf-8` });
            const fileUrl = URL.createObjectURL(fileBlob);

            const downloadLink = window.document.createElement("a");
            downloadLink.href = fileUrl;
            downloadLink.download = slotKey;
            window.document.body.appendChild(downloadLink);
            downloadLink.click();
            downloadLink.remove();
            URL.revokeObjectURL(fileUrl);
        } catch {
            setError(t("documents.downloadError", { status: "network" }));
        } finally {
            setIsDownloading(false);
        }
    }

    return (
        <div className="flex items-center gap-3">
            {error && <p className="text-sm text-danger">{error}</p>}
            <button
                type="button"
                onClick={handleDownload}
                disabled={isDownloading}
                className="btn-secondary text-sm"
            >
                {isDownloading ? t("documents.downloading") : t("documents.download")}
            </button>
        </div>
    );
}
