-- Ranked ratings (Elo), one row per player per game.
CREATE TABLE IF NOT EXISTS tea_ratings (
  player_uuid CHAR(36)    NOT NULL,
  game_id     VARCHAR(64) NOT NULL,
  player_name VARCHAR(16) NOT NULL,
  rating      INT         NOT NULL,
  games       INT         NOT NULL DEFAULT 0,
  wins        INT         NOT NULL DEFAULT 0,
  losses      INT         NOT NULL DEFAULT 0,
  updated_at  BIGINT      NOT NULL,
  PRIMARY KEY (player_uuid, game_id)
);
CREATE INDEX idx_tea_ratings_board ON tea_ratings (game_id, rating);
