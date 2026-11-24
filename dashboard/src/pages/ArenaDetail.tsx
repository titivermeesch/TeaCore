import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Eye, MoveRight, Play, Square, Trophy, UserX } from 'lucide-react';
import { api, apiPost } from '../api';
import { P, useCan } from '../auth';
import type { Arena, ArenaDetail, ChatEvent, KillEvent } from '../types';
import { useDashboardEvent } from '../ws';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';
import { KillFeed } from '../components/KillFeed';
import { stateVariant } from '../lib/arena';
import { McText, duration } from '../lib/mc';

/** Live view of one arena: teams, K/D/A, sidebar, kills, chat and actions. */
export default function ArenaDetailPage() {
    const { id = '' } = useParams();
    const navigate = useNavigate();
    const [detail, setDetail] = useState<ArenaDetail | null>(null);
    const [gone, setGone] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [picking, setPicking] = useState(false);
    const [moving, setMoving] = useState<string | null>(null);
    const [others, setOthers] = useState<Arena[]>([]);

    const canStart = useCan(P.arenaStart);
    const canEnd = useCan(P.arenaEnd);
    const canRelease = useCan(P.arenaRelease);
    const canKick = useCan(P.playerKick);
    const canSpectator = useCan(P.playerSpectator);
    const canMove = useCan(P.playerMove);

    const load = useCallback(async () => {
        try {
            const d = await api<ArenaDetail>(`/api/arenas/${id}`);
            // Keep live kills/chat that arrived since the last fetch.
            setDetail(d);
            setGone(false);
        } catch (err) {
            if ((err as { status?: number }).status === 404) setGone(true);
            else setError((err as Error).message);
        }
    }, [id]);

    useEffect(() => {
        load();
        const t = window.setInterval(load, 2000);
        return () => window.clearInterval(t);
    }, [load]);

    useDashboardEvent((e) => {
        if (e.type === 'arena.kill' && e.arenaId === id) {
            setDetail((d) => (d ? { ...d, kills: [...d.kills, e as KillEvent].slice(-50) } : d));
        } else if (e.type === 'arena.chat' && e.arenaId === id) {
            setDetail((d) => (d && d.chat ? { ...d, chat: [...d.chat, e as ChatEvent].slice(-100) } : d));
        } else if (e.type === 'arena.state' && e.arenaId === id) {
            load();
        }
    }, [id, load]);

    async function act(label: string, fn: () => Promise<unknown>, confirmMsg?: string) {
        if (confirmMsg && !window.confirm(confirmMsg)) return;
        try {
            await fn();
            setError(null);
            setNotice(`${label}: sent`);
            window.setTimeout(load, 600);
        } catch (err) {
            setError(`${label}: ${(err as Error).message}`);
        }
    }

    async function openMove(uuid: string) {
        setMoving(uuid);
        try {
            const all = await api<Arena[]>('/api/arenas');
            setOthers(all.filter((a) => a.id !== id && a.state !== 'RELEASED' && a.players.length < a.maxPlayers));
        } catch {
            setOthers([]);
        }
    }

    if (gone) {
        return (
            <div className="space-y-4">
                <BackLink />
                <div className="text-muted">This arena no longer exists.</div>
            </div>
        );
    }
    if (!detail) return <BackLink />;

    const a = detail.arena;
    const live = a.state === 'LIVE';
    const participants = detail.players.filter((p) => !p.spectator);
    const spectators = detail.players.filter((p) => p.spectator);
    const hasTeams = detail.teams.length > 0;

    return (
        <div className="space-y-6">
            <BackLink />
            <div className="flex items-start justify-between gap-4 flex-wrap">
                <div>
                    <h1 className="text-2xl font-semibold">
                        {a.gameId} <span className="text-muted font-mono text-base">#{a.id.slice(0, 8)}</span>
                    </h1>
                    <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted">
                        <Badge variant={stateVariant(a.state)}>{a.state}</Badge>
                        <span>Map: <span className="text-slate-200">{detail.mapName ?? '-'}</span></span>
                        <span>Time left: <span className="text-slate-200 tabular-nums">{duration(detail.secondsRemaining)}</span></span>
                        <span>Server: <span className="font-mono">{a.serverId}</span></span>
                        <span>{participants.length}/{a.maxPlayers} players, {spectators.length} watching</span>
                    </div>
                </div>
                <div className="flex flex-wrap gap-2">
                    {canStart && (a.state === 'WAITING' || a.state === 'COUNTDOWN') && (
                        <Button onClick={() => act('Skip phase', () => apiPost(`/api/arenas/${id}/start`))}>
                            <Play className="h-4 w-4" /> Skip phase
                        </Button>
                    )}
                    {canEnd && live && (
                        <Button onClick={() => setPicking((v) => !v)}>
                            <Trophy className="h-4 w-4" /> End match
                        </Button>
                    )}
                    {canRelease && (
                        <Button
                            variant="danger"
                            onClick={() =>
                                act('Release', async () => {
                                    await apiPost(`/api/arenas/${id}/release`);
                                    navigate('/arenas');
                                }, 'Release this arena? Everyone in it is sent out.')
                            }
                        >
                            <Square className="h-4 w-4" /> Release
                        </Button>
                    )}
                </div>
            </div>

            {picking && live && (
                <Card>
                    <CardHeader><CardTitle>Who wins?</CardTitle></CardHeader>
                    <CardContent className="flex flex-wrap gap-2">
                        {hasTeams
                            ? detail.teams.map((t) => (
                                  <Button key={t.team} onClick={() => {
                                      setPicking(false);
                                      act('End match', () => apiPost(`/api/arenas/${id}/end-match`, { winnerTeam: t.team }),
                                          `End the match with team ${t.team} as the winner?`);
                                  }}>
                                      Team {t.team} <span className="text-muted">({t.kills} kills)</span>
                                  </Button>
                              ))
                            : participants.map((p) => (
                                  <Button key={p.uuid} onClick={() => {
                                      setPicking(false);
                                      act('End match', () => apiPost(`/api/arenas/${id}/end-match`, { winnerUuid: p.uuid }),
                                          `End the match with ${p.name} as the winner?`);
                                  }}>
                                      {p.name}
                                  </Button>
                              ))}
                        <Button variant="ghost" onClick={() => {
                            setPicking(false);
                            act('End match', () => apiPost(`/api/arenas/${id}/end-match`, {}), 'End the match as a draw?');
                        }}>
                            Draw
                        </Button>
                    </CardContent>
                </Card>
            )}

            {error && <div className="text-error text-sm">{error}</div>}
            {notice && !error && <div className="text-success text-sm">{notice}</div>}

            <div className="grid gap-6 xl:grid-cols-3">
                <div className="xl:col-span-2 space-y-6">
                    {hasTeams && (
                        <Card>
                            <CardHeader><CardTitle>Teams</CardTitle></CardHeader>
                            <CardContent className="px-0 pt-0">
                                <Table>
                                    <Thead><Tr><Th>Team</Th><Th>Players</Th><Th>Kills</Th><Th>Deaths</Th><Th>Assists</Th></Tr></Thead>
                                    <Tbody>
                                        {detail.teams.map((t) => (
                                            <Tr key={t.team}>
                                                <Td className="font-medium capitalize">{t.team}</Td>
                                                <Td className="tabular-nums">{t.players}</Td>
                                                <Td className="tabular-nums">{t.kills}</Td>
                                                <Td className="tabular-nums">{t.deaths}</Td>
                                                <Td className="tabular-nums">{t.assists}</Td>
                                            </Tr>
                                        ))}
                                    </Tbody>
                                </Table>
                            </CardContent>
                        </Card>
                    )}
                    <Card>
                        <CardHeader><CardTitle>Players</CardTitle></CardHeader>
                        <CardContent className="px-0 pt-0">
                            {detail.players.length === 0 ? (
                                <div className="px-5 py-4 text-sm text-muted">Nobody in this arena.</div>
                            ) : (
                                <Table>
                                    <Thead>
                                        <Tr>
                                            <Th>Player</Th><Th>Team</Th><Th>K</Th><Th>D</Th><Th>A</Th>
                                            <Th className="text-right">Actions</Th>
                                        </Tr>
                                    </Thead>
                                    <Tbody>
                                        {[...participants, ...spectators].map((p) => (
                                            <Tr key={p.uuid}>
                                                <Td>
                                                    <Link to={`/players/${p.uuid}`} className="hover:underline">{p.name}</Link>
                                                    {p.spectator && <Badge variant="muted" className="ml-2">spectating</Badge>}
                                                </Td>
                                                <Td className="capitalize">{p.team ?? '-'}</Td>
                                                <Td className="tabular-nums">{p.spectator ? '-' : p.kills}</Td>
                                                <Td className="tabular-nums">{p.spectator ? '-' : p.deaths}</Td>
                                                <Td className="tabular-nums">{p.spectator ? '-' : p.assists}</Td>
                                                <Td>
                                                    <div className="flex justify-end gap-1">
                                                        {canSpectator && !p.spectator && (
                                                            <Button variant="ghost" title="Make spectator" onClick={() =>
                                                                act(`Make ${p.name} a spectator`, () =>
                                                                    apiPost(`/api/arenas/${id}/players/${p.uuid}/spectator`),
                                                                    `Make ${p.name} a spectator?`)}>
                                                                <Eye className="h-4 w-4" />
                                                            </Button>
                                                        )}
                                                        {canMove && (
                                                            <Button variant="ghost" title="Move to another arena" onClick={() => openMove(p.uuid)}>
                                                                <MoveRight className="h-4 w-4" />
                                                            </Button>
                                                        )}
                                                        {canKick && (
                                                            <Button variant="danger" title="Kick to the lobby" onClick={() =>
                                                                act(`Kick ${p.name}`, () =>
                                                                    apiPost(`/api/arenas/${id}/players/${p.uuid}/kick`),
                                                                    `Kick ${p.name} out of the arena?`)}>
                                                                <UserX className="h-4 w-4" />
                                                            </Button>
                                                        )}
                                                    </div>
                                                    {moving === p.uuid && (
                                                        <div className="mt-2 flex flex-wrap justify-end gap-1">
                                                            {others.length === 0 && <span className="text-xs text-muted">No other arena has room.</span>}
                                                            {others.map((o) => (
                                                                <Button key={o.id} variant="ghost" onClick={() => {
                                                                    setMoving(null);
                                                                    act(`Move ${p.name}`, () => apiPost(
                                                                        `/api/arenas/${id}/players/${p.uuid}/move`, { targetArenaId: o.id }));
                                                                }}>
                                                                    {o.gameId} #{o.id.slice(0, 8)} <span className="text-muted">({o.state.toLowerCase()})</span>
                                                                </Button>
                                                            ))}
                                                            <Button variant="ghost" onClick={() => setMoving(null)}>Cancel</Button>
                                                        </div>
                                                    )}
                                                </Td>
                                            </Tr>
                                        ))}
                                    </Tbody>
                                </Table>
                            )}
                        </CardContent>
                    </Card>
                    <Card>
                        <CardHeader><CardTitle>Kills</CardTitle></CardHeader>
                        <CardContent><KillFeed kills={detail.kills} /></CardContent>
                    </Card>
                </div>
                <div className="space-y-6">
                    <Card>
                        <CardHeader>
                            <CardTitle>Sidebar</CardTitle>
                        </CardHeader>
                        <CardContent>
                            {detail.sidebar ? (
                                <div className="rounded-md bg-black/60 p-3 font-mono text-sm leading-6">
                                    <div className="text-center mb-1"><McText text={detail.sidebar.title} /></div>
                                    {detail.sidebar.lines.map((l, i) => (
                                        <div key={i} className="whitespace-pre"><McText text={l} /></div>
                                    ))}
                                    <div className="mt-2 text-xs text-muted font-sans">As seen by {detail.sidebar.viewer}</div>
                                </div>
                            ) : (
                                <div className="text-sm text-muted">No sidebar right now.</div>
                            )}
                        </CardContent>
                    </Card>
                    {detail.chat && (
                        <Card>
                            <CardHeader><CardTitle>Chat</CardTitle></CardHeader>
                            <CardContent>
                                {detail.chat.length === 0 ? (
                                    <div className="text-sm text-muted">No messages yet.</div>
                                ) : (
                                    <ul className="space-y-1 text-sm max-h-96 overflow-auto">
                                        {detail.chat.map((c, i) => (
                                            <li key={`${c.at}-${i}`}>
                                                <span className="text-muted text-xs tabular-nums">
                                                    {new Date(c.at).toLocaleTimeString()}{' '}
                                                </span>
                                                <span className="font-medium">{c.playerName}</span>
                                                {c.spectator && <span className="text-xs text-muted"> (spectator)</span>}
                                                <span className="text-muted">: </span>
                                                <span className="break-words">{c.message}</span>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </CardContent>
                        </Card>
                    )}
                </div>
            </div>
        </div>
    );
}

function BackLink() {
    return (
        <Link to="/arenas" className="inline-flex items-center gap-1 text-sm text-muted hover:text-slate-200">
            <ArrowLeft className="h-4 w-4" /> Arenas
        </Link>
    );
}
