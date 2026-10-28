plugins {
    id("fabric-loom") version "1.18.4"
}

val modId = property("mod_id") as String
version = "${property("mod_version")}+${property("minecraft_version")}"
group = property("maven_group") as String
base.archivesName = modId

repositories {
    maven("https://maven.terraformersmc.com/releases") {
        name = "Terraformers"
        content { includeGroup("com.terraformersmc") }
    }
    maven("https://maven.parchmentmc.org") {
        name = "ParchmentMC"
        content { includeGroup("org.parchmentmc.data") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${property("minecraft_version")}:${property("parchment_version")}@zip")
    })
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modCompileOnly("com.terraformersmc:modmenu:11.0.3")
}

loom {
    runs.configureEach { ideConfigGenerated(false) }
    runs.named("client") {
        if (project.hasProperty("bench")) {
            vmArg("-Dboost.bench=${project.property("bench").toString().ifEmpty { "1" }}")
        }
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 21
    }
}
