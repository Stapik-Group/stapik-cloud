"use client";

import { useState, type SubmitEventHandler } from "react";
import { useRouter } from "next/navigation";
import type { components } from "@/lib/api-types";
import { useTranslation } from "@/lib/i18n/LocaleProvider";
import { BackupCodesList } from "@/components/BackupCodesList";

type BackupCodesResponse = components["schemas"]["BackupCodesResponse"];
type Action = "regenerate" | "disable";

const ENDPOINTS: Record<Action, string> = {
    regenerate: "/api/account/two-factor/backup-codes",
    disable: "/api/account/two-factor/disable",
};

export function TwoFactorManage({ backupCodesRemaining }: { backupCodesRemaining: number }) {
    const router = useRouter();
    const { t } = useTranslation();
    const [action, setAction] = useState<Action | null>(null);
    const [password, setPassword] = useState("");
    const [code, setCode] = useState("");
    const [backupCodes, setBackupCodes] = useState<string[] | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    function handleCancel() {
        setAction(null);
        setPassword("");
        setCode("");
        setError(null);
    }

    const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();
        if (!action) {
            return;
        }
        setError(null);
        setIsSubmitting(true);
        try {
            const response = await fetch(ENDPOINTS[action], {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ password, code }),
            });
            if (!response.ok) {
                setError(t("account.confirmError"));
                return;
            }
            if (action === "disable") {
                router.refresh();
                return;
            }
            const data: BackupCodesResponse = await response.json();
            setBackupCodes(data.backupCodes);
        } finally {
            setIsSubmitting(false);
        }
    }

    if (backupCodes) {
        return (
            <div className="panel">
                <BackupCodesList codes={backupCodes} onDone={() => router.refresh()} />
            </div>
        );
    }

    return (
        <div className="panel space-y-4">
            <p className="text-sm">{t("account.backupCodesRemaining", { count: backupCodesRemaining })}</p>

            {!action && (
                <div className="flex gap-2">
                    <button type="button" className="btn-secondary" onClick={() => setAction("regenerate")}>
                        {t("account.regenerate")}
                    </button>
                    <button type="button" className="btn-secondary text-danger" onClick={() => setAction("disable")}>
                        {t("account.disable")}
                    </button>
                </div>
            )}

            {action && (
                <form onSubmit={handleSubmit} className="space-y-3">
                    <p className="text-sm text-text-muted">{t("account.confirmHint")}</p>
                    <div className="space-y-1">
                        <label htmlFor="confirmPassword" className="text-sm text-text-muted">
                            {t("login.password")}
                        </label>
                        <input
                            id="confirmPassword"
                            type="password"
                            required
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            className="input w-full"
                        />
                    </div>
                    <div className="space-y-1">
                        <label htmlFor="confirmCode" className="text-sm text-text-muted">
                            {t("login.twoFactorCode")}
                        </label>
                        <input
                            id="confirmCode"
                            type="text"
                            autoComplete="one-time-code"
                            required
                            value={code}
                            onChange={(e) => setCode(e.target.value)}
                            className="input w-full"
                        />
                    </div>
                    {error && <p className="text-sm text-danger">{error}</p>}
                    <div className="flex gap-2">
                        <button type="submit" disabled={isSubmitting} className="btn-primary">
                            {action === "disable" ? t("account.disable") : t("account.regenerate")}
                        </button>
                        <button type="button" className="btn-secondary" onClick={handleCancel}>
                            {t("login.back")}
                        </button>
                    </div>
                </form>
            )}
        </div>
    );
}
