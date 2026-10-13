import { NextRequest } from "next/server";
import { forwardPost } from "@/lib/admin-proxy";

export async function POST(
    _request: NextRequest,
    { params }: { params: Promise<{ adminUserId: string }> },
) {
    const { adminUserId } = await params;
    return forwardPost(`/api/admin/admin-users/${adminUserId}/password/reset`);
}
