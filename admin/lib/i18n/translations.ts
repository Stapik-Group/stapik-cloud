import pl from "./locales/pl.json";
import en from "./locales/en.json";
import de from "./locales/de.json";
import type { Locale } from "./locale";

type TranslationKey = keyof typeof pl;

const DICTIONARIES: Record<Locale, Record<string, string>> = { pl, en, de };

export function translate(
    locale: Locale,
    key: TranslationKey,
    vars?: Record<string, string | number>,
): string {
    const dictionary = DICTIONARIES[locale] ?? DICTIONARIES.pl;
    let text = dictionary[key] ?? DICTIONARIES.pl[key] ?? key;

    if (vars) {
        for (const [varKey, varValue] of Object.entries(vars)) {
            text = text.replaceAll(`{${varKey}}`, String(varValue));
        }
    }

    return text;
}