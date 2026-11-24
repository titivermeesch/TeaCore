-- Player names in match history, so the dashboard can filter by name.
ALTER TABLE tea_match_players ADD COLUMN name VARCHAR(16);

CREATE INDEX idx_tea_match_history_ended ON tea_match_history (ended_at);

-- One-time dashboard login codes from /tea dashboard login. permissions is a
-- comma-separated list of the dashboard nodes the player had when asking.
CREATE TABLE IF NOT EXISTS tea_dashboard_logins (
  code        VARCHAR(32) NOT NULL PRIMARY KEY,
  uuid        VARCHAR(36) NOT NULL,
  name        VARCHAR(16) NOT NULL,
  permissions TEXT NOT NULL,
  expires_at  TIMESTAMP NOT NULL
);
