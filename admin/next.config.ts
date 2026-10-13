import type { NextConfig } from "next";

const isDevelopment = process.env.NODE_ENV === "development";

const contentSecurityPolicy = [
    "default-src 'self'",
    `script-src 'self' 'unsafe-inline'${isDevelopment ? " 'unsafe-eval'" : ""}`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: blob:",
    "font-src 'self' data:",
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
].join("; ");

const assetPreviewContentSecurityPolicy = [
    "default-src 'none'",
    "object-src 'self'",
    "frame-ancestors 'self'",
].join("; ");

const nextConfig: NextConfig = {
    output: "standalone",
    devIndicators: false,
    async headers() {
        return [
            {
                source: "/:path*",
                headers: [
                    { key: "Content-Security-Policy", value: contentSecurityPolicy },
                    { key: "X-Content-Type-Options", value: "nosniff" },
                    { key: "Referrer-Policy", value: "no-referrer" },
                ],
            },
            {
                source: "/api/extensions/:extensionId/assets/:slotKey/:filename",
                headers: [{ key: "Content-Security-Policy", value: assetPreviewContentSecurityPolicy }],
            },
        ];
    },
};

export default nextConfig;
