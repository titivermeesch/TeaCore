package me.playbosswar.tea.core.impl.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.impl.scoreboard.SidebarProvider;
import org.bukkit.entity.Player;

/**
 * Sidebar that walks an admin through the remaining map-setup steps while they
 * author a map, showing the next {@link #WINDOW} incomplete steps plus a
 * "+N more" footer rather than the whole checklist at once. The step list comes
 * from {@link MapEditingService#remainingSteps(Player)}, which folds in each
 * game's required map extensions, so every game gets identical guidance for
 * free. Registered with the {@link me.playbosswar.tea.core.impl.scoreboard.ScoreboardManager}.
 */
public final class MapSetupSidebar implements SidebarProvider {

    private static final int WINDOW = 3;

    private final MapEditingService editing;
    private final MessageService messageService;
    /** Players who toggled the sidebar off for their current authoring session. */
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    public MapSetupSidebar(MapEditingService editing, MessageService messageService) {
        this.editing = editing;
        this.messageService = messageService;
    }

    /**
     * Toggles the sidebar for an editing player.
     *
     * @return {@code true} if it is now shown, {@code false} if hidden.
     */
    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        if (hidden.remove(id))
            return true;
        hidden.add(id);
        return false;
    }

    @Override
    public Optional<SidebarProvider.Sidebar> sidebarFor(Player player) {
        MapDraft draft = editing.currentDraft(player).orElse(null);
        if (draft == null) {
            // Session ended: reset the hide flag so the next draft defaults on.
            hidden.remove(player.getUniqueId());
            return Optional.empty();
        }
        if (hidden.contains(player.getUniqueId()))
            return Optional.empty();

        String locale = messageService.resolveLocale(player);
        List<String> lines = new ArrayList<>();
        lines.add(render(locale, "core.map.scoreboard.context",
                Map.of("game", draft.gameId().value(), "map", draft.mapId())));
        lines.add("");

        List<MapEditingService.SetupStep> steps = editing.remainingSteps(player);
        if (steps.isEmpty()) {
            lines.add(render(locale, "core.map.scoreboard.ready", Map.of()));
        } else {
            int shown = Math.min(WINDOW, steps.size());
            for (int i = 0; i < shown; i++) {
                MapEditingService.SetupStep step = steps.get(i);
                lines.add(render(locale, step.labelKey(), step.placeholders()));
                lines.add(render(locale, step.hintKey(), step.placeholders()));
            }
            int more = steps.size() - shown;
            if (more > 0) {
                lines.add(render(locale, "core.map.scoreboard.more",
                        Map.of("count", String.valueOf(more))));
            }
        }

        String title = render(locale, "core.map.scoreboard.title", Map.of());
        return Optional.of(new SidebarProvider.Sidebar(title, lines));
    }

    private String render(String locale, String key, Map<String, String> placeholders) {
        return messageService.renderLegacyNoPrefix(locale, key, placeholders);
    }
}
