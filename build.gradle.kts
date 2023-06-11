plugins {
    id("java-library")
    id("maven-publish")

    id("xyz.jpenilla.run-paper") version "1.0.6"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.booky"
version = "1.0.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Provided
    compileOnlyApi("io.papermc.paper:paper-api:1.19.2-R0.1-SNAPSHOT")

    // Provided using library api
    compileOnlyApi("org.spongepowered:configurate-gson:4.1.2")
    compileOnlyApi("org.spongepowered:configurate-yaml:4.1.2")
    compileOnlyApi("com.discord4j:discord4j-core:3.2.3")

    // Optional dependency plugins
    compileOnlyApi("me.lucko:spark-api:0.1-SNAPSHOT")

    // Shadowed
    api("org.bstats:bstats-bukkit:3.0.0")
}

tasks {
    runServer {
        minecraftVersion("1.19.2")
    }

    processResources {
        inputs.property("version", project.version)
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    shadowJar {
        relocate("org.bstats", "dev.booky.cloudbot.bstats")
    }

    build {
        dependsOn(shadowJar)
    }
}

java {
    withSourcesJar()
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

publishing {
    publications.create<MavenPublication>("maven") {
        artifactId = project.name.lowercase()
        from(components["java"])
    }
}
