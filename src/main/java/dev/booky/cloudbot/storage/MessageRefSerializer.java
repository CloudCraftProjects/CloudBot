package dev.booky.cloudbot.storage;
// Created by booky10 in CloudBot (21:31 26.05.2024)

import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.function.Predicate;

public final class MessageRefSerializer extends ScalarSerializer<MessageRef> {

    public static final TypeSerializer<MessageRef> INSTANCE = new MessageRefSerializer();

    private MessageRefSerializer() {
        super(MessageRef.class);
    }

    @Override
    public MessageRef deserialize(Type type, Object obj) throws SerializationException {
        try {
            return MessageRef.of(String.valueOf(obj));
        } catch (IllegalArgumentException exception) {
            throw new SerializationException(exception);
        }
    }

    @Override
    protected Object serialize(MessageRef item, Predicate<Class<?>> typeSupported) {
        return item.getChannelId() + "-" + item.getMessageId();
    }
}
