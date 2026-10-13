import { NextRequest, NextResponse } from "next/server";
import { adminApiFetch } from "@/lib/api-client";

type AssetRouteContext = {
    params: Promise<{ extensionId: string; slotKey: string; filename: string }>;
};

const FORWARDED_FILE_HEADERS = ["Content-Type", "Content-Disposition", "Content-Length"];

async function backendAssetPath({ params }: AssetRouteContext): Promise<string> {
    const { extensionId, slotKey, filename } = await params;
    return `/api/admin/extensions/${extensionId}/assets/${encodeURIComponent(slotKey)}/${encodeURIComponent(filename)}`;
}

export async function GET(request: NextRequest, context: AssetRouteContext) {
    const inline = request.nextUrl.searchParams.get("inline") === "true";
    const backendResponse = await adminApiFetch(`${await backendAssetPath(context)}?inline=${inline}`);

    if (!backendResponse.ok) {
        const problem = await backendResponse.json().catch(() => null);
        return NextResponse.json(problem, { status: backendResponse.status });
    }

    const headers = new Headers({ "Cache-Control": "private, no-store" });
    for (const name of FORWARDED_FILE_HEADERS) {
        const value = backendResponse.headers.get(name);
        if (value) {
            headers.set(name, value);
        }
    }
    return new NextResponse(backendResponse.body, { status: 200, headers });
}

export async function PUT(request: NextRequest, context: AssetRouteContext) {
    const backendResponse = await adminApiFetch(await backendAssetPath(context), {
        method: "PUT",
        body: await request.formData(),
    });

    const data = await backendResponse.json().catch(() => null);
    return NextResponse.json(data, { status: backendResponse.status });
}

export async function DELETE(_request: NextRequest, context: AssetRouteContext) {
    const backendResponse = await adminApiFetch(await backendAssetPath(context), { method: "DELETE" });

    if (backendResponse.status === 204) {
        return new NextResponse(null, { status: 204 });
    }

    const data = await backendResponse.json().catch(() => null);
    return NextResponse.json(data, { status: backendResponse.status });
}
