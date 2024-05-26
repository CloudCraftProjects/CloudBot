package dev.booky.cloudbot.storage;
// Created by booky10 in CloudBot (21:31 26.05.2024)

import discord4j.rest.util.Color;
import net.kyori.adventure.text.format.TextColor;
import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.function.Predicate;

public final class ColorSerializer extends ScalarSerializer<Color> {

    public static final TypeSerializer<Color> INSTANCE = new ColorSerializer();

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
