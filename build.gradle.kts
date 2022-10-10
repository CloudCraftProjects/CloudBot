plugins {
    id("java-library")
    id("maven-publish")

    id("xyz.jpenilla.run-paper") version "1.0.6"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "dev.booky"
version = "1.0.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnlyApi("io.papermc.paper:paper-api:1.19.2-R0.1-SNAPSHOT")
    compileOnlyApi("com.discord4j:discord4j-core:3.2.3")

    api("org.bstats:bstats-bukkit:3.0.0")
}

tasks{
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
        artifactId = project.name.toLowerCase()
        from(components["java"])
    }
}
