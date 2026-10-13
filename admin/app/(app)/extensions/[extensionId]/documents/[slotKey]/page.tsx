import { notFound, redirect } from "next/navigation";
import Link from "next/link";
import { adminApiFetch } from "@/lib/api-client";
import { DownloadDocumentButton } from "@/components/DownloadDocumentButton";
import { JsonDocumentEditor } from "@/components/JsonDocumentEditor";
import { RestoreVersionButton } from "@/components/RestoreVersionButton";
import { VersionDiff } from "@/components/VersionDiff";
import { formatBytes } from "@/lib/format-bytes";
import type { components } from "@/lib/api-types";

import { getLocale } from "@/lib/i18n/locale-cookie";
import { translate } from "@/lib/i18n/translations";

type AdminDocumentResponse = components["schemas"]["AdminDocumentResponse"];
type AdminDocumentVersionListResponse = components["schemas"]["AdminDocumentVersionListResponse"];
type AdminDocumentPartitionListResponse = components["schemas"]["AdminDocumentPartitionListResponse"];
type DocumentSlotListResponse = components["schemas"]["DocumentSlotListResponse"];
type DocumentVersionEntry = NonNullable<AdminDocumentVersionListResponse["versions"]>[number];

export default async function DocumentBrowserPage({ params, searchParams }: {
    params: Promise<{ extensionId: string; slotKey: string }>;
    searchParams: Promise<{ partition?: string }>;
}) {
    const { extensionId, slotKey } = await params;
    const partition = (await searchParams).partition || undefined;
    const partitionQuery = partition ? `?partition=${encodeURIComponent(partition)}` : "";
    const documentPath = `/api/admin/extensions/${extensionId}/documents/${slotKey}`;

    const locale = await getLocale();
    const t = (key: Parameters<typeof translate>[1], vars?: Record<string, string | number>) =>
        translate(locale, key, vars);

    const REASON_KEYS: Record<DocumentVersionEntry["reason"], Parameters<typeof translate>[1]> = {
        NORMAL_WRITE: "documents.reason.NORMAL_WRITE",
        CONFLICT_DISCARDED: "documents.reason.CONFLICT_DISCARDED",
        MANUAL_RESTORE: "documents.reason.MANUAL_RESTORE",
    };

    const [documentRes, versionsRes, slotsRes, partitionsRes] = await Promise.all([
        adminApiFetch(`${documentPath}${partitionQuery}`),
        adminApiFetch(`${documentPath}/versions${partitionQuery}`),
        adminApiFetch(`/api/admin/extensions/${extensionId}/slots`),
        adminApiFetch(`${documentPath}/partitions`),
    ]);

    if (documentRes.status === 401) {
        redirect("/login");
    }

    if (partitionsRes.status === 404 || documentRes.status === 400) {
        notFound();
    }

    if (!partitionsRes.ok) {
        return (
            <main className="p-8">
                <p className="text-danger">{t("documents.fetchError")}</p>
            </main>
        );
    }

    const partitionsData: AdminDocumentPartitionListResponse = await partitionsRes.json();
    const partitions = partitionsData.partitions;
    const partitionTabs = (
        <PartitionTabs
            extensionId={extensionId}
            slotKey={slotKey}
            partitions={partitions}
            activePartition={partition}
            locale={locale}
            mainLabel={t("documents.mainDocument")}
        />
    );

    if (documentRes.status === 404) {
        if (partition || partitions.length === 0) {
            notFound();
        }

        return (
            <main className="p-8 space-y-6 max-w-4xl">
                <div>
                    <Link href={`/extensions/${extensionId}`} className="text-sm text-primary">
                        {t("documents.backToExtension")}
                    </Link>
                    <h1 className="text-xl font-semibold mt-2">{slotKey}</h1>
                </div>
                {partitionTabs}
                <p className="text-text-muted">{t("documents.noMainDocument")}</p>
            </main>
        );
    }

    if (!documentRes.ok || !versionsRes.ok || !slotsRes.ok) {
        return (
            <main className="p-8">
                <p className="text-danger">{t("documents.fetchError")}</p>
            </main>
        );
    }

    const document: AdminDocumentResponse = await documentRes.json();
    const versionsData: AdminDocumentVersionListResponse = await versionsRes.json();
    const versions = versionsData.versions ?? [];
    const slotsData: DocumentSlotListResponse = await slotsRes.json();
    const slot = (slotsData.slots ?? []).find((slotEntry) => slotEntry.slotKey === slotKey);
    const isJsonEditable = slot?.contentType === "JSON" && !document.encrypted;
    const versionOptions = versions.map((version) => ({
        id: version.id,
        label: `${new Date(version.savedAt).toLocaleString(locale)} · ${t(REASON_KEYS[version.reason])}`,
    }));
    const downloadMimeType = slot?.contentType === "JSON" ? "application/json" : "text/plain";

    return (
        <main className="p-8 space-y-6 max-w-4xl">
            <div>
                <Link href={`/extensions/${extensionId}`} className="text-sm text-primary">
                    {t("documents.backToExtension")}
                </Link>
                <h1 className="text-xl font-semibold mt-2">
                    {slotKey}
                    {partition && <span className="text-text-muted"> / {partition}</span>}
                </h1>
                <p className="text-sm text-text-muted">
                    {t("documents.lastUpdated", {
                        date: new Date(document.updatedAt).toLocaleString(locale),
                    })}
                </p>
            </div>

            {partitions.length > 0 && partitionTabs}

            <section className="space-y-2">
                <div className="flex items-center justify-between">
                    <h2 className="font-medium">{t("documents.currentContent")}</h2>
                    <DownloadDocumentButton
                        extensionId={extensionId}
                        slotKey={slotKey}
                        partition={partition}
                        mimeType={downloadMimeType}
                    />
                </div>
                {document.encrypted && (
                    <p className="text-sm text-text-muted">{t("documents.encryptedNotice")}</p>
                )}
                {isJsonEditable ? (
                    <JsonDocumentEditor
                        key={document.contentHash}
                        extensionId={extensionId}
                        slotKey={slotKey}
                        partition={partition}
                        storedContent={document.content}
                        contentHash={document.contentHash}
                    />
                ) : (
                    <pre className="panel overflow-auto max-h-96 text-sm whitespace-pre-wrap break-all">
          {document.content}
        </pre>
                )}
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
                                    {version.deviceLabel && (
                                        <p className="text-xs text-text-muted">
                                            {t("documents.writtenBy", { device: version.deviceLabel })}
                                        </p>
                                    )}
                                </div>
                                <RestoreVersionButton
                                    extensionId={extensionId}
                                    slotKey={slotKey}
                                    partition={partition}
                                    versionId={version.id}
                                />
                            </div>
                        ))}
                    </div>
                )}
            </section>

            {isJsonEditable && versions.length > 0 && (
                <section className="space-y-2">
                    <h2 className="font-medium">{t("documents.diffTitle")}</h2>
                    <VersionDiff
                        extensionId={extensionId}
                        slotKey={slotKey}
                        partition={partition}
                        versions={versionOptions}
                    />
                </section>
            )}
        </main>
    );
}
type PartitionSummary = AdminDocumentPartitionListResponse["partitions"][number];

function PartitionTabs({
                           extensionId,
                           slotKey,
                           partitions,
                           activePartition,
                           locale,
                           mainLabel,
                       }: {
    extensionId: string;
    slotKey: string;
    partitions: PartitionSummary[];
    activePartition: string | undefined;
    locale: string;
    mainLabel: string;
}) {
    const basePath = `/extensions/${extensionId}/documents/${slotKey}`;
    const tabs = [
        { key: undefined, label: mainLabel, href: basePath, hint: undefined },
        ...partitions.map((entry) => ({
            key: entry.partition,
            label: entry.partition,
            href: `${basePath}?partition=${encodeURIComponent(entry.partition)}`,
            hint: formatBytes(entry.sizeBytes, locale),
        })),
    ];

    return (
        <nav className="flex flex-wrap gap-2">
            {tabs.map((tab) => (
                <Link
                    key={tab.key ?? "main"}
                    href={tab.href}
                    className={`rounded-lg border px-3 py-1.5 text-sm ${
                        tab.key === activePartition
                            ? "bg-primary text-white border-primary"
                            : "border-border bg-surface hover:border-primary"
                    }`}
                >
                    {tab.label}
                    {tab.hint && <span className="ml-2 text-xs opacity-70">{tab.hint}</span>}
                </Link>
            ))}
        </nav>
    );
}
