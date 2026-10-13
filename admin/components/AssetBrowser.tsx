"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslation } from "@/lib/i18n/LocaleProvider";
import { formatBytes } from "@/lib/format-bytes";
import type { components } from "@/lib/api-types";

type Asset = components["schemas"]["AdminAssetResponse"];

const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

export function AssetBrowser({
                                 extensionId,
                                 slotKey,
                                 assets,
                             }: {
    extensionId: string;
    slotKey: string;
    assets: Asset[];
}) {
    const router = useRouter();
    const { t, locale } = useTranslation();
    const [previewed, setPreviewed] = useState<Asset | null>(null);
    const [busyFilename, setBusyFilename] = useState<string | null>(null);
    const [isUploading, setIsUploading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    function assetUrl(filename: string, inline = false) {
        const base = `/api/extensions/${extensionId}/assets/${encodeURIComponent(slotKey)}/${encodeURIComponent(filename)}`;
        return inline ? `${base}?inline=true` : base;
    }

    async function handleDelete(asset: Asset) {
        if (!confirm(t("assets.deleteConfirm", { filename: asset.filename }))) {
            return;
        }

        setError(null);
        setBusyFilename(asset.filename);
        try {
            const response = await fetch(assetUrl(asset.filename), { method: "DELETE" });
            if (!response.ok) {
                setError(t("assets.deleteError", { status: response.status }));
                return;
            }
            if (previewed?.filename === asset.filename) {
                setPreviewed(null);
            }
            router.refresh();
        } catch {
            setError(t("assets.deleteError", { status: "network" }));
        } finally {
            setBusyFilename(null);
        }
    }

    async function handleUpload(event: React.ChangeEvent<HTMLInputElement>) {
        const input = event.target;
        const file = input.files?.[0];
        input.value = "";
        if (!file) {
            return;
        }

        setError(null);
        if (file.size > MAX_UPLOAD_BYTES) {
            setError(t("assets.tooLarge", { limit: formatBytes(MAX_UPLOAD_BYTES, locale) }));
            return;
        }
        const replacesExisting = assets.some((asset) => asset.filename === file.name);
        if (replacesExisting && !confirm(t("assets.overwriteConfirm", { filename: file.name }))) {
            return;
        }

        setIsUploading(true);
        try {
            const formData = new FormData();
            formData.append("file", file);
            const response = await fetch(assetUrl(file.name), { method: "PUT", body: formData });
            if (!response.ok) {
                const problem = await response.json().catch(() => null);
                setError(problem?.detail ?? t("assets.uploadError", { status: response.status }));
                return;
            }
            router.refresh();
        } catch {
            setError(t("assets.uploadError", { status: "network" }));
        } finally {
            setIsUploading(false);
        }
    }

    return (
        <div className="space-y-4">
            <div className="flex items-center gap-3">
                <label className="btn-primary cursor-pointer text-sm">
                    {isUploading ? t("assets.uploading") : t("assets.upload")}
                    <input type="file" className="hidden" disabled={isUploading} onChange={handleUpload} />
                </label>
                {error && <p className="text-sm text-danger">{error}</p>}
            </div>

            {previewed && (
                <div className="panel space-y-3">
                    <div className="flex items-center justify-between">
                        <p className="font-medium text-sm">{previewed.filename}</p>
                        <button type="button" className="text-sm text-text-muted" onClick={() => setPreviewed(null)}>
                            {t("assets.closePreview")}
                        </button>
                    </div>
                    {previewed.mimeType === "application/pdf" ? (
                        <iframe
                            src={assetUrl(previewed.filename, true)}
                            title={previewed.filename}
                            className="w-full h-[70vh] rounded-lg border border-border"
                        />
                    ) : (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img
                            src={assetUrl(previewed.filename, true)}
                            alt={previewed.filename}
                            className="max-h-[70vh] max-w-full mx-auto"
                        />
                    )}
                </div>
            )}

            {assets.length === 0 ? (
                <p className="text-text-muted">{t("assets.empty")}</p>
            ) : (
                <div className="panel divide-y divide-border">
                    {assets.map((asset) => (
                        <div key={asset.filename} className="py-3 first:pt-0 last:pb-0 flex items-center justify-between gap-4">
                            <div className="min-w-0">
                                <p className="font-medium text-sm truncate">{asset.filename}</p>
                                <p className="text-xs text-text-muted">
                                    {asset.mimeType} · {formatBytes(asset.sizeBytes, locale)} ·{" "}
                                    {new Date(asset.updatedAt).toLocaleString(locale)}
                                </p>
                            </div>
                            <div className="flex items-center gap-3 shrink-0 text-sm">
                                {asset.previewable && (
                                    <button type="button" className="text-primary" onClick={() => setPreviewed(asset)}>
                                        {t("assets.preview")}
                                    </button>
                                )}
                                <a href={assetUrl(asset.filename)} className="text-primary">
                                    {t("assets.download")}
                                </a>
                                <button
                                    type="button"
                                    className="text-danger"
                                    disabled={busyFilename === asset.filename}
                                    onClick={() => handleDelete(asset)}
                                >
                                    {busyFilename === asset.filename ? t("slots.deleting") : t("slots.deleteButton")}
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
