import { NextRequest } from "next/server";
import { forwardDelete, forwardPut } from "@/lib/admin-proxy";

type RouteContext = { params: Promise<{ adminUserId: string }> };

export async function PUT(request: NextRequest, { params }: RouteContext) {
    const { adminUserId } = await params;
    return forwardPut(`/api/admin/admin-users/${adminUserId}`, request);
}

export async function DELETE(_request: NextRequest, { params }: RouteContext) {
    const { adminUserId } = await params;
    return forwardDelete(`/api/admin/admin-users/${adminUserId}`);
}
