package me.playbosswar.tea.core.api.stats;

import java.util.List;
import java.util.Objects;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * Description of a game's stats table, persisted by TeaCore in
 * {@code tea_stats_schemas} so any server in the fleet — including a lobby that
 * doesn't have the game's own jar loaded — can render the stats GUI by running
 * a generic {@code SELECT} against the shared database.
 *
 * <p>Game plugins call {@link StatsRegistry#registerSchema(StatsSchema)} once in
 * {@code onEnable}. Schemas are upserted, so re-registering with a changed
 * column list replaces the previous definition.
 *
 * @param gameId         the game this schema belongs to.
 * @param displayNameKey i18n key for the game's display name (the same key the
 *                       game registers in {@code GameDefinition#displayNameKey()}).
 * @param sqlTable       table that holds per-player stats (one row per player).
 * @param sqlUuidColumn  column in {@code sqlTable} holding the player UUID.
 *                       Default {@code "uuid"}.
 * @param columns        ordered list of stat columns to display, with their
 *                       human-readable labels.
 */
public record StatsSchema(
        GameId gameId,
        String displayNameKey,
        String sqlTable,
        String sqlUuidColumn,
        List<Column> columns) {

    public StatsSchema {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(sqlTable, "sqlTable");
        Objects.requireNonNull(sqlUuidColumn, "sqlUuidColumn");
        Objects.requireNonNull(columns, "columns");
        columns = List.copyOf(columns);
    }

    /** Convenience constructor that defaults the uuid column to {@code "uuid"}. */
    public StatsSchema(GameId gameId, String displayNameKey, String sqlTable, List<Column> columns) {
        this(gameId, displayNameKey, sqlTable, "uuid", columns);
    }

    /**
     * One stat column.
     *
     * @param label     human-readable label (e.g. {@code "Kills"}).
     * @param sqlColumn column name in the underlying table.
     */
    public record Column(String label, String sqlColumn) {
        public Column {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(sqlColumn, "sqlColumn");
        }
    }
}
