package me.playbosswar.tea.core.api.chat;

/**
 * Scopes a chat message to a particular audience.
 *
 * <p>A player's active channel, managed by {@link ChatRouter}, determines where their next
 * untagged message is routed.
 */
public enum ChatChannel {
    /** Players in the same match (or same lobby for players outside any arena). */
    MATCH,
    /** Only teammates in the sender's team. */
    TEAM,
    /** Only players holding the {@code tea.chat.staff} permission. */
    STAFF,
    /** All online players; requires {@code tea.chat.global} permission. */
    GLOBAL
}
