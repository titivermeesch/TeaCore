-- Achievement definitions, written by each server that runs the game, so a
-- lobby without the game's plugin can still list them. name and description
-- are already rendered in the server's default locale.
CREATE TABLE IF NOT EXISTS tea_achievement_defs (
  game_id     VARCHAR(64) NOT NULL,
  achievement VARCHAR(64) NOT NULL,
  icon        VARCHAR(64) NOT NULL,
  name        VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  position    INT NOT NULL DEFAULT 0,
  PRIMARY KEY (game_id, achievement)
);
