# TeaCore

TeaCore is a minigame engine for Paper (or Spigot) servers. It runs arenas and gives you in-game map authoring, kits, abilities, cooldowns, chat channels, stats, translations, theming, proxy handoff, matchmaking and a web dashboard. Your game plugin only has to add the gameplay.

Game plugins ship as separate jars. Each one declares `depend: [TeaCore]` and registers a `GameDefinition` when it enables. The lobby UI (connection signs, `/play`, lobby spawn management) is part of TeaCore: set `lobby.enabled` to true on the server(s) you use as a lobby and leave it off on the others.

Full user and developer documentation: [TeaDocs](https://titivermeesch.github.io/TeaDocs/).

## Requirements

- Java 21
- Paper (or Spigot) from **1.8.8** to the latest release. The same jar runs on all of them: TeaCore picks a 1.8-1.12 or a 1.13+ platform at startup.
- [Vault](https://www.spigotmc.org/resources/vault.34315/) (hard dependency)

Soft dependencies, all optional:
- WorldEdit or FastAsyncWorldEdit. You install it yourself; TeaCore doesn't bundle it. Maps need it on 1.13+. On 1.8-1.12 TeaCore reads and writes MCEdit `.schematic` files itself.
- PlaceholderAPI
- floodgate, for Bedrock forms through Geyser
- Parties or Spigot-Party-API-PAF

Other services:
- Redis, for multi-server setups (`arena-pool.mode: central` or `external`).
- MySQL, if you don't want the default SQLite database (SQLite needs no setup).
- CloudNet v3, if you run TeaCore as a CloudNet service. See [CloudNet](https://titivermeesch.github.io/TeaDocs/docs/tea/user/cloudnet).

## Building

You need JDK 21 (with `JAVA_HOME` pointing at it) and Node.js + npm, because the build compiles the React dashboard (`dashboard/`) into the jar.

```
./gradlew build
```

The build writes two jars to `build/libs/`:
- `teacore-<version>-all.jar`, the shadow jar you drop into a server's `plugins/` folder.
- `teacore-<version>.jar`, the plain jar other plugins compile against.

The code is split into source sets:
- `main`: shared code, compiled against the Spigot 1.8.8 API.
- `modern` (`src/modern/java`): code for 1.13+.
- `legacy` (`src/legacy/java`): code for 1.8-1.12.
- `cloudnet3` (`src/cloudnet3/java`): the CloudNet v3 integration. It compiles against the CloudNet 3.4.5 jars in `libs/cloudnet-v3/`, since v3 isn't on any Maven repository.

`./gradlew check` runs the tests plus `modernCheck`. That task compiles the shared code a second time against the modern API, so it fails if `main` uses a call that only one of the two APIs has.

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

The current version is 0.2.0 (`0.2.0-SNAPSHOT` in the build). Your plugin compiles against the plain `teacore` jar (no shading, no transitive impl deps) and lists TeaCore as a dependency in `plugin.yml`.

Compile your game against `org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT` and exclude the `net.md-5` group, because its old bungeecord-chat snapshot no longer resolves. Add a `modernCheck` task like TeaCore's so you only use calls that exist on both old and new servers. For anything version-specific (items, sounds, particles, tags, holograms, attributes and so on), go through `core.platform()`. The [platform page](https://titivermeesch.github.io/TeaDocs/docs/tea/dev/platform) has the full Gradle setup.

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
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT") { exclude(group = "net.md-5") }
    compileOnly("me.playbosswar.tea:teacore:0.2.0")
}
```

GitHub Packages wants authentication for reads, even on public packages. Put a PAT with `read:packages` in `~/.gradle/gradle.properties`:

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
        <version>1.8.8-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
        <exclusions>
            <exclusion>
                <groupId>net.md-5</groupId>
                <artifactId>*</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
    <dependency>
        <groupId>me.playbosswar.tea</groupId>
        <artifactId>teacore</artifactId>
        <version>0.2.0</version>
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

Keep `api-version: "1.20"`. Servers on 1.8 ignore it, and newer servers need it or they load your plugin in legacy-material mode.

```yaml
name: MyGame
main: com.example.MyGamePlugin
version: 1.0.0
api-version: "1.20"
depend: [TeaCore]
```

### Resolving the API from your plugin

Check the TeaCore version first, then register your game with `core.games().register(definition)`:

```java
import me.playbosswar.tea.core.api.TeaCoreAPI;

@Override
public void onEnable() {
    TeaCoreAPI core = TeaCoreAPI.get();
    if (!core.requireVersion(this, ">=0.2.0")) return;
    core.games().register(definition);
}
```

## Installing on a server

1. Put `teacore-<version>-all.jar` and Vault into `plugins/`.
2. Restart the server. On first start TeaCore writes `config.yml`, `theme.yml`, `sidebar.yml`, `quests.yml`, `lang/en_US.yml` and the `menus/` folder.
3. Install a game plugin that depends on TeaCore.
4. (Optional) Set `dashboard.enabled: true` on one server and open `http://<host>:8080`. Staff sign in by running `/dashboard` in game, which gives them a one-time code or link. The admin token in `config.yml` is for scripts and break-glass access.

[TeaDocs](https://titivermeesch.github.io/TeaDocs/) covers deployment, configuration and administration in full.

## License

MIT. See `LICENSE` in the repository root.
