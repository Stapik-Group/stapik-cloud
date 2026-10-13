"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { components } from "@/lib/api-types";
import { useTranslation } from "@/lib/i18n/LocaleProvider";

type Session = components["schemas"]["SessionResponse"];

export function SessionsList({ sessions }: { sessions: Session[] }) {
    const router = useRouter();
    const { t, locale } = useTranslation();
    const [pendingId, setPendingId] = useState<string | null>(null);
    const [isRevokingOthers, setIsRevokingOthers] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const hasOtherSessions = sessions.some((session) => !session.current);

    async function revoke(url: string, onStart: () => void, onFinish: () => void) {
        setError(null);
        onStart();
        try {
            const response = await fetch(url, { method: "DELETE" });
            if (!response.ok) {
                setError(t("account.sessionsError"));
                return;
            }
            router.refresh();
        } finally {
            onFinish();
        }
    }

    function handleRevoke(session: Session) {
        return revoke(
            `/api/account/sessions/${session.id}`,
            () => setPendingId(session.id),
            () => setPendingId(null),
        );
    }

    function handleRevokeOthers() {
        if (!confirm(t("account.sessionsRevokeOthersConfirm"))) {
            return;
        }
        return revoke(
            "/api/account/sessions",
            () => setIsRevokingOthers(true),
            () => setIsRevokingOthers(false),
        );
    }

    return (
        <div className="panel space-y-3">
            <div className="divide-y divide-border">
                {sessions.map((session) => (
                    <div key={session.id} className="py-3 first:pt-0 last:pb-0 flex items-start justify-between gap-4">
                        <div className="min-w-0 space-y-0.5">
                            <p className="text-sm font-medium">
                                {session.ipAddress ?? t("account.sessionUnknownAddress")}
                                {session.current && (
                                    <span className="ml-2 text-xs text-success">{t("account.sessionCurrent")}</span>
                                )}
                            </p>
                            <p className="text-xs text-text-muted truncate">
                                {session.userAgent ?? t("account.sessionUnknownDevice")}
                            </p>
                            <p className="text-xs text-text-muted">
                                {t("account.sessionLastActive", {
                                    date: new Date(session.lastActiveAt).toLocaleString(locale),
                                })}
                                {" · "}
                                {t("account.sessionStarted", {
                                    date: new Date(session.startedAt).toLocaleString(locale),
                                })}
                            </p>
                        </div>
                        {!session.current && (
                            <button
                                type="button"
                                onClick={() => handleRevoke(session)}
                                disabled={pendingId === session.id}
                                className="text-sm text-danger shrink-0"
                            >
                                {t("account.sessionSignOut")}
                            </button>
                        )}
                    </div>
                ))}
            </div>

            {error && <p className="text-sm text-danger">{error}</p>}

            {hasOtherSessions && (
                <button
                    type="button"
                    onClick={handleRevokeOthers}
                    disabled={isRevokingOthers}
                    className="btn-secondary text-danger"
                >
                    {t("account.sessionsRevokeOthers")}
                </button>
            )}
        </div>
    );
}
