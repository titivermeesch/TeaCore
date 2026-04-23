import { useCallback, useEffect, useState } from 'react';
import { Layers, MapPin, RefreshCw, Timer, Trash2, UsersRound } from 'lucide-react';
import { api, apiDelete, apiPost } from '../api';
import type { GameSummary, MapInfo } from '../types';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';

export default function Games() {
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

    async function deleteMap(gameId: string, mapId: string) {
        if (!window.confirm(`Delete map ${gameId}/${mapId}? The schematic file is removed too.`)) {
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
            <div className="flex items-center justify-between">
                <h1 className="text-2xl font-semibold">Games</h1>
                <Button onClick={reloadAllMaps} disabled={reloading}>
                    <RefreshCw className={`h-4 w-4 ${reloading ? 'animate-spin' : ''}`} />
                    {reloading ? 'Reloading...' : 'Reload maps'}
                </Button>
            </div>
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
}: {
    game: GameSummary;
    maps: MapInfo[];
    onDeleteMap: (mapId: string) => void;
}) {
    return (
        <Card>
            <CardHeader>
                <div className="flex items-start justify-between">
                    <div>
                        <CardTitle className="font-mono">{game.id}</CardTitle>
                        <CardDescription>{game.displayNameKey}</CardDescription>
                    </div>
                    <Badge variant="muted">{game.teamMode}</Badge>
                </div>
            </CardHeader>
            <CardContent className="space-y-4">
                <div className="grid grid-cols-2 gap-3 text-sm">
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
                                    <Tr key={m.mapId}>
                                        <Td className="font-medium">{m.mapId}</Td>
                                        <Td className="tabular-nums">{m.participantSpawnCount}</Td>
                                        <Td className="tabular-nums">
                                            {m.minPlayers}-{m.maxPlayers}
                                        </Td>
                                        <Td className="text-xs text-muted">
                                            {m.extensions.length > 0 ? m.extensions.join(', ') : '-'}
                                        </Td>
                                        <Td className="text-right">
                                            <Button
                                                variant="danger"
                                                title="Delete map"
                                                onClick={() => onDeleteMap(m.mapId)}
                                            >
                                                <Trash2 className="h-4 w-4" />
                                            </Button>
                                        </Td>
                                    </Tr>
                                ))}
                            </Tbody>
                        </Table>
                    </div>
                )}
            </CardContent>
        </Card>
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
