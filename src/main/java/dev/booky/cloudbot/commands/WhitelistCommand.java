package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (16:38 12.10.22)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import dev.booky.cloudbot.util.FloodgateUtil;
import dev.booky.cloudbot.util.MarkdownEscape;
import dev.booky.cloudbot.util.McApiUtil;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class WhitelistCommand extends AbstractBotCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("CloudBot");

    private final ReentrantLock listLock = new ReentrantLock();

    public WhitelistCommand(CloudBotManager manager) {
        super(manager, "whitelist");
    }

    @Override
    protected void buildRequest(ImmutableApplicationCommandRequest.Builder builder) {
        builder
                .description("Access and modify the whitelist on the minecraft server")
                .descriptionLocalizationsOrNull(Map.of("de", "Lässt dich auf die Whitelist des Minecraft Servers zugreifen und bearbeiten"))
                .dmPermission(false)
                .addOption(ApplicationCommandOptionData.builder()
                        .name("java")
                        .description("Whitelists you on the minecraft server")
                        .descriptionLocalizationsOrNull(Map.of("de", "Whitelisted dich auf dem Minecraft Server"))
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("username")
                                .nameLocalizationsOrNull(Map.of("de", "nutzername"))
                                .description("Your minecraft ingame name")
                                .descriptionLocalizationsOrNull(Map.of("de", "Dein Minecraft Ingame-Nutzername"))
                                .type(ApplicationCommandOption.Type.STRING.getValue())
                                .minLength(1).maxLength(16)
                                .required(true)
                                .build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("bedrock")
                        .description("Whitelists you on the minecraft server")
                        .descriptionLocalizationsOrNull(Map.of("de", "Whitelisted dich auf dem Minecraft Server"))
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("gamertag")
                                .description("Your xbox gamertag")
                                .descriptionLocalizationsOrNull(Map.of("de", "Dein Xbox Gamertag"))
                                .type(ApplicationCommandOption.Type.STRING.getValue())
                                .minLength(1).maxLength(16)
                                .required(true)
                                .build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("list")
                        .description("Lists all currently whitelisted players")
                        .descriptionLocalizationsOrNull(Map.of("de", "Listet alle Spieler auf, die sich auf der Whitelist befinden"))
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .build());
    }

    private Mono<Void> list(ChatInputInteractionEvent event, Translator i18n) {
        return event.deferReply().withEphemeral(true).then().and(Mono.defer(() -> {
            this.listLock.lock();

            try {
                StringBuilder builder = new StringBuilder();
                Set<UUID> playerIds = Set.copyOf(this.manager.getStorage().getWhitelist().keySet());

                try {
                    for (UUID playerId : playerIds) {
                        if (!builder.isEmpty()) {
                            builder.append(", ");
                        }

                        String name = null;
                        try {
                            Optional<McApiUtil.McProfile> profile = McApiUtil.loadProfile(playerId);
                            if (profile.isPresent()) {
                                name = profile.get().getUsername();
                            }
                        } catch (IllegalStateException | IllegalArgumentException exception) {
                            LOGGER.error("Error caused while loading username for {}", playerId, exception);
                        }

                        builder.append(MarkdownEscape.escape(
                                Objects.requireNonNullElseGet(name,
                                        () -> playerId.toString().substring(0, 8))));
                    }

                    if (builder.length() > 4096) {
                        String tooManyStr = "... \n> **" + i18n.apply("command.whitelist.list.too-many-players", builder.length()) + "**";
                        builder.delete(4096 - tooManyStr.length(), builder.length()).append(tooManyStr);
                    }
                } catch (Throwable throwable) {
                    LOGGER.error("Error while listing whitelisted players", throwable);
                    builder.append(i18n.apply("command.whitelist.list.error",
                            "`" + MarkdownEscape.codeEscape(throwable.toString()) + "`"));
                }

                return event.createFollowup()
                        .withEmbeds(EmbedCreateSpec.builder()
                                .title(i18n.apply("command.whitelist.list.success", playerIds.size()))
                                .description(builder.toString())
                                .color(Color.CYAN)
                                .build())
                        .then();
            } finally {
                this.listLock.unlock();
            }
        }));
    }

    @Override
    public Mono<Void> run(ChatInputInteractionEvent event, User user, Translator i18n) {
        if (event.getOption("list").isPresent()) {
            return this.list(event, i18n);
        }

        Optional<ApplicationCommandInteractionOption> javaOption = event.getOption("java");
        if (javaOption.isPresent()) {
            return this.addJava(event, user, i18n, javaOption.get());
        }

        Optional<ApplicationCommandInteractionOption> bedrockOption = event.getOption("bedrock");
        if (bedrockOption.isPresent()) {
            return this.addBedrock(event, user, i18n, bedrockOption.get());
        }

        throw new IllegalStateException("Neither list nor java or bedrock options found");
    }

    private Mono<Void> addJava(
            ChatInputInteractionEvent event, User user, Translator i18n,
            ApplicationCommandInteractionOption option
    ) {
        String username = option.getOption("username")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElseThrow();

        try {
            Optional<McApiUtil.McProfile> optProfile = McApiUtil.loadProfile(username);
            if (optProfile.isEmpty()) {
                return event.reply(i18n.apply("command.whitelist.add.error.unknown-user",
                        "`" + MarkdownEscape.codeEscape(username) + "`"));
            }

            McApiUtil.McProfile profile = optProfile.get();
            if (this.manager.getStorage().getWhitelist().containsKey(profile.getUniqueId())) {
                return event.reply(i18n.apply("command.whitelist.add.error.mc-already-whitelisted",
                        "`" + MarkdownEscape.codeEscape(profile.getUsername()) + "`"));
            }
            if (this.manager.getStorage().getBedrockWhitelist().containsValue(user.getId().asLong())
                    || this.manager.getStorage().getWhitelist().containsValue(user.getId().asLong())) {
                boolean bypass = event.getInteraction().getGuildId()
                        .map(user::asMember).flatMap(Mono::blockOptional)
                        .map(Member::getBasePermissions).flatMap(Mono::blockOptional)
                        .map(set -> set.contains(Permission.MANAGE_MESSAGES))
                        .orElse(false);

                if (!bypass) {
                    return event.reply(i18n.apply("command.whitelist.add.error.dc-already-whitelisted"));
                }
            }

            this.manager.updateStorage(storage -> storage.getWhitelist().put(profile.getUniqueId(), user.getId().asLong()));
            return event.reply().withEmbeds(EmbedCreateSpec.builder()
                    .color(Color.GREEN).title(i18n.apply("command.whitelist.add.success.title"))
                    .description(i18n.apply("command.whitelist.add.success.description", user.getMention(),
                            "`" + MarkdownEscape.codeEscape(profile.getUsername()) + "`"))
                    .footer(user.getTag(), user.getAvatarUrl())
                    .thumbnail("https://crafthead.net/helm/" + profile.getUniqueId() + "/128")
                    .timestamp(Instant.now())
                    .build());
        } catch (Throwable throwable) {
            LOGGER.error("Error while adding user '{}' to whitelist", username, throwable);
            return event.reply(i18n.apply("command.whitelist.add.error.general",
                    MarkdownEscape.codeEscape(throwable.toString())));
        }
    }

    private Mono<Void> addBedrock(
            ChatInputInteractionEvent event, User user, Translator i18n,
            ApplicationCommandInteractionOption option
    ) {
        String gamertag = option.getOption("gamertag")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElseThrow();

        try {
            Long xuid = FloodgateUtil.getXuid(gamertag).join();
            String username = "`" + MarkdownEscape.codeEscape(gamertag) + "`";
            if (xuid == null) {
                return event.reply(
                        i18n.apply("command.whitelist.add.error.unknown-user", username) + "\n"
                                + i18n.apply("command.whitelist.add.error.unknown-user.bedrock-note"));
            }

            if (this.manager.getStorage().getBedrockWhitelist().containsKey(xuid)) {
                return event.reply(i18n.apply("command.whitelist.add.error.mc-already-whitelisted", username));
            }
            if (this.manager.getStorage().getBedrockWhitelist().containsValue(user.getId().asLong())
                    || this.manager.getStorage().getWhitelist().containsValue(user.getId().asLong())) {
                boolean bypass = event.getInteraction().getGuildId()
                        .map(user::asMember).flatMap(Mono::blockOptional)
                        .map(Member::getBasePermissions).flatMap(Mono::blockOptional)
                        .map(set -> set.contains(Permission.MANAGE_MESSAGES))
                        .orElse(false);
                if (!bypass) {
                    return event.reply(i18n.apply("command.whitelist.add.error.dc-already-whitelisted"));
                }
            }
            this.manager.updateStorage(storage -> storage.getBedrockWhitelist().put(xuid, user.getId().asLong()));

            UUID javaXuid = FloodgateUtil.createJavaUniqueId(xuid);
            return event.reply().withEmbeds(EmbedCreateSpec.builder()
                    .color(Color.GREEN).title(i18n.apply("command.whitelist.add.success.title"))
                    .description(i18n.apply("command.whitelist.add.success.description", user.getMention(),
                            username))
                    .footer(user.getTag(), user.getAvatarUrl())
                    .thumbnail("https://api.tydiumcraft.net/v1/players/skin?uuid=" + javaXuid + "&type=avatar&size=128")
                    .timestamp(Instant.now())
                    .build());
        } catch (Throwable throwable) {
            LOGGER.error("Error while adding user '{}' to whitelist", gamertag, throwable);
            return event.reply(i18n.apply("command.whitelist.add.error.general",
                    MarkdownEscape.codeEscape(throwable.toString())));
        }
    }
}
