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
    | { type: 'queue.counts'; counts: Record<string, number> };
