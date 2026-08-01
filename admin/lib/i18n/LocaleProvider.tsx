"use client";

import { createContext, useContext, useState, useCallback, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { translate } from "./translations";
import { LOCALE_COOKIE_NAME, type Locale } from "./locale";

type TranslateFn = (key: Parameters<typeof translate>[1], vars?: Record<string, string | number>) => string;

type LocaleContextValue = {
    locale: Locale;
    setLocale: (locale: Locale) => void;
    t: TranslateFn;
};

const LocaleContext = createContext<LocaleContextValue | null>(null);

export function LocaleProvider({
                                   initialLocale,
                                   children,
                               }: {
    initialLocale: Locale;
    children: ReactNode;
}) {
    const router = useRouter();
    const [locale, setLocaleState] = useState<Locale>(initialLocale);

    const setLocale = useCallback(
        (next: Locale) => {
            setLocaleState(next);
            document.cookie = `${LOCALE_COOKIE_NAME}=${next}; path=/; max-age=31536000; samesite=lax`;
            router.refresh();
        },
        [router],
    );

    const t: TranslateFn = useCallback(
        (key, vars) => translate(locale, key, vars),
        [locale],
    );

    return (
        <LocaleContext.Provider value={{ locale, setLocale, t }}>
            {children}
        </LocaleContext.Provider>
    );
}

export function useTranslation() {
    const context = useContext(LocaleContext);
    if (!context) {
        throw new Error("useTranslation must be used within LocaleProvider");
    }
    return context;
}