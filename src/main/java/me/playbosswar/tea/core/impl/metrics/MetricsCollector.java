package me.playbosswar.tea.core.impl.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.CoordinationEvent;
import me.playbosswar.tea.core.api.game.GameId;

/** Counters cover the network's events since this server started, not all time. */
public final class MetricsCollector {

    private record Stats(Instant at, Map<String, String> data, int players) { }

    private static final Duration STALE = Duration.ofSeconds(60);

    private final CoordinationBackend backend;
    private final Map<String, Stats> servers = new ConcurrentHashMap<>();
    /** Counter name -> label string -> value. */
    private final Map<String, Map<String, LongAdder>> counters = new ConcurrentHashMap<>();

    public MetricsCollector(CoordinationBackend backend) {
        this.backend = backend;
        backend.subscribeEvents(this::onEvent);
    }

    private void onEvent(CoordinationEvent e) {
        String game = e.gameId() == null ? "" : e.gameId();
        String labels = "server=\"" + esc(e.serverId()) + "\",game=\"" + esc(game) + "\"";
        switch (e.type()) {
            case SERVER_STATS -> servers.put(e.serverId(),
                    new Stats(Instant.now(), e.data() == null ? Map.of() : e.data(), e.playerCount()));
            case MATCH_END -> count("tea_matches_total", labels);
            case KILL -> count("tea_deaths_total", labels + ",kind=\""
                    + (e.data() != null && e.data().get("killer") != null ? "kill" : "other") + "\"");
            case ARENA_PLAYER_JOIN -> count("tea_arena_joins_total", labels + ",spectator=\""
                    + Boolean.TRUE.equals(e.spectator()) + "\"");
            case ARENA_STATE -> {
                if ("ALLOCATING".equals(e.fromState()) && "WAITING".equals(e.toState())) {
                    count("tea_arenas_allocated_total", labels);
                } else if ("LIVE".equals(e.toState())) {
                    count("tea_matches_started_total", labels);
                }
            }
            default -> { }
        }
    }

    private void count(String name, String labels) {
        counters.computeIfAbsent(name, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(labels, k -> new LongAdder()).increment();
    }

    public String render() {
        StringBuilder out = new StringBuilder(4096);
        Instant now = Instant.now();
        servers.entrySet().removeIf(e -> Duration.between(e.getValue().at(), now).compareTo(STALE) > 0);

        header(out, "tea_server_up", "gauge", "1 for each server that sent stats in the last minute.");
        servers.keySet().stream().sorted().forEach(s -> line(out, "tea_server_up", srv(s), 1));
        stat(out, "tea_server_tps", "TPS over the last minute.", "tps");
        stat(out, "tea_server_mspt", "Average tick time in milliseconds.", "mspt");
        stat(out, "tea_server_memory_used_bytes", "JVM heap in use.", "memoryUsed");
        stat(out, "tea_server_memory_max_bytes", "JVM maximum heap.", "memoryMax");
        stat(out, "tea_arena_spare_worlds", "Released arena worlds kept for reuse.", "spareWorlds");
        header(out, "tea_server_players", "gauge", "Players online on each server.");
        servers.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> line(out, "tea_server_players", srv(e.getKey()), e.getValue().players()));

        Map<String, int[]> arenas = new TreeMap<>();
        for (ArenaSnapshot a : backend.allArenas()) {
            String key = "server=\"" + esc(a.serverId()) + "\",game=\"" + esc(a.gameId().value())
                    + "\",state=\"" + a.state().name().toLowerCase(Locale.ROOT) + "\"";
            int[] v = arenas.computeIfAbsent(key, k -> new int[2]);
            v[0]++;
            v[1] += a.state() == ArenaState.RELEASED ? 0 : a.players();
        }
        header(out, "tea_arenas", "gauge", "Arenas by server, game and state.");
        arenas.forEach((k, v) -> line(out, "tea_arenas", k, v[0]));
        header(out, "tea_arena_players", "gauge", "Players (not spectators) in arenas by server, game and state.");
        arenas.forEach((k, v) -> line(out, "tea_arena_players", k, v[1]));

        header(out, "tea_queue_players", "gauge", "Players waiting in each game's queue.");
        java.util.List<GameId> games = backend.allHostedGames().stream()
                .sorted(java.util.Comparator.comparing(GameId::value)).toList();
        for (GameId game : games) {
            line(out, "tea_queue_players", "game=\"" + esc(game.value()) + "\"", backend.queueFor(game).size());
        }
        header(out, "tea_game_hosts", "gauge", "Servers hosting each game.");
        for (GameId game : games) {
            line(out, "tea_game_hosts", "game=\"" + esc(game.value()) + "\"", backend.hostsOf(game).size());
        }

        counter(out, "tea_matches_started_total", "Matches that went live.");
        counter(out, "tea_matches_total", "Matches that ended.");
        counter(out, "tea_deaths_total", "Deaths in live arenas; kind=kill when a player got the credit.");
        counter(out, "tea_arena_joins_total", "Players joining arenas.");
        counter(out, "tea_arenas_allocated_total", "Arenas that finished allocating.");
        return out.toString();
    }

    private void stat(StringBuilder out, String name, String help, String key) {
        header(out, name, "gauge", help);
        servers.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            String raw = e.getValue().data().get(key);
            if (raw != null) {
                try {
                    line(out, name, srv(e.getKey()), Double.parseDouble(raw));
                } catch (NumberFormatException ignored) {
                }
            }
        });
    }

    private void counter(StringBuilder out, String name, String help) {
        header(out, name, "counter", help);
        new TreeMap<>(counters.getOrDefault(name, Map.of()))
                .forEach((labels, v) -> line(out, name, labels, v.sum()));
    }

    private static String srv(String server) {
        return "server=\"" + esc(server) + "\"";
    }

    private static void header(StringBuilder out, String name, String type, String help) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n');
        out.append("# TYPE ").append(name).append(' ').append(type).append('\n');
    }

    private static void line(StringBuilder out, String name, String labels, double value) {
        out.append(name).append('{').append(labels).append("} ");
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            out.append((long) value);
        } else {
            out.append(String.format(Locale.ROOT, "%.3f", value));
        }
        out.append('\n');
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
