import { notFound, redirect } from "next/navigation";
import Link from "next/link";
import { adminApiFetch } from "@/lib/api-client";
import { AssetBrowser } from "@/components/AssetBrowser";
import type { components } from "@/lib/api-types";

import { getLocale } from "@/lib/i18n/locale-cookie";
import { translate } from "@/lib/i18n/translations";

type AdminAssetListResponse = components["schemas"]["AdminAssetListResponse"];

export default async function AssetBrowserPage({
                                                   params,
                                               }: {
    params: Promise<{ extensionId: string; slotKey: string }>;
}) {
    const { extensionId, slotKey } = await params;

    const locale = await getLocale();
    const t = (key: Parameters<typeof translate>[1], vars?: Record<string, string | number>) =>
        translate(locale, key, vars);

    const response = await adminApiFetch(
        `/api/admin/extensions/${extensionId}/assets/${encodeURIComponent(slotKey)}`,
    );

    if (response.status === 401) {
        redirect("/login");
    }

    if (response.status === 404) {
        notFound();
    }

    if (!response.ok) {
        return (
            <main className="p-8">
                <p className="text-danger">{t("assets.fetchError")}</p>
            </main>
        );
    }

    const data: AdminAssetListResponse = await response.json();

    return (
        <main className="p-8 space-y-6 max-w-4xl">
            <div>
                <Link href={`/extensions/${extensionId}`} className="text-sm text-primary">
                    {t("documents.backToExtension")}
                </Link>
                <h1 className="text-xl font-semibold mt-2">{slotKey}</h1>
            </div>

            <AssetBrowser extensionId={extensionId} slotKey={slotKey} assets={data.assets} />
        </main>
    );
}
