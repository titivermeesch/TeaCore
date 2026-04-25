CREATE TABLE IF NOT EXISTS tea_kit_unlocks (
  player_uuid  VARCHAR(36) NOT NULL,
  game_id      VARCHAR(64) NOT NULL,
  kit_id       VARCHAR(64) NOT NULL,
  unlocked_at  TIMESTAMP NOT NULL,
  PRIMARY KEY (player_uuid, game_id, kit_id)
);
