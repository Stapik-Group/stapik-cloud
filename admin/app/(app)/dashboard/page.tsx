import { redirect } from "next/navigation";
import Link from "next/link";
import { adminApiFetch } from "@/lib/api-client";
import type { components } from "@/lib/api-types";

import { getLocale } from "@/lib/i18n/locale-cookie";
import { translate } from "@/lib/i18n/translations";

type Extension = components["schemas"]["ExtensionResponse"];
type ExtensionListResponse = components["schemas"]["ExtensionListResponse"];

export default async function DashboardPage() {
    const locale = await getLocale();
    const t = (key: Parameters<typeof translate>[1], vars?: Record<string, string | number>) =>
        translate(locale, key, vars);

    const response = await adminApiFetch("/api/admin/extensions");

    if (response.status === 401) {
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

    return (
        <main className="p-8 space-y-6">
            <div className="flex items-center justify-between">
                <h1 className="text-xl font-semibold">{t("dashboard.title")}</h1>
                <Link href="/extensions/new" className="btn-primary">
                    {t("dashboard.newExtension")}
                </Link>
            </div>

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