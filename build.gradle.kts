import java.security.MessageDigest

plugins {
    java
    `maven-publish`
    alias(libs.plugins.shadow)
}

group = "me.playbosswar.tea"
version = (project.findProperty("teacoreVersion") as String?) ?: "0.2.0-SNAPSHOT"

// Spigot API version driving compileOnly / testImplementation. Defaults to the
// lowest supported release (the first Minecraft version that requires Java 21)
// so Maven-published artifacts have the widest server compatibility. CI
// overrides this in a matrix to also verify the build against the latest
// release.
val spigotApiVersion = (project.findProperty("spigotApiVersion") as String?) ?: "1.20.5-R0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.faststats.dev/releases")
    maven("https://repo.extendedclip.com/releases/")
    // VaultAPI is published via JitPack (hard runtime dep; shipped by the
    // server admin, not shaded into our jar).
    maven("https://jitpack.io")
    // Floodgate API + Cumulus forms (soft dependency: Bedrock menus as forms).
    maven("https://repo.opencollab.dev/main/")
    // Party plugin APIs (soft dependencies: parties queue together).
    maven("https://repo.alessiodp.com/releases/")
    // Libby (runtime library loader) is only published as snapshots.
    maven("https://repo.alessiodp.com/snapshots/") {
        mavenContent { includeGroup("com.alessiodp.libby") }
    }
    maven("https://simonsator.de/repo/")
}

// --- Version support ---------------------------------------------------------
// TeaCore runs from 1.8.8 to the latest release, on plain Spigot or any fork.
//   main    shared code. Compiled against the 1.8.8 API, and `modernCheck`
//           compiles it again against the modern API: it may only use what
//           both have.
//   modern  src/modern/java: the platform for 1.13+ servers (modern API only).
//   legacy  src/legacy/java: the platform for 1.8-1.12 servers (1.8.8 API).
//   cloudnet3 src/cloudnet3/java: the CloudNet v3 integration, used only when
//           the server runs as a CloudNet v3 service.
// The plugin jar holds all of them; Platforms picks one at startup.
val legacyApiDep = "org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT"
val modernApiDep = "org.spigotmc:spigot-api:$spigotApiVersion"
val shared: Configuration by configurations.creating
configurations.compileOnly { extendsFrom(shared) }

// --- Libraries ---------------------------------------------------------------
// Small libraries are shaded. The large ones are downloaded from Maven Central
// at first start (LibraryLoader), each group only when its feature is in use:
//   core       always (settings, webhooks)
//   redis      network.mode is not standalone
//   dashboard  dashboard.enabled
//   mysql      database.type is mysql
// The build writes their coordinates, SHA-256 and relocations to
// teacore-libraries.txt; the loader relocates them to the same packages the
// shadow jar uses for TeaCore's own references.
val shadedLibs: Configuration by configurations.creating
val libraryGroups = linkedMapOf(
    "core" to configurations.create("coreLibs"),
    "redis" to configurations.create("redisLibs"),
    "dashboard" to configurations.create("dashboardLibs"),
    "mysql" to configurations.create("mysqlLibs"),
)
configurations.implementation { extendsFrom(shadedLibs, *libraryGroups.values.toTypedArray()) }
(listOf(shadedLibs) + libraryGroups.values).forEach {
    it.isCanBeConsumed = false
    it.shouldResolveConsistentlyWith(configurations.runtimeClasspath.get())
}
val libsPackage = "me.playbosswar.tea.core.libs"
val downloadedRelocations = linkedMapOf(
    "io.lettuce" to "lettuce",
    "io.netty" to "netty",
    "reactor" to "reactor",
    "org.reactivestreams" to "reactivestreams",
    "io.javalin" to "javalin",
    "org.eclipse.jetty" to "jetty",
    "com.fasterxml.jackson" to "jackson",
    "org.jetbrains.annotations" to "annotations",
    "kotlin" to "kotlin",
).mapValues { "$libsPackage.${it.value}" }

val libraryManifest = tasks.register("libraryManifest") {
    description = "Lists the libraries TeaCore downloads at startup."
    val out = layout.buildDirectory.file("generated/library-manifest/teacore-libraries.txt")
    inputs.files(libraryGroups.values, shadedLibs)
    inputs.property("relocations", downloadedRelocations)
    outputs.file(out)
    doLast {
        fun ResolvedArtifact.key() = moduleVersion.id.let { "${it.group}:${it.name}:${it.version}" } +
            (classifier?.let { ":$it" } ?: "")
        val shadedKeys = shadedLibs.resolvedConfiguration.resolvedArtifacts.map { it.key() }.toSet()
        val features = linkedMapOf<String, MutableSet<String>>()
        val files = mutableMapOf<String, File>()
        libraryGroups.forEach { (feature, conf) ->
            conf.resolvedConfiguration.resolvedArtifacts
                .filter { it.extension == "jar" && it.key() !in shadedKeys }
                .forEach {
                    features.getOrPut(it.key()) { linkedSetOf() } += feature
                    files[it.key()] = it.file
                }
        }
        val lines = mutableListOf("# feature(s) group:artifact:version[:classifier] sha256")
        downloadedRelocations.forEach { (from, to) -> lines += "relocate $from $to" }
        features.forEach { (key, groups) ->
            val sha = MessageDigest.getInstance("SHA-256")
                .digest(files.getValue(key).readBytes()).joinToString("") { "%02x".format(it) }
            val group = if ("core" in groups) "core" else groups.joinToString(",")
            lines += "library $group $key $sha"
        }
        out.get().asFile.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}
sourceSets {
    create("modern") { java.srcDir("src/modern/java") }
    create("legacy") { java.srcDir("src/legacy/java") }
    create("cloudnet3") { java.srcDir("src/cloudnet3/java") }
}
listOf("modern", "legacy").forEach { set ->
    configurations["${set}CompileOnly"].extendsFrom(shared)
    configurations["${set}Implementation"].extendsFrom(configurations.implementation.get())
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    // Compiles for Java 17; some compile-only APIs (WorldEdit 7.3) declare Java 21.
    disableAutoTargetJvm()
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<ProcessResources>().configureEach {
    // Without this, Gradle keeps an old plugin.yml after a version change.
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

dependencies {
    // Its old bungeecord-chat snapshot no longer resolves; Tea never uses it.
    compileOnly(legacyApiDep) { exclude(group = "net.md-5") }
    "modernCompileOnly"(modernApiDep)
    "modernCompileOnly"(sourceSets.main.get().output)
    "legacyCompileOnly"(legacyApiDep) { exclude(group = "net.md-5") }
    "legacyCompileOnly"(sourceSets.main.get().output)
    // CloudNet v3.4.5 is no longer published to any Maven repository; the API
    // jars (Apache-2.0) come from its release zip. CloudNet provides them at runtime.
    "cloudnet3CompileOnly"(legacyApiDep) { exclude(group = "net.md-5") }
    "cloudnet3CompileOnly"(sourceSets.main.get().output)
    "cloudnet3CompileOnly"(files("libs/cloudnet-v3/wrapper.jar", "libs/cloudnet-v3/cloudnet-bridge.jar"))
    shared(libs.worldedit.bukkit) { isTransitive = false }
    shared(libs.worldedit.core) { isTransitive = false }
    shared("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    shared("me.clip:placeholderapi:2.11.6")
    shared("org.geysermc.floodgate:api:2.2.4-SNAPSHOT") { isTransitive = false }
    shared("org.geysermc.cumulus:cumulus:1.1.2") { isTransitive = false }
    shared("com.alessiodp.parties:parties-api:3.2.17") { isTransitive = false }
    shared("de.simonsator:spigot-party-api-for-party-and-friends:1.0.7-RELEASE") { isTransitive = false }
    shared("de.simonsator:Party-and-Friends-MySQL-Edition-Spigot-API:1.6.2-RELEASE") { isTransitive = false }
    // Item, sound and particle names across versions (shaded, relocated).
    shadedLibs("com.github.cryptomorin:XSeries:14.0.0")

    shadedLibs(libs.adventure.api)
    shadedLibs(libs.adventure.minimessage)
    shadedLibs(libs.adventure.platform.bukkit)
    shadedLibs(libs.hikari)
    shadedLibs(libs.caffeine)
    shadedLibs(libs.libby.bukkit) { isTransitive = false }
    shadedLibs(libs.libby.core)
    // FastStats — error tracking + custom metrics, shaded with relocation so
    // it can't collide with another plugin's copy.
    shadedLibs("dev.faststats.metrics:bukkit:0.22.0")
    "coreLibs"(libs.jackson.databind)
    "redisLibs"(libs.lettuce)
    "dashboardLibs"(libs.javalin)
    "mysqlLibs"(libs.mysql.connector) { exclude(group = "com.google.protobuf") }
    compileOnly(libs.sqlite.jdbc)
    testImplementation(libs.sqlite.jdbc)

    testImplementation(modernApiDep)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.assertj)
}

// Build the React dashboard SPA and bundle the output onto the plugin classpath
// under /dashboard/. Javalin serves it at runtime.
val dashboardDir = layout.projectDirectory.dir("dashboard")
val dashboardDist = dashboardDir.dir("dist")

val dashboardBuild = tasks.register<Exec>("dashboardBuild") {
    group = "build"
    description = "Runs the dashboard SPA npm build."
    workingDir = dashboardDir.asFile
    commandLine("sh", "-c", "npm ci && npm run build")
    inputs.file(dashboardDir.file("package.json"))
    inputs.file(dashboardDir.file("package-lock.json"))
    inputs.dir(dashboardDir.dir("src"))
    inputs.file(dashboardDir.file("index.html"))
    inputs.file(dashboardDir.file("vite.config.ts"))
    inputs.file(dashboardDir.file("tsconfig.json"))
    outputs.dir(dashboardDist)
}

tasks.named<Copy>("processResources") {
    dependsOn(dashboardBuild)
    from(libraryManifest)
    from(dashboardDist) {
        into("dashboard")
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// The shared code must also compile against the modern API (no 1.8-only calls).
val modernCheck by tasks.registering(JavaCompile::class) {
    description = "Compiles the shared code against the modern API too."
    group = "verification"
    source = sourceSets.main.get().java
    classpath = shared + configurations["compileClasspath"].filter { !it.name.let { n -> n.startsWith("spigot-api-1.8.8") } } +
        configurations.detachedConfiguration(dependencies.create(modernApiDep))
    destinationDirectory.set(layout.buildDirectory.dir("modern-check"))
    options.release.set(17)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000"))
}
tasks.named("check") { dependsOn(modernCheck) }
tasks.jar {
    from(sourceSets["modern"].output, sourceSets["legacy"].output, sourceSets["cloudnet3"].output)
}

tasks.shadowJar {
    archiveClassifier.set("all")
    configurations = listOf(shadedLibs)
    mergeServiceFiles()
    from(sourceSets["modern"].output, sourceSets["legacy"].output, sourceSets["cloudnet3"].output)

    val base = libsPackage
    relocate("net.kyori", "$base.kyori")
    relocate("com.zaxxer.hikari", "$base.hikari")
    relocate("com.github.benmanes.caffeine", "$base.caffeine")
    relocate("dev.faststats", "$base.faststats")
    relocate("com.cryptomorin.xseries", "$base.xseries")
    relocate("com.alessiodp.libby", "$base.libby")
    // The downloaded libraries are not in this jar; this rewrites TeaCore's references to them.
    downloadedRelocations.forEach { (from, to) -> relocate(from, to) }
    exclude("**/*.js.map")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.register<Javadoc>("apiJavadoc") {
    description = "Generates Javadoc for the public TeaCore API (api/** packages)."
    group = "documentation"
    source = sourceSets.main.get().java
    include("me/playbosswar/tea/core/api/**/*.java")
    classpath = sourceSets.main.get().compileClasspath
    setDestinationDir(layout.buildDirectory.dir("docs/api-javadoc").get().asFile)
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        windowTitle = "TeaCore API"
        docTitle = "TeaCore API"
    }
}

// Publishing. Downstream plugins (tealobby, ascend) compile against the plain
// jar; the shadow jar is the runtime artifact you drop into plugins/.
publishing {
    publications {
        create<MavenPublication>("teacore") {
            artifactId = "teacore"
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri(
                (project.findProperty("teacoreRepoUrl") as String?)
                    ?: "https://maven.pkg.github.com/titivermeesch/teacore"
            )
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: (project.findProperty("gpr.user") as String?)
                password = System.getenv("GITHUB_TOKEN")
                    ?: (project.findProperty("gpr.key") as String?)
            }
        }
    }
}
