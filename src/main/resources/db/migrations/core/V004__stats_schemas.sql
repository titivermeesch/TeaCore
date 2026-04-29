-- Per-game stats schema. Game plugins write a row here so that other servers
-- in the fleet (notably the lobby) can render /tea stats and the stats GUI
-- without having the game's own jar loaded — they look up the table/columns
-- here and run a generic SELECT against the shared database.
CREATE TABLE IF NOT EXISTS tea_stats_schemas (
  game_id          VARCHAR(64)  NOT NULL PRIMARY KEY,
  display_name_key VARCHAR(128) NOT NULL,
  sql_table        VARCHAR(64)  NOT NULL,
  sql_uuid_column  VARCHAR(64)  NOT NULL DEFAULT 'uuid'
);

CREATE TABLE IF NOT EXISTS tea_stats_schema_columns (
  game_id    VARCHAR(64) NOT NULL,
  ordinal    INT         NOT NULL,
  label      VARCHAR(64) NOT NULL,
  sql_column VARCHAR(64) NOT NULL,
  PRIMARY KEY (game_id, ordinal)
);
