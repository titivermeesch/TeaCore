package me.playbosswar.tea.core.api.stats;

import java.util.List;
import java.util.Objects;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * Describes a game's stats table so any server, including a lobby without the
 * game's jar, can render the stats GUI with a generic {@code SELECT}. Register
 * via {@link StatsRegistry#registerSchema(StatsSchema)}.
 *
 * @param displayNameKey same key as {@code GameDefinition#displayNameKey()}.
 * @param sqlTable       table with one row per player.
 * @param sqlUuidColumn  column holding the player UUID. Default {@code "uuid"}.
 * @param columns        stat columns in display order.
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

    public StatsSchema(GameId gameId, String displayNameKey, String sqlTable, List<Column> columns) {
        this(gameId, displayNameKey, sqlTable, "uuid", columns);
    }

    /** @param label display label such as {@code "Kills"}. */
    public record Column(String label, String sqlColumn) {
        public Column {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(sqlColumn, "sqlColumn");
        }
    }
}
