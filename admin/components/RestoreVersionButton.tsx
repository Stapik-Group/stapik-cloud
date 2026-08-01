"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslation } from "@/lib/i18n/LocaleProvider";

export function RestoreVersionButton({
                                         extensionId,
                                         slotKey,
                                         versionId,
                                     }: {
    extensionId: string;
    slotKey: string;
    versionId: string;
}) {
    const router = useRouter();
    const { t } = useTranslation();
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleRestore() {
        if (!confirm(t("documents.restoreConfirm"))) {
            return;
        }

        setIsSubmitting(true);
        try {
            await fetch(
                `/api/extensions/${extensionId}/documents/${slotKey}/versions/${versionId}/restore`,
                { method: "POST" },
            );
            router.refresh();
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <button
            type="button"
            onClick={handleRestore}
            disabled={isSubmitting}
            className="btn-secondary text-sm"
        >
            {isSubmitting ? t("documents.restoring") : t("documents.restore")}
        </button>
    );
}