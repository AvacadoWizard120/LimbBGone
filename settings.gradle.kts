pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.quiltmc.org/repository/release/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases/")
        maven("https://maven.kikugie.dev/snapshots/")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

stonecutter {
    create(rootProject) {
        fun minecraft(version: String, vararg loaders: String) {
            loaders.forEach { loader ->
                version("$version-$loader", version).buildscript =
                    if (loader == "forge") "build.modernforge.gradle" else "build.$loader.gradle.kts"
            }
        }

        // First implementation slice only. Add future nodes only after confirming that
        // the Minecraft version is not supported by the original iChun release.
        minecraft("1.21.1", "fabric", "quilt", "forge", "neoforge")

        vcsVersion = "1.21.1-fabric"
    }
}

rootProject.name = "Mob-Amputation"
