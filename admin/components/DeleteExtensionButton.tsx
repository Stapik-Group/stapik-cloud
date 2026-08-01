"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslation } from "@/lib/i18n/LocaleProvider";

export function DeleteExtensionButton({ extensionId }: { extensionId: string }) {
    const router = useRouter();
    const { t } = useTranslation();
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleDelete() {
        if (!confirm(t("extensionDetail.deleteConfirm"))) {
            return;
        }

        setIsSubmitting(true);
        try {
            await fetch(`/api/extensions/${extensionId}`, { method: "DELETE" });
            router.push("/dashboard");
            router.refresh();
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <button
            type="button"
            onClick={handleDelete}
            disabled={isSubmitting}
            className="text-sm text-danger"
        >
            {isSubmitting ? t("extensionDetail.deleting") : t("extensionDetail.deleteButton")}
        </button>
    );
}