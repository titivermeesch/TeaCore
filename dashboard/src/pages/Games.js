import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useCallback, useEffect, useState } from 'react';
import { Layers, MapPin, RefreshCw, Timer, Trash2, UsersRound } from 'lucide-react';
import { api, apiDelete, apiPost } from '../api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';
export default function Games() {
    const [games, setGames] = useState(null);
    const [maps, setMaps] = useState({});
    const [error, setError] = useState(null);
    const [reloading, setReloading] = useState(false);
    const refresh = useCallback(async () => {
        try {
            const list = await api('/api/games');
            setGames(list);
            const mapsByGame = await Promise.all(list.map(async (g) => [g.id, await api(`/api/maps/${g.id}`)]));
            setMaps(Object.fromEntries(mapsByGame));
        }
        catch (err) {
            setError(err.message);
        }
    }, []);
    useEffect(() => {
        refresh();
    }, [refresh]);
    async function reloadAllMaps() {
        setReloading(true);
        setError(null);
        try {
            await apiPost('/api/maps/reload');
            await refresh();
        }
        catch (err) {
            setError(`Reload: ${err.message}`);
        }
        finally {
            setReloading(false);
        }
    }
    async function deleteMap(gameId, mapId) {
        if (!window.confirm(`Delete map ${gameId}/${mapId}? The schematic file is removed too.`)) {
            return;
        }
        try {
            await apiDelete(`/api/maps/${gameId}/${mapId}`);
            refresh();
        }
        catch (err) {
            setError(`Delete ${mapId}: ${err.message}`);
        }
    }
    if (error && !games)
        return _jsx("div", { className: "text-error text-sm", children: error });
    if (!games)
        return _jsx("div", { className: "text-muted text-sm", children: "Loading..." });
    return (_jsxs("div", { className: "space-y-6", children: [_jsxs("div", { className: "flex items-center justify-between", children: [_jsx("h1", { className: "text-2xl font-semibold", children: "Games" }), _jsxs(Button, { onClick: reloadAllMaps, disabled: reloading, children: [_jsx(RefreshCw, { className: `h-4 w-4 ${reloading ? 'animate-spin' : ''}` }), reloading ? 'Reloading...' : 'Reload maps'] })] }), error && (_jsx(Card, { className: "border-error/40", children: _jsx(CardContent, { className: "text-error text-sm", children: error }) })), games.length === 0 ? (_jsx("div", { className: "text-muted text-sm", children: "No games registered." })) : (_jsx("div", { className: "grid lg:grid-cols-2 gap-4", children: games.map((g) => (_jsx(GameCard, { game: g, maps: maps[g.id] ?? [], onDeleteMap: (mapId) => deleteMap(g.id, mapId) }, g.id))) }))] }));
}
function GameCard({ game, maps, onDeleteMap, }) {
    return (_jsxs(Card, { children: [_jsx(CardHeader, { children: _jsxs("div", { className: "flex items-start justify-between", children: [_jsxs("div", { children: [_jsx(CardTitle, { className: "font-mono", children: game.id }), _jsx(CardDescription, { children: game.displayNameKey })] }), _jsx(Badge, { variant: "muted", children: game.teamMode })] }) }), _jsxs(CardContent, { className: "space-y-4", children: [_jsxs("div", { className: "grid grid-cols-2 gap-3 text-sm", children: [_jsxs(KV, { icon: _jsx(Timer, {}), label: "Waiting", children: ["max ", game.lifecycleMaxWaitSeconds, "s"] }), _jsxs(KV, { icon: _jsx(Timer, {}), label: "Countdown", children: [game.lifecycleCountdownSeconds, "s"] }), _jsxs(KV, { icon: _jsx(Timer, {}), label: "Starting", children: [game.lifecycleStartingSeconds, "s"] }), _jsx(KV, { icon: _jsx(Timer, {}), label: "Match cap", children: game.lifecycleMaxMatchSeconds > 0 ? `${game.lifecycleMaxMatchSeconds}s` : 'unlimited' }), _jsxs(KV, { icon: _jsx(UsersRound, {}), label: "Pool", children: ["min ", game.poolMinAvailable, " \u00B7 max ", game.poolMaxTotal] }), _jsx(KV, { icon: _jsx(Layers, {}), label: "On end", children: game.endMode }), _jsx(KV, { icon: _jsx(MapPin, {}), label: "Map selection", children: game.mapSelectionMode }), _jsx(KV, { icon: _jsx(MapPin, {}), label: "Maps", children: game.mapCount })] }), maps.length > 0 && (_jsx("div", { className: "border-t border-border -mx-5 px-5 pt-3", children: _jsxs(Table, { children: [_jsx(Thead, { children: _jsxs(Tr, { children: [_jsx(Th, { children: "Map" }), _jsx(Th, { children: "Spawns" }), _jsx(Th, { children: "Range" }), _jsx(Th, { children: "Extensions" }), _jsx(Th, {})] }) }), _jsx(Tbody, { children: maps.map((m) => (_jsxs(Tr, { children: [_jsx(Td, { className: "font-medium", children: m.mapId }), _jsx(Td, { className: "tabular-nums", children: m.participantSpawnCount }), _jsxs(Td, { className: "tabular-nums", children: [m.minPlayers, "-", m.maxPlayers] }), _jsx(Td, { className: "text-xs text-muted", children: m.extensions.length > 0 ? m.extensions.join(', ') : '-' }), _jsx(Td, { className: "text-right", children: _jsx(Button, { variant: "danger", title: "Delete map", onClick: () => onDeleteMap(m.mapId), children: _jsx(Trash2, { className: "h-4 w-4" }) }) })] }, m.mapId))) })] }) }))] })] }));
}
function KV({ icon, label, children, }) {
    return (_jsxs("div", { className: "flex items-start gap-2", children: [icon && _jsx("div", { className: "text-muted mt-0.5 [&>svg]:h-4 [&>svg]:w-4", children: icon }), _jsxs("div", { children: [_jsx("div", { className: "text-xs text-muted uppercase tracking-wide", children: label }), _jsx("div", { className: "tabular-nums", children: children })] })] }));
}
