CREATE TABLE IF NOT EXISTS tea_lobby_signs (
  world      VARCHAR(64) NOT NULL,
  x          INT NOT NULL,
  y          INT NOT NULL,
  z          INT NOT NULL,
  game_id    VARCHAR(64) NOT NULL,
  map_filter VARCHAR(64),
  PRIMARY KEY (world, x, y, z)
);
