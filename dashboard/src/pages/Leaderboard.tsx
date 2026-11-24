import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import type { GameSummary } from '../types';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';

type Row = { rank: number; uuid: string; name: string; rating: number; games: number; wins: number; losses: number };

export default function Leaderboard() {
    const [games, setGames] = useState<string[]>([]);
    const [game, setGame] = useState<string>('');
    const [rows, setRows] = useState<Row[] | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        api<GameSummary[]>('/api/games')
            .then((list) => {
                const ids = list.map((g) => g.id).sort();
                setGames(ids);
                setGame((g) => g || ids[0] || '');
            })
            .catch((err) => setError((err as Error).message));
    }, []);

    useEffect(() => {
        if (!game) return;
        setRows(null);
        api<Row[]>(`/api/leaderboard/${game}?limit=100`)
            .then(setRows)
            .catch((err) => setError((err as Error).message));
    }, [game]);

    return (
        <div className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <h1 className="text-2xl font-semibold">Leaderboard</h1>
                <select
                    className="bg-bg-card border border-border rounded px-2 py-1 text-sm"
                    value={game}
                    onChange={(e) => setGame(e.target.value)}
                    aria-label="Game"
                >
                    {games.map((g) => (
                        <option key={g} value={g}>
                            {g}
                        </option>
                    ))}
                </select>
            </div>
            {error && <div className="text-error text-sm">{error}</div>}
            <Card>
                <CardHeader>
                    <CardTitle>Ranked {game}</CardTitle>
                </CardHeader>
                <CardContent className="overflow-x-auto">
                    {!rows ? (
                        <div className="text-muted text-sm">Loading...</div>
                    ) : rows.length === 0 ? (
                        <div className="text-muted text-sm">Nobody has played ranked {game} yet.</div>
                    ) : (
                        <Table>
                            <Thead>
                                <Tr>
                                    <Th>#</Th>
                                    <Th>Player</Th>
                                    <Th>Rating</Th>
                                    <Th>Wins</Th>
                                    <Th>Losses</Th>
                                    <Th>Games</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {rows.map((r) => (
                                    <Tr key={r.uuid}>
                                        <Td className="tabular-nums">{r.rank}</Td>
                                        <Td>
                                            <Link className="hover:underline" to={`/players/${r.uuid}`}>
                                                {r.name}
                                            </Link>
                                        </Td>
                                        <Td className="tabular-nums font-medium">{r.rating}</Td>
                                        <Td className="tabular-nums">{r.wins}</Td>
                                        <Td className="tabular-nums">{r.losses}</Td>
                                        <Td className="tabular-nums">{r.games}</Td>
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
