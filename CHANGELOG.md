# Changelog

## 0.2.0

Games built for 0.1.x need a rebuild: several API records gained fields (see Developers).

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
