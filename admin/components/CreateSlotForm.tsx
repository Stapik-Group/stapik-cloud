"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import type { components } from "@/lib/api-types";
import { useTranslation } from "@/lib/i18n/LocaleProvider";

type ContentType = components["schemas"]["ContentType"];
type ConflictStrategy = components["schemas"]["ConflictStrategy"];
type CreateDocumentSlotRequest = components["schemas"]["CreateDocumentSlotRequest"];

const CONTENT_TYPES: ContentType[] = ["JSON", "TEXT", "BINARY", "BINARY_COLLECTION"];
const CONFLICT_STRATEGIES: ConflictStrategy[] = [
    "LAST_WRITE_WINS",
    "LAST_WRITE_WINS_WITH_SHADOW_COPY",
];

export function CreateSlotForm({ extensionId }: { extensionId: string }) {
    const router = useRouter();
    const { t } = useTranslation();
    const [slotKey, setSlotKey] = useState("");
    const [contentType, setContentType] = useState<ContentType>("JSON");
    const [versioningEnabled, setVersioningEnabled] = useState(true);
    const [maxVersionsRetained, setMaxVersionsRetained] = useState(20);
    const [encryptionRequired, setEncryptionRequired] = useState(false);
    const [conflictStrategy, setConflictStrategy] = useState<ConflictStrategy | "">("");
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        setIsSubmitting(true);

        const payload: CreateDocumentSlotRequest = {
            slotKey,
            contentType,
            versioningEnabled,
            maxVersionsRetained,
            encryptionRequired,
            ...(conflictStrategy ? { conflictStrategy } : {}),
        };

        try {
            const response = await fetch(`/api/extensions/${extensionId}/slots`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });

            if (!response.ok) {
                setError(t("slots.createError"));
                return;
            }

            setSlotKey("");
            router.refresh();
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <form onSubmit={handleSubmit} className="panel space-y-3">
            <h3 className="font-medium text-sm">{t("slots.addSlot")}</h3>

            <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1">
                    <label htmlFor="slotKey" className="text-sm text-text-muted">
                        {t("slots.slotKey")}
                    </label>
                    <input
                        id="slotKey"
                        type="text"
                        required
                        value={slotKey}
                        onChange={(e) => setSlotKey(e.target.value)}
                        className="input w-full"
                        placeholder="calendar.json"
                    />
                </div>

                <div className="space-y-1">
                    <label htmlFor="contentType" className="text-sm text-text-muted">
                        {t("slots.contentType")}
                    </label>
                    <select
                        id="contentType"
                        value={contentType}
                        onChange={(e) => setContentType(e.target.value as ContentType)}
                        className="input w-full"
                    >
                        {CONTENT_TYPES.map((type) => (
                            <option key={type} value={type}>
                                {type}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="space-y-1">
                    <label htmlFor="conflictStrategy" className="text-sm text-text-muted">
                        {t("slots.conflictStrategy")}
                    </label>
                    <select
                        id="conflictStrategy"
                        value={conflictStrategy}
                        onChange={(e) =>
                            setConflictStrategy(e.target.value as ConflictStrategy | "")
                        }
                        className="input w-full"
                    >
                        <option value="">{t("slots.default")}</option>
                        {CONFLICT_STRATEGIES.map((strategy) => (
                            <option key={strategy} value={strategy}>
                                {strategy}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="space-y-1">
                    <label htmlFor="maxVersionsRetained" className="text-sm text-text-muted">
                        {t("slots.maxVersionsRetained")}
                    </label>
                    <input
                        id="maxVersionsRetained"
                        type="number"
                        min={1}
                        value={maxVersionsRetained}
                        onChange={(e) => setMaxVersionsRetained(Number(e.target.value))}
                        className="input w-full"
                    />
                </div>
            </div>

            <div className="flex gap-4">
                <label className="flex items-center gap-2 text-sm">
                    <input
                        type="checkbox"
                        checked={versioningEnabled}
                        onChange={(e) => setVersioningEnabled(e.target.checked)}
                    />
                    {t("slots.versioning")}
                </label>
                <label className="flex items-center gap-2 text-sm">
                    <input
                        type="checkbox"
                        checked={encryptionRequired}
                        onChange={(e) => setEncryptionRequired(e.target.checked)}
                    />
                    {t("slots.encryption")}
                </label>
            </div>

            {error && <p className="text-sm text-danger">{error}</p>}

            <button type="submit" disabled={isSubmitting} className="btn-primary">
                {isSubmitting ? t("slots.submitting") : t("slots.submit")}
            </button>
        </form>
    );
}