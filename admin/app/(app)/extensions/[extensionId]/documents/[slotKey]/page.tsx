import { notFound, redirect } from "next/navigation";
import Link from "next/link";
import { adminApiFetch } from "@/lib/api-client";
import { RestoreVersionButton } from "@/components/RestoreVersionButton";
import type { components } from "@/lib/api-types";

import { getLocale } from "@/lib/i18n/locale-cookie";
import { translate } from "@/lib/i18n/translations";

type AdminDocumentResponse = components["schemas"]["AdminDocumentResponse"];
type AdminDocumentVersionListResponse = components["schemas"]["AdminDocumentVersionListResponse"];
type DocumentVersionEntry = NonNullable<AdminDocumentVersionListResponse["versions"]>[number];

export default async function DocumentBrowserPage({ params, }: {
    params: Promise<{ extensionId: string; slotKey: string }>;
}) {
    const { extensionId, slotKey } = await params;

    const locale = await getLocale();
    const t = (key: Parameters<typeof translate>[1], vars?: Record<string, string | number>) =>
        translate(locale, key, vars);

    const REASON_KEYS: Record<DocumentVersionEntry["reason"], Parameters<typeof translate>[1]> = {
        NORMAL_WRITE: "documents.reason.NORMAL_WRITE",
        CONFLICT_DISCARDED: "documents.reason.CONFLICT_DISCARDED",
        MANUAL_RESTORE: "documents.reason.MANUAL_RESTORE",
    };

    const [documentRes, versionsRes] = await Promise.all([
        adminApiFetch(`/api/admin/extensions/${extensionId}/documents/${slotKey}`),
        adminApiFetch(
            `/api/admin/extensions/${extensionId}/documents/${slotKey}/versions`,
        ),
    ]);

    if (documentRes.status === 401) {
        redirect("/login");
    }

    if (documentRes.status === 404) {
        notFound();
    }

    if (!documentRes.ok || !versionsRes.ok) {
        return (
            <main className="p-8">
                <p className="text-danger">{t("documents.fetchError")}</p>
            </main>
        );
    }

    const document: AdminDocumentResponse = await documentRes.json();
    const versionsData: AdminDocumentVersionListResponse = await versionsRes.json();
    const versions = versionsData.versions ?? [];

    return (
        <main className="p-8 space-y-6 max-w-4xl">
            <div>
                <Link href={`/extensions/${extensionId}`} className="text-sm text-primary">
                    {t("documents.backToExtension")}
                </Link>
                <h1 className="text-xl font-semibold mt-2">{slotKey}</h1>
                <p className="text-sm text-text-muted">
                    {t("documents.lastUpdated", {
                        date: new Date(document.updatedAt).toLocaleString(locale),
                    })}
                </p>
            </div>

            <section className="space-y-2">
                <h2 className="font-medium">{t("documents.currentContent")}</h2>
                <pre className="panel overflow-auto max-h-96 text-sm whitespace-pre-wrap break-all">
          {document.content}
        </pre>
            </section>

            <section className="space-y-2">
                <h2 className="font-medium">{t("documents.versionHistory")}</h2>
                {versions.length === 0 ? (
                    <p className="text-sm text-text-muted">{t("documents.noHistory")}</p>
                ) : (
                    <div className="space-y-2">
                        {versions.map((version) => (
                            <div key={version.id} className="panel flex items-center justify-between">
                                <div>
                                    <p className="text-sm">
                                        {new Date(version.savedAt).toLocaleString(locale)}
                                    </p>
                                    <p
                                        className={`text-xs ${
                                            version.reason === "CONFLICT_DISCARDED"
                                                ? "text-danger"
                                                : "text-text-muted"
                                        }`}
                                    >
                                        {t(REASON_KEYS[version.reason])}
                                    </p>
                                </div>
                                <RestoreVersionButton
                                    extensionId={extensionId}
                                    slotKey={slotKey}
                                    versionId={version.id}
                                />
                            </div>
                        ))}
                    </div>
                )}
            </section>
        </main>
    );
}