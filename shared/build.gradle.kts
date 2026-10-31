plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// Platform-independent PhantomFloor: scenes, games, sound synthesis, the frame loop and a GL
// facade. The Android app supplies an OpenGL ES backend; the browser build (jsMain) supplies
// WebGL2 and is packaged by :shared:webDist into a static site.
kotlin {
    jvm {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    js(IR) {
        moduleName = "phantomfloor"
        browser()
        useEsModules()
        binaries.executable()
    }
    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmTest.dependencies { implementation("junit:junit:4.13.2") }
    }
}

// Longer bot sessions: ./gradlew :shared:jvmTest --tests '*PlaytestTest*' -PplaytestSeconds=600
tasks.withType<Test>().configureEach {
    (findProperty("playtestSeconds") as String?)?.let { systemProperty("playtest.seconds", it) }
}

// --- Browser build ---------------------------------------------------------------------------
// The compiled ES modules + index.html/style.css + every shader (from the Android assets, the
// single source of truth) + a shader manifest, as a static site in build/dist/web. No bundler or
// Node.js needed: browsers load the .mjs modules directly.
val webVersion = (findProperty("appVersionName") as String?) ?: "dev"
val shaderDir = rootProject.file("app/src/main/assets/shaders")

val webShaderManifest by tasks.registering {
    val out = layout.buildDirectory.file("generated/web/shaders/manifest.json")
    inputs.dir(shaderDir)
    outputs.file(out)
    doLast {
        val names = shaderDir.listFiles()!!.filter { it.isFile }.map { it.name }.sorted()
        out.get().asFile.apply { parentFile.mkdirs() }
            .writeText(names.joinToString(",\n  ", "[\n  ", "\n]\n") { "\"$it\"" })
    }
}

val webDist by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Builds the browser version into build/dist/web"
    dependsOn("jsProductionExecutableCompileSync")
    inputs.property("version", webVersion)
    from(layout.buildDirectory.dir("compileSync/js/main/productionExecutable/kotlin")) { include("*.mjs") }
    from("src/jsMain/web") {
        filesMatching("index.html") { filter { it.replace("@VERSION@", webVersion) } }
    }
    from(shaderDir) { into("shaders") }
    from(webShaderManifest) { into("shaders") }
    into(layout.buildDirectory.dir("dist/web"))
}

val webZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Zips the browser version for itch.io (index.html at the root)"
    from(webDist)
    archiveFileName.set("PhantomFloor-web-$webVersion.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}
