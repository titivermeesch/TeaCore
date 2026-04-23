CREATE TABLE IF NOT EXISTS tea_schema_versions (
  plugin_name VARCHAR(64) NOT NULL,
  version     INT NOT NULL,
  applied_at  TIMESTAMP NOT NULL,
  PRIMARY KEY (plugin_name, version)
);

CREATE TABLE IF NOT EXISTS tea_player_profile (
  uuid          VARCHAR(36) NOT NULL PRIMARY KEY,
  first_seen    TIMESTAMP NOT NULL,
  last_seen     TIMESTAMP NOT NULL,
  locale        VARCHAR(16),
  metadata_json TEXT
);
