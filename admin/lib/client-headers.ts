import type { NextRequest } from "next/server";

export function clientHeaders(request: NextRequest): Record<string, string> {
    const headers: Record<string, string> = {};

    const forwardedFor = request.headers.get("x-forwarded-for");
    if (forwardedFor) {
        headers["X-Forwarded-For"] = forwardedFor;
    }

    const userAgent = request.headers.get("user-agent");
    if (userAgent) {
        headers["User-Agent"] = userAgent;
    }

    return headers;
}
