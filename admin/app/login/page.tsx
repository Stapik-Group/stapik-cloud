"use client";

import { useState, type FormEvent } from "react";
import { useTranslation } from "@/lib/i18n/LocaleProvider";

export default function LoginPage() {
    const { t } = useTranslation();
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
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

            window.location.href = "/dashboard";
        } finally {
            setIsSubmitting(false);
        }
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