package me.playbosswar.tea.core.api.game;

import java.util.List;

/**
 * Arena lifecycle phase durations, in seconds. The minimum player count lives
 * on each map ({@code MapDefinition#minPlayers}), not here.
 *
 * @param maxWaitSeconds          cap on the waiting phase; must be {@code > 0}
 * @param startingSeconds         freeze between countdown and gameplay
 * @param maxMatchSeconds         cap on {@code LIVE}; {@code 0} means no limit. On timeout a
 *                                {@link me.playbosswar.tea.core.api.arena.MatchTimedOutEvent}
 *                                fires and the game decides the winner.
 * @param titleCountdownThreshold seconds left at which the title overlay and sound start;
 *                                above it only chat announcements are sent
 * @param chatAnnounceAtSeconds   seconds left at which to announce in chat; values
 *                                {@code <= titleCountdownThreshold} are ignored
 */
public record LifecycleConfig(
        int maxWaitSeconds,
        int countdownSeconds,
        int startingSeconds,
        int endingSeconds,
        int maxMatchSeconds,
        int titleCountdownThreshold,
        List<Integer> chatAnnounceAtSeconds) {

    public LifecycleConfig {
        chatAnnounceAtSeconds = chatAnnounceAtSeconds == null
                ? List.of()
                : List.copyOf(chatAnnounceAtSeconds);
    }

    /** Waiting 120, countdown 30, starting 5, ending 10, no match cap, title at 10, chat at 30 and 15. */
    public static LifecycleConfig defaults() {
        return new LifecycleConfig(120, 30, 5, 10, 0, 10, List.of(30, 15));
    }

    /** Title threshold 10, chat announcements at 30 and 15. */
    public LifecycleConfig(int maxWaitSeconds, int countdownSeconds, int startingSeconds,
            int endingSeconds, int maxMatchSeconds) {
        this(maxWaitSeconds, countdownSeconds, startingSeconds, endingSeconds, maxMatchSeconds,
                10, List.of(30, 15));
    }

    /** No match cap; title threshold 10, chat announcements at 30 and 15. */
    public LifecycleConfig(int maxWaitSeconds, int countdownSeconds, int startingSeconds, int endingSeconds) {
        this(maxWaitSeconds, countdownSeconds, startingSeconds, endingSeconds, 0);
    }
}
