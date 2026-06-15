plugins {
    java
    `maven-publish`
    alias(libs.plugins.shadow)
}

group = "me.playbosswar.tea"
version = (project.findProperty("teacoreVersion") as String?) ?: "0.1.0-SNAPSHOT"

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
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<ProcessResources>().configureEach {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:$spigotApiVersion")
    compileOnly(libs.worldedit.bukkit) { isTransitive = false }
    compileOnly(libs.worldedit.core) { isTransitive = false }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation(libs.adventure.api)
    implementation(libs.adventure.minimessage)
    implementation(libs.adventure.platform.bukkit)
    implementation(libs.hikari)
    implementation(libs.lettuce)
    implementation(libs.caffeine)
    implementation(libs.javalin)
    implementation(libs.jackson.databind)
    implementation(libs.sqlite.jdbc)
    implementation(libs.mysql.connector)
    // FastStats — error tracking + custom metrics, shaded with relocation so
    // it can't collide with another plugin's copy.
    implementation("dev.faststats.metrics:bukkit:0.22.0")

    testImplementation("org.spigotmc:spigot-api:$spigotApiVersion")
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
    from(dashboardDist) {
        into("dashboard")
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.shadowJar {
    archiveClassifier.set("all")
    mergeServiceFiles()

    val base = "me.playbosswar.tea.core.libs"
    relocate("net.kyori", "$base.kyori")
    relocate("com.zaxxer.hikari", "$base.hikari")
    relocate("io.lettuce", "$base.lettuce")
    relocate("io.netty", "$base.netty")
    relocate("reactor", "$base.reactor")
    relocate("org.reactivestreams", "$base.reactivestreams")
    relocate("com.github.benmanes.caffeine", "$base.caffeine")
    relocate("io.javalin", "$base.javalin")
    relocate("org.eclipse.jetty", "$base.jetty")
    relocate("com.fasterxml.jackson", "$base.jackson")
    relocate("org.jetbrains.annotations", "$base.annotations")
    relocate("kotlin", "$base.kotlin")
    relocate("dev.faststats", "$base.faststats")
    // org.sqlite.* is intentionally NOT relocated: the native .so binds JNI
    // symbols to the original package name and relocating breaks at connect.
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
