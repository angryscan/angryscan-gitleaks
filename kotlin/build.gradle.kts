import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

group = (project.findProperty("group") as String?) ?: "org.angryscan"
version = (project.findProperty("version") as String?) ?: "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
    
    jvm()

    // Native targets temporarily disabled - need to prepare angryscan-core first
    // TODO: Re-enable Native targets after angryscan-core supports all Native platforms
    // mingwX64()
    // linuxX64()
    // macosX64()
    // macosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.angryscan.core)
                implementation(libs.kotlinx.serialization)
            }
        }
        val jvmMain by getting {
            dependencies {
                // JNA is used to load libgitleaks shared library at runtime
                // The library (libgitleaks.so/.dll/.dylib) must be built from Go code
                // and available in java.library.path or system library path
                implementation(libs.jna)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Native source sets temporarily disabled
        // val nativeMain by creating {
        //     dependsOn(commonMain)
        // }
        // val nativeTest by creating {
        //     dependsOn(commonTest)
        // }
    }

    // Native target configuration temporarily disabled
    // targets.withType<KotlinNativeTarget>().configureEach { ... }
}

// Configure JVM tests to have access to native library
tasks.named<Test>("jvmTest") {
    // Add library path to JVM system properties for tests
    val repoRoot = projectDir.parentFile
    val osName = System.getProperty("os.name", "").lowercase()
    val libDir = when {
        osName.startsWith("windows") -> repoRoot.resolve("build/out/windows-amd64")
        osName.startsWith("linux") -> repoRoot.resolve("build/out/linux-amd64")
        osName.startsWith("mac") -> {
            val arch = System.getProperty("os.arch", "")
            if (arch == "aarch64") {
                repoRoot.resolve("build/out/darwin-arm64")
            } else {
                repoRoot.resolve("build/out/darwin-amd64")
            }
        }
        else -> repoRoot.resolve("build/out")
    }
    
    // Set java.library.path to include the library directory
    systemProperty("java.library.path", libDir.absolutePath)
    
    // Also set jna.library.path as fallback
    systemProperty("jna.library.path", libDir.absolutePath)
    
    // Ensure library directory exists or provide helpful error
    doFirst {
        if (!libDir.exists()) {
            throw GradleException(
                "Native library directory does not exist: ${libDir.absolutePath}\n" +
                "Please build the native library first using:\n" +
                "  - Windows: build-scripts/build-windows.sh\n" +
                "  - Linux: build-scripts/build-linux.sh\n" +
                "  - macOS: build-scripts/build-darwin.sh"
            )
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = "org.angryscan",
        artifactId = "angryscan-gitleaks",
        version = project.version.toString()
    )

    pom {
        name.set("AngryScan Gitleaks Kotlin Matcher")
        description.set("Kotlin Multiplatform wrapper for Gitleaks secret detection engine")
        url.set("https://github.com/angryscan/angryscan-gitleaks")

        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }

        developers {
            developer {
                id.set("stellalupus")
                name.set("StellaLupus")
                email.set("soulofpain.k@gmail.com")
            }
        }

        scm {
            url.set("https://github.com/angryscan/angryscan-gitleaks")
        }
    }

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()
}

