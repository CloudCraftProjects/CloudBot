package dev.booky.cloudbot;
// Created by booky10 in CloudBot (17:46 13.06.23)

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

public final class CloudBotLoader implements PluginLoader {

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        PluginLibraries libraries;
        try (InputStream input = this.getClass().getResourceAsStream("/paper-libraries.json")) {
            Preconditions.checkState(input != null, "No libraries definition found in jar");
            try (InputStreamReader reader = new InputStreamReader(input)) {
                libraries = new Gson().fromJson(reader, PluginLibraries.class);
            }
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        if (libraries.isEmpty()) {
            return;
        }

        MavenLibraryResolver resolver = new MavenLibraryResolver();
        libraries.addTo(resolver);
        classpathBuilder.addLibrary(resolver);
    }

    private record PluginLibraries(Map<String, String> repositories, List<String> dependencies) {

        public void addTo(MavenLibraryResolver resolver) {
            this.repositories.entrySet().stream()
                    .map(entry -> new RemoteRepository.Builder(entry.getKey(), "default", entry.getValue()).build())
                    .forEach(resolver::addRepository);
            this.dependencies.stream()
                    .map(DefaultArtifact::new)
                    .map(artifact -> new Dependency(artifact, null))
                    .forEach(resolver::addDependency);
        }

        public boolean isEmpty() {
            return this.repositories == null || this.repositories.isEmpty()
                    || this.dependencies == null || this.dependencies.isEmpty();
        }
    }
}
