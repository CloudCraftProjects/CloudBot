package dev.booky.cloudbot.storage;
// Created by booky10 in CloudBot (16:08 10.10.22)

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@SuppressWarnings("FieldMayBeFinal") // configurate
@ConfigSerializable
public class CloudBotConfig {

    private String token = "REPLACE_ME";
    private String inviteLink = "null";
    private boolean whitelistActive = true;

    @SuppressWarnings("unused") // configurate
    private CloudBotConfig() {
    }

    public String getToken() {
        return this.token;
    }

    public String getInviteLink() {
        return "null".equals(this.inviteLink) ? null : this.inviteLink;
    }

    public boolean isWhitelistActive() {
        return whitelistActive;
    }
}
