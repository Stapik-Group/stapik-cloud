import { forwardPost } from "@/lib/admin-proxy";

export async function POST() {
    return forwardPost("/api/admin/account/two-factor/setup");
}
