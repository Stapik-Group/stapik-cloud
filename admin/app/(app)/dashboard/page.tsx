import { redirect } from "next/navigation";
import Link from "next/link";
import { adminApiFetch } from "@/lib/api-client";
import type { components } from "@/lib/api-types";

import { getLocale } from "@/lib/i18n/locale-cookie";
import { translate } from "@/lib/i18n/translations";
import { formatBytes } from "@/lib/format-bytes";
import { WritesChart } from "@/components/WritesChart";

type Extension = components["schemas"]["ExtensionResponse"];
type ExtensionListResponse = components["schemas"]["ExtensionListResponse"];
type DashboardStats = components["schemas"]["DashboardStatsResponse"];

export default async function DashboardPage() {
    const locale = await getLocale();
    const t = (key: Parameters<typeof translate>[1], vars?: Record<string, string | number>) =>
        translate(locale, key, vars);

    const [response, statsResponse] = await Promise.all([
        adminApiFetch("/api/admin/extensions"),
        adminApiFetch("/api/admin/dashboard/stats"),
    ]);

    if (response.status === 401 || statsResponse.status === 401) {
        redirect("/login");
    }

    if (!response.ok) {
        return (
            <main className="p-8">
                <p className="text-danger">{t("dashboard.fetchError")}</p>
            </main>
        );
    }

    const data: ExtensionListResponse = await response.json();
    const extensions: Extension[] = data.extensions ?? [];
    const stats: DashboardStats | null = statsResponse.ok ? await statsResponse.json() : null;

    return (
        <main className="p-8 space-y-6">
            <div className="flex items-center justify-between">
                <h1 className="text-xl font-semibold">{t("dashboard.title")}</h1>
                <Link href="/extensions/new" className="btn-primary">
                    {t("dashboard.newExtension")}
                </Link>
            </div>

            {stats ? (
                <StatsSection stats={stats} locale={locale} t={t} />
            ) : (
                <p className="text-danger">{t("dashboard.statsFetchError")}</p>
            )}

            <h2 className="font-medium">{t("dashboard.extensionsTitle")}</h2>

            {extensions.length === 0 ? (
                <p className="text-text-muted">{t("dashboard.empty")}</p>
            ) : (
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                    {extensions.map((extension) => (
                        <Link
                            key={extension.id}
                            href={`/extensions/${extension.id}`}
                            className="panel block hover:border-primary transition-colors"
                        >
                            <div className="flex items-center justify-between">
                                <h2 className="font-medium">{extension.displayName}</h2>
                                <span
                                    className={
                                        extension.enabled
                                            ? "text-xs text-success"
                                            : "text-xs text-text-muted"
                                    }
                                >
                  {extension.enabled ? t("dashboard.active") : t("dashboard.disabled")}
                </span>
                            </div>
                            <p className="text-sm text-text-muted">{extension.slug}</p>
                        </Link>
                    ))}
                </div>
            )}
        </main>
    );
}

type Translate = (key: Parameters<typeof translate>[1], vars?: Record<string, string | number>) => string;

function StatsSection({ stats, locale, t }: { stats: DashboardStats; locale: string; t: Translate }) {
    const totalBytes = stats.documentStorageBytes + stats.assetStorageBytes;

    return (
        <section className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-3">
                <StatTile
                    label={t("dashboard.storage")}
                    value={formatBytes(totalBytes, locale)}
                    hint={t("dashboard.storageBreakdown", {
                        documents: formatBytes(stats.documentStorageBytes, locale),
                        assets: formatBytes(stats.assetStorageBytes, locale),
                    })}
                />
                <StatTile label={t("dashboard.documents")} value={stats.documentCount.toLocaleString(locale)} />
                <StatTile label={t("dashboard.assets")} value={stats.assetCount.toLocaleString(locale)} />
            </div>

            <div className="panel space-y-3">
                <div>
                    <h2 className="font-medium">{t("dashboard.writesTitle")}</h2>
                    <p className="text-xs text-text-muted">{t("dashboard.writesHint")}</p>
                </div>
                <WritesChart days={stats.writesPerDay} locale={locale} />
            </div>

            <div className="grid gap-4 lg:grid-cols-2">
                <div className="panel space-y-3">
                    <h2 className="font-medium">{t("dashboard.keysTitle")}</h2>
                    {stats.apiKeyActivity.length === 0 ? (
                        <p className="text-sm text-text-muted">{t("dashboard.keysEmpty")}</p>
                    ) : (
                        <ul className="divide-y divide-border">
                            {stats.apiKeyActivity.map((key) => (
                                <li key={key.id} className="py-2 first:pt-0 last:pb-0">
                                    <div className="flex items-center justify-between gap-2">
                                        <span className="text-sm font-medium">{key.label}</span>
                                        <span className="text-xs text-text-muted">
                                            {key.lastUsedAt
                                                ? t("dashboard.keyLastUsed", {
                                                    date: new Date(key.lastUsedAt).toLocaleString(locale),
                                                })
                                                : t("dashboard.keyNeverUsed")}
                                        </span>
                                    </div>
                                    <p className="text-xs text-text-muted">
                                        {key.extensionName} · {key.keyPrefix}
                                    </p>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>

                <div className="panel space-y-3">
                    <h2 className="font-medium">{t("dashboard.conflictsTitle")}</h2>
                    {stats.recentConflicts.length === 0 ? (
                        <p className="text-sm text-text-muted">{t("dashboard.conflictsEmpty")}</p>
                    ) : (
                        <ul className="divide-y divide-border">
                            {stats.recentConflicts.map((conflict) => (
                                <li key={conflict.versionId} className="py-2 first:pt-0 last:pb-0">
                                    <div className="flex items-center justify-between gap-2">
                                        <Link
                                            href={`/extensions/${conflict.extensionId}/documents/${conflict.slotKey}`}
                                            className="text-sm font-medium hover:text-primary"
                                        >
                                            {conflict.slotKey}
                                        </Link>
                                        <span className="text-xs text-text-muted">
                                            {new Date(conflict.savedAt).toLocaleString(locale)}
                                        </span>
                                    </div>
                                    <p className="text-xs text-text-muted">
                                        {[
                                            conflict.extensionName,
                                            conflict.partition &&
                                                t("dashboard.conflictPartition", { partition: conflict.partition }),
                                            conflict.deviceLabel,
                                        ]
                                            .filter(Boolean)
                                            .join(" · ")}
                                    </p>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            </div>
        </section>
    );
}

function StatTile({ label, value, hint }: { label: string; value: string; hint?: string }) {
    return (
        <div className="panel">
            <p className="text-sm text-text-muted">{label}</p>
            <p className="text-2xl font-semibold">{value}</p>
            {hint && <p className="text-xs text-text-muted mt-1">{hint}</p>}
        </div>
    );
}
