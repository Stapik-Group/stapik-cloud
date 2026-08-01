"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslation } from "@/lib/i18n/LocaleProvider";

export function DeleteSlotButton({
                                     extensionId,
                                     slotId,
                                 }: {
    extensionId: string;
    slotId: string;
}) {
    const router = useRouter();
    const { t } = useTranslation();
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleDelete(event: React.MouseEvent) {
        event.preventDefault();
        event.stopPropagation();

        if (!confirm(t("slots.deleteConfirm"))) {
            return;
        }

        setIsSubmitting(true);
        try {
            await fetch(`/api/extensions/${extensionId}/slots/${slotId}`, {
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
            onClick={handleDelete}
            disabled={isSubmitting}
            className="text-sm text-danger"
        >
            {isSubmitting ? t("slots.deleting") : t("slots.deleteButton")}
        </button>
    );
}