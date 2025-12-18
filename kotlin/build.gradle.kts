import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

group = (project.findProperty("group") as String?) ?: "org.angryscan"
version = (project.findProperty("version") as String?) ?: "0.1.0"

repositories {
    mavenCentral()
}

val angryscanCoreVersion: String =
    (project.findProperty("angryscanCoreVersion") as String?) ?: "1.4.4"

kotlin {
    jvm()

    // Native targets used by angryscan-app
    mingwX64()
    linuxX64()
    macosX64()
    macosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.angryscan:core:$angryscanCoreVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("net.java.dev.jna:jna:5.15.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Shared source set for all Kotlin/Native targets
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        // Wire common sources for native targets
        compilations.getByName("main").defaultSourceSet.dependsOn(sourceSets.getByName("nativeMain"))
        compilations.getByName("test").defaultSourceSet.dependsOn(sourceSets.getByName("nativeTest"))

        // cinterop bindings
        compilations.getByName("main").cinterops.create("gitleaks") {
            defFile(project.file("src/nativeInterop/cinterop/gitleaks.def"))
        }

        // Linker options are target-specific; we try to use build outputs from repo root.
        val repoRoot = projectDir.parentFile
        val libDir = when (name) {
            "linuxX64" -> repoRoot.resolve("build/out/linux-amd64")
            "macosX64" -> repoRoot.resolve("build/out/darwin-amd64")
            "macosArm64" -> repoRoot.resolve("build/out/darwin-arm64")
            // NOTE: Go buildmode=c-shared on Windows produces DLL + header; linking from K/N
            // typically needs an import library (.a). This is handled separately.
            "mingwX64" -> repoRoot.resolve("build/out/windows-amd64")
            else -> repoRoot.resolve("build/out")
        }

        binaries.all {
            if (libDir.exists()) {
                linkerOpts("-L${libDir.absolutePath}", "-lgitleaks")
            }
        }
    }
}


