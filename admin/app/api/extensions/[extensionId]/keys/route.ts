import { NextRequest, NextResponse } from "next/server";
import { adminApiFetch } from "@/lib/api-client";

export async function POST(
    request: NextRequest,
    { params }: { params: Promise<{ extensionId: string }> },
) {
    const { extensionId } = await params;
    const body = await request.json();

    const backendResponse = await adminApiFetch(
        `/api/admin/extensions/${extensionId}/keys`,
        {
            method: "POST",
            body: JSON.stringify(body),
        },
    );

    const data = await backendResponse.json();
    return NextResponse.json(data, { status: backendResponse.status });
}