import { forwardDelete } from "@/lib/admin-proxy";

export async function DELETE() {
    return forwardDelete("/api/admin/account/sessions");
}
