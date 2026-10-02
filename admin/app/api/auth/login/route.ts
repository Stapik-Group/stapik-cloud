import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";
import { API_BASE_URL, AUTH_COOKIE_NAME, REFRESH_COOKIE_NAME } from "@/lib/api-client";
import { sessionCookieOptions, type LoginTokens } from "@/lib/auth-session";

export async function POST(request: NextRequest) {
    const body = await request.json();

    const backendResponse = await fetch(`${API_BASE_URL}/api/admin/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
    });

    if (!backendResponse.ok) {
        return NextResponse.json(
            { message: "Invalid credentials" },
            { status: backendResponse.status },
        );
    }

    const { token, expiresAt, refreshToken, refreshExpiresAt }: LoginTokens = await backendResponse.json();

    const cookieStore = await cookies();
    cookieStore.set(AUTH_COOKIE_NAME, token, sessionCookieOptions(expiresAt));
    cookieStore.set(REFRESH_COOKIE_NAME, refreshToken, sessionCookieOptions(refreshExpiresAt));

    return NextResponse.json({ ok: true });
}