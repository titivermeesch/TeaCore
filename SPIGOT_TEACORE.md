# TeaCore — The Minigame Engine

> **Build minigames on a battle-tested foundation. Arena lifecycle, maps, kits, abilities, chat routing, stats, i18n, theme, dashboard — solved. Ship the fun part.**

TeaCore is a reusable engine for PvP minigames. It doesn't ship a game of its own — it gives you the scaffolding every minigame needs so you can focus on what makes your game unique.

Pair it with [TeaLobby](link-to-TeaLobby-resource) for hub/matchmaker features, or install one of the **official minigames built on TeaCore** (listed at the bottom).

📚 **Full documentation**: [link-to-docs-site] — installation, three deployment shapes, admin guide, and the complete developer API reference.

---

## Requirements

- Paper (or Spigot) **1.21.4**
- Java **21**

Optional:
- Redis — only for multi-server fleets. Single-server deployments work without it.
- MySQL — for production persistence. SQLite is the default and needs no setup.

---

## What you get

### 🎮 Arena lifecycle
A full state machine for every arena (waiting → countdown → starting → live → ending → reset), with configurable timings per game. Your game plugin plugs in and subscribes to the transitions it cares about.

### 🗺 In-game map authoring
Admins build maps without touching YAML:

```
/tea map create <game> <mapId>
# build + position spawns in the edit world
/tea map save
```

WorldEdit or FastAsyncWorldEdit (installed separately as a standard plugin) drives region capture and paste — the plugin is a soft-dependency, so arenas fall back to empty void worlds if it's missing. Each live arena gets a freshly-pasted copy of the schematic for a guaranteed-clean state per match.

### 🧩 Map extensions framework
Games plug custom per-map data into the same authoring flow — additional spawn points, boss pads, ceremony chambers, whatever they need. The data serializes into the map YAML automatically and games read it back via typed keys. No per-game command prefix; everything stays under `/tea map`.

### 🏟 Warm arena pool with three deployment shapes
Per-game pool sizes keep joinable arenas ready so first players never wait on a schematic paste. One config knob, three shapes:

- **Standalone** — one Paper/Spigot server hosts lobby + games. Pool scales locally.
- **Central** — proxy (BungeeCord or Velocity) + lobby + multiple arena servers. The matchmaker leader balances new arenas across available servers via Redis.
- **External** — one game per pod (k8s HPA-style). Core doesn't scale; your orchestrator does.

```yaml
arena-pool:
  mode: standalone   # or central / external
  games:
    ascend:
      min-available: 2
      max-total: 8
```

### 👕 Kits, abilities, cooldowns
Your game declares kits (icon, description, starter items, ability list). Abilities hook into right-click, left-click, sneak, jump, damage-taken, and bow-release triggers. Cooldowns render on the XP bar, action bar, or boss bar — your pick per ability.

### 💬 Match-scoped chat
Chat is automatically routed to the player's arena. Five channels (match, team, spectator, staff, global) with prefix shortcuts and a `/chat <channel>` switcher. No more kill-feeds leaking between concurrent matches on the same server.

### 📊 Embedded web dashboard
TeaCore ships a full management UI — a React SPA bundled into the plugin jar and served over HTTP from the plugin itself. Every running arena, queue entry, online player, and registered game on one screen. Filter arenas by state, expand to see rosters + match uuid, force-start, release, allocate, end-match, delete maps, reload — all with live push updates over WebSocket. No external hosting, no extra deploy step.

### 🎨 Theme + i18n
A single `theme.yml` defines your server's visual language (primary, accent, success, etc.) as MiniMessage tags. Per-plugin translation bundles resolve to each player's `Player#getLocale()`. Add new lang keys in your jar — admin edits are preserved and new keys are merged into the on-disk file automatically.

### 🗃 Stats + persistence
SQLite by default, MySQL for production — flip one config line. Versioned SQL migrations apply automatically on plugin enable. Games register their own stats tables and expose them via `/tea stats <player>`.

### 🔗 Proxy-ready matchmaking
Queue with `/play <game>` on a lobby server; the matchmaker finds an available arena on any arena server and routes the player via the legacy `BungeeCord` plugin-message channel. Works on BungeeCord AND Velocity — no proxy-side plugin needed.

---

## Commands

All provided by TeaCore:

| Command | What |
| ------- | ---- |
| `/tea help` | Top-level help |
| `/tea arena list\|allocate\|join\|spectate\|observe\|leave\|release\|start` | Manage arenas (admin) |
| `/tea map create\|edit\|pos1\|pos2\|waiting\|addspawn\|clearspawns\|min\|max\|save\|discard\|list\|info\|reload\|delete\|status` | Author maps (admin) |
| `/tea game list` | Registered games |
| `/tea stats [player]` | Per-player stats across all games |
| `/tea reload` | Reload config + theme + locale bundles |
| `/tea version` | Version info |
| `/kit [game] [kitId]` | Kit selector / picker |
| `/chat <channel>` | Switch chat channel |

Games can register their own `/tea map <subcommand>` extensions — e.g. Ascend adds `/tea map evolutionviewer` and `/tea map evolutionmob` for its ceremony spawns. One prefix, any number of games.

---

## Web dashboard

Enable once, run anywhere:

```yaml
dashboard:
  enabled: true             # set on EXACTLY one server (usually the lobby)
  bind-address: "0.0.0.0"
  port: 8080
  admin-token: ""           # auto-generated on first boot
```

Point your browser at `http://<lobby-host>:8080`, paste the token TeaCore wrote into your `config.yml`, and you see every arena, queue, and player across the fleet. Control actions (force-start / release / end-match / allocate / reload maps / delete maps) travel over Redis pub/sub to the owning server — so the lobby's dashboard can drive arenas hosted on separate pods.

Every action requires the admin token; there are no player-facing endpoints.

---

## Who needs TeaCore?

**Everyone running a Tea minigame.** TeaCore is a required dependency of every game in the Tea family — if you want to run [Ascend](link-to-Ascend-resource) (or any other Tea game) on your server, you install TeaCore first, then the game plugin on top.

**Developers building new minigames.** TeaCore gives you the scaffolding every PvP minigame needs so you don't have to reinvent arenas, waiting rooms, kit pickers, per-arena chat, map storage, stats persistence, i18n, theming, proxy handoff, matchmaking queues, or a web dashboard. Dev docs walk you through building a game end-to-end: [link-to-docs-site].

Server owners who only want to play: follow the Install section below, then grab one of the minigame plugins built on TeaCore (list at the bottom).

---

## Install

1. Drop `TeaCore-x.y.z-all.jar` into `plugins/`.
2. Restart. TeaCore extracts `config.yml`, `theme.yml`, `lang/en_US.yml`.
3. Pick your deployment shape:
   - **Standalone** → defaults work. Go to step 4.
   - **Central / External** → edit `config.yml` to set `arena-pool.mode`, point `redis.host` + `database.mysql.*` at your shared infra. See [link-to-docs-site] for the full per-mode setup.
4. Install one of the official Tea minigame plugins — or your own — that depends on TeaCore.
5. (Optional) Flip `dashboard.enabled: true` on one server to enable the web UI.

TeaCore provides nothing user-facing on its own — it's infrastructure for games.

---

## Official minigames built on TeaCore

- [**Ascend**](link-to-Ascend-resource) — classic evolution-style ladder fights. 14 configurable stages, mob disguises, per-player evolution ceremony. _Available now._
- _More in development — watch this space._

Building your own? The developer documentation walks you through it end-to-end: [link-to-docs-site].

---

## License

**TeaCore is free.** No cost to download, no cost to use, no limits on server count.

- Use it on as many servers as you like.
- Build your own minigame plugins against the TeaCore API and ship them however you want — free, commercial, private, whatever fits.
- Updates are free for the lifetime of the Minecraft version this resource targets (1.21.x).

**Not permitted:**

- Redistribution of the plugin jar or source on any platform other than this Spigot listing. Link to the resource page instead.
- Shading or bundling TeaCore into another jar.

A credit line in your plugin's description ("powered by TeaCore") is appreciated but not required.

Support: [Discord / email — fill in before posting]. I answer config questions, bug reports, and reasonable feature requests for TeaCore itself. Debugging your unrelated plugin conflicts or writing custom code for your game is out of scope.

---

## Pairs well with

- [**TeaLobby**](link-to-TeaLobby-resource) — hub features: connection signs, `/play`, queue, spawn management

---

## Credits

Adventure + MiniMessage by KyoriPowered · HikariCP · Lettuce · Caffeine · SQLite JDBC · MySQL Connector/J · FastAsyncWorldEdit · Javalin · Jackson · React · Tailwind.
