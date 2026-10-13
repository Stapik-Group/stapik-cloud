import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";
import { API_BASE_URL, CHALLENGE_COOKIE_NAME } from "@/lib/api-client";
import { sessionCookieOptions, setSessionCookies, type LoginResult } from "@/lib/auth-session";

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

    const result: LoginResult = await backendResponse.json();
    const cookieStore = await cookies();

    if (result.twoFactorRequired && result.challengeToken && result.challengeExpiresAt) {
        cookieStore.set(CHALLENGE_COOKIE_NAME, result.challengeToken, sessionCookieOptions(result.challengeExpiresAt));
        return NextResponse.json({ twoFactorRequired: true });
    }

    if (result.session) {
        setSessionCookies(cookieStore, result.session);
        return NextResponse.json({ twoFactorRequired: false });
    }

    return NextResponse.json({ message: "Unexpected login response" }, { status: 502 });
}
