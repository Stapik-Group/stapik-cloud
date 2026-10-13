import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";
import { API_BASE_URL, CHALLENGE_COOKIE_NAME } from "@/lib/api-client";
import { setSessionCookies, type LoginTokens } from "@/lib/auth-session";

export async function POST(request: NextRequest) {
    const { code } = await request.json();

    const cookieStore = await cookies();
    const challengeToken = cookieStore.get(CHALLENGE_COOKIE_NAME)?.value;
    if (!challengeToken) {
        return NextResponse.json({ message: "Challenge expired" }, { status: 401 });
    }

    const backendResponse = await fetch(`${API_BASE_URL}/api/admin/auth/login/two-factor`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ challengeToken, code }),
    });

    if (!backendResponse.ok) {
        return NextResponse.json(
            { message: "Invalid verification code" },
            { status: backendResponse.status },
        );
    }

    const tokens: LoginTokens = await backendResponse.json();
    setSessionCookies(cookieStore, tokens);
    cookieStore.delete(CHALLENGE_COOKIE_NAME);

    return NextResponse.json({ ok: true });
}
