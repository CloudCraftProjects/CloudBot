package dev.booky.cloudbot.storage;
// Created by booky10 in CloudBot (21:18 11.10.22)

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("FieldMayBeFinal") // configurate
@ConfigSerializable
public class CloudBotStorage {

    private Set<UUID> whitelist = new LinkedHashSet<>();

    @SuppressWarnings("unused") // configurate
    private CloudBotStorage() {
    }

    public Set<UUID> getWhitelist() {
        return whitelist;
    }
}
