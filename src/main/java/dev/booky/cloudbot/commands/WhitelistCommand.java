package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (16:38 12.10.22)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import dev.booky.cloudbot.util.McApiUtil;
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
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class WhitelistCommand implements BotCommand {

    private final ReentrantLock listLock = new ReentrantLock();

    @Override
    public ApplicationCommandRequest provideCommandData() {
        return ApplicationCommandRequest.builder()
                .name("whitelist")
                .description("Access and modify the whitelist on the minecraft server")
                .descriptionLocalizationsOrNull(Map.of("de", "Lässt dich auf die Whitelist des Minecraft Servers zugreifen und bearbeiten"))
                .dmPermission(false)
                .addOption(ApplicationCommandOptionData.builder()
                        .name("add")
                        .description("Whitelists you on the minecraft server")
                        .descriptionLocalizationsOrNull(Map.of("de", "Whitelisted dich auf dem Minecraft Server"))
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("username")
                                .nameLocalizationsOrNull(Map.of("de", "nutzername"))
                                .description("Your minecraft ingame name")
                                .descriptionLocalizationsOrNull(Map.of("de", "Dein Minecraft Ingame-Nutzername"))
                                .type(ApplicationCommandOption.Type.STRING.getValue())
                                .minLength(3).maxLength(16)
                                .required(true)
                                .build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("list")
                        .description("Lists all currently whitelisted players")
                        .descriptionLocalizationsOrNull(Map.of("de", "Listet alle Spieler auf, die sich auf der Whitelist befinden"))
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .build())
                .build();
    }

    @Override
    public Publisher<Void> run(CloudBotManager manager, String label, ChatInputInteractionEvent event, User user, Translator i18n) {
        if (event.getOption("list").isPresent()) {
            return event.deferReply().withEphemeral(true).then(Mono.fromRunnable(() -> {
                this.listLock.lock();

                try {
                    StringBuilder builder = new StringBuilder();
                    Set<UUID> playerIds = manager.getStorage().getWhitelist().keySet();

                    try {
                        for (UUID playerId : playerIds) {
                            if (!builder.isEmpty()) {
                                builder.append(", ");
                            }

                            String name = McApiUtil.loadProfile(playerId).getUsername();
                            if (name != null) {
                                name = name.replace("_", "\\_");
                            } else {
                                name = playerId.toString().substring(0, 8);
                            }

                            builder.append(name);
                        }

                        if (builder.length() > 4096) {
                            String tooManyStr = "... \n> **" + i18n.apply("command.whitelist.list.too-many-players", builder.length()) + "**";
                            builder.delete(4096 - tooManyStr.length(), builder.length()).append(tooManyStr);
                        }
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                        builder.append(i18n.apply("command.whitelist.list.error", throwable));
                    }

                    event.createFollowup()
                            .withEmbeds(EmbedCreateSpec.builder()
                                    .title(i18n.apply("command.whitelist.list.success", playerIds.size()))
                                    .description(builder.toString())
                                    .color(Color.CYAN)
                                    .build())
                            .block();
                } finally {
                    listLock.unlock();
                }
            }));
        }

        Optional<ApplicationCommandInteractionOption> addOption = event.getOption("add");
        if (addOption.isEmpty()) {
            throw new IllegalStateException("Neither list, nor add options are supplied");
        }

        String username = addOption
                .flatMap(opt -> opt.getOption("username"))
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElseThrow();

        try {
            McApiUtil.McProfile profile = McApiUtil.loadProfile(username);
            if (manager.getStorage().getWhitelist().containsKey(profile.getUniqueId())) {
                throw new IllegalArgumentException(profile + " is already whitelisted");
            }
            if (manager.getStorage().getWhitelist().containsValue(user.getId().asLong())) {
                boolean bypass = event.getInteraction().getGuildId()
                        .map(user::asMember).flatMap(Mono::blockOptional)
                        .map(Member::getBasePermissions).flatMap(Mono::blockOptional)
                        .map(set -> set.contains(Permission.MANAGE_MESSAGES))
                        .orElse(false);

                if (!bypass) {
                    throw new IllegalArgumentException(user.getTag() + " has already whitelisted someone");
                }
            }

            manager.updateStorage(storage -> storage.getWhitelist().put(profile.getUniqueId(), user.getId().asLong()));
            return event.reply().withEmbeds(EmbedCreateSpec.builder()
                    .color(Color.GREEN).title(i18n.apply("command.whitelist.add.success.title"))
                    .description(i18n.apply("command.whitelist.add.success.description", user.getMention(), profile.getUsername()))
                    .timestamp(Instant.now()).footer(user.getTag(), user.getAvatarUrl())
                    .thumbnail("https://crafthead.net/helm/" + profile.getUniqueId() + "/128")
                    .build());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return event.reply(i18n.apply("command.whitelist.add.error", throwable));
        }
    }
}
