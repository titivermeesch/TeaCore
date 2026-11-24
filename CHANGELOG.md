# Changelog

## 0.2.0

Games built for 0.1.x need a rebuild: several API records gained fields (see Developers).

### Arena servers
- Maps, map versions and waiting rooms are stored in the database (`maps.storage: database`, the default). Every server keeps a local copy, downloads changes at startup and when another server saves, and uploads maps copied into the folder by hand. A new arena server needs no map files.
- Settings changed in game or on the dashboard are stored in the database too, and a server that starts later applies them before any game reads its config.
- New `hosting:` section: `games` (`any` or a list) limits which installed games a server runs, and `map` (`default`, `random`, `vote` or a map id, per game under `maps`) sets how arenas pick their map.
- When the lobby asks a server for several arenas at once, it opens them a quarter second apart instead of all in the same tick, so pasting maps doesn't freeze the server.
- A one-match-per-server (`external`) server with several games opens the game the most players are queued for, and switches when its arena sits empty while another game has a queue. Before, it opened an arena for every installed game.

### Fixes
- CloudNet: only servers with `network.eligible-matchmaker: true` start and stop services. Before, a game service could take the matchmaker role, and the lobby then opened no arenas and moved nobody.
- MySQL 8.4 with default settings refused TeaCore's connection ("Public Key Retrieval is not allowed"); TeaCore now connects.

### Match recap and charts
- Every player gets a personal match recap in chat: result, map and length, their kills, deaths, assists, damage and place, the MVP, and their rating change in ranked (`features.match-recap`).
- Match history stores each player's damage and the match's MVP; the dashboard's match page shows both.
- The dashboard has an Activity page with charts of players online, players queued, matches running and matches per day by game.
- Games can let a cosmetics plugin announce kills (`CombatTracker.setKillMessageProvider`).

### Java 17
- Every Tea jar now runs on Java 17 or newer, so 1.17 to 1.20 servers can stay on the Java they use. Servers from 1.13 to 1.19 no longer print hundreds of "Fatal error trying to convert" lines at startup. Building still needs JDK 21.
- On 1.8.8, Paper 1.8.8 runs Tea on Java 17; Spigot 1.8.8 needs `use-native-transport=false`.

### Dashboard and sharing
- A Servers page: every server with its group, version, players, TPS, memory, hosted games, map choice and open arenas.
- Download a map, or every map and waiting room, as a zip, and upload a zip to the whole network. In game: `/tea map export` and `/tea map import-zip`. New permission `tea.dashboard.maps.transfer`.
- Settings can be saved for one group of servers (`network.group`, default `lobby` or `arena`), and the Settings page has a history of changes with undo.
- Menus, sidebars, the theme and language files of TeaCore and every Tea game are shared through the database too (`share-config-files`). `/tea reload` sends local edits to the other servers, and so does a restart after editing a file while the server was off.
- Message keys starting with `literal:` render their text as is, so games can show names typed into the settings, such as a new kit's name.
- The Games page shows game names instead of their language keys.

### Settings in game and on the dashboard
- `/tea admin` has a Settings menu and the dashboard a Settings page for TeaCore, quests, every Tea game and TeaCosmetics: switches, numbers, choices, items, text, and lists such as kits and quests. Kit gear can be copied from your inventory.
- Saving changes only the edited lines of the file, so comments survive, and sends the change to every server that has the same file.
- Plugins add their own settings with a schema in their jar and `TeaCoreAPI.settings().register(...)`.
- New permissions `tea.settings` (part of `tea.admin`) and `tea.dashboard.settings` (part of `tea.dashboard.admin`).

### Menus and sidebars
- YAML inventory menus in `plugins/TeaCore/menus/`: layout patterns, paging, click actions per click type (including shift + left/right), and list areas filled by sources. The kit picker, stats and game shops use them. `/menu <id> [player]` opens any menu.
- A `confirm` menu and `confirm:<question>|<action>` action for "Are you sure?" questions.
- A public sidebar API with per-phase YAML layouts, `@players`/`@teams` rows and PlaceholderAPI in every line. `/board` hides your own sidebar.
- Feature switches in one `features:` block, with per-game overrides.

### Lobby and profile
- Lobby hotbar with Play and Profile items, the `/play` game selector with maps and live arenas, and `/profile` with stats, achievements, match history and kits.
- Match history for every game, with a `match-history.keep-days` retention setting (default 90). Players who leave early stay in the history with result `left`.
- Achievements of games a server doesn't run are listed from the database, so a lobby without the game shows them.

### Placeholders and stats
- More `%tea_...%` placeholders, cached leaderboards (`%tea_top_<game>_<stat>_<n>_name|value%`), and the short names `wins`, `played` and `games` for `games_won` / `games_played`.
- Kill credit and assists in core, team-aware wins, and a draw counts as a game played instead of a loss.
- The sidebar winner value reads "Nobody" after a draw instead of "-".

### Arena manager (`/tea admin`)
- Menus for every arena on every server: skip phase, end with a chosen winner or a draw, release, observe, spectate.
- Per player: kick to the lobby, move to another arena (across servers), make spectator, teleport to them.
- A maps browser to allocate, edit and delete maps.

### Map editor tools
- While editing a map: a tool hotbar (region wand, point tool, checklist, save), coloured particle markers with labels, and click-to-remove.
- A checklist menu with every point type, player limits and save/discard.
- Map extensions are scoped to their game, so one game's required points never block another game's maps.

### Web dashboard
- Sign in with `/dashboard`: a one-time link or code, a 12-hour session cookie, and the admin token as a break-glass login.
- Per-action permissions (`tea.dashboard.*`) with viewer, moderator and admin bundles. The dashboard logs each action with the account name.
- Live arena view: teams, K/D/A, the mirrored sidebar, kill feed and match chat.
- Player actions, end with a winning team, match history with filters and CSV export, player history with streaks, and a sessions page.

### Networks
- Servers sharing a MySQL database no longer race each other's migrations at startup.
- Arena snapshots carry each arena's roster, K/D/A, sidebar and timer, so admin tools work for arenas on other servers.

### Fixes
- `/kit <game> <kit>` no longer selects a priced kit for free.
- No more "unknown config key" warnings for new settings.
- TeaCore credits a one-shot kill when a game fakes the death.

### Developers
- New: `MultiPointExtension`; map editor hooks on `MapExtension` (`points`, `removePoint`, `placePoint`, `pointOnClickedBlock`, `editorIcon`, `markerColor`); `MapExtension.game()` / `appliesTo`.
- `ArenaSnapshot` gained `secondsRemaining`, `roster` and `sidebar`; `RosterEntry` and `SidebarView` records.
- `ControlCommand` gained `teamId`, `targetArenaId` and `mode` and the types `KICK_PLAYER`, `MAKE_SPECTATOR`, `MOVE_PLAYER`, `EXPECT_PLAYER`; use `ControlCommand.of(...)` for the short form.
- `CoordinationEvent` gained `data` and the types `KILL` and `CHAT`.
- `MatchHistoryService`: `search`, `count`, `pruneBefore`; `PlayerMatch` gained `uuid` and `name`.
