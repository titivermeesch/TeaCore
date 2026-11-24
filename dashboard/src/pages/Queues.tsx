import { useCallback, useEffect, useMemo, useState } from 'react';
import { UserMinus } from 'lucide-react';
import { api, apiDelete } from '../api';
import { P, useCan } from '../auth';
import type { QueueEntry } from '../types';
import { useDashboardWs } from '../ws';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';

export default function Queues() {
    const [byGame, setByGame] = useState<Record<string, QueueEntry[]>>({});
    const [error, setError] = useState<string | null>(null);
    const { queueCounts } = useDashboardWs();

    const refresh = useCallback(async () => {
        try {
            const data = await api<Record<string, QueueEntry[]>>('/api/queues');
            setByGame(data);
        } catch (err) {
            setError((err as Error).message);
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

    async function dequeue(gameId: string, uuid: string, name: string) {
        if (!window.confirm(`Remove ${name} from the ${gameId} queue?`)) return;
        try {
            await apiDelete(`/api/queues/${gameId}/${uuid}`);
            refresh();
        } catch (err) {
            setError(`Dequeue: ${(err as Error).message}`);
        }
    }

    const gameIds = useMemo(
        () => Array.from(new Set([...Object.keys(byGame), ...Object.keys(queueCounts)])).sort(),
        [byGame, queueCounts],
    );

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-semibold">Queues</h1>
            {error && <div className="text-error text-sm">{error}</div>}
            {gameIds.length === 0 ? (
                <Card>
                    <CardContent className="text-sm text-muted">Nobody queued right now.</CardContent>
                </Card>
            ) : (
                <div className="grid lg:grid-cols-2 gap-4">
                    {gameIds.map((gid) => (
                        <QueueCard
                            key={gid}
                            gameId={gid}
                            entries={byGame[gid] ?? []}
                            liveCount={queueCounts[gid] ?? (byGame[gid]?.length ?? 0)}
                            onDequeue={(uuid, name) => dequeue(gid, uuid, name)}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}

function QueueCard({
    gameId,
    entries,
    liveCount,
    onDequeue,
}: {
    gameId: string;
    entries: QueueEntry[];
    liveCount: number;
    onDequeue: (uuid: string, name: string) => void;
}) {
    const canRemove = useCan(P.queueRemove);
    return (
        <Card>
            <CardHeader>
                <div className="flex items-center justify-between">
                    <CardTitle className="font-mono">{gameId}</CardTitle>
                    <Badge variant={liveCount > 0 ? 'default' : 'muted'}>
                        {liveCount} queued
                    </Badge>
                </div>
            </CardHeader>
            <CardContent className="px-0 pt-0">
                {entries.length === 0 ? (
                    <div className="px-5 py-3 text-sm text-muted">Queue empty.</div>
                ) : (
                    <Table>
                        <Thead>
                            <Tr>
                                <Th className="w-8">#</Th>
                                <Th>Player</Th>
                                <Th>Maps</Th>
                                <Th>Queued</Th>
                                <Th></Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {entries.map((e) => (
                                <Tr key={e.playerUuid}>
                                    <Td className="font-mono text-muted tabular-nums">{e.position + 1}</Td>
                                    <Td className="font-medium">{e.playerName}</Td>
                                    <Td className="text-xs text-muted">
                                        {e.mapIds.length === 0 ? 'any' : e.mapIds.join(', ')}
                                    </Td>
                                    <Td className="font-mono text-xs text-muted">{formatAgo(e.queuedAt)}</Td>
                                    <Td className="text-right">
                                        {canRemove && (
                                            <Button
                                                variant="ghost"
                                                title="Remove from queue"
                                                onClick={() => onDequeue(e.playerUuid, e.playerName)}
                                            >
                                                <UserMinus className="h-4 w-4" />
                                            </Button>
                                        )}
                                    </Td>
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                )}
            </CardContent>
        </Card>
    );
}

function formatAgo(iso: string) {
    const diff = Date.now() - Date.parse(iso);
    if (Number.isNaN(diff)) return iso;
    const s = Math.max(0, Math.floor(diff / 1000));
    if (s < 60) return `${s}s ago`;
    if (s < 3600) return `${Math.floor(s / 60)}m ago`;
    return `${Math.floor(s / 3600)}h ago`;
}
