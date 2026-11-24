import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { api } from '../api';
import type { MatchDetail } from '../types';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';
import { dateTime, duration } from '../lib/mc';

export default function MatchPage() {
    const { matchId = '' } = useParams();
    const [m, setM] = useState<MatchDetail | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        api<MatchDetail>(`/api/history/${matchId}`).then(setM).catch((e) => setError((e as Error).message));
    }, [matchId]);

    return (
        <div className="space-y-6">
            <Link to="/history" className="inline-flex items-center gap-1 text-sm text-muted hover:text-slate-200">
                <ArrowLeft className="h-4 w-4" /> Match history
            </Link>
            {error && <div className="text-error text-sm">{error}</div>}
            {m && (
                <>
                    <div>
                        <h1 className="text-2xl font-semibold">{m.game} on {m.map ?? '-'}</h1>
                        <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted">
                            <span>{dateTime(m.endedAt)}</span>
                            <span>Duration {duration(m.durationSeconds)}</span>
                            <span>Winner: <span className="text-slate-200 capitalize">{m.winner}</span></span>
                            {m.server && <span className="font-mono">{m.server}</span>}
                        </div>
                    </div>
                    <Card>
                        <CardHeader><CardTitle>Players</CardTitle></CardHeader>
                        <CardContent className="px-0 pt-0">
                            <Table>
                                <Thead><Tr><Th>Player</Th><Th>Team</Th><Th>Result</Th><Th>K</Th><Th>D</Th><Th>A</Th><Th>Damage</Th></Tr></Thead>
                                <Tbody>
                                    {m.players.map((p, i) => (
                                        <Tr key={p.uuid ?? i}>
                                            <Td>
                                                {p.uuid ? <Link to={`/players/${p.uuid}`} className="hover:underline">{p.name ?? p.uuid.slice(0, 8)}</Link> : p.name}
                                                {p.uuid && p.uuid === m.mvpUuid && <Badge className="ml-2">MVP</Badge>}
                                            </Td>
                                            <Td className="capitalize">{p.team ?? '-'}</Td>
                                            <Td><Badge variant={p.result === 'win' ? 'success' : p.result === 'loss' ? 'error' : 'muted'}>{p.result}</Badge></Td>
                                            <Td className="tabular-nums">{p.kills}</Td>
                                            <Td className="tabular-nums">{p.deaths}</Td>
                                            <Td className="tabular-nums">{p.assists}</Td>
                                            <Td className="tabular-nums">{p.damage == null ? '-' : Math.round(p.damage / 2)} <span className="text-muted text-xs">hearts</span></Td>
                                        </Tr>
                                    ))}
                                </Tbody>
                            </Table>
                        </CardContent>
                    </Card>
                </>
            )}
        </div>
    );
}
