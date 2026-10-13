"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslation } from "@/lib/i18n/LocaleProvider";

export function ResetTwoFactorButton({ adminUserId, username }: { adminUserId: string; username: string }) {
    const router = useRouter();
    const { t } = useTranslation();
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleReset() {
        if (!confirm(t("adminUsers.resetConfirm", { username }))) {
            return;
        }

        setIsSubmitting(true);
        try {
            await fetch(`/api/admin-users/${adminUserId}/two-factor/reset`, { method: "POST" });
            router.refresh();
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <button type="button" onClick={handleReset} disabled={isSubmitting} className="text-sm text-danger">
            {isSubmitting ? t("adminUsers.resetting") : t("adminUsers.reset")}
        </button>
    );
}
