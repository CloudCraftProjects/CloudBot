import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    id("java-library")
    id("maven-publish")

    alias(libs.plugins.pluginyml.paper)
    alias(libs.plugins.runtask.paper)
    alias(libs.plugins.shadow)
}

group = "dev.booky"
version = "1.0.2-SNAPSHOT"

val plugin: Configuration by configurations.creating {
    isTransitive = false
}

repositories {
    maven("https://repo.cloudcraftmc.de/public/")
}

dependencies {
    compileOnly(libs.paper.api)

    compileOnlyApi(libs.cloudcore)

    // downloaded at runtime using library loader
    sequenceOf(
        libs.configurate.gson,
        libs.discord4j.core
    ).forEach {
        compileOnlyApi(it)
        paperLibrary(it)
    }

    // optional dependency
    compileOnlyApi(libs.spark.api)

    // integrated metrics
    implementation(libs.bstats)

    plugin(variantOf(libs.cloudcore) { classifier("all") })
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        artifactId = project.name.lowercase()
        from(components["java"])
    }
    repositories.maven("https://repo.cloudcraftmc.de/private/") {
        name = "horreo"
        credentials(PasswordCredentials::class.java)
    }
}

paper {
    // generates a json file into our runtime jar
    // this lists all libraries, which are then downloaded using paper plugin loaders
    generateLibrariesJson = true

    main = "$group.cloudbot.CloudBotMain"
    loader = "$group.cloudbot.CloudBotLoader"

    apiVersion = "1.20"
    authors = listOf("booky10")
    load = BukkitPluginDescription.PluginLoadOrder.POSTWORLD

    serverDependencies {
        register("CloudCore") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
        }
        register("spark") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = false
        }
    }
}

tasks {
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        pluginJars.from(plugin.resolve())
    }

    shadowJar {
        relocate("org.bstats", "${project.group}.cloudbot.bstats")
    }

    assemble {
        dependsOn(shadowJar)
    }

    withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }

    withType<Jar> {
        manifest.attributes(
            "paperweight-mappings-namespace" to "mojang"
        )
    }
}
