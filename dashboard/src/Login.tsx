import { FormEvent, useEffect, useRef, useState } from 'react';
import { KeyRound, LogIn } from 'lucide-react';
import { apiPost } from './api';
import type { Me } from './auth';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './components/ui/Card';
import { Button } from './components/ui/Button';
import { Input } from './components/ui/Input';

/**
 * Sign in with the one-time code from /dashboard (or /tea dashboard login).
 * The link from the game opens /login?code=..., which signs in at once.
 */
export default function Login({ onAuthed }: { onAuthed: (me: Me) => void }) {
    const [code, setCode] = useState(() => new URLSearchParams(location.search).get('code') ?? '');
    const [useToken, setUseToken] = useState(false);
    const [token, setToken] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const autoTried = useRef(false);

    async function signIn(path: string, body: unknown) {
        setBusy(true);
        setError(null);
        try {
            const me = await apiPost<Me>(path, body);
            history.replaceState(null, '', '/');
            onAuthed(me);
        } catch (err) {
            setError((err as Error).message);
        } finally {
            setBusy(false);
        }
    }

    useEffect(() => {
        if (code && !autoTried.current) {
            autoTried.current = true;
            signIn('/api/auth/login', { code });
        }
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    function submit(e: FormEvent) {
        e.preventDefault();
        if (useToken) signIn('/api/auth/token', { token: token.trim() });
        else signIn('/api/auth/login', { code: code.trim() });
    }

    return (
        <div className="min-h-dvh grid place-items-center p-6">
            <Card className="w-full max-w-md">
                <CardHeader>
                    <CardTitle>TeaCore Dashboard</CardTitle>
                    <CardDescription>
                        {useToken ? (
                            <>Paste the admin token from <code className="font-mono text-primary">plugins/TeaCore/config.yml</code>.</>
                        ) : (
                            <>In game, run <code className="font-mono text-primary">/dashboard</code> and click the link, or type the code here.</>
                        )}
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <form onSubmit={submit} className="space-y-4">
                        {useToken ? (
                            <Input
                                type="password"
                                autoComplete="off"
                                value={token}
                                onChange={(e) => setToken(e.target.value)}
                                placeholder="admin-token"
                                className="font-mono"
                            />
                        ) : (
                            <Input
                                autoFocus
                                autoComplete="off"
                                value={code}
                                onChange={(e) => setCode(e.target.value.toUpperCase())}
                                placeholder="ABCD-EFGH"
                                className="font-mono tracking-widest text-center text-lg"
                                maxLength={9}
                            />
                        )}
                        {error && <div className="text-error text-sm">{error}</div>}
                        <Button disabled={busy} type="submit" className="w-full justify-center">
                            {useToken ? <KeyRound className="h-4 w-4" /> : <LogIn className="h-4 w-4" />}
                            {busy ? 'Signing in...' : 'Sign in'}
                        </Button>
                        <button
                            type="button"
                            onClick={() => {
                                setUseToken((v) => !v);
                                setError(null);
                            }}
                            className="w-full text-xs text-muted hover:text-slate-200"
                        >
                            {useToken ? 'Sign in with a code from the game' : 'Use the admin token instead'}
                        </button>
                    </form>
                </CardContent>
            </Card>
        </div>
    );
}
