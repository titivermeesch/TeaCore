package me.playbosswar.tea.core.api.chat;

import org.bukkit.entity.Player;

/**
 * Tracks each player's active {@link ChatChannel}.
 *
 * <p>The active channel is consulted by the chat listener to decide which audience a
 * player's next message reaches. Selections are kept in memory only and reset on
 * disconnect.
 */
public interface ChatRouter {

    /**
     * Sets the active default channel for a player.
     *
     * <p>The change persists for the remainder of the player's session and affects all
     * subsequent untagged chat messages from them.
     *
     * @param player  player whose channel to update; must not be {@code null}.
     * @param channel channel to activate; must not be {@code null}.
     */
    void setActiveChannel(Player player, ChatChannel channel);

    /**
     * Returns the player's active channel.
     *
     * @param player player to inspect; must not be {@code null}.
     * @return the active channel, or {@link ChatChannel#MATCH} as a fallback when none has
     *         been explicitly set; never {@code null}.
     */
    ChatChannel activeChannel(Player player);
}
