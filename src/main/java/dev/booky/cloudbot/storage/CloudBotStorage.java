package dev.booky.cloudbot.storage;
// Created by booky10 in CloudBot (21:18 11.10.22)

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("FieldMayBeFinal") // configurate
@ConfigSerializable
public class CloudBotStorage {

    private Map<UUID, Long> whitelist = new LinkedHashMap<>();

    @SuppressWarnings("unused") // configurate
    private CloudBotStorage() {
    }

    public Map<UUID, Long> getWhitelist() {
        return whitelist;
    }
}
