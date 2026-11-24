package me.playbosswar.tea.core.api.coordination;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * Shares arena state, queues and matchmaker leadership across the network.
 * In-memory for standalone servers, Redis for proxied networks.
 *
 * <p>All methods are thread-safe. Redis calls block, so avoid them on the main
 * thread; the in-memory backend is synchronous.
 */
public interface CoordinationBackend extends AutoCloseable {

    String serverId();

    /**
     * Publishes or overwrites the snapshot for this arena. Call it at short
     * intervals: on Redis, snapshots expire when not refreshed.
     */
    void publishArena(ArenaSnapshot snapshot);

    /** No-op if no snapshot is published for {@code arenaId}. */
    void removeArena(ArenaId arenaId);

    /** Every non-expired snapshot across the network. */
    Collection<ArenaSnapshot> allArenas();

    /** No-op if the player is already queued for the same game with the same options. */
    void enqueue(QueueEntry entry);

    /** Removes the player from whichever queue they are in. */
    void dequeue(UUID playerUuid);

    /** Oldest first. */
    Collection<QueueEntry> queueFor(GameId gameId);

    /** Searches every game's queue; empty if the player is not queued. */
    Optional<QueueEntry> queueEntryOf(UUID playerUuid);

    /** Zero-based position, or {@code -1} if the player is not queued for this game. */
    int queuePosition(UUID playerUuid, GameId gameId);

    /**
     * Acquires or refreshes the matchmaker leader lock. Acquiring also extends
     * its expiry, so call it periodically or another server takes over.
     *
     * @return {@code true} if this server holds the lock after the call
     */
    boolean tryAcquireMatchmakerLock();

    /** No-op if this server does not hold the lock. */
    void releaseMatchmakerLock();

    /**
     * Declares which games this server can host arenas for, so the
     * {@code central} pool leader knows where to send
     * {@link ControlCommand.Type#ALLOCATE_ARENA}. Call once every game has
     * registered and refresh on a heartbeat, or the Redis entry expires.
     */
    void publishHostedGames(Set<GameId> gameIds);

    /** Server ids that declared {@code gameId} via {@link #publishHostedGames(Set)}. */
    Set<String> hostsOf(GameId gameId);

    /**
     * Every game id declared via {@link #publishHostedGames(Set)} across the
     * network, including games not registered in this server's
     * {@link me.playbosswar.tea.core.api.game.GameRegistry}.
     */
    Set<GameId> allHostedGames();

    /**
     * Sends a command to one server. Best-effort: dropped if nothing on the
     * target is subscribed. In-memory delivery is synchronous.
     */
    void publishControl(String targetServerId, ControlCommand command);

    /**
     * Receives commands addressed to this server. Handlers run on a
     * backend thread; switch to the main thread before touching Bukkit.
     */
    void subscribeControl(Consumer<ControlCommand> handler);

    /** Broadcasts to every server. Best-effort: Redis failures are logged and dropped. */
    void publishEvent(CoordinationEvent event);

    /** Receives every broadcast event, including ones this server published. */
    void subscribeEvents(Consumer<CoordinationEvent> handler);

    @Override
    void close();
}
