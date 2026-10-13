"use client";

import { useState, type SubmitEventHandler } from "react";
import { useRouter } from "next/navigation";
import type { components } from "@/lib/api-types";
import { useTranslation } from "@/lib/i18n/LocaleProvider";
import { ADMIN_ROLES } from "@/lib/admin-roles";
import { MAX_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH } from "@/lib/password-policy";

type AdminRole = components["schemas"]["AdminRole"];
type CreateAdminUserRequest = components["schemas"]["CreateAdminUserRequest"];

export function CreateAdminUserForm() {
    const router = useRouter();
    const { t } = useTranslation();
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [role, setRole] = useState<AdminRole>("VIEWER");
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();
        setError(null);
        setIsSubmitting(true);

        try {
            const payload: CreateAdminUserRequest = { username, password, role };
            const response = await fetch("/api/admin-users", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });

            if (response.status === 409) {
                setError(t("adminUsers.usernameTaken"));
                return;
            }
            if (!response.ok) {
                setError(t("adminUsers.createError"));
                return;
            }

            setUsername("");
            setPassword("");
            setRole("VIEWER");
            router.refresh();
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <form onSubmit={handleSubmit} className="panel space-y-3">
            <h3 className="font-medium text-sm">{t("adminUsers.newUser")}</h3>

            <div className="grid gap-3 sm:grid-cols-3">
                <div className="space-y-1">
                    <label htmlFor="newAdminUsername" className="text-sm text-text-muted">
                        {t("adminUsers.username")}
                    </label>
                    <input
                        id="newAdminUsername"
                        type="text"
                        required
                        minLength={3}
                        maxLength={100}
                        autoComplete="off"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        className="input w-full"
                    />
                </div>
                <div className="space-y-1">
                    <label htmlFor="newAdminPassword" className="text-sm text-text-muted">
                        {t("adminUsers.password")}
                    </label>
                    <input
                        id="newAdminPassword"
                        type="password"
                        required
                        minLength={MIN_PASSWORD_LENGTH}
                        maxLength={MAX_PASSWORD_LENGTH}
                        autoComplete="new-password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        className="input w-full"
                    />
                </div>
                <div className="space-y-1">
                    <label htmlFor="newAdminRole" className="text-sm text-text-muted">
                        {t("adminUsers.role")}
                    </label>
                    <select
                        id="newAdminRole"
                        value={role}
                        onChange={(e) => setRole(e.target.value as AdminRole)}
                        className="input w-full"
                    >
                        {ADMIN_ROLES.map((adminRole) => (
                            <option key={adminRole} value={adminRole}>
                                {adminRole}
                            </option>
                        ))}
                    </select>
                </div>
            </div>
            <p className="text-xs text-text-muted">{t("account.passwordHint", { min: MIN_PASSWORD_LENGTH })}</p>

            {error && <p className="text-sm text-danger">{error}</p>}

            <button type="submit" disabled={isSubmitting} className="btn-primary">
                {t("adminUsers.create")}
            </button>
        </form>
    );
}
