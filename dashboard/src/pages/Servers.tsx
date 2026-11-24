import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { cn } from '../lib/cn';

interface ServerArena {
    id: string;
    game: string;
    map: string | null;
    state: string;
    players: number;
    maxPlayers: number;
}

interface ServerInfo {
    id: string;
    online: boolean;
    lastSeen: number;
    players: number;
    tps: number | null;
    mspt: number | null;
    memoryUsed: number | null;
    memoryMax: number | null;
    group: string;
    lobby: boolean;
    poolMode: string;
    teacore: string;
    minecraft: string;
    software: string;
    installed: string[];
    hosted: string[];
    mapModes: Record<string, string>;
    arenas: ServerArena[];
}

const POOL_MODE: Record<string, string> = {
    standalone: 'Single server',
    central: 'Network',
    external: 'One match at a time',
};

function ago(ms: number) {
    const s = Math.max(0, Math.round((Date.now() - ms) / 1000));
    if (s < 60) return `${s}s ago`;
    if (s < 3600) return `${Math.round(s / 60)}m ago`;
    return `${Math.round(s / 3600)}h ago`;
}

function gb(bytes: number | null) {
    return bytes == null ? '?' : (bytes / 1024 ** 3).toFixed(1);
}

export default function Servers() {
    const [servers, setServers] = useState<ServerInfo[] | null>(null);
    const [error, setError] = useState<string | null>(null);

    const refresh = useCallback(async () => {
        try {
            setServers(await api<ServerInfo[]>('/api/servers'));
            setError(null);
        } catch (err) {
            setError((err as Error).message);
        }
    }, []);

    useEffect(() => {
        refresh();
        const t = window.setInterval(refresh, 5000);
        return () => window.clearInterval(t);
    }, [refresh]);

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-semibold">Servers</h1>
            {error && <div className="text-error text-sm">{error}</div>}
            {servers !== null && servers.length === 0 && (
                <Card>
                    <CardContent className="text-sm text-muted">
                        No server has reported in yet. Servers report every 15 seconds.
                    </CardContent>
                </Card>
            )}
            <div className="grid xl:grid-cols-2 gap-4">
                {(servers ?? []).map((s) => (
                    <ServerCard key={s.id} server={s} />
                ))}
            </div>
        </div>
    );
}

function ServerCard({ server: s }: { server: ServerInfo }) {
    const tpsColor =
        s.tps == null ? 'text-muted' : s.tps >= 19 ? 'text-success' : s.tps >= 15 ? 'text-accent' : 'text-error';
    const notHosted = s.installed.filter((g) => !s.hosted.includes(g));
    return (
        <Card className={cn(!s.online && 'opacity-60')}>
            <CardHeader>
                <div className="flex flex-wrap items-center gap-2">
                    <CardTitle className="mr-auto">{s.id}</CardTitle>
                    {s.lobby && <Badge>lobby</Badge>}
                    <Badge variant="muted">group: {s.group || '-'}</Badge>
                    {s.online ? (
                        <Badge variant="success">online</Badge>
                    ) : (
                        <Badge variant="error">offline, seen {ago(s.lastSeen)}</Badge>
                    )}
                </div>
                <div className="text-xs text-muted">
                    {s.software} {s.minecraft} · TeaCore {s.teacore} · {POOL_MODE[s.poolMode] ?? s.poolMode}
                </div>
            </CardHeader>
            <CardContent className="space-y-4">
                <div className="grid grid-cols-3 gap-3 text-sm">
                    <Stat label="Players" value={String(s.players)} />
                    <Stat
                        label="TPS"
                        value={s.tps == null ? '?' : s.tps.toFixed(1)}
                        className={tpsColor}
                        hint={s.mspt == null ? undefined : `${s.mspt.toFixed(1)} ms per tick`}
                    />
                    <Stat label="Memory" value={`${gb(s.memoryUsed)} / ${gb(s.memoryMax)} GB`} />
                </div>
                <div className="space-y-1">
                    <div className="text-xs text-muted">Hosts</div>
                    <div className="flex flex-wrap gap-1.5">
                        {s.hosted.length === 0 && <span className="text-sm text-muted">No games</span>}
                        {s.hosted.map((g) => (
                            <Badge key={g} title={`Map: ${s.mapModes[g] ?? 'default'}`}>
                                {g}
                                {s.mapModes[g] && s.mapModes[g] !== 'default' && (
                                    <span className="ml-1 opacity-70">· {s.mapModes[g]}</span>
                                )}
                            </Badge>
                        ))}
                        {notHosted.map((g) => (
                            <Badge key={g} variant="muted" title="Installed, but not in hosting.games">
                                {g} (not hosted)
                            </Badge>
                        ))}
                    </div>
                </div>
                <div className="space-y-1">
                    <div className="text-xs text-muted">Arenas</div>
                    {s.arenas.length === 0 ? (
                        <div className="text-sm text-muted">None open</div>
                    ) : (
                        <div className="space-y-1">
                            {s.arenas.map((a) => (
                                <Link
                                    key={a.id}
                                    to={`/arenas/${a.id}`}
                                    className="flex items-center gap-2 rounded-md border border-border px-3 py-1.5 text-sm hover:bg-bg-raised"
                                >
                                    <span className="font-medium">{a.game}</span>
                                    <span className="text-muted">{a.map ?? '?'}</span>
                                    <Badge variant="muted" className="ml-auto">
                                        {a.state.toLowerCase()}
                                    </Badge>
                                    <span className="tabular-nums text-muted">
                                        {a.players}/{a.maxPlayers}
                                    </span>
                                </Link>
                            ))}
                        </div>
                    )}
                </div>
            </CardContent>
        </Card>
    );
}

function Stat({ label, value, className, hint }: { label: string; value: string; className?: string; hint?: string }) {
    return (
        <div className="rounded-md border border-border px-3 py-2" title={hint}>
            <div className="text-xs text-muted">{label}</div>
            <div className={cn('font-semibold tabular-nums', className)}>{value}</div>
        </div>
    );
}
