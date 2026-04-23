import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
import { KeyRound } from 'lucide-react';
import { api, setToken } from './api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './components/ui/Card';
import { Button } from './components/ui/Button';
import { Input } from './components/ui/Input';
export default function Login({ onAuthed }) {
    const [token, setTokenInput] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(null);
    async function submit(e) {
        e.preventDefault();
        setBusy(true);
        setError(null);
        setToken(token.trim());
        try {
            await api('/api/health');
            onAuthed();
        }
        catch {
            setError('Token rejected by server.');
        }
        finally {
            setBusy(false);
        }
    }
    return (_jsx("div", { className: "min-h-dvh grid place-items-center p-6", children: _jsxs(Card, { className: "w-full max-w-md", children: [_jsxs(CardHeader, { children: [_jsx(CardTitle, { children: "TeaCore Dashboard" }), _jsxs(CardDescription, { children: ["Paste the admin token from", ' ', _jsx("code", { className: "font-mono text-primary", children: "plugins/TeaCore/config.yml" }), "."] })] }), _jsx(CardContent, { children: _jsxs("form", { onSubmit: submit, className: "space-y-4", children: [_jsx(Input, { type: "password", autoComplete: "off", value: token, onChange: (e) => setTokenInput(e.target.value), placeholder: "admin-token", className: "font-mono" }), error && _jsx("div", { className: "text-error text-sm", children: error }), _jsxs(Button, { disabled: busy, type: "submit", className: "w-full justify-center", children: [_jsx(KeyRound, { className: "h-4 w-4" }), busy ? 'Checking...' : 'Sign in'] })] }) })] }) }));
}
