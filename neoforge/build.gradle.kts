plugins {
    id("net.neoforged.moddev")
}

val modId: String by project
val modVersion: String by project
val minecraftVersion: String by project
val neoforgeVersion: String by project
val javaVersion: String by project
val jeiVersion: String by project

neoForge {
    version = neoforgeVersion

    runs {
        register("client") {
            client()
            gameDirectory = file("run/client")
        }
        register("server") {
            server()
            gameDirectory = file("run/server")
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(project(":common").sourceSets.main.get())
        }
    }
}

dependencies {
    compileOnly(project(":common"))
    compileOnly("mezz.jei:jei-${minecraftVersion}-common-api:${jeiVersion}")
    compileOnly("mezz.jei:jei-${minecraftVersion}-neoforge-api:${jeiVersion}")
    runtimeOnly("mezz.jei:jei-${minecraftVersion}-neoforge:${jeiVersion}")
}

tasks.named<Jar>("jar") {
    from(project(":common").sourceSets.main.get().output)
    archiveBaseName.set("${modId}-neoforge")
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "modId" to modId,
        "modVersion" to modVersion,
        "minecraftVersion" to minecraftVersion,
        "neoforgeVersion" to neoforgeVersion,
        "javaVersion" to javaVersion
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
}