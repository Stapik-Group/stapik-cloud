"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslation } from "@/lib/i18n/LocaleProvider";

export function DeleteAdminUserButton({ adminUserId, username }: { adminUserId: string; username: string }) {
    const router = useRouter();
    const { t } = useTranslation();
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleDelete() {
        if (!confirm(t("adminUsers.deleteConfirm", { username }))) {
            return;
        }

        setIsSubmitting(true);
        try {
            await fetch(`/api/admin-users/${adminUserId}`, { method: "DELETE" });
            router.refresh();
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <button type="button" onClick={handleDelete} disabled={isSubmitting} className="text-sm text-danger">
            {t("adminUsers.delete")}
        </button>
    );
}
