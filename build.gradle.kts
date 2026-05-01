plugins {
    java
    `java-library`
}

val javaVersion: String by project
val modVersion: String by project
val modGroupId: String by project

allprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")

    group = modGroupId
    version = modVersion

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://libraries.minecraft.net/") { name = "Mojang" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.blamejared.com/") { name = "BlameJared (JEI)" }
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaVersion.toInt())
        }
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = javaVersion.toInt()
    }
}