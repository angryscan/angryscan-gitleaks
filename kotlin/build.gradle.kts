import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.Copy
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

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
        @Suppress("unused")
        val commonMain by getting {
            dependencies {
                api(libs.angryscan.core)
                implementation(libs.kotlinx.serialization)
            }
        }
        @Suppress("unused")
        val jvmMain by getting {
            dependencies {
                // JNA is used to load libgitleaks shared library at runtime
                // The library (libgitleaks.so/.dll/.dylib) must be built from Go code
                // and available in java.library.path or system library path
                implementation(libs.jna)
            }
        }
        @Suppress("unused")
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
    // targets.withType<KotlinNativeTarget>().configureEach { ...     }
}

// Task to copy native libraries into JAR resources for cross-platform support
// JNA will automatically load the correct library from META-INF/native/<os>/<arch>/
val copyNativeLibraries = tasks.register<Copy>("copyNativeLibraries") {
    val repoRoot = projectDir.parentFile
    val resourcesDir = sourceSets.getByName("jvmMain").resources.srcDirs.first()
    val nativeResourcesDir = File(resourcesDir, "META-INF/native")
    
    // Define required native libraries with their source paths and target JNA paths
    // JNA uses specific OS-ARCH format: win32-x86-64, linux-x86-64, darwin-x86-64, darwin-aarch64
    val requiredLibraries = listOf(
        Triple("windows-amd64", "libgitleaks.dll", "win32-x86-64"),
        Triple("linux-amd64", "libgitleaks.so", "linux-x86-64"),
        Triple("darwin-amd64", "libgitleaks.dylib", "darwin-x86-64"),
        Triple("darwin-arm64", "libgitleaks.dylib", "darwin-aarch64")
    )
    
    val skipCheck = project.findProperty("skipNativeLibraryCheck")?.toString() == "true"
    
    // Copy available libraries to the correct JNA structure
    into(nativeResourcesDir)
    
    requiredLibraries.forEach { (platformDir, libName, jnaPath) ->
        val sourceFile = File(repoRoot, "build/out/$platformDir/$libName")
        if (sourceFile.exists()) {
            from(sourceFile) {
                into(jnaPath)
            }
        }
    }
    
    // Ensure output directory exists and check for missing libraries
    doFirst {
        nativeResourcesDir.mkdirs()
        
        // Check for missing libraries if check is enabled
        if (!skipCheck) {
            val missingLibraries = mutableListOf<String>()
            requiredLibraries.forEach { (platformDir, libName, _) ->
                val sourceFile = File(repoRoot, "build/out/$platformDir/$libName")
                if (!sourceFile.exists()) {
                    missingLibraries.add("  - $platformDir/$libName")
                }
            }
            
            if (missingLibraries.isNotEmpty()) {
                logger.warn(
                    "WARNING: Some native libraries are missing:\n" +
                    missingLibraries.joinToString("\n") + "\n" +
                    "The JAR will be built with only available libraries.\n" +
                    "To build all libraries, run: bash build-scripts/build-all.sh\n" +
                    "To skip this check, use: -PskipNativeLibraryCheck=true"
                )
            }
        }
        
        // Log copied libraries
        requiredLibraries.forEach { (platformDir, libName, jnaPath) ->
            val sourceFile = File(repoRoot, "build/out/$platformDir/$libName")
            if (sourceFile.exists()) {
                logger.info("Copying native library: $platformDir/$libName -> META-INF/native/$jnaPath/$libName")
            } else if (!skipCheck) {
                logger.debug("Skipping missing library: $platformDir/$libName")
            }
        }
    }
}

// Make jvmJar depend on copyNativeLibraries to ensure native libraries are included
tasks.named<org.gradle.jvm.tasks.Jar>("jvmJar") {
    dependsOn(copyNativeLibraries)
}

// Gradle validation: jvmProcessResources reads from the same resources dir that copyNativeLibraries writes into.
// Declare the dependency explicitly to avoid ordering issues.
tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn(copyNativeLibraries)
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
        artifactId = "gitleaks",
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

    signAllPublications()

    // For SNAPSHOT versions, use DEFAULT host (supports snapshots via OSSRH)
    // For release versions, use Central Portal
    if (project.version.toString().endsWith("SNAPSHOT")) {
        publishToMavenCentral(SonatypeHost.DEFAULT)
    } else {
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    }
}

