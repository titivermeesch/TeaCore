import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
import { Search } from 'lucide-react';
import { api, ApiError } from '../api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';
export default function Players() {
    const [online, setOnline] = useState([]);
    const [query, setQuery] = useState('');
    const [profile, setProfile] = useState(null);
    const [error, setError] = useState(null);
    const [busy, setBusy] = useState(false);
    useEffect(() => {
        api('/api/players/online').then(setOnline).catch(() => { });
    }, []);
    async function load(uuid) {
        setBusy(true);
        setError(null);
        try {
            const result = await api(`/api/players/${uuid}`);
            setProfile(result);
        }
        catch (err) {
            setProfile(null);
            if (err instanceof ApiError && err.status === 404) {
                setError('No profile or stats for that uuid yet.');
            }
            else {
                setError(err.message);
            }
        }
        finally {
            setBusy(false);
        }
    }
    function onSubmit(e) {
        e.preventDefault();
        if (query.trim())
            load(query.trim());
    }
    return (_jsxs("div", { className: "space-y-6", children: [_jsx("h1", { className: "text-2xl font-semibold", children: "Players" }), _jsxs("form", { onSubmit: onSubmit, className: "flex gap-2", children: [_jsx(Input, { value: query, onChange: (e) => setQuery(e.target.value), placeholder: "Player UUID", className: "max-w-md" }), _jsxs(Button, { type: "submit", disabled: busy, children: [_jsx(Search, { className: "h-4 w-4" }), busy ? 'Looking up...' : 'Look up'] })] }), error && _jsx("div", { className: "text-error text-sm", children: error }), _jsxs("div", { className: "grid lg:grid-cols-3 gap-4", children: [_jsxs(Card, { className: "lg:col-span-1", children: [_jsxs(CardHeader, { children: [_jsxs(CardTitle, { children: ["Online (", online.length, ")"] }), _jsx(CardDescription, { children: "Click to load a player's profile and stats." })] }), _jsx(CardContent, { className: "px-0 pt-0", children: online.length === 0 ? (_jsx("div", { className: "px-5 py-3 text-sm text-muted", children: "Nobody is online." })) : (_jsx("ul", { className: "divide-y divide-border", children: online.map((p) => (_jsxs("li", { className: "px-5 py-2 text-sm cursor-pointer hover:bg-bg-raised/50", onClick: () => {
                                            setQuery(p.uuid);
                                            load(p.uuid);
                                        }, children: [_jsx("div", { className: "font-medium", children: p.name ?? p.uuid.slice(0, 8) }), _jsx("div", { className: "font-mono text-xs text-muted", children: p.uuid })] }, p.uuid))) })) })] }), _jsx("div", { className: "lg:col-span-2", children: profile ? _jsx(ProfileCard, { profile: profile }) : (_jsx(Card, { children: _jsx(CardContent, { className: "text-sm text-muted", children: "Paste a UUID and press Look up, or pick a player from the online list." }) })) })] })] }));
}
function ProfileCard({ profile }) {
    return (_jsxs("div", { className: "space-y-4", children: [_jsxs(Card, { children: [_jsx(CardHeader, { children: _jsxs("div", { className: "flex items-start justify-between", children: [_jsxs("div", { children: [_jsx(CardTitle, { children: profile.name ?? 'Unknown' }), _jsx(CardDescription, { className: "font-mono", children: profile.uuid })] }), _jsx(Badge, { variant: profile.online ? 'success' : 'muted', children: profile.online ? 'online' : 'offline' })] }) }), _jsx(CardContent, { children: _jsxs("dl", { className: "grid grid-cols-3 gap-4 text-sm", children: [_jsx(Meta, { label: "First seen", value: profile.firstSeen }), _jsx(Meta, { label: "Last seen", value: profile.lastSeen }), _jsx(Meta, { label: "Locale", value: profile.locale })] }) })] }), Object.keys(profile.statsByGame).length === 0 ? (_jsx(Card, { children: _jsx(CardContent, { className: "text-sm text-muted", children: "No recorded stats for this player yet." }) })) : (Object.entries(profile.statsByGame).map(([gameId, entries]) => (_jsxs(Card, { children: [_jsx(CardHeader, { children: _jsx(CardTitle, { className: "font-mono", children: gameId }) }), _jsx(CardContent, { className: "px-0 pt-0", children: _jsxs(Table, { children: [_jsx(Thead, { children: _jsxs(Tr, { children: [_jsx(Th, { children: "Stat" }), _jsx(Th, { children: "Value" })] }) }), _jsx(Tbody, { children: Object.entries(entries).map(([label, value]) => (_jsxs(Tr, { children: [_jsx(Td, { children: label }), _jsx(Td, { className: "font-mono tabular-nums", children: value })] }, label))) })] }) })] }, gameId))))] }));
}
function Meta({ label, value }) {
    return (_jsxs("div", { children: [_jsx("div", { className: "text-xs text-muted uppercase tracking-wide", children: label }), _jsx("div", { className: "font-mono text-sm mt-0.5", children: value ?? '-' })] }));
}
