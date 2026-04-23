// The dashboard module wraps a Vite/React/TS app. Gradle shells out to npm so
// the build machine needs Node 20+ on PATH. The produced `dist/` directory is
// consumed by :core via processResources, so `:core:shadowJar` automatically
// embeds the compiled SPA into the plugin jar under /dashboard/.

plugins {
    base
}

val nodeCmd = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

val dashboardDist = layout.projectDirectory.dir("dist")

val npmInstall by tasks.registering(Exec::class) {
    description = "Installs the dashboard's npm dependencies."
    group = "dashboard"
    workingDir = projectDir
    commandLine = listOf(nodeCmd, "install", "--no-audit", "--no-fund")
    inputs.file("package.json")
    inputs.file(file("package-lock.json").takeIf { it.exists() } ?: file("package.json"))
    outputs.dir("node_modules")
}

val npmBuild by tasks.registering(Exec::class) {
    description = "Builds the dashboard SPA (vite build)."
    group = "dashboard"
    dependsOn(npmInstall)
    workingDir = projectDir
    commandLine = listOf(nodeCmd, "run", "build")
    inputs.dir("src")
    inputs.file("index.html")
    inputs.file("vite.config.ts")
    inputs.file("tsconfig.json")
    inputs.file("tailwind.config.js")
    inputs.file("postcss.config.js")
    inputs.file("package.json")
    outputs.dir(dashboardDist)
}

tasks.named("build") {
    dependsOn(npmBuild)
}

tasks.named<Delete>("clean") {
    delete(dashboardDist)
}
