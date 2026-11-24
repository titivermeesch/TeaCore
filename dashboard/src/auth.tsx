import { createContext, ReactNode, useContext } from 'react';

/** The signed-in account, from GET /api/auth/me. */
export interface Me {
    name: string;
    uuid: string | null;
    permissions: string[];
    expires: string;
}

/** Dashboard permission nodes (see DashboardPermission.java). */
export const P = {
    view: 'tea.dashboard.view',
    chat: 'tea.dashboard.chat',
    historyExport: 'tea.dashboard.history.export',
    queueRemove: 'tea.dashboard.queue.remove',
    playerKick: 'tea.dashboard.player.kick',
    playerMove: 'tea.dashboard.player.move',
    playerSpectator: 'tea.dashboard.player.spectator',
    arenaAllocate: 'tea.dashboard.arena.allocate',
    arenaStart: 'tea.dashboard.arena.start',
    arenaEnd: 'tea.dashboard.arena.end',
    arenaRelease: 'tea.dashboard.arena.release',
    mapsReload: 'tea.dashboard.maps.reload',
    mapsDelete: 'tea.dashboard.maps.delete',
    mapsRestore: 'tea.dashboard.maps.restore',
    sessions: 'tea.dashboard.sessions',
    settings: 'tea.dashboard.settings',
    mapsTransfer: 'tea.dashboard.maps.transfer',
} as const;

const MeCtx = createContext<Me | null>(null);

export function MeProvider({ me, children }: { me: Me; children: ReactNode }) {
    return <MeCtx.Provider value={me}>{children}</MeCtx.Provider>;
}

export function useMe(): Me {
    const me = useContext(MeCtx);
    if (!me) throw new Error('useMe outside MeProvider');
    return me;
}

/** Whether the signed-in account has a permission. */
export function useCan(permission: string): boolean {
    return useMe().permissions.includes(permission);
}
