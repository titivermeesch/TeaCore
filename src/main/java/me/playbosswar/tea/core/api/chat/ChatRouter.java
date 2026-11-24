package me.playbosswar.tea.core.api.chat;

import org.bukkit.entity.Player;

/**
 * Tracks the {@link ChatChannel} each player's untagged messages go to.
 * Selections are in memory only and reset on disconnect.
 */
public interface ChatRouter {

    void setActiveChannel(Player player, ChatChannel channel);

    /** @return {@link ChatChannel#MATCH} if none has been set */
    ChatChannel activeChannel(Player player);
}
