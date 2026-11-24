-- Quest progress per player, quest and period (d2026-11-20 or w2026-47).
CREATE TABLE IF NOT EXISTS tea_quest_progress (
  player_uuid CHAR(36)    NOT NULL,
  period_key  VARCHAR(16) NOT NULL,
  quest_id    VARCHAR(64) NOT NULL,
  progress    INT         NOT NULL,
  completed   INT         NOT NULL DEFAULT 0,
  updated_at  BIGINT      NOT NULL,
  PRIMARY KEY (player_uuid, period_key, quest_id)
);
