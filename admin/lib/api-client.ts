import { cookies } from "next/headers";

const API_BASE_URL = process.env.STAPIK_CLOUD_API_URL ?? "http://localhost:8099";
const AUTH_COOKIE_NAME = "stapik_admin_token";

export async function getAuthToken(): Promise<string | undefined> {
    const cookieStore = await cookies();
    return cookieStore.get(AUTH_COOKIE_NAME)?.value;
}

export async function adminApiFetch(path: string, init: RequestInit = {}): Promise<Response> {
    const token = await getAuthToken();

    const headers = new Headers(init.headers);
    headers.set("Content-Type", "application/json");
    if (token) {
        headers.set("Authorization", `Bearer ${token}`);
    }

    return fetch(`${API_BASE_URL}${path}`, {
        ...init,
        headers,
    });
}

export { AUTH_COOKIE_NAME, API_BASE_URL };