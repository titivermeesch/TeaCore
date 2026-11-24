package me.playbosswar.tea.core.impl.arena;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.game.GameRegistry;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/** The {@code hosting:} section: which installed games this server runs and how arenas pick a map. */
public final class Hosting {

    /** How an arena picks its map. {@code mapId} is set for {@link Mode#FIXED}. */
    public record MapChoice(Mode mode, @Nullable String mapId) { }

    public enum Mode { DEFAULT, RANDOM, VOTE, FIXED }

    private final Plugin plugin;
    private final GameRegistry games;

    public Hosting(Plugin plugin, GameRegistry games) {
        this.plugin = plugin;
        this.games = games;
    }

    /** The installed games this server may host. */
    public Set<GameId> games() {
        List<String> listed = plugin.getConfig().getStringList("hosting.games");
        boolean any = listed.isEmpty() || listed.stream().anyMatch(s -> s.equalsIgnoreCase("any"));
        Set<GameId> out = new LinkedHashSet<>();
        for (GameDefinition def : games.all()) {
            if (any || listed.stream().anyMatch(s -> s.equalsIgnoreCase(def.id().value()))) {
                out.add(def.id());
            }
        }
        return out;
    }

    public boolean hosts(GameId game) {
        return games().contains(game);
    }

    public MapChoice mapChoice(GameId game) {
        String raw = plugin.getConfig().getString("hosting.maps." + game.value());
        if (raw == null || raw.isBlank()) {
            raw = plugin.getConfig().getString("hosting.map", "default");
        }
        String v = raw.trim();
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "", "default" -> new MapChoice(Mode.DEFAULT, null);
            case "random" -> new MapChoice(Mode.RANDOM, null);
            case "vote" -> new MapChoice(Mode.VOTE, null);
            default -> new MapChoice(Mode.FIXED, v);
        };
    }

    /**
     * The game with the most queued players, the current one on a tie, else a
     * random one. {@code null} when there is nothing to pick from.
     */
    public static @Nullable GameId pick(List<GameId> candidates, ToIntFunction<GameId> queued,
                                        @Nullable GameId current) {
        if (candidates.isEmpty()) {
            return null;
        }
        int best = -1;
        List<GameId> top = new ArrayList<>();
        for (GameId g : candidates) {
            int q = queued.applyAsInt(g);
            if (q > best) {
                best = q;
                top.clear();
            }
            if (q == best) {
                top.add(g);
            }
        }
        if (current != null && top.contains(current)) {
            return current;
        }
        return top.get(ThreadLocalRandom.current().nextInt(top.size()));
    }
}
