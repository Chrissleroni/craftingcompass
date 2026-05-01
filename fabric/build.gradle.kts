plugins {
    id("net.fabricmc.fabric-loom")
}

val modId: String by project
val modVersion: String by project
val minecraftVersion: String by project
val fabricLoaderVersion: String by project
val fabricApiVersion: String by project
val jeiVersion: String by project

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    compileOnly(project(":common"))
    compileOnly("mezz.jei:jei-${minecraftVersion}-common-api:${jeiVersion}")
}

tasks.named<Jar>("jar") {
    from(project(":common").sourceSets["main"].output)
    archiveBaseName.set("${modId}-fabric")
}