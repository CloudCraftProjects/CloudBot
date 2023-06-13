package dev.booky.cloudbot.storage;
// Created by booky10 in CloudBot (16:08 10.10.22)

import com.google.common.base.Preconditions;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.util.Color;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            return this.getMessage(Map.of());
        }

        public @Nullable String getMessage(Map<String, ?> props) {
            if (this.messages.isEmpty()) {
                return null;
            }

            int randomIndex = ThreadLocalRandom.current().nextInt(this.messages.size());
            String message = this.messages.get(randomIndex);

            for (Map.Entry<String, ?> prop : props.entrySet()) {
                String key = "${" + prop.getKey() + "}";
                String val = String.valueOf(prop.getValue());
                message = message.replace(key, val);
            }
            return message;
        }

        public long getChannelId() {
            return this.channelId;
        }
    }

    private Map<String, CustomCommand> customCommands = Map.of();

    @ConfigSerializable
    public static final class CustomCommand {

        private String description = null;
        private Map<String, String> l10nDescription = null;
        private CommandResponse response = new CommandResponse();
        private Map<String, CommandResponse> l10nResponse = null;

        @ConfigSerializable
        public static final class CommandResponse {

            private boolean ephemeral = true;
            private ResponseType responseType = ResponseType.EMPTY;
            private String content = null;
            private String title = null;
            private Color color = null;

            private CommandResponse() {
            }

            public enum ResponseType {

                EMPTY {
                    @Override
                    protected Mono<Void> reply(ChatInputInteractionEvent event, CommandResponse data) {
                        return event.reply("\u200B")
                                .withEphemeral(data.ephemeral);
                    }
                },
                MESSAGE {
                    @Override
                    protected Mono<Void> reply(ChatInputInteractionEvent event, CommandResponse data) {
                        Preconditions.checkState(data.content != null, "No content specified in response data");
                        return event.reply(data.content)
                                .withEphemeral(data.ephemeral);
                    }
                },
                EMBED {
                    @Override
                    protected Mono<Void> reply(ChatInputInteractionEvent event, CommandResponse data) {
                        Preconditions.checkState(data.content != null || data.title != null,
                                "No content and no title specified in response data");
                        User user = event.getInteraction().getUser();
                        return event.reply()
                                .withEphemeral(data.ephemeral)
                                .withEmbeds(EmbedCreateSpec.builder()
                                        .description(ofNullable(data.content))
                                        .title(ofNullable(data.title))
                                        .color(ofNullable(data.color))
                                        .footer(user.getTag(), user.getAvatarUrl())
                                        .timestamp(Instant.now())
                                        .build());
                    }
                };

                protected abstract Mono<Void> reply(ChatInputInteractionEvent event, CommandResponse data);
            }
        }

        private CustomCommand() {
        }

        private static <T> Possible<T> ofNullable(T val) {
            if (val != null) {
                return Possible.of(val);
            }
            return Possible.absent();
        }

        public ApplicationCommandRequest buildRequest(String label) {
            ImmutableApplicationCommandRequest.Builder builder = ApplicationCommandRequest.builder().name(label)
                    .description(ofNullable(this.description));
            if (this.l10nDescription != null) {
                builder.descriptionLocalizationsOrNull(this.l10nDescription);
            }
            return builder.build();
        }

        public Mono<Void> run(ChatInputInteractionEvent event) {
            CommandResponse response = this.response;
            if (this.l10nResponse != null && !this.l10nResponse.isEmpty()) {
                Locale locale = new Locale(event.getInteraction().getUserLocale());
                CommandResponse l10nResponse = this.l10nResponse.get(locale.getLanguage());
                if (l10nResponse != null) {
                    response = l10nResponse;
                }
            }
            return response.responseType.reply(event, response);
        }
    }

    private Map<Long, ReactionRole> reactionRoles = Map.of();

    @ConfigSerializable
    public static final class ReactionRole {

        private long channelId = -1L;
        private String emoji = "\u2705";
        private long roleId = -1L;

        private ReactionRole() {
        }

        public long getChannelId() {
            return this.channelId;
        }

        public String getEmoji() {
            return this.emoji;
        }

        public long getRoleId() {
            return this.roleId;
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

    public Map<String, CustomCommand> getCustomCommands() {
        return this.customCommands;
    }

    public Map<Long, ReactionRole> getReactionRoles() {
        return this.reactionRoles;
    }
}
