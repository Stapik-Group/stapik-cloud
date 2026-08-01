"use client";

import { useTranslation } from "@/lib/i18n/LocaleProvider";
import type { Locale } from "@/lib/i18n/locale";

const LABELS: Record<Locale, string> = { pl: "PL", en: "EN", de: "DE" };

export function LocaleSwitcher() {
    const { locale, setLocale } = useTranslation();

    return (
        <div className="flex gap-1">
            {(Object.keys(LABELS) as Locale[]).map((code) => (
                <button
                    key={code}
                    onClick={() => setLocale(code)}
                    className={code === locale ? "btn-primary" : "btn-secondary"}
                    aria-current={code === locale}
                >
                    {LABELS[code]}
                </button>
            ))}
        </div>
    );
}