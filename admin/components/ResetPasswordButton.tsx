"use client";

import { useState } from "react";
import type { components } from "@/lib/api-types";
import { useTranslation } from "@/lib/i18n/LocaleProvider";

type PasswordResetResponse = components["schemas"]["PasswordResetResponse"];

export function ResetPasswordButton({ adminUserId, username }: { adminUserId: string; username: string }) {
    const { t } = useTranslation();
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [temporaryPassword, setTemporaryPassword] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    async function handleReset() {
        if (!confirm(t("adminUsers.resetPasswordConfirm", { username }))) {
            return;
        }

        setError(null);
        setIsSubmitting(true);
        try {
            const response = await fetch(`/api/admin-users/${adminUserId}/password/reset`, { method: "POST" });
            if (!response.ok) {
                setError(t("adminUsers.resetPasswordError"));
                return;
            }
            const data: PasswordResetResponse = await response.json();
            setTemporaryPassword(data.temporaryPassword);
        } finally {
            setIsSubmitting(false);
        }
    }

    if (temporaryPassword) {
        return (
            <div className="w-full space-y-2">
                <p className="text-sm text-danger">{t("adminUsers.temporaryPasswordWarning", { username })}</p>
                <code className="block break-all bg-background border border-border rounded-lg p-2 text-sm">
                    {temporaryPassword}
                </code>
                <div className="flex gap-2">
                    <button
                        type="button"
                        className="btn-secondary"
                        onClick={() => navigator.clipboard.writeText(temporaryPassword)}
                    >
                        {t("apiKeys.copy")}
                    </button>
                    <button type="button" className="btn-primary" onClick={() => setTemporaryPassword(null)}>
                        {t("apiKeys.confirmClose")}
                    </button>
                </div>
            </div>
        );
    }

    return (
        <>
            <button type="button" onClick={handleReset} disabled={isSubmitting} className="text-sm text-danger">
                {t("adminUsers.resetPassword")}
            </button>
            {error && <span className="text-sm text-danger">{error}</span>}
        </>
    );
}
