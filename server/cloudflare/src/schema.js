// Tables of the Onix server (also in ../schema.sql). Created on the first request, so no setup step is needed.
export default [
  "CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
  "CREATE TABLE IF NOT EXISTS accounts (\n  uuid TEXT PRIMARY KEY, name TEXT NOT NULL, coins INTEGER NOT NULL DEFAULT 0,\n  play_ms INTEGER NOT NULL DEFAULT 0, pending_ms INTEGER NOT NULL DEFAULT 0, last_play INTEGER NOT NULL DEFAULT 0,\n  created INTEGER NOT NULL)",
  "CREATE INDEX IF NOT EXISTS accounts_name ON accounts (name COLLATE NOCASE)",
  "CREATE TABLE IF NOT EXISTS owned (uuid TEXT NOT NULL, item TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY (uuid, item))",
  "CREATE TABLE IF NOT EXISTS gifts (\n  id TEXT PRIMARY KEY, to_uuid TEXT NOT NULL, from_uuid TEXT NOT NULL, from_name TEXT NOT NULL,\n  item TEXT NOT NULL, message TEXT NOT NULL, at INTEGER NOT NULL, seen INTEGER NOT NULL DEFAULT 0)",
  "CREATE INDEX IF NOT EXISTS gifts_to ON gifts (to_uuid, seen)",
  "CREATE TABLE IF NOT EXISTS online (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, seen INTEGER NOT NULL)",
  "CREATE TABLE IF NOT EXISTS challenges (uuid TEXT PRIMARY KEY, server_id TEXT NOT NULL, name TEXT NOT NULL, expires INTEGER NOT NULL)"
];
