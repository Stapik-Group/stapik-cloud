"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslation } from "@/lib/i18n/LocaleProvider";

export function RevokeKeyButton({
                                    extensionId,
                                    keyId,
                                }: {
    extensionId: string;
    keyId: string;
}) {
    const router = useRouter();
    const { t } = useTranslation();
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleRevoke() {
        if (!confirm(t("apiKeys.revokeConfirm"))) {
            return;
        }

        setIsSubmitting(true);
        try {
            await fetch(`/api/extensions/${extensionId}/keys/${keyId}`, {
                method: "DELETE",
            });
            router.refresh();
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <button
            type="button"
            onClick={handleRevoke}
            disabled={isSubmitting}
            className="text-sm text-danger"
        >
            {isSubmitting ? t("apiKeys.revoking") : t("apiKeys.revokeButton")}
        </button>
    );
}