package me.playbosswar.tea.core.api.feature;

import me.playbosswar.tea.core.api.game.GameId;

/**
 * On/off switches for optional TeaCore features, read from the {@code features:}
 * block of TeaCore's {@code config.yml}:
 *
 * <pre>
 * features:
 *   menus: true            # every TeaCore menu (per-menu files also have enabled:)
 *   sidebar: true          # every sidebar (lobby + in-game)
 *   placeholders: true     # the PlaceholderAPI expansion
 *   queue-bossbar: true
 *   games:
 *     fireball:
 *       sidebar: false     # per-game override of any key above
 * </pre>
 *
 * Unknown keys default to {@code true}, so a feature added later is on until an
 * admin turns it off. Values are re-read on {@code /tea reload}.
 */
public interface FeatureService {

    /** Whether a feature is on server-wide. */
    boolean enabled(String feature);

    /**
     * Whether a feature is on for a game: the game's override under
     * {@code features.games.<id>} if present, else the server-wide value.
     */
    boolean enabled(GameId game, String feature);
}
