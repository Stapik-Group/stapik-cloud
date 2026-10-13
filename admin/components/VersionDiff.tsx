"use client";

import { useState } from "react";
import { documentApiUrl } from "@/lib/document-api-url";
import { useTranslation } from "@/lib/i18n/LocaleProvider";
import { formatForEditing } from "@/lib/json-format";
import type { components } from "@/lib/api-types";

type AdminDocumentDiffResponse = components["schemas"]["AdminDocumentDiffResponse"];
type AdminDocumentDiffSide = components["schemas"]["AdminDocumentDiffSide"];
type AdminDocumentDiffChange = components["schemas"]["AdminDocumentDiffChange"];

const CURRENT_CONTENT = "";

const CHANGE_STYLES: Record<AdminDocumentDiffChange["type"], string> = {
    ADDED: "text-success",
    REMOVED: "text-danger",
    CHANGED: "text-primary",
};

export type VersionOption = {
    id: string;
    label: string;
};

export function VersionDiff({
                                extensionId,
                                slotKey,
                                partition,
                                versions,
                            }: {
    extensionId: string;
    slotKey: string;
    partition?: string;
    versions: VersionOption[];
}) {
    const { t } = useTranslation();
    const [fromVersionId, setFromVersionId] = useState(versions[0].id);
    const [toVersionId, setToVersionId] = useState(CURRENT_CONTENT);
    const [diff, setDiff] = useState<AdminDocumentDiffResponse | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(false);

    async function handleCompare() {
        setError(null);
        setIsLoading(true);

        try {
            const query = new URLSearchParams({ from: fromVersionId });
            if (toVersionId !== CURRENT_CONTENT) {
                query.set("to", toVersionId);
            }

            const response = await fetch(documentApiUrl(extensionId, slotKey, "/versions/diff", partition, query));

            if (!response.ok) {
                setDiff(null);
                setError(t("documents.diffError", { status: response.status }));
                return;
            }

            setDiff(await response.json());
        } finally {
            setIsLoading(false);
        }
    }

    return (
        <div className="space-y-4">
            <div className="flex flex-wrap items-end gap-3">
                <label className="space-y-1 text-sm">
                    <span className="block text-text-muted">{t("documents.diffFrom")}</span>
                    <select
                        value={fromVersionId}
                        onChange={(event) => setFromVersionId(event.target.value)}
                        className="input"
                    >
                        {versions.map((version) => (
                            <option key={version.id} value={version.id}>{version.label}</option>
                        ))}
                    </select>
                </label>
                <label className="space-y-1 text-sm">
                    <span className="block text-text-muted">{t("documents.diffTo")}</span>
                    <select
                        value={toVersionId}
                        onChange={(event) => setToVersionId(event.target.value)}
                        className="input"
                    >
                        <option value={CURRENT_CONTENT}>{t("documents.diffCurrent")}</option>
                        {versions.map((version) => (
                            <option key={version.id} value={version.id}>{version.label}</option>
                        ))}
                    </select>
                </label>
                <button
                    type="button"
                    onClick={handleCompare}
                    disabled={isLoading}
                    className="btn-secondary text-sm"
                >
                    {isLoading ? t("documents.diffComparing") : t("documents.diffCompare")}
                </button>
            </div>

            {error && <p className="text-sm text-danger">{error}</p>}

            {diff && <DiffResult diff={diff} />}
        </div>
    );
}

function DiffResult({ diff }: { diff: AdminDocumentDiffResponse }) {
    const { t } = useTranslation();

    return (
        <div className="space-y-4">
            {diff.changes.length === 0 ? (
                <p className="text-sm text-text-muted">{t("documents.diffNoChanges")}</p>
            ) : (
                <ul className="space-y-2">
                    {diff.changes.map((change) => (
                        <li key={`${change.type}:${change.path}`} className="panel space-y-1 text-sm">
                            <p className="font-mono break-all">
                                {change.path === "" ? t("documents.diffRoot") : change.path}
                                {" "}
                                <span className={`text-xs ${CHANGE_STYLES[change.type]}`}>
                                    {t(`documents.diffType.${change.type}`)}
                                </span>
                            </p>
                            {typeof change.before === "string" && (
                                <p className="font-mono text-danger break-all">− {change.before}</p>
                            )}
                            {typeof change.after === "string" && (
                                <p className="font-mono text-success break-all">+ {change.after}</p>
                            )}
                        </li>
                    ))}
                </ul>
            )}

            <div className="grid gap-4 md:grid-cols-2">
                <JsonPreview side={diff.from} />
                <JsonPreview side={diff.to} />
            </div>
        </div>
    );
}

function JsonPreview({ side }: { side: AdminDocumentDiffSide }) {
    const { t, locale } = useTranslation();

    return (
        <div className="space-y-1 min-w-0">
            <p className="text-sm text-text-muted">
                {side.versionId
                    ? new Date(side.savedAt).toLocaleString(locale)
                    : t("documents.diffCurrent")}
            </p>
            <pre
                style={{ tabSize: 2 }}
                className="panel overflow-auto max-h-96 text-sm whitespace-pre-wrap break-all"
            >
                {formatForEditing(side.content)}
            </pre>
        </div>
    );
}
