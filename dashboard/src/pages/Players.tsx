import { FormEvent, useEffect, useState } from 'react';
import { Search } from 'lucide-react';
import { api, ApiError } from '../api';
import type { PlayerProfile, PlayerRef } from '../types';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';

export default function Players() {
    const [online, setOnline] = useState<PlayerRef[]>([]);
    const [query, setQuery] = useState('');
    const [profile, setProfile] = useState<PlayerProfile | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        api<PlayerRef[]>('/api/players/online').then(setOnline).catch(() => {});
    }, []);

    async function load(uuid: string) {
        setBusy(true);
        setError(null);
        try {
            const result = await api<PlayerProfile>(`/api/players/${uuid}`);
            setProfile(result);
        } catch (err) {
            setProfile(null);
            if (err instanceof ApiError && err.status === 404) {
                setError('No profile or stats for that uuid yet.');
            } else {
                setError((err as Error).message);
            }
        } finally {
            setBusy(false);
        }
    }

    function onSubmit(e: FormEvent) {
        e.preventDefault();
        if (query.trim()) load(query.trim());
    }

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-semibold">Players</h1>
            <form onSubmit={onSubmit} className="flex gap-2">
                <Input
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="Player UUID"
                    className="max-w-md"
                />
                <Button type="submit" disabled={busy}>
                    <Search className="h-4 w-4" />
                    {busy ? 'Looking up...' : 'Look up'}
                </Button>
            </form>
            {error && <div className="text-error text-sm">{error}</div>}
            <div className="grid lg:grid-cols-3 gap-4">
                <Card className="lg:col-span-1">
                    <CardHeader>
                        <CardTitle>Online ({online.length})</CardTitle>
                        <CardDescription>Click to load a player's profile and stats.</CardDescription>
                    </CardHeader>
                    <CardContent className="px-0 pt-0">
                        {online.length === 0 ? (
                            <div className="px-5 py-3 text-sm text-muted">Nobody is online.</div>
                        ) : (
                            <ul className="divide-y divide-border">
                                {online.map((p) => (
                                    <li
                                        key={p.uuid}
                                        className="px-5 py-2 text-sm cursor-pointer hover:bg-bg-raised/50"
                                        onClick={() => {
                                            setQuery(p.uuid);
                                            load(p.uuid);
                                        }}
                                    >
                                        <div className="font-medium">{p.name ?? p.uuid.slice(0, 8)}</div>
                                        <div className="font-mono text-xs text-muted">{p.uuid}</div>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </CardContent>
                </Card>
                <div className="lg:col-span-2">
                    {profile ? <ProfileCard profile={profile} /> : (
                        <Card>
                            <CardContent className="text-sm text-muted">
                                Paste a UUID and press Look up, or pick a player from the online list.
                            </CardContent>
                        </Card>
                    )}
                </div>
            </div>
        </div>
    );
}

function ProfileCard({ profile }: { profile: PlayerProfile }) {
    return (
        <div className="space-y-4">
            <Card>
                <CardHeader>
                    <div className="flex items-start justify-between">
                        <div>
                            <CardTitle>{profile.name ?? 'Unknown'}</CardTitle>
                            <CardDescription className="font-mono">{profile.uuid}</CardDescription>
                        </div>
                        <Badge variant={profile.online ? 'success' : 'muted'}>
                            {profile.online ? 'online' : 'offline'}
                        </Badge>
                    </div>
                </CardHeader>
                <CardContent>
                    <dl className="grid grid-cols-3 gap-4 text-sm">
                        <Meta label="First seen" value={profile.firstSeen} />
                        <Meta label="Last seen" value={profile.lastSeen} />
                        <Meta label="Locale" value={profile.locale} />
                    </dl>
                </CardContent>
            </Card>
            {Object.keys(profile.statsByGame).length === 0 ? (
                <Card>
                    <CardContent className="text-sm text-muted">No recorded stats for this player yet.</CardContent>
                </Card>
            ) : (
                Object.entries(profile.statsByGame).map(([gameId, entries]) => (
                    <Card key={gameId}>
                        <CardHeader>
                            <CardTitle className="font-mono">{gameId}</CardTitle>
                        </CardHeader>
                        <CardContent className="px-0 pt-0">
                            <Table>
                                <Thead>
                                    <Tr>
                                        <Th>Stat</Th>
                                        <Th>Value</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {Object.entries(entries).map(([label, value]) => (
                                        <Tr key={label}>
                                            <Td>{label}</Td>
                                            <Td className="font-mono tabular-nums">{value}</Td>
                                        </Tr>
                                    ))}
                                </Tbody>
                            </Table>
                        </CardContent>
                    </Card>
                ))
            )}
        </div>
    );
}

function Meta({ label, value }: { label: string; value: string | null }) {
    return (
        <div>
            <div className="text-xs text-muted uppercase tracking-wide">{label}</div>
            <div className="font-mono text-sm mt-0.5">{value ?? '-'}</div>
        </div>
    );
}
