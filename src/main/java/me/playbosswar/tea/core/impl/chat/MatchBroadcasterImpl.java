package me.playbosswar.tea.core.impl.chat;

import java.util.Map;
import java.util.UUID;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.chat.MatchBroadcaster;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.api.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class MatchBroadcasterImpl implements MatchBroadcaster {

    private final MessageService messageService;

    public MatchBroadcasterImpl(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void toMatch(Arena arena, String key, Map<String, String> placeholders) {
        sendToUuids(arena.players(), key, placeholders);
        sendToUuids(arena.spectators(), key, placeholders);
    }

    @Override
    public void toTeam(Team team, String key, Map<String, String> placeholders) {
        sendToUuids(team.members(), key, placeholders);
    }

    @Override
    public void toSpectators(Arena arena, String key, Map<String, String> placeholders) {
        sendToUuids(arena.spectators(), key, placeholders);
    }

    @Override
    public void actionBarToMatch(Arena arena, String key, Map<String, String> placeholders) {
        sendActionBarToUuids(arena.players(), key, placeholders);
        sendActionBarToUuids(arena.spectators(), key, placeholders);
    }

    private void sendToUuids(java.util.Set<UUID> uuids, String key, Map<String, String> placeholders) {
        for (UUID uuid : uuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null)
                messageService.send(p, key, placeholders);
        }
    }

    private void sendActionBarToUuids(java.util.Set<UUID> uuids, String key,
            Map<String, String> placeholders) {
        for (UUID uuid : uuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null)
                continue;
            messageService.sendActionBar(p, key, placeholders);
        }
    }
}
