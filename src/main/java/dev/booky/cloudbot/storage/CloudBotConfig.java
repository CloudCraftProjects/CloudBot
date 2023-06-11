package dev.booky.cloudbot.storage;
// Created by booky10 in CloudBot (16:08 10.10.22)

import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

    private RandomMessages joinMessages = new RandomMessages();
    private RandomMessages leaveMessages = new RandomMessages();

    @ConfigSerializable
    public static final class RandomMessages {

        private long channelId = -1L;
        private List<String> messages = List.of();

        private RandomMessages() {
        }

        public @Nullable String getMessage() {
            if (this.messages.isEmpty()) {
                return null;
            }

            int randomIndex = ThreadLocalRandom.current().nextInt(this.messages.size());
            return this.messages.get(randomIndex);
        }

        public long getChannelId() {
            return this.channelId;
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

    public RandomMessages getJoinMessages() {
        return this.joinMessages;
    }

    public RandomMessages getLeaveMessages() {
        return this.leaveMessages;
    }
}
