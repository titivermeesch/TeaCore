# TeaCore

A reusable minigame engine for Paper/Spigot servers. TeaCore handles the scaffolding every PvP minigame needs — arena lifecycle, in-game map authoring, kits, abilities, cooldowns, chat routing, stats persistence, i18n, theming, proxy handoff, matchmaking, and an embedded web dashboard — so game plugins can focus on gameplay.

TeaCore is consumed by separate game plugin jars that declare `depend: [TeaCore]` and register a `GameDefinition` at enable time. The hub-side lobby UI (connection signs, `/play`, lobby spawn management) is bundled inside TeaCore behind a `lobby.enabled` config toggle — flip it on for the server(s) you want to act as your lobby and leave it off elsewhere.

Full user and developer documentation: [TeaDocs](https://titivermeesch.github.io/TeaDocs/).

## Requirements

- Java 21
- Paper (or Spigot) **1.20.5** or newer for runtime (first Minecraft version to require Java 21)
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

## Consuming TeaCore from another plugin

Your plugin compiles against the plain `teacore` jar (no shading, no transitive impl deps) and declares TeaCore as a runtime plugin dependency in `plugin.yml`.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven {
        url = uri("https://maven.pkg.github.com/titivermeesch/teacore")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
            password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
        }
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.5-R0.1-SNAPSHOT")
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
        <url>https://maven.pkg.github.com/titivermeesch/teacore</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.spigotmc</groupId>
        <artifactId>spigot-api</artifactId>
        <version>1.20.5-R0.1-SNAPSHOT</version>
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
api-version: "1.20"
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

Full deployment, configuration, and admin documentation lives in [TeaDocs](https://titivermeesch.github.io/TeaDocs/).

## License

MIT. See `LICENSE` in the repository root.
