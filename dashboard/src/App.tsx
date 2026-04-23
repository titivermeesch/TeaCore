import { useEffect, useState } from 'react';
import { Route, Routes, NavLink, Navigate } from 'react-router-dom';
import {
    Activity,
    Gamepad2,
    LayoutDashboard,
    LogOut,
    Swords,
    Users,
} from 'lucide-react';
import Login from './Login';
import Overview from './pages/Overview';
import Games from './pages/Games';
import Arenas from './pages/Arenas';
import Queues from './pages/Queues';
import Players from './pages/Players';
import { clearToken, getToken } from './api';
import { DashboardWsProvider, useDashboardWs } from './ws';
import { cn } from './lib/cn';

export default function App() {
    const [authed, setAuthed] = useState<boolean>(!!getToken());

    useEffect(() => {
        const handler = () => setAuthed(!!getToken());
        window.addEventListener('storage', handler);
        return () => window.removeEventListener('storage', handler);
    }, []);

    if (!authed) return <Login onAuthed={() => setAuthed(true)} />;

    return (
        <DashboardWsProvider>
            <Shell onSignOut={() => {
                clearToken();
                setAuthed(false);
            }} />
        </DashboardWsProvider>
    );
}

function Shell({ onSignOut }: { onSignOut: () => void }) {
    return (
        <div className="min-h-dvh flex">
            <aside className="w-60 shrink-0 border-r border-border bg-bg-raised p-4 space-y-4 flex flex-col">
                <div className="flex items-center gap-2 px-2 py-1">
                    <LayoutDashboard className="h-5 w-5 text-primary" />
                    <div className="text-primary font-semibold">TeaCore</div>
                </div>
                <nav className="flex flex-col text-sm gap-0.5">
                    <NavItem to="/" icon={<LayoutDashboard className="h-4 w-4" />}>Overview</NavItem>
                    <NavItem to="/games" icon={<Gamepad2 className="h-4 w-4" />}>Games</NavItem>
                    <NavItem to="/arenas" icon={<Swords className="h-4 w-4" />}>Arenas</NavItem>
                    <NavItem to="/queues" icon={<Activity className="h-4 w-4" />}>Queues</NavItem>
                    <NavItem to="/players" icon={<Users className="h-4 w-4" />}>Players</NavItem>
                </nav>
                <div className="mt-auto space-y-2">
                    <ConnectionStatus />
                    <button
                        onClick={onSignOut}
                        className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-xs text-muted hover:bg-bg-card hover:text-slate-200"
                    >
                        <LogOut className="h-4 w-4" /> Sign out
                    </button>
                </div>
            </aside>
            <main className="flex-1 p-6 overflow-auto">
                <Routes>
                    <Route path="/" element={<Overview />} />
                    <Route path="/games" element={<Games />} />
                    <Route path="/arenas" element={<Arenas />} />
                    <Route path="/queues" element={<Queues />} />
                    <Route path="/players" element={<Players />} />
                    <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
            </main>
        </div>
    );
}

function NavItem({
    to,
    icon,
    children,
}: {
    to: string;
    icon?: React.ReactNode;
    children: React.ReactNode;
}) {
    return (
        <NavLink
            to={to}
            end
            className={({ isActive }) =>
                cn(
                    'flex items-center gap-2 px-2 py-1.5 rounded-md transition-colors',
                    isActive
                        ? 'bg-primary/10 text-primary'
                        : 'text-slate-300 hover:bg-bg-card',
                )
            }
        >
            {icon}
            {children}
        </NavLink>
    );
}

function ConnectionStatus() {
    const { status } = useDashboardWs();
    const color =
        status === 'open' ? 'bg-success' : status === 'connecting' ? 'bg-accent' : 'bg-error';
    return (
        <div className="flex items-center gap-2 text-xs text-muted px-2">
            <span className={cn('h-2 w-2 rounded-full', color)} />
            {status === 'open' ? 'live' : status === 'connecting' ? 'connecting...' : 'disconnected'}
        </div>
    );
}
