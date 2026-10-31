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
