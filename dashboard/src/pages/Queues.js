import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useCallback, useEffect, useMemo, useState } from 'react';
import { UserMinus } from 'lucide-react';
import { api, apiDelete } from '../api';
import { useDashboardWs } from '../ws';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';
export default function Queues() {
    const [byGame, setByGame] = useState({});
    const [error, setError] = useState(null);
    const { queueCounts } = useDashboardWs();
    const refresh = useCallback(async () => {
        try {
            const data = await api('/api/queues');
            setByGame(data);
        }
        catch (err) {
            setError(err.message);
        }
    }, []);
    useEffect(() => {
        refresh();
    }, [refresh]);
    // Refresh queue contents every 3s; counts come from WS live.
    useEffect(() => {
        const t = window.setInterval(refresh, 3000);
        return () => window.clearInterval(t);
    }, [refresh]);
    async function dequeue(gameId, uuid, name) {
        if (!window.confirm(`Remove ${name} from the ${gameId} queue?`))
            return;
        try {
            await apiDelete(`/api/queues/${gameId}/${uuid}`);
            refresh();
        }
        catch (err) {
            setError(`Dequeue: ${err.message}`);
        }
    }
    const gameIds = useMemo(() => Array.from(new Set([...Object.keys(byGame), ...Object.keys(queueCounts)])).sort(), [byGame, queueCounts]);
    return (_jsxs("div", { className: "space-y-6", children: [_jsx("h1", { className: "text-2xl font-semibold", children: "Queues" }), error && _jsx("div", { className: "text-error text-sm", children: error }), gameIds.length === 0 ? (_jsx(Card, { children: _jsx(CardContent, { className: "text-sm text-muted", children: "Nobody queued right now." }) })) : (_jsx("div", { className: "grid lg:grid-cols-2 gap-4", children: gameIds.map((gid) => (_jsx(QueueCard, { gameId: gid, entries: byGame[gid] ?? [], liveCount: queueCounts[gid] ?? (byGame[gid]?.length ?? 0), onDequeue: (uuid, name) => dequeue(gid, uuid, name) }, gid))) }))] }));
}
function QueueCard({ gameId, entries, liveCount, onDequeue, }) {
    return (_jsxs(Card, { children: [_jsx(CardHeader, { children: _jsxs("div", { className: "flex items-center justify-between", children: [_jsx(CardTitle, { className: "font-mono", children: gameId }), _jsxs(Badge, { variant: liveCount > 0 ? 'default' : 'muted', children: [liveCount, " queued"] })] }) }), _jsx(CardContent, { className: "px-0 pt-0", children: entries.length === 0 ? (_jsx("div", { className: "px-5 py-3 text-sm text-muted", children: "Queue empty." })) : (_jsxs(Table, { children: [_jsx(Thead, { children: _jsxs(Tr, { children: [_jsx(Th, { className: "w-8", children: "#" }), _jsx(Th, { children: "Player" }), _jsx(Th, { children: "Maps" }), _jsx(Th, { children: "Queued" }), _jsx(Th, {})] }) }), _jsx(Tbody, { children: entries.map((e) => (_jsxs(Tr, { children: [_jsx(Td, { className: "font-mono text-muted tabular-nums", children: e.position + 1 }), _jsx(Td, { className: "font-medium", children: e.playerName }), _jsx(Td, { className: "text-xs text-muted", children: e.mapIds.length === 0 ? 'any' : e.mapIds.join(', ') }), _jsx(Td, { className: "font-mono text-xs text-muted", children: formatAgo(e.queuedAt) }), _jsx(Td, { className: "text-right", children: _jsx(Button, { variant: "ghost", title: "Remove from queue", onClick: () => onDequeue(e.playerUuid, e.playerName), children: _jsx(UserMinus, { className: "h-4 w-4" }) }) })] }, e.playerUuid))) })] })) })] }));
}
function formatAgo(iso) {
    const diff = Date.now() - Date.parse(iso);
    if (Number.isNaN(diff))
        return iso;
    const s = Math.max(0, Math.floor(diff / 1000));
    if (s < 60)
        return `${s}s ago`;
    if (s < 3600)
        return `${Math.floor(s / 60)}m ago`;
    return `${Math.floor(s / 3600)}h ago`;
}
