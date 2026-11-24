-- Damage per player and the match's MVP, for the recap and the dashboard.
ALTER TABLE tea_match_players ADD COLUMN damage INT NOT NULL DEFAULT 0;
ALTER TABLE tea_match_history ADD COLUMN mvp_uuid VARCHAR(36);
