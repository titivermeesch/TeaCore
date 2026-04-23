import { FormEvent, useState } from 'react';
import { KeyRound } from 'lucide-react';
import { api, setToken } from './api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './components/ui/Card';
import { Button } from './components/ui/Button';
import { Input } from './components/ui/Input';

export default function Login({ onAuthed }: { onAuthed: () => void }) {
    const [token, setTokenInput] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    async function submit(e: FormEvent) {
        e.preventDefault();
        setBusy(true);
        setError(null);
        setToken(token.trim());
        try {
            await api('/api/health');
            onAuthed();
        } catch {
            setError('Token rejected by server.');
        } finally {
            setBusy(false);
        }
    }

    return (
        <div className="min-h-dvh grid place-items-center p-6">
            <Card className="w-full max-w-md">
                <CardHeader>
                    <CardTitle>TeaCore Dashboard</CardTitle>
                    <CardDescription>
                        Paste the admin token from{' '}
                        <code className="font-mono text-primary">plugins/TeaCore/config.yml</code>.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <form onSubmit={submit} className="space-y-4">
                        <Input
                            type="password"
                            autoComplete="off"
                            value={token}
                            onChange={(e) => setTokenInput(e.target.value)}
                            placeholder="admin-token"
                            className="font-mono"
                        />
                        {error && <div className="text-error text-sm">{error}</div>}
                        <Button disabled={busy} type="submit" className="w-full justify-center">
                            <KeyRound className="h-4 w-4" />
                            {busy ? 'Checking...' : 'Sign in'}
                        </Button>
                    </form>
                </CardContent>
            </Card>
        </div>
    );
}
