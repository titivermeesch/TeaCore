package me.playbosswar.tea.core.impl.coordination;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.coordination.ControlCommand;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.CoordinationEvent;
import me.playbosswar.tea.core.api.coordination.QueueEntry;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.impl.config.CoreConfig;

/**
 * Redis-backed coordination via Lettuce. Layout:
 *
 * <pre>{@code
 *   tea:arena:<arenaId>                hash, TTL 10s, fields: server, game, map, state, players, min, max, updatedAt
 *   tea:arenas:index                    set of active arenaIds
 *   tea:queue:<gameId>                  sorted set, score = queuedAt-epoch-millis, member = "<uuid>|<name>|<mapIds>"
 *   tea:matchmaker:leader               SET-NX-EX key; TTL 10s, refreshed every 3s
 * }</pre>
 *
 * Arenas whose hash has expired are treated as offline. Queue entries are
 * persistent
 * until explicitly removed.
 */
public final class RedisBackend implements CoordinationBackend {

    private static final Duration ARENA_TTL = Duration.ofSeconds(10);
    private static final Duration HOSTS_TTL = Duration.ofSeconds(30);
    private static final Duration LEADER_TTL = Duration.ofSeconds(10);
    private static final String LEADER_KEY = "tea:matchmaker:leader";
    private static final String ARENA_INDEX = "tea:arenas:index";
    private static final String HOSTS_PREFIX = "tea:hosts:";
    private static final String CONTROL_CHANNEL_PREFIX = "tea:control:";
    private static final String EVENTS_CHANNEL = "tea:events";

    private final String serverId;
    private final Logger logger;
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> redis;
    private final StatefulRedisPubSubConnection<String, String> pubsubConnection;
    private final ObjectMapper mapper = new ObjectMapper();
    private final CopyOnWriteArrayList<Consumer<ControlCommand>> controlHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<CoordinationEvent>> eventHandlers = new CopyOnWriteArrayList<>();

    public RedisBackend(String serverId, CoreConfig.RedisConfig config, Logger logger) {
        this.serverId = serverId;
        this.logger = logger;
        RedisURI.Builder uri = RedisURI.builder().withHost(config.host()).withPort(config.port());
        if (config.password() != null && !config.password().isEmpty()) {
            uri.withPassword(config.password().toCharArray());
        }
        this.client = RedisClient.create(uri.build());
        this.connection = client.connect();
        this.redis = connection.sync();
        this.redis.ping();
        this.pubsubConnection = client.connectPubSub();
        this.pubsubConnection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String payload) {
                if (channel.equals(controlChannel())) dispatchControl(payload);
                else if (channel.equals(EVENTS_CHANNEL)) dispatchEvent(payload);
            }
        });
        this.pubsubConnection.sync().subscribe(controlChannel(), EVENTS_CHANNEL);
        logger.info("Connected to Redis at " + config.host() + ":" + config.port());
    }

    private String controlChannel() {
        return CONTROL_CHANNEL_PREFIX + serverId;
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public void publishArena(ArenaSnapshot s) {
        String key = "tea:arena:" + s.arenaId().value();
        Map<String, String> h = new HashMap<>();
        h.put("server", s.serverId());
        h.put("game", s.gameId().value());
        h.put("map", s.mapId() == null ? "" : s.mapId());
        h.put("state", s.state().name());
        h.put("players", String.valueOf(s.players()));
        h.put("min", String.valueOf(s.minPlayers()));
        h.put("max", String.valueOf(s.maxPlayers()));
        h.put("updatedAt", String.valueOf(s.updatedAt().toEpochMilli()));
        try {
            redis.hset(key, h);
            redis.expire(key, ARENA_TTL.toSeconds());
            redis.sadd(ARENA_INDEX, s.arenaId().value().toString());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to publish arena " + s.arenaId(), e);
        }
    }

    @Override
    public void removeArena(ArenaId arenaId) {
        try {
            redis.del("tea:arena:" + arenaId.value());
            redis.srem(ARENA_INDEX, arenaId.value().toString());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to remove arena " + arenaId, e);
        }
    }

    @Override
    public Collection<ArenaSnapshot> allArenas() {
        List<ArenaSnapshot> out = new ArrayList<>();
        try {
            for (String idStr : redis.smembers(ARENA_INDEX)) {
                String key = "tea:arena:" + idStr;
                Map<String, String> h = redis.hgetall(key);
                if (h == null || h.isEmpty()) {
                    redis.srem(ARENA_INDEX, idStr);
                    continue;
                }
                try {
                    out.add(new ArenaSnapshot(
                            h.getOrDefault("server", ""),
                            new ArenaId(UUID.fromString(idStr)),
                            GameId.of(h.getOrDefault("game", "?")),
                            h.getOrDefault("map", ""),
                            ArenaState.valueOf(h.getOrDefault("state", "WAITING")),
                            Integer.parseInt(h.getOrDefault("players", "0")),
                            Integer.parseInt(h.getOrDefault("min", "2")),
                            Integer.parseInt(h.getOrDefault("max", "16")),
                            Instant.ofEpochMilli(Long.parseLong(h.getOrDefault("updatedAt", "0")))));
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Malformed arena entry " + idStr, e);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to read arenas", e);
        }
        return out;
    }

    @Override
    public void enqueue(QueueEntry entry) {
        String key = "tea:queue:" + entry.gameId().value();
        String member = encodeEntry(entry);
        try {
            // Remove any previous entry for this player in this queue
            for (String existing : redis.zrange(key, 0, -1)) {
                if (existing.startsWith(entry.playerUuid() + "|")) {
                    redis.zrem(key, existing);
                }
            }
            redis.zadd(key, entry.queuedAt().toEpochMilli(), member);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to enqueue " + entry.playerUuid(), e);
        }
    }

    @Override
    public void dequeue(UUID playerUuid) {
        try {
            for (String key : redis.keys("tea:queue:*")) {
                for (String member : redis.zrange(key, 0, -1)) {
                    if (member.startsWith(playerUuid + "|")) {
                        redis.zrem(key, member);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to dequeue " + playerUuid, e);
        }
    }

    @Override
    public Collection<QueueEntry> queueFor(GameId gameId) {
        String key = "tea:queue:" + gameId.value();
        List<QueueEntry> out = new ArrayList<>();
        try {
            for (String member : redis.zrange(key, 0, -1)) {
                QueueEntry e = decodeEntry(gameId, member);
                if (e != null)
                    out.add(e);
            }
            out.sort(Comparator.comparing(QueueEntry::queuedAt));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to read queue " + gameId, e);
        }
        return out;
    }

    @Override
    public int queuePosition(UUID playerUuid, GameId gameId) {
        int pos = 0;
        for (QueueEntry e : queueFor(gameId)) {
            if (e.playerUuid().equals(playerUuid))
                return pos;
            pos++;
        }
        return -1;
    }

    @Override
    public void publishHostedGames(java.util.Set<GameId> gameIds) {
        String key = HOSTS_PREFIX + serverId;
        try {
            redis.del(key);
            if (!gameIds.isEmpty()) {
                String[] members = gameIds.stream().map(GameId::value).toArray(String[]::new);
                redis.sadd(key, members);
                redis.expire(key, HOSTS_TTL.toSeconds());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to publish hosted games for " + serverId, e);
        }
    }

    @Override
    public java.util.Set<String> hostsOf(GameId gameId) {
        java.util.Set<String> out = new java.util.HashSet<>();
        try {
            for (String key : redis.keys(HOSTS_PREFIX + "*")) {
                if (Boolean.TRUE.equals(redis.sismember(key, gameId.value()))) {
                    out.add(key.substring(HOSTS_PREFIX.length()));
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to read host set for " + gameId, e);
        }
        return out;
    }

    @Override
    public void publishControl(String targetServerId, ControlCommand command) {
        try {
            String payload = mapper.writeValueAsString(command);
            redis.publish(CONTROL_CHANNEL_PREFIX + targetServerId, payload);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to publish control command to " + targetServerId, e);
        }
    }

    @Override
    public void subscribeControl(Consumer<ControlCommand> handler) {
        controlHandlers.add(handler);
    }

    @Override
    public void publishEvent(CoordinationEvent event) {
        try {
            String payload = mapper.writeValueAsString(event);
            redis.publish(EVENTS_CHANNEL, payload);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to publish coordination event", e);
        }
    }

    @Override
    public void subscribeEvents(Consumer<CoordinationEvent> handler) {
        eventHandlers.add(handler);
    }

    private void dispatchControl(String payload) {
        ControlCommand cmd;
        try {
            cmd = mapper.readValue(payload, ControlCommand.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Malformed control command payload: " + payload, e);
            return;
        }
        for (Consumer<ControlCommand> h : controlHandlers) {
            try {
                h.accept(cmd);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void dispatchEvent(String payload) {
        CoordinationEvent event;
        try {
            event = mapper.readValue(payload, CoordinationEvent.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Malformed coordination event payload: " + payload, e);
            return;
        }
        for (Consumer<CoordinationEvent> h : eventHandlers) {
            try {
                h.accept(event);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public boolean tryAcquireMatchmakerLock() {
        try {
            String ok = redis.set(LEADER_KEY, serverId,
                    SetArgs.Builder.nx().ex(LEADER_TTL.toSeconds()));
            if ("OK".equals(ok))
                return true;
            // Already held - extend it only if we're the holder
            String current = redis.get(LEADER_KEY);
            if (serverId.equals(current)) {
                redis.expire(LEADER_KEY, LEADER_TTL.toSeconds());
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Leader acquire failed", e);
            return false;
        }
    }

    @Override
    public void releaseMatchmakerLock() {
        try {
            String current = redis.get(LEADER_KEY);
            if (serverId.equals(current)) {
                redis.del(LEADER_KEY);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        try {
            pubsubConnection.close();
        } catch (Exception ignored) {
        }
        try {
            connection.close();
        } catch (Exception ignored) {
        }
        try {
            client.shutdown();
        } catch (Exception ignored) {
        }
    }

    // --- encode/decode ---

    private static String encodeEntry(QueueEntry entry) {
        return entry.playerUuid() + "|" + entry.playerName() + "|"
                + String.join(",", entry.mapIds()) + "|" + entry.queuedAt().toEpochMilli();
    }

    private static QueueEntry decodeEntry(GameId gameId, String member) {
        try {
            String[] parts = member.split("\\|", 4);
            if (parts.length < 4)
                return null;
            UUID uuid = UUID.fromString(parts[0]);
            String name = parts[1];
            List<String> mapIds = parts[2].isEmpty() ? List.of() : List.of(parts[2].split(","));
            Instant queuedAt = Instant.ofEpochMilli(Long.parseLong(parts[3]));
            return new QueueEntry(uuid, name, gameId, mapIds, queuedAt);
        } catch (Exception e) {
            return null;
        }
    }
}
