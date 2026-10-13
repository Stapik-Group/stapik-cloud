import { redirect } from "next/navigation";
import { adminApiFetch } from "@/lib/api-client";
import type { components } from "@/lib/api-types";
import { getLocale } from "@/lib/i18n/locale-cookie";
import { translate } from "@/lib/i18n/translations";
import { ChangePasswordForm } from "@/components/ChangePasswordForm";
import { SessionsList } from "@/components/SessionsList";
import { TwoFactorManage } from "@/components/TwoFactorManage";
import { TwoFactorSetup } from "@/components/TwoFactorSetup";

type AccountResponse = components["schemas"]["AccountResponse"];
type SessionListResponse = components["schemas"]["SessionListResponse"];

export default async function AccountPage() {
    const locale = await getLocale();
    const t = (key: Parameters<typeof translate>[1], vars?: Record<string, string | number>) =>
        translate(locale, key, vars);

    const [response, sessionsResponse] = await Promise.all([
        adminApiFetch("/api/admin/account"),
        adminApiFetch("/api/admin/account/sessions"),
    ]);

    if (response.status === 401 || sessionsResponse.status === 401) {
        redirect("/login");
    }

    if (!response.ok || !sessionsResponse.ok) {
        return (
            <main className="p-8">
                <p className="text-danger">{t("account.fetchError")}</p>
            </main>
        );
    }

    const account: AccountResponse = await response.json();
    const { items: sessions }: SessionListResponse = await sessionsResponse.json();

    return (
        <main className="p-8 space-y-6 max-w-xl">
            <div>
                <h1 className="text-xl font-semibold">{t("account.title")}</h1>
                <p className="text-sm text-text-muted">
                    {account.username} · {account.role}
                </p>
            </div>

            <section className="space-y-3">
                <h2 className="font-medium">{t("account.passwordTitle")}</h2>
                <ChangePasswordForm />
            </section>

            <section className="space-y-3">
                <h2 className="font-medium">{t("account.twoFactorTitle")}</h2>
                {account.twoFactorEnabled ? (
                    <TwoFactorManage backupCodesRemaining={account.backupCodesRemaining} />
                ) : (
                    <TwoFactorSetup />
                )}
            </section>

            <section className="space-y-3">
                <h2 className="font-medium">{t("account.sessionsTitle")}</h2>
                <SessionsList sessions={sessions} />
            </section>
        </main>
    );
}
