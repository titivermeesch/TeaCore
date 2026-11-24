import { useEffect, useState } from 'react';
import { LogOut } from 'lucide-react';
import { api, apiDelete } from '../api';
import type { SessionInfo } from '../types';
import { Card, CardContent } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Table, Tbody, Td, Th, Thead, Tr } from '../components/ui/Table';
import { dateTime } from '../lib/mc';

/** Who is signed in to the dashboard (tea.dashboard.sessions). */
export default function Sessions() {
    const [sessions, setSessions] = useState<SessionInfo[]>([]);
    const [error, setError] = useState<string | null>(null);

    function load() {
        api<SessionInfo[]>('/api/auth/sessions').then(setSessions).catch((e) => setError((e as Error).message));
    }

    useEffect(load, []);

    async function signOut(s: SessionInfo) {
        if (!window.confirm(`Sign out ${s.name}'s session?`)) return;
        try {
            await apiDelete(`/api/auth/sessions/${s.id}`);
            load();
        } catch (e) {
            setError((e as Error).message);
        }
    }

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-semibold">Sessions</h1>
            <p className="text-sm text-muted">Sessions last 12 hours. A server restart signs everyone out.</p>
            {error && <div className="text-error text-sm">{error}</div>}
            <Card>
                <CardContent className="px-0 pt-0">
                    <Table>
                        <Thead><Tr><Th>Account</Th><Th>Signed in</Th><Th>Expires</Th><Th>Address</Th><Th>Permissions</Th><Th /></Tr></Thead>
                        <Tbody>
                            {sessions.map((s) => (
                                <Tr key={s.id}>
                                    <Td className="font-medium">{s.name} {s.current && <Badge variant="muted" className="ml-1">you</Badge>}</Td>
                                    <Td>{dateTime(s.created)}</Td>
                                    <Td>{dateTime(s.expires)}</Td>
                                    <Td className="font-mono text-xs">{s.address}</Td>
                                    <Td className="text-xs text-muted" title={s.permissions.join('\n')}>{s.permissions.length} permission(s)</Td>
                                    <Td className="text-right">
                                        {!s.current && (
                                            <Button variant="danger" onClick={() => signOut(s)}><LogOut className="h-4 w-4" /> Sign out</Button>
                                        )}
                                    </Td>
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                </CardContent>
            </Card>
        </div>
    );
}
