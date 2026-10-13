"use client";

import { useState, type SubmitEventHandler } from "react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import type { components } from "@/lib/api-types";
import { useTranslation } from "@/lib/i18n/LocaleProvider";
import { BackupCodesList } from "@/components/BackupCodesList";

type TwoFactorSetupResponse = components["schemas"]["TwoFactorSetupResponse"];
type BackupCodesResponse = components["schemas"]["BackupCodesResponse"];

export function TwoFactorSetup() {
    const router = useRouter();
    const { t } = useTranslation();
    const [setup, setSetup] = useState<TwoFactorSetupResponse | null>(null);
    const [backupCodes, setBackupCodes] = useState<string[] | null>(null);
    const [code, setCode] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleBegin() {
        setError(null);
        setIsSubmitting(true);
        try {
            const response = await fetch("/api/account/two-factor/setup", { method: "POST" });
            if (!response.ok) {
                setError(t("account.setupError"));
                return;
            }
            setSetup(await response.json());
        } finally {
            setIsSubmitting(false);
        }
    }

    const handleEnable: SubmitEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();
        setError(null);
        setIsSubmitting(true);
        try {
            const response = await fetch("/api/account/two-factor/enable", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ code }),
            });
            if (!response.ok) {
                setError(t("account.invalidCode"));
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

    if (!setup) {
        return (
            <div className="panel space-y-3">
                <p className="text-sm text-text-muted">{t("account.twoFactorOffHint")}</p>
                {error && <p className="text-sm text-danger">{error}</p>}
                <button type="button" onClick={handleBegin} disabled={isSubmitting} className="btn-primary">
                    {t("account.enable")}
                </button>
            </div>
        );
    }

    return (
        <form onSubmit={handleEnable} className="panel space-y-4">
            <p className="text-sm text-text-muted">{t("account.scanHint")}</p>
            <Image
                src={`data:image/svg+xml;utf8,${encodeURIComponent(setup.qrCodeSvg)}`}
                alt={t("account.qrAlt")}
                width={192}
                height={192}
                unoptimized
            />
            <div className="space-y-1">
                <span className="text-sm text-text-muted">{t("account.manualKey")}</span>
                <code className="block break-all bg-background border border-border rounded-lg p-2 text-sm">
                    {setup.secret}
                </code>
            </div>
            <div className="space-y-1">
                <label htmlFor="setupCode" className="text-sm text-text-muted">
                    {t("account.confirmCode")}
                </label>
                <input
                    id="setupCode"
                    type="text"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    required
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                    className="input w-full"
                />
            </div>
            {error && <p className="text-sm text-danger">{error}</p>}
            <button type="submit" disabled={isSubmitting} className="btn-primary">
                {t("account.confirmEnable")}
            </button>
        </form>
    );
}
