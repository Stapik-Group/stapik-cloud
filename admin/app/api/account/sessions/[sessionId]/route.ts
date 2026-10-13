import { NextRequest } from "next/server";
import { forwardDelete } from "@/lib/admin-proxy";

export async function DELETE(
    _request: NextRequest,
    { params }: { params: Promise<{ sessionId: string }> },
) {
    const { sessionId } = await params;
    return forwardDelete(`/api/admin/account/sessions/${sessionId}`);
}
