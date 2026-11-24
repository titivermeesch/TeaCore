import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Play, Plus, Square, Trophy } from 'lucide-react';
import { api, apiPost } from '../api';
import { P, useCan } from '../auth';
import type { Arena, GameSummary, MapInfo } from '../types';
import { useDashboardEvent } from '../ws';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';
import { stateVariant } from '../lib/arena';

const STATE_FILTERS = ['ALL', 'WAITING', 'COUNTDOWN', 'LIVE', 'ENDING'] as const;
type StateFilter = (typeof STATE_FILTERS)[number];

export default function Arenas() {
    const [arenas, setArenas] = useState<Arena[]>([]);
    const [games, setGames] = useState<GameSummary[]>([]);
    const [filter, setFilter] = useState<StateFilter>('ALL');
    const [showAllocate, setShowAllocate] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const canAllocate = useCan(P.arenaAllocate);

    async function refresh() {
        try {
            const data = await api<Arena[]>('/api/arenas');
            setArenas(data);
        } catch (err) {
            setError((err as Error).message);
        }
    }

    useEffect(() => {
        refresh();
        api<GameSummary[]>('/api/games').then(setGames).catch(() => {});
    }, []);

    useDashboardEvent((event) => {
        if (event.type === 'arena.state') {
            setArenas((prev) =>
                prev.map((a) =>
                    a.id === event.arenaId
                        ? { ...a, state: event.to, phaseSecondsRemaining: event.phaseSecondsRemaining }
                        : a,
                ),
            );
            // State transitions that allocate/release change the roster,
            // so refetch for the authoritative view.
            if (event.to === 'RELEASED' || event.to === 'WAITING') refresh();
        } else if (event.type === 'arena.player.join' || event.type === 'arena.player.leave') {
            refresh();
        }
    });

    const filtered = useMemo(
        () => arenas.filter((a) => filter === 'ALL' || a.state === filter),
        [arenas, filter],
    );

    async function doAction(label: string, fn: () => Promise<unknown>, confirmMsg?: string) {
        if (confirmMsg && !window.confirm(confirmMsg)) return;
        try {
            await fn();
            setError(null);
            refresh();
        } catch (err) {
            setError(`${label}: ${(err as Error).message}`);
        }
    }

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between gap-4 flex-wrap">
                <h1 className="text-2xl font-semibold">Arenas</h1>
                <div className="flex items-center gap-2">
                    <div className="flex gap-1">
                        {STATE_FILTERS.map((s) => (
                            <Button
                                key={s}
                                variant={filter === s ? 'default' : 'ghost'}
                                onClick={() => setFilter(s)}
                            >
                                {s}
                            </Button>
                        ))}
                    </div>
                    {canAllocate && (
                        <Button onClick={() => setShowAllocate((v) => !v)}>
                            <Plus className="h-4 w-4" />
                            Allocate
                        </Button>
                    )}
                </div>
            </div>
            {showAllocate && (
                <AllocateForm
                    games={games}
                    onAllocated={() => {
                        setShowAllocate(false);
                        refresh();
                    }}
                    onError={setError}
                />
            )}
            {error && (
                <Card className="border-error/40">
                    <CardContent className="text-error text-sm">{error}</CardContent>
                </Card>
            )}
            <Card>
                <CardContent className="px-0 pt-0">
                    {filtered.length === 0 ? (
                        <div className="px-5 py-6 text-sm text-muted text-center">No arenas match this filter.</div>
                    ) : (
                        <Table>
                            <Thead>
                                <Tr>
                                    <Th>Server</Th>
                                    <Th>Game</Th>
                                    <Th>Map</Th>
                                    <Th>State</Th>
                                    <Th>Players</Th>
                                    <Th>Phase</Th>
                                    <Th className="text-right">Actions</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {filtered.map((a) => (
                                    <ArenaRow key={a.id} arena={a} onAction={doAction} />
                                ))}
                            </Tbody>
                        </Table>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}

function AllocateForm({
    games,
    onAllocated,
    onError,
}: {
    games: GameSummary[];
    onAllocated: () => void;
    onError: (msg: string) => void;
}) {
    const [gameId, setGameId] = useState(games[0]?.id ?? '');
    const [mapId, setMapId] = useState('');
    const [maps, setMaps] = useState<MapInfo[]>([]);
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        if (!gameId) return;
        api<MapInfo[]>(`/api/maps/${gameId}`).then(setMaps).catch(() => setMaps([]));
    }, [gameId]);

    async function submit(e: FormEvent) {
        e.preventDefault();
        setBusy(true);
        try {
            await apiPost('/api/arenas', { gameId, mapId: mapId || undefined });
            onAllocated();
        } catch (err) {
            onError(`Allocate: ${(err as Error).message}`);
        } finally {
            setBusy(false);
        }
    }

    return (
        <Card>
            <CardHeader>
                <CardTitle>Allocate arena</CardTitle>
            </CardHeader>
            <CardContent>
                <form onSubmit={submit} className="flex flex-wrap gap-3 items-end">
                    <Field label="Game">
                        <select
                            value={gameId}
                            onChange={(e) => setGameId(e.target.value)}
                            className="rounded-md bg-bg-raised border border-border px-3 py-2 text-sm"
                        >
                            {games.map((g) => (
                                <option key={g.id} value={g.id}>
                                    {g.id}
                                </option>
                            ))}
                        </select>
                    </Field>
                    <Field label="Map (optional)">
                        <select
                            value={mapId}
                            onChange={(e) => setMapId(e.target.value)}
                            className="rounded-md bg-bg-raised border border-border px-3 py-2 text-sm"
                        >
                            <option value="">(pool policy)</option>
                            {maps.map((m) => (
                                <option key={m.mapId} value={m.mapId}>
                                    {m.mapId}
                                </option>
                            ))}
                        </select>
                    </Field>
                    <Button type="submit" disabled={busy || !gameId}>
                        {busy ? 'Allocating...' : 'Allocate'}
                    </Button>
                </form>
            </CardContent>
        </Card>
    );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
    return (
        <label className="flex flex-col gap-1 text-xs text-muted uppercase tracking-wide">
            {label}
            <div className="normal-case text-sm text-slate-100">{children}</div>
        </label>
    );
}

function ArenaRow({
    arena,
    onAction,
}: {
    arena: Arena;
    onAction: (label: string, fn: () => Promise<unknown>, confirmMsg?: string) => Promise<void>;
}) {
    const canStartPerm = useCan(P.arenaStart);
    const canEndPerm = useCan(P.arenaEnd);
    const canReleasePerm = useCan(P.arenaRelease);
    const canStart = canStartPerm && (arena.state === 'WAITING' || arena.state === 'COUNTDOWN');
    const isLive = arena.state === 'LIVE';
    const isReleased = arena.state === 'RELEASED';

    return (
        <Tr>
            <Td className="font-mono text-xs text-muted">{arena.serverId}</Td>
            <Td className="font-medium">
                <Link to={`/arenas/${arena.id}`} className="text-primary hover:underline">
                    {arena.gameId} <span className="font-mono text-xs text-muted">#{arena.id.slice(0, 8)}</span>
                </Link>
            </Td>
            <Td>{arena.mapId ?? '-'}</Td>
            <Td>
                <Badge variant={stateVariant(arena.state)}>{arena.state}</Badge>
            </Td>
            <Td className="tabular-nums">
                {arena.players.length}/{arena.maxPlayers}
                {arena.spectators.length > 0 && <span className="text-muted"> +{arena.spectators.length}</span>}
            </Td>
            <Td className="font-mono text-muted tabular-nums">
                {arena.phaseSecondsRemaining < 0 ? '-' : `${arena.phaseSecondsRemaining}s`}
            </Td>
            <Td>
                <div className="flex justify-end gap-1">
                    {canStart && (
                        <Button
                            variant="ghost"
                            title="Skip to the next phase"
                            onClick={() => onAction('force-start', () => apiPost(`/api/arenas/${arena.id}/start`))}
                        >
                            <Play className="h-4 w-4" />
                        </Button>
                    )}
                    {canEndPerm && isLive && (
                        <Link to={`/arenas/${arena.id}`} title="End match (pick the winner)">
                            <Button variant="ghost">
                                <Trophy className="h-4 w-4" />
                            </Button>
                        </Link>
                    )}
                    {canReleasePerm && !isReleased && (
                        <Button
                            variant="danger"
                            title="Release"
                            onClick={() =>
                                onAction(
                                    'release',
                                    () => apiPost(`/api/arenas/${arena.id}/release`),
                                    `Release arena ${arena.id}? Players will be evacuated.`,
                                )
                            }
                        >
                            <Square className="h-4 w-4" />
                        </Button>
                    )}
                </div>
            </Td>
        </Tr>
    );
}
