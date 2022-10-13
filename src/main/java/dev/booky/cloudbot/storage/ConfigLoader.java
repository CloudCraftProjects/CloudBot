package dev.booky.cloudbot.storage;
// Created by booky10 in ********** (12:55 27.06.22)

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.gson.GsonConfigurationLoader;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class ConfigLoader {

    private static final Map<Path, GsonConfigurationLoader> GSON_LOADER_CACHE = new HashMap<>();
    private static final Map<Path, YamlConfigurationLoader> YAML_LOADER_CACHE = new HashMap<>();

    public static YamlConfigurationLoader createYamlLoader(Path path) {
        return YAML_LOADER_CACHE.computeIfAbsent(path, $ -> YamlConfigurationLoader.builder().path(path)
                .nodeStyle(NodeStyle.BLOCK).indent(2).build());
    }

    public static GsonConfigurationLoader createGsonLoader(Path path) {
        return GSON_LOADER_CACHE.computeIfAbsent(path, $ -> GsonConfigurationLoader.builder().path(path)
                .indent(0).build());
    }

    public static <T> T loadObject(Path path, Class<T> clazz, FileType type) {
        try {
            ConfigurationLoader<? extends ConfigurationNode> loader = type.create(path);
            T obj;

            if (Files.exists(path)) {
                obj = loader.load().get(clazz);
            } else {
                try {
                    Constructor<T> ctor = clazz.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    obj = ctor.newInstance();
                } catch (ReflectiveOperationException exception) {
                    throw new RuntimeException(exception);
                }
            }

            ConfigurationNode node = loader.createNode();
            node.set(clazz, obj);

            loader.save(node);
            return obj;
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static <T> void saveObject(Path path, T obj, FileType type) {
        try {
            ConfigurationLoader<? extends ConfigurationNode> loader = type.create(path);
            ConfigurationNode node = loader.createNode();

            node.set(obj.getClass(), obj);
            loader.save(node);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public enum FileType {

        YAML(ConfigLoader::createYamlLoader),
        JSON(ConfigLoader::createGsonLoader);

        private final Function<Path, ConfigurationLoader<? extends ConfigurationNode>> creator;

        FileType(Function<Path, ConfigurationLoader<? extends ConfigurationNode>> creator) {
            this.creator = creator;
        }

        public ConfigurationLoader<? extends ConfigurationNode> create(Path path) {
            return this.creator.apply(path);
        }
    }
}
