"use client";

import { useState, type SubmitEventHandler } from "react";
import { useRouter } from "next/navigation";
import type { components } from "@/lib/api-types";
import { useTranslation } from "@/lib/i18n/LocaleProvider";
import { MAX_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH } from "@/lib/password-policy";

type ChangePasswordRequest = components["schemas"]["ChangePasswordRequest"];

export function ChangePasswordForm() {
    const router = useRouter();
    const { t } = useTranslation();
    const [currentPassword, setCurrentPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmation, setConfirmation] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [isChanged, setIsChanged] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();
        setError(null);
        setIsChanged(false);

        if (newPassword !== confirmation) {
            setError(t("account.passwordMismatch"));
            return;
        }

        setIsSubmitting(true);
        try {
            const payload: ChangePasswordRequest = { currentPassword, newPassword };
            const response = await fetch("/api/account/password", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });

            if (!response.ok) {
                setError(t("account.passwordChangeError"));
                return;
            }

            setCurrentPassword("");
            setNewPassword("");
            setConfirmation("");
            setIsChanged(true);
            router.refresh();
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <form onSubmit={handleSubmit} className="panel space-y-3">
            <div className="space-y-1">
                <label htmlFor="currentPassword" className="text-sm text-text-muted">
                    {t("account.currentPassword")}
                </label>
                <input
                    id="currentPassword"
                    type="password"
                    autoComplete="current-password"
                    required
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    className="input w-full"
                />
            </div>
            <div className="space-y-1">
                <label htmlFor="newPassword" className="text-sm text-text-muted">
                    {t("account.newPassword")}
                </label>
                <input
                    id="newPassword"
                    type="password"
                    autoComplete="new-password"
                    required
                    minLength={MIN_PASSWORD_LENGTH}
                    maxLength={MAX_PASSWORD_LENGTH}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    className="input w-full"
                />
                <p className="text-xs text-text-muted">{t("account.passwordHint", { min: MIN_PASSWORD_LENGTH })}</p>
            </div>
            <div className="space-y-1">
                <label htmlFor="confirmNewPassword" className="text-sm text-text-muted">
                    {t("account.confirmNewPassword")}
                </label>
                <input
                    id="confirmNewPassword"
                    type="password"
                    autoComplete="new-password"
                    required
                    value={confirmation}
                    onChange={(e) => setConfirmation(e.target.value)}
                    className="input w-full"
                />
            </div>

            {error && <p className="text-sm text-danger">{error}</p>}
            {isChanged && <p className="text-sm text-success">{t("account.passwordChanged")}</p>}

            <button type="submit" disabled={isSubmitting} className="btn-primary">
                {t("account.changePassword")}
            </button>
        </form>
    );
}
