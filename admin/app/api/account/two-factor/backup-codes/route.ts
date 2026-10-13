import { NextRequest } from "next/server";
import { forwardPost } from "@/lib/admin-proxy";

export async function POST(request: NextRequest) {
    return forwardPost("/api/admin/account/two-factor/backup-codes", request);
}
