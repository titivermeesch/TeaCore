import { useEffect, useState } from 'react';
import { Route, Routes, NavLink, Navigate, useLocation } from 'react-router-dom';
import {
    Activity,
    Gamepad2,
    History as HistoryIcon,
    KeyRound,
    LayoutDashboard,
    LogOut,
    Menu,
    Settings as SettingsIcon,
    Swords,
    Trophy,
    Users,
} from 'lucide-react';
import Login from './Login';
import Overview from './pages/Overview';
import Games from './pages/Games';
import Arenas from './pages/Arenas';
import ArenaDetailPage from './pages/ArenaDetail';
import Queues from './pages/Queues';
import Players from './pages/Players';
import HistoryPage from './pages/History';
import MatchPage from './pages/Match';
import Sessions from './pages/Sessions';
import Leaderboard from './pages/Leaderboard';
import Settings from './pages/Settings';
import { api, apiPost, UNAUTHORIZED_EVENT } from './api';
import { Me, MeProvider, P, useCan, useMe } from './auth';
import { DashboardWsProvider, useDashboardWs } from './ws';
import { cn } from './lib/cn';

export default function App() {
    // undefined = still checking the session cookie.
    const [me, setMe] = useState<Me | null | undefined>(undefined);

    useEffect(() => {
        api<Me>('/api/auth/me').then(setMe).catch(() => setMe(null));
        const onUnauthorized = () => setMe(null);
        window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
        return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    }, []);

    if (me === undefined) return null;
    if (me === null || location.pathname === '/login') {
        return <Login onAuthed={setMe} />;
    }

    return (
        <MeProvider me={me}>
            <DashboardWsProvider>
                <Shell
                    onSignOut={async () => {
                        await apiPost('/api/auth/logout').catch(() => {});
                        setMe(null);
                    }}
                />
            </DashboardWsProvider>
        </MeProvider>
    );
}

function Shell({ onSignOut }: { onSignOut: () => void }) {
    const me = useMe();
    const canSessions = useCan(P.sessions);
    const canSettings = useCan(P.settings);
    // Below md the sidebar is a drawer behind the top bar's menu button.
    const [navOpen, setNavOpen] = useState(false);
    const location = useLocation();
    useEffect(() => setNavOpen(false), [location.pathname]);
    return (
        <div className="min-h-dvh md:flex">
            <header className="md:hidden sticky top-0 z-30 flex items-center gap-3 border-b border-border bg-bg-raised px-4 py-3">
                <button
                    aria-label="Open navigation"
                    onClick={() => setNavOpen(true)}
                    className="rounded-md p-1.5 text-slate-300 hover:bg-bg-card"
                >
                    <Menu className="h-5 w-5" />
                </button>
                <LayoutDashboard className="h-5 w-5 text-primary" />
                <div className="text-primary font-semibold">TeaCore</div>
            </header>
            {navOpen && (
                <div className="md:hidden fixed inset-0 z-40 bg-black/60" onClick={() => setNavOpen(false)} />
            )}
            <aside
                className={cn(
                    'w-60 shrink-0 border-r border-border bg-bg-raised p-4 space-y-4 flex flex-col',
                    'fixed inset-y-0 left-0 z-50 transition-transform md:static md:translate-x-0',
                    navOpen ? 'translate-x-0' : '-translate-x-full',
                )}
            >
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
                    <NavItem to="/history" icon={<HistoryIcon className="h-4 w-4" />}>History</NavItem>
                    <NavItem to="/leaderboard" icon={<Trophy className="h-4 w-4" />}>Leaderboard</NavItem>
                    {canSettings && (
                        <NavItem to="/settings" icon={<SettingsIcon className="h-4 w-4" />}>Settings</NavItem>
                    )}
                    {canSessions && (
                        <NavItem to="/sessions" icon={<KeyRound className="h-4 w-4" />}>Sessions</NavItem>
                    )}
                </nav>
                <div className="mt-auto space-y-2">
                    <ConnectionStatus />
                    <div className="px-2 text-xs text-muted truncate" title={me.permissions.join('\n')}>
                        Signed in as <span className="text-slate-200">{me.name}</span>
                    </div>
                    <button
                        onClick={onSignOut}
                        className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-xs text-muted hover:bg-bg-card hover:text-slate-200"
                    >
                        <LogOut className="h-4 w-4" /> Sign out
                    </button>
                </div>
            </aside>
            <main className="flex-1 min-w-0 p-4 md:p-6 overflow-auto">
                <Routes>
                    <Route path="/" element={<Overview />} />
                    <Route path="/games" element={<Games />} />
                    <Route path="/arenas" element={<Arenas />} />
                    <Route path="/arenas/:id" element={<ArenaDetailPage />} />
                    <Route path="/queues" element={<Queues />} />
                    <Route path="/players" element={<Players />} />
                    <Route path="/players/:uuid" element={<Players />} />
                    <Route path="/history" element={<HistoryPage />} />
                    <Route path="/history/:matchId" element={<MatchPage />} />
                    <Route path="/leaderboard" element={<Leaderboard />} />
                    {canSessions && <Route path="/sessions" element={<Sessions />} />}
                    {canSettings && <Route path="/settings" element={<Settings />} />}
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
            end={to === '/'}
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
