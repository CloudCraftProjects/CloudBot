package dev.booky.cloudbot.storage;
// Created by booky10 in ********** (12:55 27.06.22)

import discord4j.rest.util.Color;
import net.kyori.adventure.text.format.TextColor;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.gson.GsonConfigurationLoader;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class ConfigLoader {

    private static final Map<Path, GsonConfigurationLoader> GSON_LOADER_CACHE = new HashMap<>();
    private static final Map<Path, YamlConfigurationLoader> YAML_LOADER_CACHE = new HashMap<>();

    public static YamlConfigurationLoader createYamlLoader(Path path) {
        return YAML_LOADER_CACHE.computeIfAbsent(path, $ -> YamlConfigurationLoader.builder()
                .path(path).nodeStyle(NodeStyle.BLOCK).indent(2)
                .defaultOptions(opts -> opts.serializers(serializers -> serializers
                        .register(new ColorSerializer())))
                .build());
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

    private static final class ColorSerializer extends ScalarSerializer<Color> {

        private ColorSerializer() {
            super(Color.class);
        }

        @Override
        public Color deserialize(Type type, Object obj) throws SerializationException {
            TextColor color = TextColor.fromCSSHexString(String.valueOf(obj));
            if (color != null) {
                return Color.of(color.value());
            }
            throw new SerializationException("Can't deserialize color string: '" + obj + "'");
        }

        @Override
        protected Object serialize(Color item, Predicate<Class<?>> typeSupported) {
            return TextColor.color(item.getRGB()).asHexString();
        }
    }
}
