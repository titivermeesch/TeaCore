CREATE TABLE IF NOT EXISTS tea_achievements (
  uuid         VARCHAR(36) NOT NULL,
  game_id      VARCHAR(64) NOT NULL,
  achievement  VARCHAR(64) NOT NULL,
  unlocked_at  BIGINT NOT NULL,
  PRIMARY KEY (uuid, game_id, achievement)
);
