import { redirect } from "next/navigation";
import Link from "next/link";
import { adminApiFetch } from "@/lib/api-client";
import type { components } from "@/lib/api-types";

import { getLocale } from "@/lib/i18n/locale-cookie";
import { translate } from "@/lib/i18n/translations";

type AuditLogPageResponse = components["schemas"]["AuditLogPageResponse"];

const PAGE_SIZE = 50;

export default async function AuditLogPage({
                                               searchParams,
                                           }: {
    searchParams: Promise<{ page?: string }>;
}) {
    const { page: pageParam } = await searchParams;
    const page = pageParam ? Number(pageParam) : 0;

    const locale = await getLocale();
    const t = (key: Parameters<typeof translate>[1], vars?: Record<string, string | number>) =>
        translate(locale, key, vars);

    const response = await adminApiFetch(
        `/api/admin/audit-log?page=${page}&size=${PAGE_SIZE}`,
    );

    if (response.status === 401) {
        redirect("/login");
    }

    if (!response.ok) {
        return (
            <main className="p-8">
                <p className="text-danger">{t("auditLog.fetchError")}</p>
            </main>
        );
    }

    const data: AuditLogPageResponse = await response.json();
    const items = data.items ?? [];
    const totalElements = data.totalElements ?? 0;
    const hasNextPage = (page + 1) * PAGE_SIZE < totalElements;

    return (
        <main className="p-8 space-y-6 max-w-4xl">
            <h1 className="text-xl font-semibold">{t("auditLog.title")}</h1>

            {items.length === 0 ? (
                <p className="text-text-muted">{t("auditLog.empty")}</p>
            ) : (
                <div className="panel divide-y divide-border">
                    {items.map((entry) => (
                        <div key={entry.id} className="py-3 first:pt-0 last:pb-0">
                            <div className="flex items-center justify-between">
                                <span className="font-medium text-sm">{entry.action}</span>
                                <span className="text-xs text-text-muted">
                  {new Date(entry.occurredAt).toLocaleString(locale)}
                </span>
                            </div>
                            {entry.details && (
                                <p className="text-sm text-text-muted mt-1">{entry.details}</p>
                            )}
                        </div>
                    ))}
                </div>
            )}

            <div className="flex items-center justify-between text-sm">
        <span className="text-text-muted">
          {t("auditLog.pagination", { page: page + 1, total: totalElements })}
        </span>
                <div className="flex gap-2">
                    {page > 0 && (
                        <Link href={`/audit-log?page=${page - 1}`} className="btn-secondary">
                            {t("auditLog.previous")}
                        </Link>
                    )}
                    {hasNextPage && (
                        <Link href={`/audit-log?page=${page + 1}`} className="btn-secondary">
                            {t("auditLog.next")}
                        </Link>
                    )}
                </div>
            </div>
        </main>
    );
}