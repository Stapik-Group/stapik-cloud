"use client";

import { useTranslation } from "@/lib/i18n/LocaleProvider";

export function BackupCodesList({ codes, onDone }: { codes: string[]; onDone: () => void }) {
    const { t } = useTranslation();
    const joinedCodes = codes.join("\n");

    function handleDownload() {
        const url = URL.createObjectURL(new Blob([joinedCodes], { type: "text/plain" }));
        const link = document.createElement("a");
        link.href = url;
        link.download = "stapik-cloud-backup-codes.txt";
        link.click();
        URL.revokeObjectURL(url);
    }

    return (
        <div className="space-y-3">
            <h3 className="font-medium text-sm">{t("account.backupCodesTitle")}</h3>
            <p className="text-sm text-danger">{t("account.backupCodesWarning")}</p>
            <ul className="grid grid-cols-2 gap-2 bg-background border border-border rounded-lg p-3 font-mono text-sm">
                {codes.map((code) => (
                    <li key={code}>{code}</li>
                ))}
            </ul>
            <div className="flex gap-2">
                <button type="button" className="btn-secondary" onClick={() => navigator.clipboard.writeText(joinedCodes)}>
                    {t("account.copy")}
                </button>
                <button type="button" className="btn-secondary" onClick={handleDownload}>
                    {t("account.download")}
                </button>
                <button type="button" className="btn-primary" onClick={onDone}>
                    {t("account.savedCodes")}
                </button>
            </div>
        </div>
    );
}
