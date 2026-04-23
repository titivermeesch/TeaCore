// Thin fetch wrapper that carries the admin token TeaCore issued. The token is
// typed in once on the login screen and persisted in localStorage; it is added
// as a Bearer header on every API call and as a query param on the WebSocket
// handshake (browsers cannot set headers on WS upgrades).
const TOKEN_KEY = 'tea.admin.token';

export function getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
    localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
    localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
    constructor(public status: number, message: string) {
        super(message);
    }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
    const token = getToken();
    const headers = new Headers(init.headers);
    if (token) headers.set('Authorization', `Bearer ${token}`);
    headers.set('Accept', 'application/json');

    const res = await fetch(path, { ...init, headers });
    if (res.status === 401) {
        clearToken();
        throw new ApiError(401, 'unauthorized');
    }
    if (!res.ok) {
        // Try to surface server-sent {"error":"..."} bodies.
        let msg = `${res.status} ${res.statusText}`;
        try {
            const body = (await res.clone().json()) as { error?: string };
            if (body?.error) msg = body.error;
        } catch {
            /* non-json body, keep the status line */
        }
        throw new ApiError(res.status, msg);
    }
    if (res.status === 204) return undefined as unknown as T;
    return (await res.json()) as T;
}

/** POST JSON helper. */
export function apiPost<T>(path: string, body?: unknown) {
    return api<T>(path, {
        method: 'POST',
        headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
}

/** DELETE helper. */
export function apiDelete<T>(path: string) {
    return api<T>(path, { method: 'DELETE' });
}

export function wsUrl(path: string): string {
    const token = getToken() ?? '';
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}${path}?token=${encodeURIComponent(token)}`;
}
