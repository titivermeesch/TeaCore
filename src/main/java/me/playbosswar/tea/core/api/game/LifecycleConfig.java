package me.playbosswar.tea.core.api.game;

import java.util.List;

/**
 * Phase durations and countdown configuration for the arena lifecycle.
 *
 * <p>The minimum-players threshold for leaving {@code WAITING} is defined on each map
 * ({@code MapDefinition#minPlayers}) rather than here, so maps with more spawns can demand
 * more players.
 *
 * @param maxWaitSeconds          hard cap on the waiting phase; must be {@code > 0}.
 * @param countdownSeconds        length of the countdown phase in seconds.
 * @param startingSeconds         length of the freeze frame between countdown and gameplay.
 * @param endingSeconds           length of the ending phase after a match is decided.
 * @param maxMatchSeconds         hard cap on the {@code LIVE} duration; {@code 0} means no
 *                                limit. When the cap is reached, a
 *                                {@link me.playbosswar.tea.core.api.arena.MatchTimedOutEvent}
 *                                fires and the owning game decides the winner.
 * @param titleCountdownThreshold seconds remaining at which the countdown title overlay
 *                                (big numbers and pling sound) starts showing. Above this
 *                                threshold only chat announcements are emitted.
 * @param chatAnnounceAtSeconds   second-remaining values at which a chat announcement is
 *                                broadcast (e.g. {@code [30, 15]} produces "starts in 30s"
 *                                and "starts in 15s"). Values {@code <=
 *                                titleCountdownThreshold} are ignored to avoid
 *                                overlapping the title overlay. Defensively copied and
 *                                never {@code null}.
 */
public record LifecycleConfig(
        int maxWaitSeconds,
        int countdownSeconds,
        int startingSeconds,
        int endingSeconds,
        int maxMatchSeconds,
        int titleCountdownThreshold,
        List<Integer> chatAnnounceAtSeconds) {

    /**
     * Canonical constructor that defensively copies {@code chatAnnounceAtSeconds} and
     * normalizes {@code null} to {@link List#of()}.
     */
    public LifecycleConfig {
        chatAnnounceAtSeconds = chatAnnounceAtSeconds == null
                ? List.of()
                : List.copyOf(chatAnnounceAtSeconds);
    }

    /**
     * Returns a lifecycle config with conservative defaults for a typical minigame.
     *
     * <p>Values: waiting 120s, countdown 30s, starting 5s, ending 10s, no match cap, title
     * overlay at 10s, chat announcements at 30s and 15s.
     *
     * @return a default configuration, never {@code null}.
     */
    public static LifecycleConfig defaults() {
        return new LifecycleConfig(120, 30, 5, 10, 0, 10, List.of(30, 15));
    }

    /**
     * Back-compat constructor that defaults title threshold to 10 and chat announcements
     * to {@code [30, 15]}.
     *
     * @param maxWaitSeconds   cap on the waiting phase.
     * @param countdownSeconds length of the countdown phase.
     * @param startingSeconds  length of the starting freeze frame.
     * @param endingSeconds    length of the ending phase.
     * @param maxMatchSeconds  cap on the {@code LIVE} duration; {@code 0} = no limit.
     */
    public LifecycleConfig(int maxWaitSeconds, int countdownSeconds, int startingSeconds,
            int endingSeconds, int maxMatchSeconds) {
        this(maxWaitSeconds, countdownSeconds, startingSeconds, endingSeconds, maxMatchSeconds,
                10, List.of(30, 15));
    }

    /**
     * Back-compat constructor with no match cap and default announcement configuration.
     *
     * @param maxWaitSeconds   cap on the waiting phase.
     * @param countdownSeconds length of the countdown phase.
     * @param startingSeconds  length of the starting freeze frame.
     * @param endingSeconds    length of the ending phase.
     */
    public LifecycleConfig(int maxWaitSeconds, int countdownSeconds, int startingSeconds, int endingSeconds) {
        this(maxWaitSeconds, countdownSeconds, startingSeconds, endingSeconds, 0);
    }
}
