import proguard.gradle.ProGuardTask

plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("io.github.goooler.shadow") version "8.1.7"
    id("xyz.jpenilla.run-paper") version "2.2.3"
}

group = "dev.pincho"
version = "1.0.0"
description = "Advanced lock system for Minecraft servers"

// ProGuard configuration
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.6.1")
    }
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    // Spigot API - 1.21+ (compatible with Bukkit, Spigot and Paper)
    compileOnly("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Adventure API (for cross-platform compatibility)
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("net.kyori:adventure-platform-bukkit:4.3.4")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "21"
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }

    processResources {
        val props = mapOf(
            "version" to version,
            "description" to description
        )
        inputs.properties(props)
        filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
            expand(props)
        }
    }

    shadowJar {
        // Remove classifier so this becomes the main JAR
        archiveClassifier.set("")
        archiveFileName.set("PinchosLocks-${version}.jar")

        relocate("kotlin", "dev.pincho.locks.libs.kotlin")
        relocate("kotlinx", "dev.pincho.locks.libs.kotlinx")
        relocate("net.kyori", "dev.pincho.locks.libs.kyori")

        minimize {
            exclude(dependency("org.jetbrains.kotlin:.*"))
            exclude(dependency("org.jetbrains.kotlinx:.*"))
            exclude(dependency("net.kyori:.*"))
        }
    }

    // Disable the default jar task to avoid confusion
    jar {
        enabled = false
    }

    // ProGuard obfuscation task
    val proguard by registering(ProGuardTask::class) {
        dependsOn(shadowJar)

        // Input: shadowed JAR
        injars(shadowJar.get().archiveFile)

        // Output: final obfuscated JAR
        outjars(layout.buildDirectory.file("libs/PinchosLocks-${version}-release.jar"))

        // Library JARs (don't obfuscate these)
        val javaHome = System.getProperty("java.home")
        if (File("$javaHome/jmods").exists()) {
            // Java 21+ modular JDK
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
                "$javaHome/jmods/java.base.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
                "$javaHome/jmods/java.logging.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
                "$javaHome/jmods/java.management.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
                "$javaHome/jmods/java.desktop.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
                "$javaHome/jmods/java.sql.jmod")
        }

        // Add all compile classpath dependencies as library jars
        configurations.compileClasspath.get().forEach { file ->
            libraryjars(file)
        }

        // ProGuard configuration file
        configuration(file("proguard-rules.pro"))

        // Generate mapping file for debugging
        printmapping(layout.buildDirectory.file("proguard/mapping.txt"))
        printseeds(layout.buildDirectory.file("proguard/seeds.txt"))
        printusage(layout.buildDirectory.file("proguard/usage.txt"))

        // Verbose output
        verbose()
    }

    // Production build with obfuscation
    val buildRelease by registering {
        dependsOn(proguard)
        group = "build"
        description = "Builds the plugin with ProGuard obfuscation for production release"

        doLast {
            val outputFile = layout.buildDirectory.file("libs/PinchosLocks-${version}-release.jar").get().asFile
            val mappingFile = layout.buildDirectory.file("proguard/mapping.txt").get().asFile

            println("")
            println("═══════════════════════════════════════════════════════════════════")
            println("  PINCHO'S LOCKS - Production Build Complete!")
            println("═══════════════════════════════════════════════════════════════════")
            println("  Output JAR: ${outputFile.absolutePath}")
            println("  Mapping:    ${mappingFile.absolutePath}")
            println("")
            println("  IMPORTANT: Keep mapping.txt for debugging stack traces!")
            println("  Use: java -jar retrace.jar mapping.txt stacktrace.txt")
            println("═══════════════════════════════════════════════════════════════════")
            println("")
        }
    }

    // Development build without obfuscation
    val buildDev by registering {
        dependsOn(shadowJar)
        group = "build"
        description = "Builds the plugin without obfuscation (for development)"

        doLast {
            val jarFile = shadowJar.get().archiveFile.get().asFile
            println("")
            println("═══════════════════════════════════════════════════════════════════")
            println("  PINCHO'S LOCKS - Development Build Complete!")
            println("═══════════════════════════════════════════════════════════════════")
            println("  Output JAR: ${jarFile.absolutePath}")
            println("═══════════════════════════════════════════════════════════════════")
            println("")
        }
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("1.21.3")
    }
}
