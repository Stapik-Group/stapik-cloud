"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslation } from "@/lib/i18n/LocaleProvider";
import { LocaleSwitcher } from "@/components/LocaleSwitcher";

export function Sidebar() {
    const pathname = usePathname();
    const { t } = useTranslation();

    const NAV_ITEMS = [
        { href: "/dashboard", label: t("nav.extensions") },
        { href: "/audit-log", label: t("nav.auditLog") },
    ];

    async function handleLogout() {
        await fetch("/api/auth/logout", { method: "POST" });
        window.location.href = "/login";
    }

    return (
        <aside className="w-56 shrink-0 border-r border-border bg-surface flex flex-col">
            <div className="p-4 font-semibold border-b border-border">Stapik Cloud</div>

            <nav className="flex-1 p-2 space-y-1">
                {NAV_ITEMS.map((item) => {
                    const isActive = pathname === item.href || pathname.startsWith(`${item.href}/`);
                    return (
                        <Link
                            key={item.href}
                            href={item.href}
                            className={`block rounded-lg px-3 py-2 text-sm ${
                                isActive ? "bg-primary text-white" : "text-text hover:bg-background"
                            }`}
                        >
                            {item.label}
                        </Link>
                    );
                })}
            </nav>

            <div className="p-2 border-t border-border space-y-2">
                <LocaleSwitcher />
                <button
                    type="button"
                    onClick={handleLogout}
                    className="w-full text-left rounded-lg px-3 py-2 text-sm text-danger hover:bg-background"
                >
                    {t("nav.logout")}
                </button>
            </div>
        </aside>
    );
}