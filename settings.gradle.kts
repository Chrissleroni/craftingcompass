pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
    }
    plugins {
        id("net.fabricmc.fabric-loom") version "1.16.1"
        id("net.neoforged.moddev") version "2.0.141"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "craftingcompass"

include("common")
include("fabric")
include("neoforge")