# TeaCore

A reusable minigame engine for Paper/Spigot 1.21.4 servers. TeaCore handles the scaffolding every PvP minigame needs — arena lifecycle, in-game map authoring, kits, abilities, cooldowns, chat routing, stats persistence, i18n, theming, proxy handoff, matchmaking, and an embedded web dashboard — so game plugins can focus on gameplay.

TeaCore is consumed by separate game plugin jars (for example TeaLobby and published minigame plugins) that declare `depend: [TeaCore]` and register a `GameDefinition` at enable time.

## Requirements

- Java 21
- Paper (or Spigot) 1.21.4 for runtime
- Node.js + npm (the dashboard SPA is bundled into the jar at build time)

Optional runtime dependencies:
- WorldEdit or FastAsyncWorldEdit — soft-dependency; used for schematic capture/paste when authoring maps and pasting arenas.
- Redis — required only when running a multi-server fleet (`arena-pool.mode: central` or `external`).
- MySQL — production persistence alternative. SQLite is the default and needs no setup.

## Building

```
./gradlew build
```

Produces `build/libs/teacore-<version>-all.jar` — the shadow jar that admins drop into `plugins/`. The Gradle build also compiles the React dashboard (`dashboard/`) and bundles its output onto the plugin classpath.

Run the test suite only:

```
./gradlew test
```

Run the ArchUnit boundary test that enforces the `api → impl` one-way dependency:

```
./gradlew test --tests me.playbosswar.tea.core.ArchitectureTest
```

Generate API Javadoc (public `api/**` packages only):

```
./gradlew apiJavadoc
```

## Publishing

TeaCore publishes two things:

1. A shadow jar on every GitHub Release (tag push), uploaded as a release asset — this is what server admins download.
2. A Maven artifact to GitHub Packages (`me.playbosswar.tea:teacore:<version>`) — this is what game plugin authors compile against.

### Publishing locally

```
./gradlew publishToMavenLocal
```

Downstream projects (TeaLobby, game plugins) can then compile against TeaCore from `~/.m2`.

### Publishing to GitHub Packages

Releases are cut via the `release.yml` workflow (tag a commit `vX.Y.Z`). The workflow sets `GITHUB_TOKEN` automatically.

To publish manually from a workstation, set `GITHUB_ACTOR` + `GITHUB_TOKEN` (a PAT with `write:packages`) in the environment, then:

```
./gradlew -PteacoreVersion=0.1.0 publish
```

## Consuming TeaCore from another plugin

Your plugin compiles against the plain `teacore` jar (no shading, no transitive impl deps) and declares TeaCore as a runtime plugin dependency in `plugin.yml`.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven {
        url = uri("https://maven.pkg.github.com/PlayBossWar/teacore")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
            password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
        }
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("me.playbosswar.tea:teacore:0.1.0")
}
```

GitHub Packages requires authentication for reads even on public packages. Put a PAT with `read:packages` in `~/.gradle/gradle.properties`:

```properties
gpr.user=your-github-username
gpr.key=ghp_xxx_personal_access_token
```

### Maven

```xml
<repositories>
    <repository>
        <id>spigot</id>
        <url>https://hub.spigotmc.org/nexus/content/repositories/snapshots/</url>
    </repository>
    <repository>
        <id>teacore-gh</id>
        <url>https://maven.pkg.github.com/PlayBossWar/teacore</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.spigotmc</groupId>
        <artifactId>spigot-api</artifactId>
        <version>1.21.4-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>me.playbosswar.tea</groupId>
        <artifactId>teacore</artifactId>
        <version>0.1.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

Put GitHub credentials in `~/.m2/settings.xml`:

```xml
<servers>
    <server>
        <id>teacore-gh</id>
        <username>your-github-username</username>
        <password>ghp_xxx_personal_access_token</password>
    </server>
</servers>
```

### Your plugin.yml

```yaml
name: MyGame
main: com.example.MyGamePlugin
version: 1.0.0
api-version: "1.21"
depend: [TeaCore]
```

### Resolving the API from your plugin

```java
import me.playbosswar.tea.core.api.TeaCoreAPI;

@Override
public void onEnable() {
    TeaCoreAPI core = TeaCoreAPI.get();
    core.games().register(...);
}
```

## Installing on a server

1. Put `teacore-<version>-all.jar` into `plugins/`.
2. Restart the server. TeaCore extracts `config.yml`, `theme.yml`, `lang/en_US.yml` on first boot.
3. Install a game plugin that depends on TeaCore.
4. (Optional) Flip `dashboard.enabled: true` on one server to enable the web UI; browse to `http://<host>:8080` with the auto-generated admin token.

Full deployment, configuration, and admin documentation lives in the separate `tea-docs` repo (rendered at the public docs site).

## Project layout

```
teacore/
├── build.gradle.kts             # single-module Gradle build + shadow + publishing
├── settings.gradle.kts
├── gradle/                      # wrapper + version catalog
├── gradlew, gradlew.bat
├── dashboard/                   # React SPA bundled into the jar under /dashboard/
└── src/
    └── main/java/me/playbosswar/tea/core/
        ├── api/                 # public surface; consumers compile against this only
        └── impl/                # internals; hidden behind the api boundary via ArchUnit
```

The `api` / `impl` split is enforced at test time by `ArchitectureTest` — `api` classes must never depend on `impl`.

## License

See the Spigot listing (`SPIGOT_TEACORE.md`) for end-user licensing. The plugin is free for any number of servers.
