package dev.booky.cloudbot.util;
// Created by booky10 in CloudBot (16:08 10.10.22)

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class CloudBotConfig {

    private String token = "REPLACE_ME";

    @SuppressWarnings("unused") // configurate
    private CloudBotConfig() {
    }

    public String getToken() {
        return token;
    }
}
