import { NextRequest, NextResponse } from "next/server";
import { adminApiFetch } from "@/lib/api-client";

export async function DELETE(
    request: NextRequest,
    { params }: { params: Promise<{ extensionId: string; slotId: string }> },
) {
    const { extensionId, slotId } = await params;

    const backendResponse = await adminApiFetch(
        `/api/admin/extensions/${extensionId}/slots/${slotId}`,
        { method: "DELETE" },
    );

    if (backendResponse.status === 204) {
        return new NextResponse(null, { status: 204 });
    }

    const data = await backendResponse.json().catch(() => null);
    return NextResponse.json(data, { status: backendResponse.status });
}