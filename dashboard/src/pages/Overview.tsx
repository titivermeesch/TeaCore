import { useEffect, useState } from 'react';
import { Activity, Gamepad2, Swords, Users } from 'lucide-react';
import { api } from '../api';
import type { Arena, GameSummary } from '../types';
import { useDashboardEvent, useDashboardWs } from '../ws';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';
import { stateVariant } from '../lib/arena';

export default function Overview() {
    const [games, setGames] = useState<GameSummary[]>([]);
    const [arenas, setArenas] = useState<Arena[]>([]);
    const [error, setError] = useState<string | null>(null);
    const { queueCounts } = useDashboardWs();

    useEffect(() => {
        (async () => {
            try {
                const [g, a] = await Promise.all([
                    api<GameSummary[]>('/api/games'),
                    api<Arena[]>('/api/arenas'),
                ]);
                setGames(g);
                setArenas(a);
            } catch (err) {
                setError((err as Error).message);
            }
        })();
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
        } else if (event.type === 'arena.player.join' || event.type === 'arena.player.leave') {
            api<Arena[]>('/api/arenas').then(setArenas).catch(() => {});
        }
    });

    const totalPlayers = arenas.reduce((n, a) => n + a.players.length, 0);
    const liveArenas = arenas.filter((a) => a.state === 'LIVE').length;
    const waitingArenas = arenas.filter((a) => a.state === 'WAITING' || a.state === 'COUNTDOWN').length;
    const queued = Object.values(queueCounts).reduce((n, v) => n + v, 0);

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-semibold">Overview</h1>
            {error && (
                <Card className="border-error/40">
                    <CardContent className="text-error">{error}</CardContent>
                </Card>
            )}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <Stat icon={<Gamepad2 />} label="Games" value={games.length} />
                <Stat
                    icon={<Swords />}
                    label="Arenas"
                    value={arenas.length}
                    detail={`${liveArenas} live · ${waitingArenas} waiting`}
                />
                <Stat icon={<Users />} label="Players in arenas" value={totalPlayers} />
                <Stat
                    icon={<Activity />}
                    label="Queued"
                    value={queued}
                    detail={
                        Object.keys(queueCounts).length
                            ? Object.entries(queueCounts).map(([g, n]) => `${g}: ${n}`).join(' · ')
                            : undefined
                    }
                />
            </div>
            <Card>
                <CardHeader>
                    <CardTitle>Arenas</CardTitle>
                </CardHeader>
                <CardContent className="px-0 pt-0">
                    {arenas.length === 0 ? (
                        <div className="px-5 py-3 text-sm text-muted">No arenas allocated.</div>
                    ) : (
                        <Table>
                            <Thead>
                                <Tr>
                                    <Th>Game</Th>
                                    <Th>Map</Th>
                                    <Th>State</Th>
                                    <Th>Players</Th>
                                    <Th>Phase</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {arenas.map((a) => (
                                    <Tr key={a.id}>
                                        <Td className="font-medium">{a.gameId}</Td>
                                        <Td>{a.mapId ?? '-'}</Td>
                                        <Td>
                                            <Badge variant={stateVariant(a.state)}>{a.state}</Badge>
                                        </Td>
                                        <Td className="tabular-nums">
                                            {a.players.length}/{a.maxPlayers}
                                        </Td>
                                        <Td className="font-mono text-muted tabular-nums">
                                            {a.phaseSecondsRemaining < 0 ? '-' : `${a.phaseSecondsRemaining}s`}
                                        </Td>
                                    </Tr>
                                ))}
                            </Tbody>
                        </Table>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}

function Stat({
    icon,
    label,
    value,
    detail,
}: {
    icon?: React.ReactNode;
    label: string;
    value: number | string;
    detail?: string;
}) {
    return (
        <Card>
            <CardContent>
                <div className="flex items-start justify-between">
                    <div>
                        <div className="text-xs text-muted uppercase tracking-wide">{label}</div>
                        <div className="text-2xl font-semibold mt-1 tabular-nums">{value}</div>
                        {detail && <div className="text-xs text-muted mt-1">{detail}</div>}
                    </div>
                    {icon && <div className="text-primary/60 [&>svg]:h-5 [&>svg]:w-5">{icon}</div>}
                </div>
            </CardContent>
        </Card>
    );
}
