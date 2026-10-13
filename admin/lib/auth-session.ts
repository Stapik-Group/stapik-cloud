import { API_BASE_URL, AUTH_COOKIE_NAME, REFRESH_COOKIE_NAME } from "@/lib/api-client";
import type { components } from "@/lib/api-types";

export type LoginTokens = components["schemas"]["LoginResponse"];
export type LoginResult = components["schemas"]["LoginResult"];

type CookieWriter = { set(name: string, value: string, options: ReturnType<typeof sessionCookieOptions>): unknown };

export type RefreshResult =
    | { status: "refreshed"; tokens: LoginTokens }
    | { status: "rejected" }
    | { status: "unavailable" };

const ACCESS_TOKEN_REFRESH_MARGIN_SECONDS = 30;

export function sessionCookieOptions(expiresAt: string) {
    return {
        httpOnly: true,
        secure: process.env.COOKIE_SECURE !== "false",
        sameSite: "lax" as const,
        path: "/",
        expires: new Date(expiresAt),
    };
}

export function setSessionCookies(cookieStore: CookieWriter, tokens: LoginTokens) {
    cookieStore.set(AUTH_COOKIE_NAME, tokens.token, sessionCookieOptions(tokens.expiresAt));
    cookieStore.set(REFRESH_COOKIE_NAME, tokens.refreshToken, sessionCookieOptions(tokens.refreshExpiresAt));
}

// Reads the "exp" claim only as a hint to refresh slightly early. The signature is verified by the backend.
export function isAccessTokenExpiring(accessToken: string): boolean {
    try {
        const payloadSegment = accessToken.split(".")[1];
        const payloadJson = atob(payloadSegment.replace(/-/g, "+").replace(/_/g, "/"));
        const { exp } = JSON.parse(payloadJson) as { exp?: number };

        if (typeof exp !== "number") {
            return false;
        }
        return exp * 1000 - Date.now() < ACCESS_TOKEN_REFRESH_MARGIN_SECONDS * 1000;
    } catch {
        return false;
    }
}

export async function exchangeRefreshToken(refreshToken: string): Promise<RefreshResult> {
    try {
        const response = await fetch(`${API_BASE_URL}/api/admin/auth/refresh`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken }),
        });

        if (response.status === 401) {
            return { status: "rejected" };
        }
        if (!response.ok) {
            return { status: "unavailable" };
        }
        return { status: "refreshed", tokens: await response.json() };
    } catch {
        return { status: "unavailable" };
    }
}

export async function revokeRefreshToken(refreshToken: string): Promise<void> {
    try {
        await fetch(`${API_BASE_URL}/api/admin/auth/logout`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken }),
        });
    } catch {
        // Logging out must work even when the backend is unreachable; the cookies are cleared regardless.
    }
}
