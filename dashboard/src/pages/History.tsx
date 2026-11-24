import { FormEvent, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Download, Search } from 'lucide-react';
import { api } from '../api';
import { P, useCan } from '../auth';
import type { GameSummary, MatchSummary } from '../types';
import { Card, CardContent } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';
import { dateTime, duration } from '../lib/mc';

const PAGE = 50;
const FILTERS = ['game', 'map', 'player', 'from', 'to'] as const;

/** Finished matches with filters; the filters live in the URL so links can be shared. */
export default function HistoryPage() {
    const [params, setParams] = useSearchParams();
    const [form, setForm] = useState<Record<string, string>>(() =>
        Object.fromEntries(FILTERS.map((f) => [f, params.get(f) ?? ''])));
    const [games, setGames] = useState<GameSummary[]>([]);
    const [data, setData] = useState<{ total: number; matches: MatchSummary[] } | null>(null);
    const [error, setError] = useState<string | null>(null);
    const canExport = useCan(P.historyExport);
    const page = Number(params.get('page') ?? '0');

    useEffect(() => {
        api<GameSummary[]>('/api/games').then(setGames).catch(() => {});
    }, []);

    useEffect(() => {
        const q = new URLSearchParams();
        for (const f of FILTERS) if (params.get(f)) q.set(f, params.get(f)!);
        q.set('limit', String(PAGE));
        q.set('offset', String(page * PAGE));
        setError(null);
        api<{ total: number; matches: MatchSummary[] }>(`/api/history?${q}`).then(setData)
            .catch((e) => setError((e as Error).message));
    }, [params, page]);

    function apply(e: FormEvent) {
        e.preventDefault();
        const next = new URLSearchParams();
        for (const f of FILTERS) if (form[f]?.trim()) next.set(f, form[f].trim());
        setParams(next);
    }

    function csvHref() {
        const q = new URLSearchParams();
        for (const f of FILTERS) if (params.get(f)) q.set(f, params.get(f)!);
        return `/api/history.csv?${q}`;
    }

    const pages = data ? Math.max(1, Math.ceil(data.total / PAGE)) : 1;

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between gap-4 flex-wrap">
                <h1 className="text-2xl font-semibold">Match history</h1>
                {canExport && (
                    <a href={csvHref()} download>
                        <Button><Download className="h-4 w-4" /> Export CSV</Button>
                    </a>
                )}
            </div>
            <form onSubmit={apply} className="flex flex-wrap gap-2 items-end">
                <label className="text-xs text-muted flex flex-col gap-1">Game
                    <select value={form.game} onChange={(e) => setForm({ ...form, game: e.target.value })}
                        className="rounded-md bg-bg-raised border border-border px-3 py-2 text-sm text-slate-100">
                        <option value="">Any</option>
                        {games.map((g) => <option key={g.id} value={g.id}>{g.id}</option>)}
                    </select>
                </label>
                <label className="text-xs text-muted flex flex-col gap-1">Map
                    <Input value={form.map} onChange={(e) => setForm({ ...form, map: e.target.value })} placeholder="map id" className="w-32" />
                </label>
                <label className="text-xs text-muted flex flex-col gap-1">Player
                    <Input value={form.player} onChange={(e) => setForm({ ...form, player: e.target.value })} placeholder="name or uuid" className="w-48" />
                </label>
                <label className="text-xs text-muted flex flex-col gap-1">From
                    <Input type="date" value={form.from} onChange={(e) => setForm({ ...form, from: e.target.value })} />
                </label>
                <label className="text-xs text-muted flex flex-col gap-1">To
                    <Input type="date" value={form.to} onChange={(e) => setForm({ ...form, to: e.target.value })} />
                </label>
                <Button type="submit"><Search className="h-4 w-4" /> Filter</Button>
            </form>
            {error && <div className="text-error text-sm">{error}</div>}
            <Card>
                <CardContent className="px-0 pt-0">
                    {!data || data.matches.length === 0 ? (
                        <div className="px-5 py-6 text-sm text-muted text-center">{data ? 'No matches found.' : 'Loading...'}</div>
                    ) : (
                        <Table>
                            <Thead>
                                <Tr><Th>Ended</Th><Th>Game</Th><Th>Map</Th><Th>Winner</Th><Th>Players</Th><Th>Duration</Th><Th>Server</Th></Tr>
                            </Thead>
                            <Tbody>
                                {data.matches.map((m) => (
                                    <Tr key={m.matchId}>
                                        <Td><Link to={`/history/${m.matchId}`} className="text-primary hover:underline">{dateTime(m.endedAt)}</Link></Td>
                                        <Td>{m.game}</Td>
                                        <Td>{m.map ?? '-'}</Td>
                                        <Td className="capitalize">{m.winner}</Td>
                                        <Td className="tabular-nums">{m.players}</Td>
                                        <Td className="tabular-nums">{duration(m.durationSeconds)}</Td>
                                        <Td className="font-mono text-xs text-muted">{m.server ?? '-'}</Td>
                                    </Tr>
                                ))}
                            </Tbody>
                        </Table>
                    )}
                </CardContent>
            </Card>
            {data && data.total > PAGE && (
                <div className="flex items-center gap-2 text-sm">
                    <Button variant="ghost" disabled={page === 0} onClick={() => { params.set('page', String(page - 1)); setParams(params); }}>Previous</Button>
                    <span className="text-muted">Page {page + 1} of {pages} ({data.total} matches)</span>
                    <Button variant="ghost" disabled={page + 1 >= pages} onClick={() => { params.set('page', String(page + 1)); setParams(params); }}>Next</Button>
                </div>
            )}
        </div>
    );
}
