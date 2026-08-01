import { cookies } from "next/headers";
import { DEFAULT_LOCALE, LOCALES, LOCALE_COOKIE_NAME, type Locale } from "./locale";

export async function getLocale(): Promise<Locale> {
    const cookieStore = await cookies();
    const value = cookieStore.get(LOCALE_COOKIE_NAME)?.value;
    return LOCALES.includes(value as Locale) ? (value as Locale) : DEFAULT_LOCALE;
}