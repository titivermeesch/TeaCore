-- One row per finished match, plus one row per participant.
CREATE TABLE IF NOT EXISTS tea_match_history (
  match_id         VARCHAR(36) NOT NULL PRIMARY KEY,
  game_id          VARCHAR(64) NOT NULL,
  map_id           VARCHAR(64),
  server_id        VARCHAR(64),
  started_at       TIMESTAMP NULL,
  ended_at         TIMESTAMP NOT NULL,
  duration_seconds INT NOT NULL DEFAULT 0,
  winner_team      VARCHAR(64),
  winner_uuid      VARCHAR(36)
);

CREATE TABLE IF NOT EXISTS tea_match_players (
  match_id VARCHAR(36) NOT NULL,
  uuid     VARCHAR(36) NOT NULL,
  team     VARCHAR(64),
  kills    INT NOT NULL DEFAULT 0,
  deaths   INT NOT NULL DEFAULT 0,
  assists  INT NOT NULL DEFAULT 0,
  result   VARCHAR(8) NOT NULL,
  PRIMARY KEY (match_id, uuid)
);

CREATE INDEX idx_tea_match_players_uuid ON tea_match_players (uuid);
