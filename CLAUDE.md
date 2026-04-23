# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

TeaCore is a single-module Gradle project that produces a Paper/Spigot 1.21.4 plugin plus a published Maven artifact.

- The plugin jar (`build/libs/teacore-<ver>-all.jar`) is dropped into `plugins/` on a server.
- The plain jar (`build/libs/teacore-<ver>.jar`) is what other plugin projects compile against from GitHub Packages.

Downstream plugins (TeaLobby, game plugins) live in separate repos and consume TeaCore through Maven coordinates: `me.playbosswar.tea:teacore:<version>`.

## Architecture

Source is split into two top-level packages under `me.playbosswar.tea.core`:

- `api.**` — public surface. Consumers compile against this.
- `impl.**` — internals. Hidden from consumers; callers resolve services via `TeaCoreAPI.get()` and never import impl classes directly.

`ArchitectureTest` (ArchUnit) enforces that `api` must never depend on `impl`. Any new public type belongs under `api/`; any new implementation belongs under `impl/`.

Subsystems follow a consistent pattern — `api/<name>/` holds the interface(s) and DTOs, `impl/<name>/` holds the service implementation. Major subsystems: `arena`, `chat`, `cooldown`, `coordination`, `effect`, `game`, `kit`, `map`, `message`, `persistence`, `profile`, `stats`, `team`, `theme`, `transport`, plus `command/` and `config/` and `dashboard/` under impl only.

`TeaCoreAPI` (api) is the root facade; `TeaCorePlugin#onEnable` populates the singleton. Downstream plugins call `TeaCoreAPI.get()` from their own `onEnable`.

## Build

```
./gradlew build                 # compile + test + shadowJar (bundles dashboard SPA)
./gradlew test                  # JUnit 5, includes ArchitectureTest
./gradlew publishToMavenLocal   # makes the artifact available for local lobby/ascend builds
./gradlew apiJavadoc            # generates API-only javadoc
```

A `build` invocation runs `dashboardBuild` (`npm ci && npm run build` in `dashboard/`) first and copies the Vite output to the plugin classpath under `/dashboard/`. Javalin serves it at runtime.

## Shadow / relocation invariants

- All shaded deps (`net.kyori`, `hikari`, `lettuce`, `netty`, `reactor`, `reactivestreams`, `caffeine`, `javalin`, `jetty`, `jackson`, `annotations`, `kotlin`) are relocated under `me.playbosswar.tea.core.libs.*` to prevent classloader conflicts with other plugins on the same server.
- **Never relocate `org.sqlite.*`.** The SQLite JDBC driver ships a native `.so` whose JNI symbols bind to the original package name; relocating breaks at connect time with `UnsatisfiedLinkError`.
- MySQL connector is intentionally left unrelocated for symmetry with SQLite and low conflict risk.

## Dashboard

The React SPA in `dashboard/` is built by Vite and bundled onto the plugin classpath. `processResources` depends on `dashboardBuild`. To iterate on the dashboard without rebuilding the jar:

```
cd dashboard
npm run dev   # Vite dev server on :5173, proxies /api to localhost:8080
```

## Publishing

- `publishToMavenLocal` for local iteration.
- `publish` with `GITHUB_ACTOR` + `GITHUB_TOKEN` env (or `gpr.user` + `gpr.key` gradle properties) pushes to GitHub Packages.
- Version is driven by the `teacoreVersion` gradle property; defaults to `0.1.0-SNAPSHOT`.

The `release.yml` CI workflow triggers on `v*` tags, publishes the artifact, and uploads the shadow jar to the GitHub Release.

## Dev environment

Local multi-server docker-compose lives in a sibling `../dev/` directory (not part of this repo). A root-level `../run-dev.sh` script builds this repo + the sibling `tealobby` + `ascend` repos and copies the shadow jars into `dev/backend-*/data/plugins/`.

## Coding conventions

- UTF-8, JUnit Platform, Spigot API compileOnly (no NMS, no Paper-only APIs — runs on Spigot, Paper, Purpur, any Paper fork).
- New public types go under `api/`. Never import `impl.*` from `api/` (ArchUnit fails the build).
- When adding a service: interface + DTOs in `api/<name>/`, implementation in `impl/<name>/`, wire it into `TeaCorePlugin#onEnable` and expose a getter on `TeaCoreAPI`.
