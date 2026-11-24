-- Maps, map versions and waiting rooms, shared by every server. tea_files lists
-- each file under plugins/TeaCore/maps and waiting-rooms with the hash of its
-- content; the content sits once per hash in tea_file_chunks, split in parts so
-- no single statement exceeds a small max_allowed_packet.
CREATE TABLE IF NOT EXISTS tea_files (
  path       VARCHAR(255) NOT NULL PRIMARY KEY,
  hash       VARCHAR(64)  NOT NULL,
  size       BIGINT       NOT NULL,
  updated_at BIGINT       NOT NULL,
  updated_by VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS tea_file_chunks (
  hash VARCHAR(64) NOT NULL,
  part INT         NOT NULL,
  data LONGBLOB    NOT NULL,
  PRIMARY KEY (hash, part)
);

-- Settings staff changed in /tea admin or the dashboard. A new server writes
-- them into its config files at startup, before the games read them. scope is
-- empty for every server, or a server group (network.group) the value is for.
CREATE TABLE IF NOT EXISTS tea_settings (
  file_id    VARCHAR(64)  NOT NULL,
  scope      VARCHAR(64)  NOT NULL DEFAULT '',
  path       VARCHAR(255) NOT NULL,
  plugin     VARCHAR(64)  NOT NULL,
  file       VARCHAR(255) NOT NULL,
  value      MEDIUMTEXT,
  updated_at BIGINT       NOT NULL,
  updated_by VARCHAR(64),
  PRIMARY KEY (file_id, scope, path)
);

-- Every settings change with the value before it, for the history page and undo.
CREATE TABLE IF NOT EXISTS tea_settings_history (
  id          VARCHAR(36)  NOT NULL PRIMARY KEY,
  file_id     VARCHAR(64)  NOT NULL,
  scope       VARCHAR(64)  NOT NULL DEFAULT '',
  path        VARCHAR(255) NOT NULL,
  old_value   MEDIUMTEXT,
  new_value   MEDIUMTEXT,
  at          BIGINT       NOT NULL,
  actor       VARCHAR(64),
  reverted_by VARCHAR(36)
);

CREATE INDEX idx_tea_settings_history_at ON tea_settings_history (at);
