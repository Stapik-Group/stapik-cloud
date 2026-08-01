import { NextRequest, NextResponse } from "next/server";
import { adminApiFetch } from "@/lib/api-client";

export async function DELETE(
    request: NextRequest,
    { params }: { params: Promise<{ extensionId: string; keyId: string }> },
) {
    const { extensionId, keyId } = await params;

    const backendResponse = await adminApiFetch(
        `/api/admin/extensions/${extensionId}/keys/${keyId}`,
        { method: "DELETE" },
    );

    if (backendResponse.status === 204) {
        return new NextResponse(null, { status: 204 });
    }

    const data = await backendResponse.json().catch(() => null);
    return NextResponse.json(data, { status: backendResponse.status });
}