import { Fragment, useCallback, useEffect, useState } from 'react';
import { Download, History, Layers, MapPin, RefreshCw, RotateCcw, Timer, Trash2, Upload, UsersRound } from 'lucide-react';
import { api, apiDelete, apiPost } from '../api';
import { P, useCan } from '../auth';
import type { GameSummary, MapInfo } from '../types';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';

export default function Games() {
    const canReload = useCan(P.mapsReload);
    const canTransfer = useCan(P.mapsTransfer);
    const [notice, setNotice] = useState<string | null>(null);
    const [uploading, setUploading] = useState(false);
    const [games, setGames] = useState<GameSummary[] | null>(null);
    const [maps, setMaps] = useState<Record<string, MapInfo[]>>({});
    const [error, setError] = useState<string | null>(null);
    const [reloading, setReloading] = useState(false);

    const refresh = useCallback(async () => {
        try {
            const list = await api<GameSummary[]>('/api/games');
            setGames(list);
            const mapsByGame = await Promise.all(
                list.map(async (g) => [g.id, await api<MapInfo[]>(`/api/maps/${g.id}`)] as const),
            );
            setMaps(Object.fromEntries(mapsByGame));
        } catch (err) {
            setError((err as Error).message);
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
        } catch (err) {
            setError(`Reload: ${(err as Error).message}`);
        } finally {
            setReloading(false);
        }
    }

    async function uploadMaps(file: File) {
        setUploading(true);
        setError(null);
        setNotice(null);
        try {
            const res = await api<{ maps: string[]; rooms: string[] }>('/api/maps-upload', {
                method: 'POST',
                headers: { 'Content-Type': 'application/zip' },
                body: file,
            });
            const parts = [];
            if (res.maps.length) parts.push(`maps ${res.maps.join(', ')}`);
            if (res.rooms.length) parts.push(`waiting rooms ${res.rooms.join(', ')}`);
            setNotice(`Uploaded ${parts.join(' and ')}. Every server gets them within seconds.`);
            refresh();
        } catch (err) {
            setError(`Upload: ${(err as Error).message}`);
        } finally {
            setUploading(false);
        }
    }

    async function deleteMap(gameId: string, mapId: string) {
        if (!window.confirm(`Delete map ${gameId}/${mapId}? A backup is kept, so you can restore it with /tea map restore.`)) {
            return;
        }
        try {
            await apiDelete(`/api/maps/${gameId}/${mapId}`);
            refresh();
        } catch (err) {
            setError(`Delete ${mapId}: ${(err as Error).message}`);
        }
    }

    if (error && !games) return <div className="text-error text-sm">{error}</div>;
    if (!games) return <div className="text-muted text-sm">Loading...</div>;

    return (
        <div className="space-y-6">
            <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-2xl font-semibold mr-auto">Games</h1>
                {canTransfer && (
                    <>
                        <a href="/api/maps-export" download>
                            <Button variant="ghost" type="button">
                                <Download className="h-4 w-4" /> Download all maps
                            </Button>
                        </a>
                        <label>
                            <input
                                type="file"
                                accept=".zip,application/zip"
                                className="hidden"
                                disabled={uploading}
                                onChange={(e) => {
                                    const f = e.target.files?.[0];
                                    e.target.value = '';
                                    if (f) uploadMaps(f);
                                }}
                            />
                            <span
                                className={`inline-flex cursor-pointer items-center gap-2 rounded-md border px-3 py-1.5 text-sm font-medium transition bg-primary/10 border-primary/40 text-primary hover:bg-primary/20 ${uploading ? 'opacity-50' : ''}`}
                            >
                                <Upload className="h-4 w-4" /> {uploading ? 'Uploading...' : 'Upload maps'}
                            </span>
                        </label>
                    </>
                )}
                {canReload && (
                    <Button onClick={reloadAllMaps} disabled={reloading}>
                        <RefreshCw className={`h-4 w-4 ${reloading ? 'animate-spin' : ''}`} />
                        {reloading ? 'Reloading...' : 'Reload maps'}
                    </Button>
                )}
            </div>
            {notice && <div className="text-success text-sm">{notice}</div>}
            {error && (
                <Card className="border-error/40">
                    <CardContent className="text-error text-sm">{error}</CardContent>
                </Card>
            )}
            {games.length === 0 ? (
                <div className="text-muted text-sm">No games registered.</div>
            ) : (
                <div className="grid lg:grid-cols-2 gap-4">
                    {games.map((g) => (
                        <GameCard
                            key={g.id}
                            game={g}
                            maps={maps[g.id] ?? []}
                            onDeleteMap={(mapId) => deleteMap(g.id, mapId)}
                            onRestored={refresh}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}

function GameCard({
    game,
    maps,
    onDeleteMap,
    onRestored,
}: {
    game: GameSummary;
    maps: MapInfo[];
    onDeleteMap: (mapId: string) => void;
    onRestored: () => void;
}) {
    const canDelete = useCan(P.mapsDelete);
    const canTransfer = useCan(P.mapsTransfer);
    const [openMap, setOpenMap] = useState<string | null>(null);
    return (
        <Card>
            <CardHeader>
                <div className="flex items-start justify-between">
                    <div>
                        <CardTitle className="font-mono">{game.id}</CardTitle>
                        <CardDescription>{game.displayNameKey}</CardDescription>
                    </div>
                    <Badge variant="muted">{game.hosts.length > 0 ? 'remote' : game.teamMode}</Badge>
                </div>
            </CardHeader>
            <CardContent className="space-y-4">
                {game.hosts.length > 0 ? (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                    <KV icon={<Layers />} label="Hosted on">
                        {game.hosts.join(', ')}
                    </KV>
                    <KV icon={<UsersRound />} label="Pool">
                        {game.poolMaxTotal > 0 ? `min ${game.poolMinAvailable} · max ${game.poolMaxTotal}` : 'game defaults'}
                    </KV>
                    <KV icon={<MapPin />} label="Maps">
                        {game.mapCount}
                    </KV>
                </div>
                ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                    <KV icon={<Timer />} label="Waiting">
                        max {game.lifecycleMaxWaitSeconds}s
                    </KV>
                    <KV icon={<Timer />} label="Countdown">
                        {game.lifecycleCountdownSeconds}s
                    </KV>
                    <KV icon={<Timer />} label="Starting">
                        {game.lifecycleStartingSeconds}s
                    </KV>
                    <KV icon={<Timer />} label="Match cap">
                        {game.lifecycleMaxMatchSeconds > 0 ? `${game.lifecycleMaxMatchSeconds}s` : 'unlimited'}
                    </KV>
                    <KV icon={<UsersRound />} label="Pool">
                        min {game.poolMinAvailable} · max {game.poolMaxTotal}
                    </KV>
                    <KV icon={<Layers />} label="On end">
                        {game.endMode}
                    </KV>
                    <KV icon={<MapPin />} label="Map selection">
                        {game.mapSelectionMode}
                    </KV>
                    <KV icon={<MapPin />} label="Maps">
                        {game.mapCount}
                    </KV>
                </div>
                )}
                {maps.length > 0 && (
                    <div className="border-t border-border -mx-5 px-5 pt-3">
                        <Table>
                            <Thead>
                                <Tr>
                                    <Th>Map</Th>
                                    <Th>Spawns</Th>
                                    <Th>Range</Th>
                                    <Th>Extensions</Th>
                                    <Th></Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {maps.map((m) => (
                                    <Fragment key={m.mapId}>
                                    <Tr>
                                        <Td className="font-medium">{m.mapId}</Td>
                                        <Td className="tabular-nums">{m.participantSpawnCount}</Td>
                                        <Td className="tabular-nums">
                                            {m.minPlayers}-{m.maxPlayers}
                                        </Td>
                                        <Td className="text-xs text-muted">
                                            {m.extensions.length > 0 ? m.extensions.join(', ') : '-'}
                                        </Td>
                                        <Td className="text-right whitespace-nowrap space-x-1">
                                            {canTransfer && (
                                                <a href={`/api/maps/${game.id}/${m.mapId}/download`} download>
                                                    <Button variant="ghost" title="Download map" type="button">
                                                        <Download className="h-4 w-4" />
                                                    </Button>
                                                </a>
                                            )}
                                            <Button
                                                variant="ghost"
                                                title="Older versions"
                                                onClick={() => setOpenMap(openMap === m.mapId ? null : m.mapId)}
                                            >
                                                <History className="h-4 w-4" />
                                            </Button>
                                            {canDelete && (
                                                <Button
                                                    variant="danger"
                                                    title="Delete map"
                                                    onClick={() => onDeleteMap(m.mapId)}
                                                >
                                                    <Trash2 className="h-4 w-4" />
                                                </Button>
                                            )}
                                        </Td>
                                    </Tr>
                                    {openMap === m.mapId && (
                                        <Tr>
                                            <Td colSpan={5}>
                                                <MapVersions gameId={game.id} mapId={m.mapId} onRestored={onRestored} />
                                            </Td>
                                        </Tr>
                                    )}
                                    </Fragment>
                                ))}
                            </Tbody>
                        </Table>
                    </div>
                )}
            </CardContent>
        </Card>
    );
}

type MapVersion = { number: number; when: string; actor: string; reason: string };

function MapVersions({ gameId, mapId, onRestored }: { gameId: string; mapId: string; onRestored: () => void }) {
    const canRestore = useCan(P.mapsRestore);
    const [versions, setVersions] = useState<MapVersion[] | null>(null);
    const [message, setMessage] = useState<string | null>(null);

    const load = useCallback(() => {
        api<MapVersion[]>(`/api/maps/${gameId}/${mapId}/versions`)
            .then(setVersions)
            .catch((err) => setMessage((err as Error).message));
    }, [gameId, mapId]);

    useEffect(load, [load]);

    async function restore(v: MapVersion) {
        if (!window.confirm(`Restore ${gameId}/${mapId} to version #${v.number}? The current version is kept as a backup.`)) {
            return;
        }
        try {
            await apiPost(`/api/maps/${gameId}/${mapId}/restore`, { version: v.number });
            setMessage(`Restored to #${v.number}.`);
            setTimeout(() => { load(); onRestored(); }, 800);
        } catch (err) {
            setMessage(`Restore: ${(err as Error).message}`);
        }
    }

    if (!versions) return <div className="text-muted text-xs">{message ?? 'Loading versions...'}</div>;
    return (
        <div className="space-y-2" data-testid={`versions-${mapId}`}>
            {message && <div className="text-xs text-muted">{message}</div>}
            {versions.length === 0 ? (
                <div className="text-muted text-xs">No older versions yet. One is kept each time the map is saved.</div>
            ) : (
                <ul className="space-y-1 text-xs">
                    {versions.map((v) => (
                        <li key={v.number} className="flex items-center justify-between gap-2">
                            <span>
                                <span className="font-mono">#{v.number}</span>{' '}
                                {new Date(v.when).toLocaleString()} <span className="text-muted">by {v.actor} ({v.reason})</span>
                            </span>
                            {canRestore && (
                                <Button variant="ghost" title={`Restore #${v.number}`} onClick={() => restore(v)}>
                                    <RotateCcw className="h-4 w-4" />
                                </Button>
                            )}
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}

function KV({
    icon,
    label,
    children,
}: {
    icon?: React.ReactNode;
    label: string;
    children: React.ReactNode;
}) {
    return (
        <div className="flex items-start gap-2">
            {icon && <div className="text-muted mt-0.5 [&>svg]:h-4 [&>svg]:w-4">{icon}</div>}
            <div>
                <div className="text-xs text-muted uppercase tracking-wide">{label}</div>
                <div className="tabular-nums">{children}</div>
            </div>
        </div>
    );
}
