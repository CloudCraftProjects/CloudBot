package dev.booky.cloudbot.storage;
// Created by booky10 in CloudBot (16:08 10.10.22)

import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@SuppressWarnings("FieldMayBeFinal") // configurate
@ConfigSerializable
public class CloudBotConfig {

    private String token = "REPLACE_ME";

    private boolean whitelistActive = true;
    private String inviteLink = "null";

    private long mainGuildId = -1L;
    private long logChannelId = -1L;
    private long teamRoleId = -1L;

    private MemberCounter memberCounter = new MemberCounter();

    @ConfigSerializable
    public static final class MemberCounter {

        private long channelId = -1L;
        private boolean excludeBots = true;
        private String format = "\uD83C\uDF0E\u2502Members: %s";

        private MemberCounter() {
        }

        public long getChannelId() {
            return this.channelId;
        }

        public boolean isExcludeBots() {
            return this.excludeBots;
        }

        public String getFormat() {
            return this.format;
        }
    }

    @SuppressWarnings("unused") // configurate
    private CloudBotConfig() {
    }

    public String getToken() {
        return this.token;
    }

    public boolean isWhitelistActive() {
        return whitelistActive;
    }

    public @Nullable String getInviteLink() {
        return "null".equals(this.inviteLink) ? null : this.inviteLink;
    }

    public long getMainGuildId() {
        return mainGuildId;
    }

    public long getLogChannelId() {
        return logChannelId;
    }

    public long getTeamRoleId() {
        return this.teamRoleId;
    }

    public MemberCounter getMemberCounter() {
        return this.memberCounter;
    }
}
