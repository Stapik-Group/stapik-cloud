import { notFound, redirect } from "next/navigation";
import Link from "next/link";
import { adminApiFetch } from "@/lib/api-client";
import { CreateSlotForm } from "@/components/CreateSlotForm";
import { CreateKeyForm } from "@/components/CreateKeyForm";
import { RevokeKeyButton } from "@/components/RevokeKeyButton";
import { DeleteExtensionButton } from "@/components/DeleteExtensionButton";
import { DeleteSlotButton } from "@/components/DeleteSlotButton";
import type { components } from "@/lib/api-types";

import { getLocale } from "@/lib/i18n/locale-cookie";
import { translate } from "@/lib/i18n/translations";

type Extension = components["schemas"]["ExtensionResponse"];
type DocumentSlot = components["schemas"]["DocumentSlotResponse"];
type ApiKey = components["schemas"]["ApiKeyResponse"];

export default async function ExtensionDetailPage({
                                                      params,
                                                  }: {
    params: Promise<{ extensionId: string }>;
}) {
    const { extensionId } = await params;

    const locale = await getLocale();
    const t = (key: Parameters<typeof translate>[1], vars?: Record<string, string | number>) =>
        translate(locale, key, vars);

    const [extensionRes, slotsRes, keysRes] = await Promise.all([
        adminApiFetch(`/api/admin/extensions/${extensionId}`),
        adminApiFetch(`/api/admin/extensions/${extensionId}/slots`),
        adminApiFetch(`/api/admin/extensions/${extensionId}/keys`),
    ]);

    if (extensionRes.status === 401) {
        redirect("/login");
    }

    if (extensionRes.status === 404) {
        notFound();
    }

    if (!extensionRes.ok || !slotsRes.ok || !keysRes.ok) {
        return (
            <main className="p-8">
                <p className="text-danger">{t("extensionDetail.fetchError")}</p>
            </main>
        );
    }

    const extension: Extension = await extensionRes.json();
    const slotsData: { slots?: DocumentSlot[] } = await slotsRes.json();
    const keysData: { keys?: ApiKey[] } = await keysRes.json();

    const slots = slotsData.slots ?? [];
    const keys = keysData.keys ?? [];

    return (
        <main className="p-8 space-y-8 max-w-4xl">
            <div>
                <Link href="/dashboard" className="text-sm text-primary">
                    {t("extensionDetail.backToList")}
                </Link>
                <div className="flex items-center justify-between mt-2">
                    <h1 className="text-xl font-semibold">{extension.displayName}</h1>
                    <div className="flex items-center gap-3">
            <span
                className={
                    extension.enabled ? "text-xs text-success" : "text-xs text-text-muted"
                }
            >
              {extension.enabled ? t("dashboard.active") : t("dashboard.disabled")}
            </span>
                        <DeleteExtensionButton extensionId={extensionId} />
                    </div>
                </div>
                <p className="text-sm text-text-muted">{extension.slug}</p>
            </div>

            <section className="space-y-3">
                <h2 className="font-medium">{t("extensionDetail.slotsTitle")}</h2>
                {slots.length === 0 ? (
                    <p className="text-sm text-text-muted">{t("extensionDetail.noSlots")}</p>
                ) : (
                    <div className="space-y-2">
                        {slots.map((slot) => (
                            <div
                                key={slot.id}
                                className="panel flex items-center justify-between hover:border-primary transition-colors"
                            >
                                <Link
                                    href={`/extensions/${extensionId}/documents/${slot.slotKey}`}
                                    className="flex-1"
                                >
                                    <p className="font-medium">{slot.slotKey}</p>
                                    <p className="text-sm text-text-muted">
                                        {slot.contentType} · {slot.conflictStrategy ?? "LAST_WRITE_WINS_WITH_SHADOW_COPY"}
                                    </p>
                                </Link>
                                <DeleteSlotButton extensionId={extensionId} slotId={slot.id} />
                            </div>
                        ))}
                    </div>
                )}
                <CreateSlotForm extensionId={extensionId} />
            </section>

            <section className="space-y-3">
                <h2 className="font-medium">{t("extensionDetail.keysTitle")}</h2>
                {keys.length === 0 ? (
                    <p className="text-sm text-text-muted">{t("extensionDetail.noKeys")}</p>
                ) : (
                    <div className="space-y-2">
                        {keys.map((key) => (
                            <div key={key.id} className="panel flex items-center justify-between">
                                <div>
                                    <p className="font-medium">{key.label}</p>
                                    <p className="text-sm text-text-muted">
                                        {key.scope} · {key.revoked ? t("extensionDetail.keyRevoked") : t("extensionDetail.keyActive")}
                                    </p>
                                </div>
                                {!key.revoked && (
                                    <RevokeKeyButton extensionId={extensionId} keyId={key.id} />
                                )}
                            </div>
                        ))}
                    </div>
                )}
                <CreateKeyForm extensionId={extensionId} />
            </section>
        </main>
    );
}