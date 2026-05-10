plugins {
    id("net.fabricmc.fabric-loom")
    `java-library`
}

val modId: String by project
val modVersion: String by project
val modGroupId: String by project
val minecraftVersion: String by project
val fabricLoaderVersion: String by project
val fabricApiVersion: String by project
val jeiVersion: String by project

base.archivesName.set("$modId-fabric-$minecraftVersion")
version = modVersion
group = modGroupId

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    maven("https://maven.blamejared.com/") { name = "BlameJared" }
    maven("https://maven.fabricmc.net/")   { name = "Fabric"     }
    mavenCentral()
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")

    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // JEI - APIs only; runtime jar is loaded by Fabric at runtime
    compileOnly("mezz.jei:jei-$minecraftVersion-common-api:$jeiVersion")
    compileOnly("mezz.jei:jei-$minecraftVersion-fabric-api:$jeiVersion")

    implementation(project(":common"))
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.named<Jar>("jar") {
    from(project(":common").sourceSets["main"].output)
}