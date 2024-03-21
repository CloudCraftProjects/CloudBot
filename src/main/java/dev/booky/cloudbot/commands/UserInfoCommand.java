package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (15:56 22.10.22)

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import dev.booky.cloudbot.util.MarkdownEscape;
import dev.booky.cloudbot.util.McApiUtil;
import dev.booky.cloudbot.util.McApiUtil.McProfile;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

import static discord4j.rest.util.Image.Format.GIF;
import static discord4j.rest.util.Image.Format.PNG;

public final class UserInfoCommand extends AbstractBotCommand {

    public UserInfoCommand(CloudBotManager manager) {
        super(manager, "user");
    }

    @Override
    protected void buildRequest(ImmutableApplicationCommandRequest.Builder builder) {
        builder
                .description("Show info about the specified user")
                .descriptionLocalizationsOrNull(Map.of("de", "Gucke dir die Informationen über den angegebenen Nutzer an"))
                .defaultMemberPermissions(Long.toString(PermissionSet.of(Permission.MANAGE_MESSAGES).getRawValue()))
                .dmPermission(false)
                .addOption(ApplicationCommandOptionData.builder()
                        .name("discord")
                        .description("Shows the info of a specified discord user")
                        .descriptionLocalizationsOrNull(Map.of("de", "Gucke dir die Informationen über den angegebenen Discord-Nutzer an"))
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("user")
                                .nameLocalizationsOrNull(Map.of("de", "nutzer"))
                                .description("The discord user to show the info of")
                                .descriptionLocalizationsOrNull(Map.of("de", "Der Discord-Nutzer, von dem die Infos angezeigt werden sollen"))
                                .type(ApplicationCommandOption.Type.USER.getValue())
                                .required(true)
                                .build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("minecraft")
                        .description("Shows the info of a specified minecraft user")
                        .descriptionLocalizationsOrNull(Map.of("de", "Gucke dir die Informationen über den angegebenen Minecraft-Nutzer an"))
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("username")
                                .nameLocalizationsOrNull(Map.of("de", "nutzername"))
                                .description("The minecraft user to show the info of")
                                .descriptionLocalizationsOrNull(Map.of("de", "Der Minecraft-Nutzer, von dem die Infos angezeigt werden sollen"))
                                .type(ApplicationCommandOption.Type.STRING.getValue())
                                .minLength(3).maxLength(16)
                                .required(true)
                                .build())
                        .build());
    }

    @Override
    public Mono<Void> run(ChatInputInteractionEvent event, User user, Translator i18n) {
        if (event.getOption("discord").isPresent()) {
            Optional<User> target = event.getOption("discord")
                    .flatMap(option -> option.getOption("user"))
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asUser)
                    .flatMap(Mono::blockOptional);
            return this.showDiscordInfo(event, user, OptionalLong.empty(), target);
        }

        McProfile target = event.getOption("minecraft")
                .flatMap(option -> option.getOption("username"))
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .map(McApiUtil::loadProfile)
                .orElseThrow();
        return this.showMinecraftInfo(event, user, target);
    }

    private Mono<Void> showMinecraftInfo(ChatInputInteractionEvent event, User user, McProfile targetProfile) {
        Long targetId = this.manager.getStorage().getWhitelist().get(targetProfile.getUniqueId());
        if (targetId == null) {
            throw new IllegalStateException("User '" + targetProfile.getUsername() + "' is not on whitelist");
        }

        Optional<User> target = event.getClient().getUserById(Snowflake.of(targetId)).blockOptional();
        return this.showDiscordInfo(event, user, OptionalLong.of(targetId), target);
    }

    private Mono<Void> showDiscordInfo(ChatInputInteractionEvent event, User user,
                                       OptionalLong inputTargetId, Optional<User> optTarget) {
        OptionalLong targetId = optTarget.map(target -> OptionalLong.of(target.getId().asLong())).orElse(inputTargetId);
        if (targetId.isEmpty()) {
            throw new IllegalArgumentException("Can't show discord info for non-existing target");
        }

        List<McProfile> profiles = this.manager.getStorage().getWhitelist().entrySet().stream()
                .filter(entry -> entry.getValue() == targetId.getAsLong())
                .map(Map.Entry::getKey).map(McApiUtil::loadProfile)
                .toList();

        StringBuilder description = new StringBuilder();

        if (optTarget.isPresent()) {
            User target = optTarget.get();
            Optional<Member> optMember = target.asMember(event.getInteraction().getGuildId().orElseThrow())
                    .onErrorResume(error -> Mono.empty()).blockOptional();
            Optional<String> guildAvatar = optMember.flatMap(member -> member.getGuildAvatarUrl(member.hasAnimatedGuildAvatar() ? GIF : PNG));
            Optional<String> nickname = optMember.flatMap(Member::getNickname).map(MarkdownEscape::codeEscape);
            Optional<Long> joinTime = optMember.flatMap(Member::getJoinTime).map(Instant::getEpochSecond);
            long createTime = target.getId().getTimestamp().getEpochSecond();

            description.append("> **Discord Info**\n")
                    .append(target.getGlobalName().map(name -> "Displayname: `" + MarkdownEscape.codeEscape(name) + "`\n").orElse(""))
                    .append("Username: `").append(MarkdownEscape.codeEscape(target.getTag())).append("`\n")
                    .append(nickname.map(name -> "Nickname: `" + name + "`\n").orElse(""))
                    .append("Id: `").append(target.getId().asString()).append("`\n")
                    .append("Mention: ").append(target.getMention()).append("\n")
                    .append("User-Avatar: ").append(target.getAvatarUrl()).append("\n")
                    .append("Default-Avatar: ").append(target.getDefaultAvatarUrl()).append("\n")
                    .append(guildAvatar.map(value -> "Guild-Avatar: " + value + "\n").orElse(""))
                    .append(target.getBannerUrl().isPresent() ? "Banner: " + target.getBannerUrl().orElseThrow() + "\n" : "")
                    .append("Created: <t:").append(createTime).append(":f> (<t:").append(createTime).append(":R>)\n")
                    .append(joinTime.map(time -> "Joined: <t:" + time + ":f> (<t:" + time + ":R>)\n").orElse(""))
                    .append("Flags: ").append(target.getPublicFlags().stream().map(flag -> "`" + flag.name() + "`").collect(Collectors.joining(", ")));
        }

        if (!profiles.isEmpty()) {
            BanList<PlayerProfile> banlist = Bukkit.getBanList(BanList.Type.PROFILE);
            description.append("\n\n");

            for (McProfile profile : profiles) {
                description
                        .append("Whitelisted User: `")
                        .append(profile.getUsername())
                        .append("` (`")
                        .append(profile.getUniqueId())
                        .append("`)\n");

                BanEntry<PlayerProfile> entry = banlist.getBanEntry(profile.createBukkit());
                if (entry == null) {
                    continue;
                }

                long created = entry.getCreated().getTime();
                long expiration = entry.getExpiration() == null ? -1 : entry.getExpiration().getTime();

                description
                        .append("> **Banned** by `")
                        .append(MarkdownEscape.codeEscape(entry.getSource()))
                        .append('`');

                if (expiration == -1) {
                    description.append(" (**Permanent**)");
                }
                description.append(":\n");

                description
                        .append("> Reason: `")
                        .append(MarkdownEscape.codeEscape(entry.getReason()))
                        .append("`\n");
                description
                        .append("> Since: <t:")
                        .append(created / 1000)
                        .append(":f> (<t:")
                        .append(created / 1000)
                        .append(":R>)\n");

                if (expiration != -1) {
                    description
                            .append("> Expires: <t:")
                            .append(expiration / 1000)
                            .append(":f> (<t:")
                            .append(expiration / 1000)
                            .append(":R>)\n");
                }
            }
        }

        return event.reply().withEphemeral(true)
                .withEmbeds(EmbedCreateSpec.builder()
                        .description(description.toString())
                        .color(Color.DISCORD_BLACK)
                        .thumbnail(optTarget.map(User::getAvatarUrl).map(Possible::of).orElseGet(Possible::absent))
                        .footer(user.getTag(), user.getAvatarUrl())
                        .timestamp(Instant.now())
                        .build());
    }
}
