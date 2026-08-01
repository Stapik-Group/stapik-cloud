import { NextRequest, NextResponse } from "next/server";
import { adminApiFetch } from "@/lib/api-client";

export async function GET(
    request: NextRequest,
    { params }: { params: Promise<{ extensionId: string; slotKey: string }> },
) {
    const { extensionId, slotKey } = await params;

    const backendResponse = await adminApiFetch(
        `/api/admin/extensions/${extensionId}/documents/${slotKey}`,
    );

    const data = await backendResponse.json();
    return NextResponse.json(data, { status: backendResponse.status });
}