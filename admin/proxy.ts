import { NextRequest, NextResponse } from "next/server";
import { AUTH_COOKIE_NAME } from "@/lib/api-client";

const PUBLIC_PATHS = ["/login"];

export default function proxy(request: NextRequest) {
    const { pathname } = request.nextUrl;

    if (
        PUBLIC_PATHS.some((path) => pathname.startsWith(path)) ||
        pathname.startsWith("/api/auth")
    ) {
        return NextResponse.next();
    }

    const token = request.cookies.get(AUTH_COOKIE_NAME)?.value;

    if (!token) {
        const loginUrl = new URL("/login", request.url);
        return NextResponse.redirect(loginUrl);
    }

    return NextResponse.next();
}

export const config = {
    matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};