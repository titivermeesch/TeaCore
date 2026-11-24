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
-- them into its config files at startup, before the games read them.
CREATE TABLE IF NOT EXISTS tea_settings (
  file_id    VARCHAR(64)  NOT NULL,
  path       VARCHAR(255) NOT NULL,
  plugin     VARCHAR(64)  NOT NULL,
  file       VARCHAR(255) NOT NULL,
  value      MEDIUMTEXT,
  updated_at BIGINT       NOT NULL,
  updated_by VARCHAR(64),
  PRIMARY KEY (file_id, path)
);
