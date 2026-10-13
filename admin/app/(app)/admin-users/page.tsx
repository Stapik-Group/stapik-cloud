import { redirect } from "next/navigation";
import { adminApiFetch } from "@/lib/api-client";
import type { components } from "@/lib/api-types";
import { getLocale } from "@/lib/i18n/locale-cookie";
import { translate } from "@/lib/i18n/translations";
import { ResetTwoFactorButton } from "@/components/ResetTwoFactorButton";

type AccountResponse = components["schemas"]["AccountResponse"];
type AdminUserListResponse = components["schemas"]["AdminUserListResponse"];

export default async function AdminUsersPage() {
    const locale = await getLocale();
    const t = (key: Parameters<typeof translate>[1], vars?: Record<string, string | number>) =>
        translate(locale, key, vars);

    const [usersResponse, accountResponse] = await Promise.all([
        adminApiFetch("/api/admin/admin-users"),
        adminApiFetch("/api/admin/account"),
    ]);

    if (usersResponse.status === 401 || accountResponse.status === 401) {
        redirect("/login");
    }

    if (!usersResponse.ok || !accountResponse.ok) {
        return (
            <main className="p-8">
                <p className="text-danger">{t("adminUsers.fetchError")}</p>
            </main>
        );
    }

    const { items }: AdminUserListResponse = await usersResponse.json();
    const account: AccountResponse = await accountResponse.json();
    const canReset = account.role === "OWNER";

    return (
        <main className="p-8 space-y-6 max-w-4xl">
            <h1 className="text-xl font-semibold">{t("adminUsers.title")}</h1>

            <div className="panel divide-y divide-border">
                {items.map((adminUser) => (
                    <div key={adminUser.id} className="py-3 first:pt-0 last:pb-0 flex items-center justify-between">
                        <div>
                            <span className="font-medium text-sm">{adminUser.username}</span>
                            <span className="text-xs text-text-muted ml-2">{adminUser.role}</span>
                        </div>
                        <div className="flex items-center gap-4">
                            <span className="text-sm text-text-muted">
                                {adminUser.twoFactorEnabled ? t("adminUsers.twoFactorOn") : t("adminUsers.twoFactorOff")}
                            </span>
                            {canReset && adminUser.twoFactorEnabled && (
                                <ResetTwoFactorButton adminUserId={adminUser.id} username={adminUser.username} />
                            )}
                        </div>
                    </div>
                ))}
            </div>
        </main>
    );
}
