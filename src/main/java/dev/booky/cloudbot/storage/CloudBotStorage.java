package dev.booky.cloudbot.storage;
// Created by booky10 in CloudBot (21:18 11.10.22)

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("FieldMayBeFinal") // configurate
@ConfigSerializable
public class CloudBotStorage {

    private Map<UUID, Long> whitelist = new LinkedHashMap<>();
    private Map<Long, Long> bedrockWhitelist = new LinkedHashMap<>();

    @SuppressWarnings("unused") // configurate
    private CloudBotStorage() {
    }

    public void postLoad() {
        this.whitelist = new ConcurrentHashMap<>(this.whitelist);
        this.bedrockWhitelist = new ConcurrentHashMap<>(this.bedrockWhitelist);
    }

    public Map<UUID, Long> getWhitelist() {
        return whitelist;
    }

    public Map<Long, Long> getBedrockWhitelist() {
        return this.bedrockWhitelist;
    }
}
