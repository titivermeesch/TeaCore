-- One row a minute from the server running the dashboard, for its charts.
CREATE TABLE IF NOT EXISTS tea_dashboard_samples (
  at      BIGINT NOT NULL PRIMARY KEY,
  players INT    NOT NULL,
  queued  INT    NOT NULL,
  arenas  INT    NOT NULL,
  live    INT    NOT NULL
);
