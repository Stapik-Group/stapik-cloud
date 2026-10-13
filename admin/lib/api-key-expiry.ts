export const EXPIRY_WARNING_DAYS = 30;

const MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

export type ApiKeyExpiry =
    | { status: "none" }
    | { status: "valid"; expiresAt: Date }
    | { status: "expiring"; expiresAt: Date; daysLeft: number }
    | { status: "expired"; expiresAt: Date };

export function getApiKeyExpiry(expiresAt: string | null | undefined, now: Date = new Date()): ApiKeyExpiry {
    if (!expiresAt) {
        return { status: "none" };
    }

    const expiresAtDate = new Date(expiresAt);
    const millisLeft = expiresAtDate.getTime() - now.getTime();

    if (millisLeft <= 0) {
        return { status: "expired", expiresAt: expiresAtDate };
    }

    const daysLeft = Math.ceil(millisLeft / MILLIS_PER_DAY);
    if (daysLeft <= EXPIRY_WARNING_DAYS) {
        return { status: "expiring", expiresAt: expiresAtDate, daysLeft };
    }

    return { status: "valid", expiresAt: expiresAtDate };
}
