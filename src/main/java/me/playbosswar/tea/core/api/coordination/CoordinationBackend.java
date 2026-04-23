package me.playbosswar.tea.core.api.coordination;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * Coordination backend used to share arena state and queue entries across the Tea network.
 *
 * <p>Two implementations ship with TeaCore: {@code InMemoryBackend} for single-server
 * setups and {@code RedisBackend} (via Lettuce) for proxy-routed networks.
 *
 * <p>The backend also mediates matchmaker leader election so only one server at a time
 * routes queued players into arenas. The current leader is expected to refresh the lock
 * periodically via {@link #tryAcquireMatchmakerLock()}.
 *
 * @apiNote All methods are thread-safe. Callers on the main thread should avoid blocking
 *          calls to the Redis backend; the in-memory backend is synchronous and fine.
 */
public interface CoordinationBackend extends AutoCloseable {

    /**
     * Returns the identifier of the local backend server.
     *
     * @return the server id, never {@code null}.
     */
    String serverId();

    // --- Arena discovery ---

    /**
     * Publishes or refreshes this server's view of one of its arenas.
     *
     * <p>Overwrites the previous snapshot for the same {@code (serverId, arenaId)} pair.
     * Intended to be called at short intervals so stale entries expire naturally on the
     * Redis side.
     *
     * @param snapshot the arena snapshot to publish; must not be {@code null}.
     */
    void publishArena(ArenaSnapshot snapshot);

    /**
     * Removes a snapshot once an arena is released.
     *
     * <p>No-op if no snapshot is currently published for that id.
     *
     * @param arenaId arena id to clear; must not be {@code null}.
     */
    void removeArena(ArenaId arenaId);

    /**
     * Returns every non-expired snapshot known across the network.
     *
     * @return a snapshot collection, never {@code null}.
     */
    Collection<ArenaSnapshot> allArenas();

    // --- Queue ---

    /**
     * Adds a player to the queue for a game.
     *
     * <p>No-op if the player is already queued for the same game with the same options.
     *
     * @param entry queue entry describing the player and their preferences; must not be
     *              {@code null}.
     */
    void enqueue(QueueEntry entry);

    /**
     * Removes a player from any queue they are in.
     *
     * @param playerUuid player to dequeue; must not be {@code null}.
     */
    void dequeue(UUID playerUuid);

    /**
     * Returns queued entries for a game, oldest first.
     *
     * @param gameId game whose queue to read; must not be {@code null}.
     * @return queue entries in FIFO order, never {@code null}.
     */
    Collection<QueueEntry> queueFor(GameId gameId);

    /**
     * Returns the zero-based position of a player in their queue.
     *
     * @param playerUuid player to look up; must not be {@code null}.
     * @param gameId     game whose queue to inspect; must not be {@code null}.
     * @return the position (0 = front of queue), or {@code -1} if the player is not queued
     *         for this game.
     */
    int queuePosition(UUID playerUuid, GameId gameId);

    // --- Leader election (matchmaker) ---

    /**
     * Attempts to become, or refresh, the active matchmaker leader.
     *
     * <p>Should be called periodically: acquiring the lock also extends its expiry, so
     * missing a refresh lets another server take over. Safe to call when this server
     * already holds the lock.
     *
     * @return {@code true} if we hold the lock after the call; {@code false} if another
     *         server is the leader.
     */
    boolean tryAcquireMatchmakerLock();

    /**
     * Releases the matchmaker leader lock on shutdown.
     *
     * <p>No-op if the lock is not currently held by this server.
     */
    void releaseMatchmakerLock();

    // --- Hosted games (central-mode fleet discovery) ---

    /**
     * Publishes the set of games this server is capable of hosting arenas
     * for.
     *
     * <p>
     * Used by the {@code central} arena-pool mode: the matchmaker leader
     * needs to know which backends can accept a new arena for game X before
     * publishing an {@link ControlCommand.Type#ALLOCATE_ARENA} command. Call
     * on plugin enable once every game has registered, and refresh on a
     * heartbeat so Redis entries don't TTL out.
     *
     * @param gameIds games this backend can host; must not be {@code null}.
     */
    void publishHostedGames(Set<GameId> gameIds);

    /**
     * Returns the set of server ids that have declared they host the given
     * game. Network-wide view populated from {@link #publishHostedGames(Set)}
     * calls across the fleet.
     *
     * @param gameId game to look up; must not be {@code null}.
     * @return server ids hosting this game, never {@code null}.
     */
    Set<String> hostsOf(GameId gameId);

    // --- Control plane (dashboard -> target backend) ---

    /**
     * Sends a fire-and-forget control command to a specific backend server.
     *
     * <p>
     * The dashboard uses this to reach arenas hosted by other pods; on the
     * in-memory backend a local subscriber gets it synchronously, on the Redis
     * backend it travels over pub/sub and arrives within a few ms. Delivery is
     * best-effort: if no subscriber exists on the target server, the command is
     * dropped.
     *
     * @param targetServerId destination backend's {@link #serverId()}; must
     *                       not be {@code null}.
     * @param command        the command to send; must not be {@code null}.
     */
    void publishControl(String targetServerId, ControlCommand command);

    /**
     * Registers a handler that receives {@link ControlCommand}s targeted at
     * <b>this</b> server only.
     *
     * <p>
     * Multiple subscribers are allowed; each receives every incoming command.
     * Handlers run on a backend-internal thread and must marshal to the
     * Bukkit main thread themselves when touching Bukkit APIs.
     *
     * @param handler callback invoked for each received command; must not be
     *                {@code null}.
     */
    void subscribeControl(Consumer<ControlCommand> handler);

    // --- Event fan-out (any backend -> all subscribers) ---

    /**
     * Broadcasts an arena or match event to every backend on the network.
     *
     * <p>
     * Used by each backend to mirror its local Bukkit events so the lobby's
     * dashboard can fan them out to WebSocket clients without pulling per-pod
     * APIs. Delivery is best-effort; transient Redis failures are logged and
     * dropped.
     *
     * @param event the event to broadcast; must not be {@code null}.
     */
    void publishEvent(CoordinationEvent event);

    /**
     * Registers a handler that receives every {@link CoordinationEvent}
     * broadcast on the network (including events this server published).
     *
     * @param handler callback invoked for each received event; must not be
     *                {@code null}.
     */
    void subscribeEvents(Consumer<CoordinationEvent> handler);

    /**
     * Releases network resources (connections, threads) used by this backend.
     */
    @Override
    void close();
}
