"use client";

import { useState, type SubmitEventHandler } from "react";
import { useTranslation } from "@/lib/i18n/LocaleProvider";

export default function LoginPage() {
    const { t } = useTranslation();
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [verificationCode, setVerificationCode] = useState("");
    const [isAwaitingCode, setIsAwaitingCode] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();
        setError(null);
        setIsSubmitting(true);

        try {
            const response = await fetch("/api/auth/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username, password }),
            });

            if (!response.ok) {
                setError(t("login.invalidCredentials"));
                return;
            }

            const { twoFactorRequired }: { twoFactorRequired: boolean } = await response.json();
            if (twoFactorRequired) {
                setPassword("");
                setIsAwaitingCode(true);
                return;
            }

            window.location.href = "/dashboard";
        } finally {
            setIsSubmitting(false);
        }
    }

    const handleCodeSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();
        setError(null);
        setIsSubmitting(true);

        try {
            const response = await fetch("/api/auth/login/two-factor", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ code: verificationCode }),
            });

            if (!response.ok) {
                setError(t("login.invalidCode"));
                return;
            }

            window.location.href = "/dashboard";
        } finally {
            setIsSubmitting(false);
        }
    }

    function handleBack() {
        setIsAwaitingCode(false);
        setVerificationCode("");
        setError(null);
    }

    if (isAwaitingCode) {
        return (
            <main className="flex min-h-screen items-center justify-center">
                <form onSubmit={handleCodeSubmit} className="panel w-full max-w-sm space-y-4">
                    <h1 className="text-lg font-semibold">{t("login.twoFactorTitle")}</h1>
                    <p className="text-sm text-text-muted">{t("login.twoFactorHint")}</p>

                    <input
                        id="verificationCode"
                        type="text"
                        required
                        autoFocus
                        autoComplete="one-time-code"
                        value={verificationCode}
                        onChange={(e) => setVerificationCode(e.target.value)}
                        className="input w-full"
                        aria-label={t("login.twoFactorCode")}
                    />

                    {error && <p className="text-sm text-danger">{error}</p>}

                    <button type="submit" disabled={isSubmitting} className="btn-primary w-full">
                        {isSubmitting ? t("login.submitting") : t("login.verify")}
                    </button>
                    <button type="button" onClick={handleBack} className="btn-secondary w-full">
                        {t("login.back")}
                    </button>
                </form>
            </main>
        );
    }

    return (
        <main className="flex min-h-screen items-center justify-center">
            <form onSubmit={handleSubmit} className="panel w-full max-w-sm space-y-4">
                <h1 className="text-lg font-semibold">{t("login.title")}</h1>

                <div className="space-y-1">
                    <label htmlFor="username" className="text-sm text-text-muted">
                        {t("login.username")}
                    </label>
                    <input
                        id="username"
                        type="text"
                        required
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        className="input w-full"
                    />
                </div>

                <div className="space-y-1">
                    <label htmlFor="password" className="text-sm text-text-muted">
                        {t("login.password")}
                    </label>
                    <input
                        id="password"
                        type="password"
                        required
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        className="input w-full"
                    />
                </div>

                {error && <p className="text-sm text-danger">{error}</p>}

                <button type="submit" disabled={isSubmitting} className="btn-primary w-full">
                    {isSubmitting ? t("login.submitting") : t("login.submit")}
                </button>
            </form>
        </main>
    );
}