"use client";

import { useState } from "react";
import type { components } from "@/lib/api-types";
import { useTranslation } from "@/lib/i18n/LocaleProvider";
import { DeleteAdminUserButton } from "@/components/DeleteAdminUserButton";
import { EditAdminUserForm } from "@/components/EditAdminUserForm";
import { ResetPasswordButton } from "@/components/ResetPasswordButton";
import { ResetTwoFactorButton } from "@/components/ResetTwoFactorButton";

type AdminUser = components["schemas"]["AdminUserResponse"];
type AdminRole = components["schemas"]["AdminRole"];

type AdminUserRowProps = {
    adminUser: AdminUser;
    isSelf: boolean;
    canManage: boolean;
};

export function AdminUserRow({ adminUser, isSelf, canManage }: AdminUserRowProps) {
    const { t } = useTranslation();
    const [isEditing, setIsEditing] = useState(false);

    return (
        <div className="py-3 first:pt-0 last:pb-0 flex flex-wrap items-center justify-between gap-2">
            {isEditing ? (
                <EditAdminUserForm
                    adminUserId={adminUser.id}
                    username={adminUser.username}
                    role={adminUser.role as AdminRole}
                    isSelf={isSelf}
                    onDone={() => setIsEditing(false)}
                />
            ) : (
                <>
                    <div>
                        <span className="font-medium text-sm">{adminUser.username}</span>
                        <span className="text-xs text-text-muted ml-2">{adminUser.role}</span>
                        {isSelf && <span className="text-xs text-text-muted ml-2">({t("adminUsers.you")})</span>}
                    </div>
                    <div className="flex flex-wrap items-center gap-4">
                        <span className="text-sm text-text-muted">
                            {adminUser.twoFactorEnabled ? t("adminUsers.twoFactorOn") : t("adminUsers.twoFactorOff")}
                        </span>
                        {canManage && (
                            <>
                                <button type="button" className="text-sm text-primary" onClick={() => setIsEditing(true)}>
                                    {t("adminUsers.edit")}
                                </button>
                                {adminUser.twoFactorEnabled && (
                                    <ResetTwoFactorButton adminUserId={adminUser.id} username={adminUser.username} />
                                )}
                                {!isSelf && (
                                    <>
                                        <ResetPasswordButton adminUserId={adminUser.id} username={adminUser.username} />
                                        <DeleteAdminUserButton adminUserId={adminUser.id} username={adminUser.username} />
                                    </>
                                )}
                            </>
                        )}
                    </div>
                </>
            )}
        </div>
    );
}
