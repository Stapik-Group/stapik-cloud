import { redirect } from "next/navigation";
import { adminApiFetch } from "@/lib/api-client";
import type { components } from "@/lib/api-types";
import { getLocale } from "@/lib/i18n/locale-cookie";
import { translate } from "@/lib/i18n/translations";
import { AdminUserRow } from "@/components/AdminUserRow";
import { CreateAdminUserForm } from "@/components/CreateAdminUserForm";

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
    const canManage = account.role === "OWNER";

    return (
        <main className="p-8 space-y-6 max-w-4xl">
            <h1 className="text-xl font-semibold">{t("adminUsers.title")}</h1>

            <div className="panel divide-y divide-border">
                {items.map((adminUser) => (
                    <AdminUserRow
                        key={adminUser.id}
                        adminUser={adminUser}
                        isSelf={adminUser.username === account.username}
                        canManage={canManage}
                    />
                ))}
            </div>

            {canManage && <CreateAdminUserForm />}
        </main>
    );
}
