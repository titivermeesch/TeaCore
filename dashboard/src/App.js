import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
import { Route, Routes, NavLink, Navigate } from 'react-router-dom';
import { Activity, Gamepad2, LayoutDashboard, LogOut, Swords, Users, } from 'lucide-react';
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
    const [authed, setAuthed] = useState(!!getToken());
    useEffect(() => {
        const handler = () => setAuthed(!!getToken());
        window.addEventListener('storage', handler);
        return () => window.removeEventListener('storage', handler);
    }, []);
    if (!authed)
        return _jsx(Login, { onAuthed: () => setAuthed(true) });
    return (_jsx(DashboardWsProvider, { children: _jsx(Shell, { onSignOut: () => {
                clearToken();
                setAuthed(false);
            } }) }));
}
function Shell({ onSignOut }) {
    return (_jsxs("div", { className: "min-h-dvh flex", children: [_jsxs("aside", { className: "w-60 shrink-0 border-r border-border bg-bg-raised p-4 space-y-4 flex flex-col", children: [_jsxs("div", { className: "flex items-center gap-2 px-2 py-1", children: [_jsx(LayoutDashboard, { className: "h-5 w-5 text-primary" }), _jsx("div", { className: "text-primary font-semibold", children: "TeaCore" })] }), _jsxs("nav", { className: "flex flex-col text-sm gap-0.5", children: [_jsx(NavItem, { to: "/", icon: _jsx(LayoutDashboard, { className: "h-4 w-4" }), children: "Overview" }), _jsx(NavItem, { to: "/games", icon: _jsx(Gamepad2, { className: "h-4 w-4" }), children: "Games" }), _jsx(NavItem, { to: "/arenas", icon: _jsx(Swords, { className: "h-4 w-4" }), children: "Arenas" }), _jsx(NavItem, { to: "/queues", icon: _jsx(Activity, { className: "h-4 w-4" }), children: "Queues" }), _jsx(NavItem, { to: "/players", icon: _jsx(Users, { className: "h-4 w-4" }), children: "Players" })] }), _jsxs("div", { className: "mt-auto space-y-2", children: [_jsx(ConnectionStatus, {}), _jsxs("button", { onClick: onSignOut, className: "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-xs text-muted hover:bg-bg-card hover:text-slate-200", children: [_jsx(LogOut, { className: "h-4 w-4" }), " Sign out"] })] })] }), _jsx("main", { className: "flex-1 p-6 overflow-auto", children: _jsxs(Routes, { children: [_jsx(Route, { path: "/", element: _jsx(Overview, {}) }), _jsx(Route, { path: "/games", element: _jsx(Games, {}) }), _jsx(Route, { path: "/arenas", element: _jsx(Arenas, {}) }), _jsx(Route, { path: "/queues", element: _jsx(Queues, {}) }), _jsx(Route, { path: "/players", element: _jsx(Players, {}) }), _jsx(Route, { path: "*", element: _jsx(Navigate, { to: "/", replace: true }) })] }) })] }));
}
function NavItem({ to, icon, children, }) {
    return (_jsxs(NavLink, { to: to, end: true, className: ({ isActive }) => cn('flex items-center gap-2 px-2 py-1.5 rounded-md transition-colors', isActive
            ? 'bg-primary/10 text-primary'
            : 'text-slate-300 hover:bg-bg-card'), children: [icon, children] }));
}
function ConnectionStatus() {
    const { status } = useDashboardWs();
    const color = status === 'open' ? 'bg-success' : status === 'connecting' ? 'bg-accent' : 'bg-error';
    return (_jsxs("div", { className: "flex items-center gap-2 text-xs text-muted px-2", children: [_jsx("span", { className: cn('h-2 w-2 rounded-full', color) }), status === 'open' ? 'live' : status === 'connecting' ? 'connecting...' : 'disconnected'] }));
}
