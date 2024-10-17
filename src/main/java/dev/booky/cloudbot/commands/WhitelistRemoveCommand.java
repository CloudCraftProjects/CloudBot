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
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

public final class WhitelistRemoveCommand extends AbstractBotCommand {

    public WhitelistRemoveCommand(CloudBotManager manager) {
        super(manager, "whitelist-remove");
    }

    @Override
    protected void buildRequest(ImmutableApplicationCommandRequest.Builder builder) {
        builder
                .description("Remove someone from the whitelist of the minecraft server")
                .descriptionLocalizationsOrNull(Map.of("de", "Entfernt jemanden von der Whitelist des Minecraft Servers"))
                .defaultMemberPermissions(Long.toString(PermissionSet.of(Permission.MANAGE_MESSAGES).getRawValue()))
                .dmPermission(false)
                .addOption(ApplicationCommandOptionData.builder()
                        .name("java")
                        .description("Remove someone from the whitelist of the minecraft server")
                        .descriptionLocalizationsOrNull(Map.of("de", "Entfernt jemanden von der Whitelist des Minecraft Servers"))
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("username")
                                .nameLocalizationsOrNull(Map.of("de", "nutzername"))
                                .description("The ingame name of the player")
                                .descriptionLocalizationsOrNull(Map.of("de", "Der Minecraft Ingame-Nutzername"))
                                .type(ApplicationCommandOption.Type.STRING.getValue())
                                .minLength(1).maxLength(16)
                                .required(true)
                                .build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("bedrock")
                        .description("Remove someone from the whitelist of the minecraft server")
                        .descriptionLocalizationsOrNull(Map.of("de", "Entfernt jemanden von der Whitelist des Minecraft Servers"))
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("gamertag")
                                .description("The xbox gamertag")
                                .descriptionLocalizationsOrNull(Map.of("de", "Der Xbox Gamertag"))
                                .type(ApplicationCommandOption.Type.STRING.getValue())
                                .minLength(1).maxLength(16)
                                .required(true)
                                .build())
                        .build());
    }

    @Override
    public Mono<Void> run(ChatInputInteractionEvent event, User user, Translator i18n) {
        Optional<ApplicationCommandInteractionOption> javaOption = event.getOption("java");
        if (javaOption.isPresent()) {
            return this.removeJava(event, i18n, javaOption.get());
        }

        Optional<ApplicationCommandInteractionOption> bedrockOption = event.getOption("bedrock");
        if (bedrockOption.isPresent()) {
            return this.removeBedrock(event, i18n, bedrockOption.get());
        }

        throw new IllegalStateException("Neither java nor bedrock options found");
    }

    private Mono<Void> removeJava(
            ChatInputInteractionEvent event, Translator i18n,
            ApplicationCommandInteractionOption option
    ) {
        String username = option.getOption("username")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElseThrow();

        McApiUtil.McProfile profile = McApiUtil.loadProfile(username);
        if (!this.manager.getStorage().getWhitelist().containsKey(profile.getUniqueId())) {
            return event.reply(i18n.apply("command.whitelist-remove.not-whitelisted",
                    "`" + MarkdownEscape.codeEscape(profile.getUsername()) + "`")).withEphemeral(true);
        }

        this.manager.updateStorage(storage -> storage.getWhitelist().remove(profile.getUniqueId()));
        return event.reply(i18n.apply("command.whitelist-remove.success",
                "`" + MarkdownEscape.codeEscape(profile.getUsername()) + "`")).withEphemeral(true);
    }

    private Mono<Void> removeBedrock(
            ChatInputInteractionEvent event, Translator i18n,
            ApplicationCommandInteractionOption option
    ) {
        String gamertag = option.getOption("gamertag")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElseThrow();

        Long xuid = FloodgateUtil.getXuid(gamertag).join();
        if (xuid == null) {
            return event.reply(i18n.apply("command.whitelist.add.error.unknown-user",
                    "`" + MarkdownEscape.codeEscape(gamertag) + "`"));
        }

        if (!this.manager.getStorage().getBedrockWhitelist().containsKey(xuid)) {
            return event.reply(i18n.apply("command.whitelist-remove.not-whitelisted",
                    "`" + MarkdownEscape.codeEscape(gamertag) + "`")).withEphemeral(true);
        }

        this.manager.updateStorage(storage -> storage.getBedrockWhitelist().remove(xuid));
        return event.reply(i18n.apply("command.whitelist-remove.success",
                "`" + MarkdownEscape.codeEscape(gamertag) + "`")).withEphemeral(true);
    }
}
