package dev.booky.cloudbot;
// Created by booky10 in CloudBot (17:46 13.06.23)

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

public final class CloudBotLoader implements PluginLoader {

    private static final String SONATYPE_SNAPSHOT_URL = "https://oss.sonatype.org/content/repositories/snapshots/";
    private static final String PAPER_PUBLIC_URL = "https://repo.papermc.io/repository/maven-public/";

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        // TODO: change when minecrell releases a new version of plugin-yml
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        resolver.addRepository(new RemoteRepository.Builder(
                "sonatype-snapshot", "default", SONATYPE_SNAPSHOT_URL).build());
        resolver.addRepository(new RemoteRepository.Builder(
                "paper-public", "default", PAPER_PUBLIC_URL).build());

        // just hardcoded dependency versions for now
        resolver.addDependency(new Dependency(new DefaultArtifact("org.spongepowered",
                "configurate-gson", "jar", "4.1.2"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("com.discord4j",
                "discord4j-core", "jar", "3.3.0-SNAPSHOT"), null));

        classpathBuilder.addLibrary(resolver);
    }
}
