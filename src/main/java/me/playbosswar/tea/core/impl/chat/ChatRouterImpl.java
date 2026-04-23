package me.playbosswar.tea.core.impl.chat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaManager;
import me.playbosswar.tea.core.api.chat.ChatChannel;
import me.playbosswar.tea.core.api.chat.ChatRouter;
import me.playbosswar.tea.core.api.team.Team;
import me.playbosswar.tea.core.api.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ChatRouterImpl implements ChatRouter, Listener {

    private final ArenaManager arenaManager;
    private final TeamManager teamManager;
    private final ConcurrentHashMap<UUID, ChatChannel> activeChannels = new ConcurrentHashMap<>();

    public ChatRouterImpl(ArenaManager arenaManager, TeamManager teamManager) {
        this.arenaManager = arenaManager;
        this.teamManager = teamManager;
    }

    @Override
    public void setActiveChannel(Player player, ChatChannel channel) {
        activeChannels.put(player.getUniqueId(), channel);
    }

    @Override
    public ChatChannel activeChannel(Player player) {
        return activeChannels.getOrDefault(player.getUniqueId(), ChatChannel.MATCH);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        ChatChannel channel = parseChannel(sender, event);

        if (channel == ChatChannel.GLOBAL && !sender.hasPermission("tea.chat.global")) {
            event.getRecipients().clear();
            event.getRecipients().add(sender);
            return;
        }
        if (channel == ChatChannel.STAFF && !sender.hasPermission("tea.chat.staff")) {
            event.getRecipients().clear();
            event.getRecipients().add(sender);
            return;
        }

        Set<Player> recipients = computeRecipients(sender, channel);
        event.getRecipients().clear();
        event.getRecipients().addAll(recipients);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeChannels.remove(event.getPlayer().getUniqueId());
    }

    private ChatChannel parseChannel(Player sender, AsyncPlayerChatEvent event) {
        String msg = event.getMessage();
        if (msg.startsWith("!")) {
            event.setMessage(msg.length() > 1 ? msg.substring(1).stripLeading() : "");
            return ChatChannel.TEAM;
        }
        if (msg.startsWith("@")) {
            event.setMessage(msg.length() > 1 ? msg.substring(1).stripLeading() : "");
            return ChatChannel.STAFF;
        }
        if (msg.startsWith("~")) {
            event.setMessage(msg.length() > 1 ? msg.substring(1).stripLeading() : "");
            return ChatChannel.GLOBAL;
        }
        return activeChannel(sender);
    }

    private Set<Player> computeRecipients(Player sender, ChatChannel channel) {
        Arena senderArena = arenaManager.arenaOf(sender.getUniqueId()).orElse(null);

        switch (channel) {
            case GLOBAL:
                return new HashSet<>(Bukkit.getOnlinePlayers());

            case STAFF: {
                Set<Player> out = new HashSet<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("tea.chat.staff"))
                        out.add(p);
                }
                return out;
            }

            case TEAM: {
                Set<Player> out = new HashSet<>();
                out.add(sender);
                if (senderArena != null) {
                    Team team = teamManager.teamOf(senderArena.id(), sender.getUniqueId()).orElse(null);
                    if (team != null) {
                        for (UUID member : team.members()) {
                            Player p = Bukkit.getPlayer(member);
                            if (p != null)
                                out.add(p);
                        }
                    }
                }
                return out;
            }

            case MATCH:
            default: {
                Set<Player> out = new HashSet<>();
                if (senderArena == null) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (arenaManager.arenaOf(p.getUniqueId()).isEmpty())
                            out.add(p);
                    }
                } else {
                    Set<UUID> occupants = new HashSet<>(senderArena.players());
                    occupants.addAll(senderArena.spectators());
                    for (UUID uuid : occupants) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null)
                            out.add(p);
                    }
                }
                return out;
            }
        }
    }
}
