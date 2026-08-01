"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import type { components } from "@/lib/api-types";
import { useTranslation } from "@/lib/i18n/LocaleProvider";

type ApiKeyScope = components["schemas"]["ApiKeyScope"];
type CreateApiKeyRequest = components["schemas"]["CreateApiKeyRequest"];
type ApiKeyCreatedResponse = components["schemas"]["ApiKeyCreatedResponse"];

const SCOPES: ApiKeyScope[] = ["READ_ONLY", "READ_WRITE"];

export function CreateKeyForm({ extensionId }: { extensionId: string }) {
    const router = useRouter();
    const { t } = useTranslation();
    const [label, setLabel] = useState("");
    const [scope, setScope] = useState<ApiKeyScope>("READ_WRITE");
    const [ipAllowlist, setIpAllowlist] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [createdKey, setCreatedKey] = useState<ApiKeyCreatedResponse | null>(null);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        setIsSubmitting(true);

        const payload: CreateApiKeyRequest = {
            label,
            scope,
            ...(ipAllowlist ? { ipAllowlist } : {}),
        };

        try {
            const response = await fetch(`/api/extensions/${extensionId}/keys`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });

            if (!response.ok) {
                setError(t("apiKeys.createError"));
                return;
            }

            const data: ApiKeyCreatedResponse = await response.json();
            setCreatedKey(data);
        } finally {
            setIsSubmitting(false);
        }
    }

    function handleClose() {
        setCreatedKey(null);
        setLabel("");
        setIpAllowlist("");
        router.refresh();
    }

    if (createdKey) {
        return (
            <div className="panel space-y-3">
                <h3 className="font-medium text-sm">{t("apiKeys.createdTitle")}</h3>
                <p className="text-sm text-danger">{t("apiKeys.createdWarning")}</p>
                <code className="block break-all bg-background border border-border rounded-lg p-2 text-sm">
                    {createdKey.rawKey}
                </code>
                <div className="flex gap-2">
                    <button
                        type="button"
                        className="btn-secondary"
                        onClick={() => navigator.clipboard.writeText(createdKey.rawKey)}
                    >
                        {t("apiKeys.copy")}
                    </button>
                    <button type="button" className="btn-primary" onClick={handleClose}>
                        {t("apiKeys.confirmClose")}
                    </button>
                </div>
            </div>
        );
    }

    return (
        <form onSubmit={handleSubmit} className="panel space-y-3">
            <h3 className="font-medium text-sm">{t("apiKeys.newKey")}</h3>

            <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1">
                    <label htmlFor="label" className="text-sm text-text-muted">
                        {t("apiKeys.label")}
                    </label>
                    <input
                        id="label"
                        type="text"
                        required
                        value={label}
                        onChange={(e) => setLabel(e.target.value)}
                        className="input w-full"
                        placeholder="desktop-laptop"
                    />
                </div>

                <div className="space-y-1">
                    <label htmlFor="scope" className="text-sm text-text-muted">
                        {t("apiKeys.scope")}
                    </label>
                    <select
                        id="scope"
                        value={scope}
                        onChange={(e) => setScope(e.target.value as ApiKeyScope)}
                        className="input w-full"
                    >
                        {SCOPES.map((s) => (
                            <option key={s} value={s}>
                                {s}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="space-y-1 sm:col-span-2">
                    <label htmlFor="ipAllowlist" className="text-sm text-text-muted">
                        {t("apiKeys.ipAllowlist")}
                    </label>
                    <input
                        id="ipAllowlist"
                        type="text"
                        value={ipAllowlist}
                        onChange={(e) => setIpAllowlist(e.target.value)}
                        className="input w-full"
                        placeholder="100.64.0.0/10"
                    />
                </div>
            </div>

            {error && <p className="text-sm text-danger">{error}</p>}

            <button type="submit" disabled={isSubmitting} className="btn-primary">
                {isSubmitting ? t("apiKeys.submitting") : t("apiKeys.submit")}
            </button>
        </form>
    );
}