plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("io.github.goooler.shadow") version "8.1.7"
    id("xyz.jpenilla.run-paper") version "2.2.3"
}

group = "dev.pincho"
version = "1.0.0"
description = "Advanced lock system for Minecraft servers"

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

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("1.21.3")
    }
}
