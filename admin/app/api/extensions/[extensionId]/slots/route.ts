import { NextRequest, NextResponse } from "next/server";
import { adminApiFetch } from "@/lib/api-client";
import type { components } from "@/lib/api-types";

type DocumentSlotListResponse = components["schemas"]["DocumentSlotListResponse"];
type CreateDocumentSlotRequest = components["schemas"]["CreateDocumentSlotRequest"];

export async function GET(
    request: NextRequest,
    { params }: { params: Promise<{ extensionId: string }> },
) {
    const { extensionId } = await params;

    const backendResponse = await adminApiFetch(`/api/admin/extensions/${extensionId}/slots`);

    const responseBody = await backendResponse.text();
    return new NextResponse(responseBody, {
        status: backendResponse.status,
        headers: { "Content-Type": "application/json" },
    });
}

export async function POST(
    request: NextRequest,
    { params }: { params: Promise<{ extensionId: string }> },
) {
    const { extensionId } = await params;
    const body: CreateDocumentSlotRequest = await request.json();

    const backendResponse = await adminApiFetch(`/api/admin/extensions/${extensionId}/slots`, {
        method: "POST",
        body: JSON.stringify(body),
    });

    const responseBody = await backendResponse.text();
    return new NextResponse(responseBody, {
        status: backendResponse.status,
        headers: { "Content-Type": "application/json" },
    });
}