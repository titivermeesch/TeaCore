// Thin fetch wrapper that carries the admin token TeaCore issued. The token is
// typed in once on the login screen and persisted in localStorage; it is added
// as a Bearer header on every API call and as a query param on the WebSocket
// handshake (browsers cannot set headers on WS upgrades).
const TOKEN_KEY = 'tea.admin.token';
export function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}
export function setToken(token) {
    localStorage.setItem(TOKEN_KEY, token);
}
export function clearToken() {
    localStorage.removeItem(TOKEN_KEY);
}
export class ApiError extends Error {
    status;
    constructor(status, message) {
        super(message);
        this.status = status;
    }
}
export async function api(path, init = {}) {
    const token = getToken();
    const headers = new Headers(init.headers);
    if (token)
        headers.set('Authorization', `Bearer ${token}`);
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
            const body = (await res.clone().json());
            if (body?.error)
                msg = body.error;
        }
        catch {
            /* non-json body, keep the status line */
        }
        throw new ApiError(res.status, msg);
    }
    if (res.status === 204)
        return undefined;
    return (await res.json());
}
/** POST JSON helper. */
export function apiPost(path, body) {
    return api(path, {
        method: 'POST',
        headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
}
/** DELETE helper. */
export function apiDelete(path) {
    return api(path, { method: 'DELETE' });
}
export function wsUrl(path) {
    const token = getToken() ?? '';
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}${path}?token=${encodeURIComponent(token)}`;
}
