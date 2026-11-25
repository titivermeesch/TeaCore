-- Values every server on the network shares. "secret" signs the messages
-- servers send each other through Redis; the first server to start writes it.
CREATE TABLE IF NOT EXISTS tea_network (
  name  VARCHAR(64)  NOT NULL PRIMARY KEY,
  value VARCHAR(255) NOT NULL
);
