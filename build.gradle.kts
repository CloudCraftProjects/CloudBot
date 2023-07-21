import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    id("java-library")
    id("maven-publish")

    alias(libs.plugins.pluginyml.paper)
    alias(libs.plugins.runpaper)
    alias(libs.plugins.shadow)
}

group = "dev.booky"
version = "1.0.1-SNAPSHOT"

repositories {
    maven("https://oss.sonatype.org/content/repositories/snapshots/") {
        content {
            includeGroup("com.discord4j")
        }
    }
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.paperapi)

    // already included as server, not exposed in api
    compileOnlyApi(libs.configurate.yaml)

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
}

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        artifactId = project.name.lowercase()
        from(components["java"])
    }
    repositories.maven("https://maven.pkg.github.com/CloudCraftProjects/CloudBot/") {
        name = "github"
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
        register("spark") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = false
        }
    }
}

tasks {
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
    }

    shadowJar {
        relocate("org.bstats", "${project.group}.cloudbot.bstats")
    }

    assemble {
        dependsOn(shadowJar)
    }

    withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
    }
}
