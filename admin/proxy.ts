import { NextRequest, NextResponse } from "next/server";
import { AUTH_COOKIE_NAME, REFRESH_COOKIE_NAME } from "@/lib/api-client";
import {
    exchangeRefreshToken,
    isAccessTokenExpiring,
    sessionCookieOptions,
    type LoginTokens,
} from "@/lib/auth-session";

const PUBLIC_PATHS = ["/login"];

function redirectToLogin(request: NextRequest): NextResponse {
    const response = NextResponse.redirect(new URL("/login", request.url));
    response.cookies.delete(AUTH_COOKIE_NAME);
    response.cookies.delete(REFRESH_COOKIE_NAME);
    return response;
}

function continueWithRefreshedSession(request: NextRequest, tokens: LoginTokens): NextResponse {
    // The incoming request is updated too, so server components of this very request already use the new token.
    request.cookies.set(AUTH_COOKIE_NAME, tokens.token);
    request.cookies.set(REFRESH_COOKIE_NAME, tokens.refreshToken);

    const response = NextResponse.next({ request });
    response.cookies.set(AUTH_COOKIE_NAME, tokens.token, sessionCookieOptions(tokens.expiresAt));
    response.cookies.set(REFRESH_COOKIE_NAME, tokens.refreshToken, sessionCookieOptions(tokens.refreshExpiresAt));
    return response;
}

export default async function proxy(request: NextRequest) {
    const { pathname } = request.nextUrl;

    if (
        PUBLIC_PATHS.some((path) => pathname.startsWith(path)) ||
        pathname.startsWith("/api/auth")
    ) {
        return NextResponse.next();
    }

    const accessToken = request.cookies.get(AUTH_COOKIE_NAME)?.value;
    const refreshToken = request.cookies.get(REFRESH_COOKIE_NAME)?.value;

    if (accessToken && !isAccessTokenExpiring(accessToken)) {
        return NextResponse.next();
    }

    if (!refreshToken) {
        // Sessions started before refresh tokens existed keep working until their access token expires.
        return accessToken ? NextResponse.next() : redirectToLogin(request);
    }

    const refreshResult = await exchangeRefreshToken(refreshToken);

    if (refreshResult.status === "rejected") {
        return redirectToLogin(request);
    }
    if (refreshResult.status === "unavailable") {
        return NextResponse.next();
    }
    return continueWithRefreshedSession(request, refreshResult.tokens);
}

export const config = {
    matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};