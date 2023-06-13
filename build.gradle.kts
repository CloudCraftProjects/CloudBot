plugins {
    id("java-library")
    id("maven-publish")

    // id("net.minecrell.plugin-yml.bukkit") version "0.5.3"
    id("xyz.jpenilla.run-paper") version "1.0.6"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.booky"
version = "1.0.0"

repositories {
    maven("https://oss.sonatype.org/content/repositories/snapshots/") {
        content {
            includeGroup("com.discord4j")
        }
    }
    maven("https://repo.papermc.io/repository/maven-public/")
}

val configurateVersion = "4.1.2"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20-R0.1-SNAPSHOT")

    // already included as server, not exposed in api
    compileOnlyApi("org.spongepowered:configurate-yaml:$configurateVersion")

    // downloaded at runtime using library loader
    compileOnlyApi("org.spongepowered:configurate-gson:$configurateVersion")
    compileOnlyApi("com.discord4j:discord4j-core:3.3.0-SNAPSHOT")

    // optional dependency
    compileOnlyApi("me.lucko:spark-api:0.1-SNAPSHOT")

    // integrated metrics
    implementation("org.bstats:bstats-bukkit:3.0.0")
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

// TODO
// bukkit {
//     main = "$group.cloudbot.CloudBotMain"
//     apiVersion = "1.19"
//     authors = listOf("booky10")
//     softDepend = listOf("spark")
//     load = BukkitPluginDescription.PluginLoadOrder.POSTWORLD
//     libraries = listOf(
//         "org.spongepowered:configurate-gson:$configurateVersion",
//         "com.discord4j:discord4j-core:$discord4jVersion")
// }

tasks {
    runServer {
        minecraftVersion("1.20")
    }

    shadowJar {
        relocate("org.bstats", "dev.booky.cloudbot.bstats")
    }

    build {
        dependsOn(shadowJar)
    }

    withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
    }
}
