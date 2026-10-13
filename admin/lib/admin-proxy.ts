import { NextRequest, NextResponse } from "next/server";
import { adminApiFetch } from "@/lib/api-client";

export async function forwardPost(path: string, request?: NextRequest): Promise<NextResponse> {
    const body = request ? await request.text() : undefined;
    const backendResponse = await adminApiFetch(path, { method: "POST", body });
    const responseBody = await backendResponse.text();

    return new NextResponse(responseBody || null, {
        status: backendResponse.status,
        headers: responseBody ? { "Content-Type": "application/json" } : undefined,
    });
}
