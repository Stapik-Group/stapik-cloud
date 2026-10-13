import { NextRequest, NextResponse } from "next/server";
import { adminApiFetch } from "@/lib/api-client";

async function forward(method: string, path: string, request?: NextRequest): Promise<NextResponse> {
    const body = request ? await request.text() : undefined;
    const backendResponse = await adminApiFetch(path, { method, body });
    const responseBody = await backendResponse.text();

    return new NextResponse(responseBody || null, {
        status: backendResponse.status,
        headers: responseBody ? { "Content-Type": "application/json" } : undefined,
    });
}

export function forwardPost(path: string, request?: NextRequest): Promise<NextResponse> {
    return forward("POST", path, request);
}

export function forwardPut(path: string, request: NextRequest): Promise<NextResponse> {
    return forward("PUT", path, request);
}

export function forwardDelete(path: string): Promise<NextResponse> {
    return forward("DELETE", path);
}
