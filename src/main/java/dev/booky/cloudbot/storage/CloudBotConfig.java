package dev.booky.cloudbot.storage;
// Created by booky10 in CloudBot (16:08 10.10.22)

import com.google.common.base.Preconditions;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.User;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.object.presence.Status;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackReplyMono;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.discordjson.possible.Possible;
import discord4j.gateway.ShardInfo;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("FieldMayBeFinal") // configurate
@ConfigSerializable
public class CloudBotConfig {

    private String token = "REPLACE_ME";

    private boolean whitelistActive = false;
    private String inviteLink = "null";

    private long mainGuildId = -1L;
    private long logChannelId = -1L;
    private long teamRoleId = -1L;

    private boolean trackInvites = true;

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

        private boolean allowBots = true;
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
            return replaceProps(message, props);
        }

        public boolean isAllowBots() {
            return this.allowBots;
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
        private Set<Permission> permissions = PermissionSet.none();

        @ConfigSerializable
        public static final class CommandResponse {

            private ResponseType responseType = ResponseType.EMPTY;
            private String content = null;
            private String title = null;
            private Color color = null;

            private CommandResponse() {
            }

            public enum ResponseType {

                EMPTY {
                    @Override
                    protected InteractionApplicationCommandCallbackReplyMono reply0(
                            InteractionApplicationCommandCallbackReplyMono reply, User user, CommandResponse data) {
                        return reply.withContent("\u200B");
                    }
                },
                MESSAGE {
                    @Override
                    protected InteractionApplicationCommandCallbackReplyMono reply0(
                            InteractionApplicationCommandCallbackReplyMono reply, User user, CommandResponse data) {
                        Preconditions.checkState(data.content != null, "No content specified in response data");
                        return reply.withContent(data.content);
                    }
                },
                EMBED {
                    @Override
                    protected InteractionApplicationCommandCallbackReplyMono reply0(
                            InteractionApplicationCommandCallbackReplyMono reply, User user, CommandResponse data) {
                        Preconditions.checkState(data.content != null || data.title != null,
                                "No content and no title specified in response data");
                        return reply.withEmbeds(EmbedCreateSpec.builder()
                                .description(ofNullable(data.content))
                                .title(ofNullable(data.title))
                                .color(ofNullable(data.color))
                                .footer(user.getTag(), user.getAvatarUrl())
                                .timestamp(Instant.now())
                                .build());
                    }
                };

                protected abstract InteractionApplicationCommandCallbackReplyMono reply0(
                        InteractionApplicationCommandCallbackReplyMono reply, User user, CommandResponse data);

                protected Mono<Void> reply(ChatInputInteractionEvent event, Optional<User> target, CommandResponse data) {
                    InteractionApplicationCommandCallbackReplyMono reply = this.reply0(
                            event.reply(), event.getInteraction().getUser(), data);

                    if (target.isEmpty()) {
                        return reply.withEphemeral(true);
                    }

                    String content = (target.get().getMention() + " " + reply.contentOrElse("")).trim();
                    reply = reply.withContent(content).withEphemeral(false);
                    reply = reply.withAllowedMentions(AllowedMentions.builder().allowUser(target.get().getId()).build());
                    return reply;
                }
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
            if (this.permissions.isEmpty()) {
                builder.defaultPermission(true);
            } else {
                PermissionSet permissions = PermissionSet.of(this.permissions.toArray(new Permission[0]));
                builder.defaultMemberPermissions(Long.toString(permissions.getRawValue()));
            }

            builder.addOption(ApplicationCommandOptionData.builder()
                    .name("target")
                    .nameLocalizationsOrNull(Map.of("de", "ziel"))
                    .description("The user to mention on execution")
                    .descriptionLocalizationsOrNull(Map.of("de", "Der Nutzer, welcher bei Ausführung gepingt werden soll"))
                    .type(ApplicationCommandOption.Type.USER.getValue())
                    .required(false)
                    .build());

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

            Optional<User> target = event.getOption("target")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asUser)
                    .flatMap(Mono::blockOptional);
            return response.responseType.reply(event, target, response);
        }
    }

    private Map<MessageRef, Set<ReactionRole>> reactionRoles = Map.of();

    @ConfigSerializable
    public static final class ReactionRole {

        private String emoji = "\u2705";
        private long roleId = -1L;

        private ReactionRole() {
        }

        public String getEmoji() {
            return this.emoji;
        }

        public long getRoleId() {
            return this.roleId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ReactionRole role)) return false;
            if (this.roleId != role.roleId) return false;
            return Objects.equals(this.emoji, role.emoji);
        }

        @Override
        public int hashCode() {
            int result = this.emoji != null ? this.emoji.hashCode() : 0;
            result = 31 * result + (int) (this.roleId ^ (this.roleId >>> 32));
            return result;
        }
    }

    private Presence presence = new Presence();

    @ConfigSerializable
    public static final class Presence {

        private Status status = Status.ONLINE;

        private Activity.Type activityType = Activity.Type.UNKNOWN;
        private String activityName = "none";
        private String activityUrl = null;

        private Presence() {
        }

        public @Nullable ClientActivity buildActivity(ShardInfo shardInfo) {
            if (this.activityType == Activity.Type.UNKNOWN) {
                return null;
            }

            Map<String, ?> props = Map.of(
                    "player_count", Bukkit.getOnlinePlayers().size(),
                    "max_players", Bukkit.getMaxPlayers(),
                    "shard_index", shardInfo.getIndex(),
                    "shard_count", shardInfo.getCount(),
                    "shard_str", shardInfo.getIndex() + "/" + shardInfo.getCount());

            String name = replaceProps(this.activityName, props);
            String url = replaceProps(this.activityUrl, props);
            return ClientActivity.of(this.activityType, name, url);
        }

        public Status getStatus() {
            return this.status;
        }
    }

    @SuppressWarnings("unused") // configurate
    private CloudBotConfig() {
    }

    private static String replaceProps(String string, Map<String, ?> props) {
        for (Map.Entry<String, ?> prop : props.entrySet()) {
            String key = "${" + prop.getKey() + "}";
            String val = String.valueOf(prop.getValue());
            string = string.replace(key, val);
        }
        return string;
    }

    public ClientPresence buildPresence(ShardInfo shardInfo) {
        return ClientPresence.of(this.presence.status, this.presence.buildActivity(shardInfo));
    }

    public String getToken() {
        return this.token;
    }

    public boolean isWhitelistActive() {
        return this.whitelistActive;
    }

    public @Nullable String getInviteLink() {
        return "null".equals(this.inviteLink) ? null : this.inviteLink;
    }

    public long getMainGuildId() {
        return this.mainGuildId;
    }

    public long getLogChannelId() {
        return this.logChannelId;
    }

    public long getTeamRoleId() {
        return this.teamRoleId;
    }

    public boolean isTrackInvites() {
        return this.trackInvites;
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

    public Map<MessageRef, Set<ReactionRole>> getReactionRoles() {
        return this.reactionRoles;
    }
}
