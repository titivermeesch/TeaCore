CREATE TABLE IF NOT EXISTS tea_core_stats (
  uuid          VARCHAR(36) NOT NULL PRIMARY KEY,
  games_played  INT NOT NULL DEFAULT 0,
  wins          INT NOT NULL DEFAULT 0,
  losses        INT NOT NULL DEFAULT 0,
  kills         INT NOT NULL DEFAULT 0,
  deaths        INT NOT NULL DEFAULT 0
);
