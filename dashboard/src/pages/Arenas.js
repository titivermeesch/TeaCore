import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useEffect, useMemo, useState } from 'react';
import { ChevronDown, ChevronRight, Play, Plus, Square, Trophy } from 'lucide-react';
import { api, apiPost } from '../api';
import { useDashboardEvent } from '../ws';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';
import { stateVariant } from '../lib/arena';
const STATE_FILTERS = ['ALL', 'WAITING', 'COUNTDOWN', 'LIVE', 'ENDING'];
export default function Arenas() {
    const [arenas, setArenas] = useState([]);
    const [games, setGames] = useState([]);
    const [filter, setFilter] = useState('ALL');
    const [expanded, setExpanded] = useState(new Set());
    const [showAllocate, setShowAllocate] = useState(false);
    const [error, setError] = useState(null);
    async function refresh() {
        try {
            const data = await api('/api/arenas');
            setArenas(data);
        }
        catch (err) {
            setError(err.message);
        }
    }
    useEffect(() => {
        refresh();
        api('/api/games').then(setGames).catch(() => { });
    }, []);
    useDashboardEvent((event) => {
        if (event.type === 'arena.state') {
            setArenas((prev) => prev.map((a) => a.id === event.arenaId
                ? { ...a, state: event.to, phaseSecondsRemaining: event.phaseSecondsRemaining }
                : a));
            // State transitions that allocate/release change the roster,
            // so refetch for the authoritative view.
            if (event.to === 'RELEASED' || event.to === 'WAITING')
                refresh();
        }
        else if (event.type === 'arena.player.join' || event.type === 'arena.player.leave') {
            refresh();
        }
    });
    const filtered = useMemo(() => arenas.filter((a) => filter === 'ALL' || a.state === filter), [arenas, filter]);
    function toggle(id) {
        setExpanded((prev) => {
            const next = new Set(prev);
            if (next.has(id))
                next.delete(id);
            else
                next.add(id);
            return next;
        });
    }
    async function doAction(label, fn, confirmMsg) {
        if (confirmMsg && !window.confirm(confirmMsg))
            return;
        try {
            await fn();
            setError(null);
            refresh();
        }
        catch (err) {
            setError(`${label}: ${err.message}`);
        }
    }
    return (_jsxs("div", { className: "space-y-6", children: [_jsxs("div", { className: "flex items-center justify-between gap-4 flex-wrap", children: [_jsx("h1", { className: "text-2xl font-semibold", children: "Arenas" }), _jsxs("div", { className: "flex items-center gap-2", children: [_jsx("div", { className: "flex gap-1", children: STATE_FILTERS.map((s) => (_jsx(Button, { variant: filter === s ? 'default' : 'ghost', onClick: () => setFilter(s), children: s }, s))) }), _jsxs(Button, { onClick: () => setShowAllocate((v) => !v), children: [_jsx(Plus, { className: "h-4 w-4" }), "Allocate"] })] })] }), showAllocate && (_jsx(AllocateForm, { games: games, onAllocated: () => {
                    setShowAllocate(false);
                    refresh();
                }, onError: setError })), error && (_jsx(Card, { className: "border-error/40", children: _jsx(CardContent, { className: "text-error text-sm", children: error }) })), _jsx(Card, { children: _jsx(CardContent, { className: "px-0 pt-0", children: filtered.length === 0 ? (_jsx("div", { className: "px-5 py-6 text-sm text-muted text-center", children: "No arenas match this filter." })) : (_jsxs(Table, { children: [_jsx(Thead, { children: _jsxs(Tr, { children: [_jsx(Th, { className: "w-6" }), _jsx(Th, { children: "Server" }), _jsx(Th, { children: "Game" }), _jsx(Th, { children: "Map" }), _jsx(Th, { children: "State" }), _jsx(Th, { children: "Players" }), _jsx(Th, { children: "Phase" }), _jsx(Th, { className: "text-right", children: "Actions" })] }) }), _jsx(Tbody, { children: filtered.map((a) => (_jsx(ArenaRows, { arena: a, open: expanded.has(a.id), onToggle: () => toggle(a.id), onAction: doAction }, a.id))) })] })) }) })] }));
}
function AllocateForm({ games, onAllocated, onError, }) {
    const [gameId, setGameId] = useState(games[0]?.id ?? '');
    const [mapId, setMapId] = useState('');
    const [maps, setMaps] = useState([]);
    const [busy, setBusy] = useState(false);
    useEffect(() => {
        if (!gameId)
            return;
        api(`/api/maps/${gameId}`).then(setMaps).catch(() => setMaps([]));
    }, [gameId]);
    async function submit(e) {
        e.preventDefault();
        setBusy(true);
        try {
            await apiPost('/api/arenas', { gameId, mapId: mapId || undefined });
            onAllocated();
        }
        catch (err) {
            onError(`Allocate: ${err.message}`);
        }
        finally {
            setBusy(false);
        }
    }
    return (_jsxs(Card, { children: [_jsx(CardHeader, { children: _jsx(CardTitle, { children: "Allocate arena" }) }), _jsx(CardContent, { children: _jsxs("form", { onSubmit: submit, className: "flex flex-wrap gap-3 items-end", children: [_jsx(Field, { label: "Game", children: _jsx("select", { value: gameId, onChange: (e) => setGameId(e.target.value), className: "rounded-md bg-bg-raised border border-border px-3 py-2 text-sm", children: games.map((g) => (_jsx("option", { value: g.id, children: g.id }, g.id))) }) }), _jsx(Field, { label: "Map (optional)", children: _jsxs("select", { value: mapId, onChange: (e) => setMapId(e.target.value), className: "rounded-md bg-bg-raised border border-border px-3 py-2 text-sm", children: [_jsx("option", { value: "", children: "(pool policy)" }), maps.map((m) => (_jsx("option", { value: m.mapId, children: m.mapId }, m.mapId)))] }) }), _jsx(Button, { type: "submit", disabled: busy || !gameId, children: busy ? 'Allocating...' : 'Allocate' })] }) })] }));
}
function Field({ label, children }) {
    return (_jsxs("label", { className: "flex flex-col gap-1 text-xs text-muted uppercase tracking-wide", children: [label, _jsx("div", { className: "normal-case text-sm text-slate-100", children: children })] }));
}
function ArenaRows({ arena, open, onToggle, onAction, }) {
    const canStart = arena.state === 'WAITING' || arena.state === 'COUNTDOWN';
    const isLive = arena.state === 'LIVE';
    const isReleased = arena.state === 'RELEASED';
    return (_jsxs(_Fragment, { children: [_jsxs(Tr, { children: [_jsx(Td, { onClick: onToggle, className: "cursor-pointer", children: open ? _jsx(ChevronDown, { className: "h-4 w-4 text-muted" }) : _jsx(ChevronRight, { className: "h-4 w-4 text-muted" }) }), _jsx(Td, { className: "font-mono text-xs text-muted", children: arena.serverId }), _jsx(Td, { className: "font-medium", children: arena.gameId }), _jsx(Td, { children: arena.mapId ?? '-' }), _jsx(Td, { children: _jsx(Badge, { variant: stateVariant(arena.state), children: arena.state }) }), _jsx(Td, { className: "tabular-nums", children: arena.players.length > 0 ? `${arena.players.length}/${arena.maxPlayers}` : `-/${arena.maxPlayers}` }), _jsx(Td, { className: "font-mono text-muted tabular-nums", children: arena.phaseSecondsRemaining < 0 ? '-' : `${arena.phaseSecondsRemaining}s` }), _jsx(Td, { children: _jsxs("div", { className: "flex justify-end gap-1", children: [canStart && (_jsx(Button, { variant: "ghost", title: "Force start", onClick: () => onAction('force-start', () => apiPost(`/api/arenas/${arena.id}/start`)), children: _jsx(Play, { className: "h-4 w-4" }) })), isLive && (_jsx(Button, { variant: "ghost", title: "End match", onClick: () => onAction('end-match', () => apiPost(`/api/arenas/${arena.id}/end-match`, {})), children: _jsx(Trophy, { className: "h-4 w-4" }) })), !isReleased && (_jsx(Button, { variant: "danger", title: "Release", onClick: () => onAction('release', () => apiPost(`/api/arenas/${arena.id}/release`), `Release arena ${arena.id}? Players will be evacuated.`), children: _jsx(Square, { className: "h-4 w-4" }) }))] }) })] }), open && (_jsxs(Tr, { className: "bg-bg-raised/30", children: [_jsx(Td, {}), _jsx(Td, { colSpan: 7, children: _jsx(ArenaDetails, { arena: arena }) })] }))] }));
}
function ArenaDetails({ arena }) {
    const remote = arena.matchId === null;
    return (_jsxs("div", { className: "space-y-3 py-2 text-sm", children: [_jsxs("div", { className: "grid grid-cols-2 gap-4 font-mono text-xs", children: [_jsxs("div", { children: [_jsx("span", { className: "text-muted", children: "arena.id " }), _jsx("span", { children: arena.id })] }), _jsxs("div", { children: [_jsx("span", { className: "text-muted", children: "server " }), _jsx("span", { children: arena.serverId })] }), _jsxs("div", { children: [_jsx("span", { className: "text-muted", children: "match.id " }), _jsx("span", { children: arena.matchId ?? '(remote)' })] }), _jsxs("div", { children: [_jsx("span", { className: "text-muted", children: "world " }), _jsx("span", { children: arena.worldName ?? '(remote)' })] })] }), remote ? (_jsxs("div", { className: "text-xs text-muted", children: ["Arena is hosted on another backend; player rosters are not mirrored over the coordination channel. Sign in to the dashboard on ", _jsx("span", { className: "font-mono", children: arena.serverId }), " for the full roster."] })) : (_jsxs(_Fragment, { children: [_jsxs("div", { children: [_jsxs("div", { className: "text-xs uppercase text-muted tracking-wide mb-1", children: ["Players (", arena.players.length, ")"] }), arena.players.length === 0 ? (_jsx("div", { className: "text-muted text-xs", children: "none" })) : (_jsx("div", { className: "flex flex-wrap gap-2", children: arena.players.map((p) => (_jsx(Badge, { variant: "muted", children: p.name ?? p.uuid.slice(0, 8) }, p.uuid))) }))] }), arena.spectators.length > 0 && (_jsxs("div", { children: [_jsxs("div", { className: "text-xs uppercase text-muted tracking-wide mb-1", children: ["Spectators (", arena.spectators.length, ")"] }), _jsx("div", { className: "flex flex-wrap gap-2", children: arena.spectators.map((p) => (_jsx(Badge, { variant: "muted", children: p.name ?? p.uuid.slice(0, 8) }, p.uuid))) })] }))] }))] }));
}
