import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
import { Activity, Gamepad2, Swords, Users } from 'lucide-react';
import { api } from '../api';
import { useDashboardEvent, useDashboardWs } from '../ws';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';
import { stateVariant } from '../lib/arena';
export default function Overview() {
    const [games, setGames] = useState([]);
    const [arenas, setArenas] = useState([]);
    const [error, setError] = useState(null);
    const { queueCounts } = useDashboardWs();
    useEffect(() => {
        (async () => {
            try {
                const [g, a] = await Promise.all([
                    api('/api/games'),
                    api('/api/arenas'),
                ]);
                setGames(g);
                setArenas(a);
            }
            catch (err) {
                setError(err.message);
            }
        })();
    }, []);
    useDashboardEvent((event) => {
        if (event.type === 'arena.state') {
            setArenas((prev) => prev.map((a) => a.id === event.arenaId
                ? { ...a, state: event.to, phaseSecondsRemaining: event.phaseSecondsRemaining }
                : a));
        }
        else if (event.type === 'arena.player.join' || event.type === 'arena.player.leave') {
            api('/api/arenas').then(setArenas).catch(() => { });
        }
    });
    const totalPlayers = arenas.reduce((n, a) => n + a.players.length, 0);
    const liveArenas = arenas.filter((a) => a.state === 'LIVE').length;
    const waitingArenas = arenas.filter((a) => a.state === 'WAITING' || a.state === 'COUNTDOWN').length;
    const queued = Object.values(queueCounts).reduce((n, v) => n + v, 0);
    return (_jsxs("div", { className: "space-y-6", children: [_jsx("h1", { className: "text-2xl font-semibold", children: "Overview" }), error && (_jsx(Card, { className: "border-error/40", children: _jsx(CardContent, { className: "text-error", children: error }) })), _jsxs("div", { className: "grid grid-cols-2 md:grid-cols-4 gap-4", children: [_jsx(Stat, { icon: _jsx(Gamepad2, {}), label: "Games", value: games.length }), _jsx(Stat, { icon: _jsx(Swords, {}), label: "Arenas", value: arenas.length, detail: `${liveArenas} live · ${waitingArenas} waiting` }), _jsx(Stat, { icon: _jsx(Users, {}), label: "Players in arenas", value: totalPlayers }), _jsx(Stat, { icon: _jsx(Activity, {}), label: "Queued", value: queued, detail: Object.keys(queueCounts).length
                            ? Object.entries(queueCounts).map(([g, n]) => `${g}: ${n}`).join(' · ')
                            : undefined })] }), _jsxs(Card, { children: [_jsx(CardHeader, { children: _jsx(CardTitle, { children: "Arenas" }) }), _jsx(CardContent, { className: "px-0 pt-0", children: arenas.length === 0 ? (_jsx("div", { className: "px-5 py-3 text-sm text-muted", children: "No arenas allocated." })) : (_jsxs(Table, { children: [_jsx(Thead, { children: _jsxs(Tr, { children: [_jsx(Th, { children: "Game" }), _jsx(Th, { children: "Map" }), _jsx(Th, { children: "State" }), _jsx(Th, { children: "Players" }), _jsx(Th, { children: "Phase" })] }) }), _jsx(Tbody, { children: arenas.map((a) => (_jsxs(Tr, { children: [_jsx(Td, { className: "font-medium", children: a.gameId }), _jsx(Td, { children: a.mapId ?? '-' }), _jsx(Td, { children: _jsx(Badge, { variant: stateVariant(a.state), children: a.state }) }), _jsxs(Td, { className: "tabular-nums", children: [a.players.length, "/", a.maxPlayers] }), _jsx(Td, { className: "font-mono text-muted tabular-nums", children: a.phaseSecondsRemaining < 0 ? '-' : `${a.phaseSecondsRemaining}s` })] }, a.id))) })] })) })] })] }));
}
function Stat({ icon, label, value, detail, }) {
    return (_jsx(Card, { children: _jsx(CardContent, { children: _jsxs("div", { className: "flex items-start justify-between", children: [_jsxs("div", { children: [_jsx("div", { className: "text-xs text-muted uppercase tracking-wide", children: label }), _jsx("div", { className: "text-2xl font-semibold mt-1 tabular-nums", children: value }), detail && _jsx("div", { className: "text-xs text-muted mt-1", children: detail })] }), icon && _jsx("div", { className: "text-primary/60 [&>svg]:h-5 [&>svg]:w-5", children: icon })] }) }) }));
}
