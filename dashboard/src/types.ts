// Mirrors the DTOs defined in core/impl/dashboard/dto. Keep this file in sync
// when you touch the Java DTOs - if fields drift the dashboard silently shows
// `undefined` instead of failing loudly.

export interface GameSummary {
    id: string;
    displayNameKey: string;
    teamMode: string;
    poolMinAvailable: number;
    poolMaxTotal: number;
    poolPreloadOnStartup: number;
    lifecycleMaxWaitSeconds: number;
    lifecycleCountdownSeconds: number;
    lifecycleStartingSeconds: number;
    lifecycleEndingSeconds: number;
    lifecycleMaxMatchSeconds: number;
    endMode: string;
    mapSelectionMode: string;
    mapCount: number;
    /** Servers hosting the game when it runs elsewhere; empty for games on this server. */
    hosts: string[];
}

export interface PlayerRef {
    uuid: string;
    name: string | null;
}

export interface Arena {
    id: string;
    gameId: string;
    serverId: string;
    mapId: string | null;
    state: string;
    matchId: string | null;
    phaseSecondsRemaining: number;
    minPlayers: number;
    maxPlayers: number;
    players: PlayerRef[];
    spectators: PlayerRef[];
    worldName: string | null;
}

export interface QueueEntry {
    playerUuid: string;
    playerName: string;
    gameId: string;
    mapIds: string[];
    queuedAt: string;
    position: number;
}

export interface MapInfo {
    gameId: string;
    mapId: string;
    schematicFile: string | null;
    minPlayers: number;
    maxPlayers: number;
    participantSpawnCount: number;
    hasWaitingSpawn: boolean;
    extensions: string[];
    extensionDescriptions: Record<string, string>;
}

export interface PlayerProfile {
    uuid: string;
    name: string | null;
    online: boolean;
    firstSeen: string | null;
    lastSeen: string | null;
    locale: string | null;
    statsByGame: Record<string, Record<string, string>>;
}

export type DashboardEvent =
    | { type: 'arena.state'; serverId: string; arenaId: string; gameId: string; from: string; to: string; phaseSecondsRemaining: number; playerCount: number }
    | { type: 'arena.player.join'; serverId: string; arenaId: string; gameId: string; playerUuid: string; playerName: string; spectator: boolean; playerCount: number }
    | { type: 'arena.player.leave'; serverId: string; arenaId: string; gameId: string; playerUuid: string; playerName: string; reason: string; playerCount: number }
    | { type: 'match.end'; serverId: string; arenaId: string; gameId: string; winnerUuid: string | null }
    | { type: 'queue.counts'; counts: Record<string, number> }
    | KillEvent
    | ChatEvent;

export interface KillEvent {
    type: 'arena.kill';
    serverId: string;
    arenaId: string;
    gameId: string;
    at: string;
    victimUuid: string;
    victimName: string;
    killerUuid: string | null;
    killerName: string | null;
    assists: string[];
    cause: string | null;
}

export interface ChatEvent {
    type: 'arena.chat';
    serverId: string;
    arenaId: string;
    gameId: string;
    at: string;
    playerUuid: string;
    playerName: string;
    spectator: boolean;
    message: string;
}

export interface RosterLine {
    uuid: string;
    name: string;
    team: string | null;
    spectator: boolean;
    kills: number;
    deaths: number;
    assists: number;
}

export interface TeamTotals {
    team: string;
    players: number;
    kills: number;
    deaths: number;
    assists: number;
}

export interface ArenaDetail {
    arena: Arena;
    mapName: string | null;
    secondsRemaining: number;
    players: RosterLine[];
    teams: TeamTotals[];
    sidebar?: { viewer: string; title: string; lines: string[] };
    kills: KillEvent[];
    /** Only present with tea.dashboard.chat. */
    chat?: ChatEvent[];
}

export interface MatchSummary {
    matchId: string;
    game: string;
    map: string | null;
    server: string | null;
    endedAt: string;
    durationSeconds: number;
    winnerTeam: string | null;
    winnerUuid: string | null;
    winner: string;
    players: number;
}

export interface MatchLine {
    uuid: string | null;
    name: string | null;
    team: string | null;
    kills: number;
    deaths: number;
    assists: number;
    result: 'win' | 'loss' | 'draw' | 'left';
}

export interface MatchDetail extends Omit<MatchSummary, 'players'> {
    players: MatchLine[];
}

export interface PlayerHistory {
    matches: (Omit<MatchSummary, 'players' | 'winner'> & MatchLine)[];
    streak: { result: 'win' | 'loss' | 'draw' | 'left' | 'none'; count: number };
}

export interface SessionInfo {
    id: string;
    name: string;
    uuid: string | null;
    permissions: string[];
    created: string;
    expires: string;
    address: string;
    current: boolean;
}
