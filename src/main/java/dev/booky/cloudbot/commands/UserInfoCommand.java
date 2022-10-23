package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (15:56 22.10.22)

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
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static discord4j.rest.util.Image.Format.GIF;
import static discord4j.rest.util.Image.Format.PNG;

public class UserInfoCommand implements BotCommand {

    @Override
    public ApplicationCommandRequest provideCommandData() {
        return ApplicationCommandRequest.builder()
                .name("user")
                .description("Show the info of a specified user")
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
                        .build())
                .build();
    }

    @Override
    public Publisher<Void> run(CloudBotManager manager, String label, ChatInputInteractionEvent event, User user, Translator i18n) {
        if (event.getOption("discord").isPresent()) {
            User target = event.getOption("discord")
                    .flatMap(option -> option.getOption("user"))
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asUser)
                    .flatMap(Mono::blockOptional)
                    .orElseThrow();
            return showDiscordInfo(manager, event, user, target);
        }

        McProfile target = event.getOption("minecraft")
                .flatMap(option -> option.getOption("username"))
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .map(McApiUtil::loadProfile)
                .orElseThrow();
        return showMinecraftInfo(manager, event, user, target);
    }

    private Publisher<Void> showMinecraftInfo(CloudBotManager manager, ChatInputInteractionEvent event, User user, McProfile targetProfile) {
        Long targetId = manager.getStorage().getWhitelist().get(targetProfile.getUniqueId());
        if (targetId == null) {
            throw new IllegalStateException("User '" + targetProfile.getUsername() + "' is not on whitelist");
        }

        User target = event.getClient().getUserById(Snowflake.of(targetId)).block();
        if (target == null) {
            throw new IllegalStateException("User '" + targetId + "' can't be found");
        }

        return showDiscordInfo(manager, event, user, target);
    }

    private Publisher<Void> showDiscordInfo(CloudBotManager manager, ChatInputInteractionEvent event, User user, User target) {
        Optional<Member> optMember = target.asMember(event.getInteraction().getGuildId().orElseThrow()).blockOptional();
        Optional<String> guildAvatar = optMember.flatMap(member -> member.getGuildAvatarUrl(member.hasAnimatedGuildAvatar() ? GIF : PNG));
        Optional<String> nickname = optMember.flatMap(Member::getNickname).map(MarkdownEscape::codeEscape);
        Optional<Long> joinTime = optMember.flatMap(Member::getJoinTime).map(Instant::getEpochSecond);
        long createTime = target.getId().getTimestamp().getEpochSecond();

        List<McProfile> profiles = manager.getStorage().getWhitelist().entrySet().stream()
                .filter(entry -> entry.getValue() == target.getId().asLong())
                .map(Map.Entry::getKey).map(McApiUtil::loadProfile)
                .toList();

        StringBuilder description = new StringBuilder("" +
                "> **Discord Info**\n" +
                "Username: `" + MarkdownEscape.codeEscape(target.getUsername()) + "`\n" +
                (nickname.map(name -> "Nickname: `" + name + "`\n").orElse("")) +
                "Discriminator: `#" + target.getDiscriminator() + "`\n" +
                "Id: `" + target.getId().asString() + "`\n" +
                "Mention: " + target.getMention() + "\n" +
                "User-Avatar: " + target.getAvatarUrl() + "\n" +
                "Default-Avatar: " + target.getDefaultAvatarUrl() + "\n" +
                (guildAvatar.map(value -> "Guild-Avatar: " + value + "\n").orElse("")) +
                (target.getBannerUrl().isPresent() ? "Banner: " + target.getBannerUrl().orElseThrow() + "\n" : "") +
                "Created: <t:" + createTime + ":f> (<t:" + createTime + ":R>)\n" +
                (joinTime.map(time -> "Joined: <t:" + time + ":f> (<t:" + time + ":R>)\n").orElse("")) +
                "Flags: " + target.getPublicFlags().stream().map(flag -> "`" + flag.name() + "`").collect(Collectors.joining(", ")));

        if (!profiles.isEmpty()) {
            BanList banlist = Bukkit.getBanList(BanList.Type.NAME);
            description.append("\n\n");

            for (McProfile profile : profiles) {
                description
                        .append("Whitelisted User: `")
                        .append(profile.getUsername())
                        .append("` (`")
                        .append(profile.getUniqueId())
                        .append("`)\n");

                BanEntry entry = banlist.getBanEntry(profile.getUniqueId().toString());
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
                        .thumbnail(target.getAvatarUrl())
                        .footer(user.getTag(), user.getAvatarUrl())
                        .timestamp(Instant.now())
                        .build());
    }
}
