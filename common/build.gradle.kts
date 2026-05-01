plugins {
    id("net.fabricmc.fabric-loom")
}

val minecraftVersion: String by project
val fabricLoaderVersion: String by project

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    compileOnly("net.fabricmc:fabric-loader:$fabricLoaderVersion")
}

loom {
    runs { configureEach { ideConfigGenerated(false) } }
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("dev")
}