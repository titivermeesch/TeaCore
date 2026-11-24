// Thin fetch wrapper. Sign-in sets an HttpOnly session cookie, which the
// browser sends on every API call and on the WebSocket handshake, so no
// token is ever stored in the page.

export class ApiError extends Error {
    constructor(public status: number, message: string) {
        super(message);
    }
}

/** Fired when the server says the session is gone; App shows the login page. */
export const UNAUTHORIZED_EVENT = 'tea:unauthorized';

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers);
    headers.set('Accept', 'application/json');

    const res = await fetch(path, { ...init, headers, credentials: 'same-origin' });
    if (res.status === 401 && !path.startsWith('/api/auth/')) {
        window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
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
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}${path}`;
}
