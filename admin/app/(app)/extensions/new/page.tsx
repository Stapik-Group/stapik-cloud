"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import type { components } from "@/lib/api-types";
import { useTranslation } from "@/lib/i18n/LocaleProvider";

type CreateExtensionRequest = components["schemas"]["CreateExtensionRequest"];

export default function NewExtensionPage() {
    const router = useRouter();
    const { t } = useTranslation();
    const [slug, setSlug] = useState("");
    const [displayName, setDisplayName] = useState("");
    const [iconGlyph, setIconGlyph] = useState("");
    const [color, setColor] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        setIsSubmitting(true);

        const payload: CreateExtensionRequest = {
            slug,
            displayName,
            ...(iconGlyph ? { iconGlyph } : {}),
            ...(color ? { color } : {}),
        };

        try {
            const response = await fetch("/api/extensions", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });

            if (!response.ok) {
                setError(t("newExtension.createError"));
                return;
            }

            router.push("/dashboard");
            router.refresh();
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <main className="p-8 flex justify-center">
            <form onSubmit={handleSubmit} className="panel w-full max-w-md space-y-4">
                <h1 className="text-lg font-semibold">{t("newExtension.title")}</h1>

                <div className="space-y-1">
                    <label htmlFor="slug" className="text-sm text-text-muted">
                        {t("newExtension.slug")}
                    </label>
                    <input
                        id="slug"
                        type="text"
                        required
                        value={slug}
                        onChange={(e) => setSlug(e.target.value)}
                        className="input w-full"
                        placeholder="stapik-calendar-desktop"
                    />
                </div>

                <div className="space-y-1">
                    <label htmlFor="displayName" className="text-sm text-text-muted">
                        {t("newExtension.displayName")}
                    </label>
                    <input
                        id="displayName"
                        type="text"
                        required
                        value={displayName}
                        onChange={(e) => setDisplayName(e.target.value)}
                        className="input w-full"
                        placeholder="Stapik Calendar (desktop)"
                    />
                </div>

                <div className="space-y-1">
                    <label htmlFor="iconGlyph" className="text-sm text-text-muted">
                        {t("newExtension.iconGlyph")}
                    </label>
                    <input
                        id="iconGlyph"
                        type="text"
                        value={iconGlyph}
                        onChange={(e) => setIconGlyph(e.target.value)}
                        className="input w-full"
                    />
                </div>

                <div className="space-y-1">
                    <label htmlFor="color" className="text-sm text-text-muted">
                        {t("newExtension.color")}
                    </label>
                    <input
                        id="color"
                        type="text"
                        value={color}
                        onChange={(e) => setColor(e.target.value)}
                        className="input w-full"
                        placeholder="#4f46e5"
                    />
                </div>

                {error && <p className="text-sm text-danger">{error}</p>}

                <div className="flex gap-2">
                    <button type="submit" disabled={isSubmitting} className="btn-primary">
                        {isSubmitting ? t("newExtension.submitting") : t("newExtension.submit")}
                    </button>
                    <button
                        type="button"
                        className="btn-secondary"
                        onClick={() => router.push("/dashboard")}
                    >
                        {t("newExtension.cancel")}
                    </button>
                </div>
            </form>
        </main>
    );
}