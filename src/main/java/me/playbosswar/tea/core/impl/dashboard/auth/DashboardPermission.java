package me.playbosswar.tea.core.impl.dashboard.auth;

import java.util.List;

/**
 * Bundles ({@code tea.dashboard.viewer}, {@code .moderator}, {@code .admin}) are
 * declared in plugin.yml as parents of these nodes.
 */
public final class DashboardPermission {

    public static final String VIEW = "tea.dashboard.view";
    public static final String CHAT = "tea.dashboard.chat";
    public static final String HISTORY_EXPORT = "tea.dashboard.history.export";
    public static final String QUEUE_REMOVE = "tea.dashboard.queue.remove";
    public static final String PLAYER_KICK = "tea.dashboard.player.kick";
    public static final String PLAYER_MOVE = "tea.dashboard.player.move";
    public static final String PLAYER_SPECTATOR = "tea.dashboard.player.spectator";
    public static final String ARENA_ALLOCATE = "tea.dashboard.arena.allocate";
    public static final String ARENA_START = "tea.dashboard.arena.start";
    public static final String ARENA_END = "tea.dashboard.arena.end";
    public static final String ARENA_RELEASE = "tea.dashboard.arena.release";
    public static final String MAPS_RELOAD = "tea.dashboard.maps.reload";
    public static final String MAPS_DELETE = "tea.dashboard.maps.delete";
    public static final String MAPS_RESTORE = "tea.dashboard.maps.restore";
    public static final String SESSIONS = "tea.dashboard.sessions";
    public static final String SETTINGS = "tea.dashboard.settings";
    public static final String MAPS_TRANSFER = "tea.dashboard.maps.transfer";

    /** Checked at login and stored on the session. */
    public static final List<String> ALL = List.of(VIEW, CHAT, HISTORY_EXPORT, QUEUE_REMOVE, PLAYER_KICK,
            PLAYER_MOVE, PLAYER_SPECTATOR, ARENA_ALLOCATE, ARENA_START, ARENA_END, ARENA_RELEASE, MAPS_RELOAD,
            MAPS_DELETE, MAPS_RESTORE, SESSIONS, SETTINGS, MAPS_TRANSFER);

    private DashboardPermission() {
    }
}
