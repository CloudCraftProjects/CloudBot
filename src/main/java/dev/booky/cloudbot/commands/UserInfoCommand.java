package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (15:56 22.10.22)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import dev.booky.cloudbot.util.MarkdownEscape;
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
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static discord4j.rest.util.Image.Format.GIF;
import static discord4j.rest.util.Image.Format.PNG;

public class UserInfoCommand implements BotCommand {

    @Override
    public ApplicationCommandRequest provideCommandData() {
        return ApplicationCommandRequest.builder()
                .name("user")
                .description("Show the info of a specified user")
                .descriptionLocalizationsOrNull(Map.of("de", "Gucke dir die Informationen über den angegebenen User an"))
                .defaultMemberPermissions(Long.toString(PermissionSet.of(Permission.MANAGE_MESSAGES).getRawValue()))
                .dmPermission(false)
                .addOption(ApplicationCommandOptionData.builder()
                        .name("user")
                        .nameLocalizationsOrNull(Map.of("de", "nutzer"))
                        .description("The user to show the info of")
                        .descriptionLocalizationsOrNull(Map.of("de", "Der Nutzer, von dem die Infos angezeigt werden sollen"))
                        .type(ApplicationCommandOption.Type.USER.getValue())
                        .required(true)
                        .build())
                .build();
    }

    @Override
    public Publisher<Void> run(CloudBotManager manager, String label, ChatInputInteractionEvent event, User user, Translator i18n) {
        User target = event.getOption("user")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asUser)
                .flatMap(Mono::blockOptional)
                .orElseThrow();

        Member targetMember = target.asMember(event.getInteraction().getGuildId().orElseThrow()).blockOptional().orElseThrow();
        String guildAvatar = targetMember.getGuildAvatarUrl(targetMember.hasAnimatedGuildAvatar() ? GIF : PNG).orElse(null);
        Long joinTime = targetMember.getJoinTime().map(Instant::getEpochSecond).orElse(null);
        long createTime = targetMember.getId().getTimestamp().getEpochSecond();

        return event.reply().withEphemeral(true)
                .withEmbeds(EmbedCreateSpec.builder()
                        .description("" +
                                "Username: `" + MarkdownEscape.codeEscape(target.getUsername()) + "`\n" +
                                (targetMember.getNickname().isPresent() ? "Nickname: `" + targetMember.getNickname().map(MarkdownEscape::codeEscape).orElseThrow() + "`\n" : "") +
                                "Discriminator: `#" + target.getDiscriminator() + "`\n" +
                                "Id: `" + target.getId().asString() + "`\n" +
                                "Mention: " + target.getMention() + "\n" +
                                "User-Avatar: " + target.getAvatarUrl() + "\n" +
                                "Default-Avatar: " + target.getDefaultAvatarUrl() + "\n" +
                                (guildAvatar != null ? "Guild-Avatar: " + guildAvatar + "\n" : "") +
                                (target.getBannerUrl().isPresent() ? "Banner: " + target.getBannerUrl().orElseThrow() + "\n" : "") +
                                "Created: <t:" + createTime + ":f> (<t:" + createTime + ":R>)\n" +
                                (joinTime != null ? "Joined: <t:" + joinTime + ":f> (<t:" + joinTime + ":R>)\n" : "") +
                                "Flags: " + target.getPublicFlags().stream().map(flag -> "`" + flag.name() + "`").collect(Collectors.joining(", "))
                        )
                        .color(Color.DISCORD_BLACK)
                        .thumbnail(target.getAvatarUrl())
                        .footer(user.getTag(), user.getAvatarUrl())
                        .timestamp(Instant.now())
                        .build());
    }
}
