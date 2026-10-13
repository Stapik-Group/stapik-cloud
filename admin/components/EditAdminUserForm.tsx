"use client";

import { useState, type SubmitEventHandler } from "react";
import { useRouter } from "next/navigation";
import type { components } from "@/lib/api-types";
import { useTranslation } from "@/lib/i18n/LocaleProvider";
import { ADMIN_ROLES } from "@/lib/admin-roles";

type AdminRole = components["schemas"]["AdminRole"];
type UpdateAdminUserRequest = components["schemas"]["UpdateAdminUserRequest"];

type EditAdminUserFormProps = {
    adminUserId: string;
    username: string;
    role: AdminRole;
    isSelf: boolean;
    onDone: () => void;
};

export function EditAdminUserForm({ adminUserId, username, role, isSelf, onDone }: EditAdminUserFormProps) {
    const router = useRouter();
    const { t } = useTranslation();
    const [newUsername, setNewUsername] = useState(username);
    const [newRole, setNewRole] = useState<AdminRole>(role);
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();
        setError(null);
        setIsSubmitting(true);

        try {
            const payload: UpdateAdminUserRequest = { username: newUsername, role: newRole };
            const response = await fetch(`/api/admin-users/${adminUserId}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });

            if (response.status === 409) {
                setError(t("adminUsers.usernameTaken"));
                return;
            }
            if (!response.ok) {
                setError(t("adminUsers.editError"));
                return;
            }

            onDone();
            router.refresh();
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <form onSubmit={handleSubmit} className="flex flex-wrap items-center gap-2">
            <input
                type="text"
                required
                minLength={3}
                maxLength={100}
                aria-label={t("adminUsers.username")}
                value={newUsername}
                onChange={(e) => setNewUsername(e.target.value)}
                className="input"
            />
            <select
                aria-label={t("adminUsers.role")}
                value={newRole}
                disabled={isSelf}
                onChange={(e) => setNewRole(e.target.value as AdminRole)}
                className="input"
            >
                {ADMIN_ROLES.map((adminRole) => (
                    <option key={adminRole} value={adminRole}>
                        {adminRole}
                    </option>
                ))}
            </select>
            <button type="submit" disabled={isSubmitting} className="btn-primary">
                {t("adminUsers.save")}
            </button>
            <button type="button" className="btn-secondary" onClick={onDone}>
                {t("login.back")}
            </button>
            {error && <p className="w-full text-sm text-danger">{error}</p>}
        </form>
    );
}
