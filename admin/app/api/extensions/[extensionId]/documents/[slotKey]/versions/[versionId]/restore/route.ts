import { NextRequest, NextResponse } from "next/server";
import { adminApiFetch } from "@/lib/api-client";

export async function POST(
    request: NextRequest,
    {
        params,
    }: {
        params: Promise<{ extensionId: string; slotKey: string; versionId: string }>;
    },
) {
    const { extensionId, slotKey, versionId } = await params;

    const backendResponse = await adminApiFetch(
        `/api/admin/extensions/${extensionId}/documents/${slotKey}/versions/${versionId}/restore`,
        { method: "POST" },
    );

    const data = await backendResponse.json();
    return NextResponse.json(data, { status: backendResponse.status });
}

export async function PUT(
    request: NextRequest,
    { params }: { params: Promise<{ extensionId: string; slotKey: string }> },
) {
    const { extensionId, slotKey } = await params;
    const body = await request.json();

    const backendResponse = await adminApiFetch(
        `/api/admin/extensions/${extensionId}/documents/${slotKey}`,
        { method: "PUT", body: JSON.stringify(body) },
    );

    const data = await backendResponse.json().catch(() => null);
    return NextResponse.json(data, { status: backendResponse.status });
}
