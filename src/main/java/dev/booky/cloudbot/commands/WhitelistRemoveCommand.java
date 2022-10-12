package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (16:38 12.10.22)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import dev.booky.cloudbot.util.MarkdownEscape;
import dev.booky.cloudbot.util.McApiUtil;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import org.reactivestreams.Publisher;

import java.util.Map;

public class WhitelistRemoveCommand implements BotCommand {

    @Override
    public ApplicationCommandRequest provideCommandData() {
        return ApplicationCommandRequest.builder()
                .name("whitelist-remove")
                .description("Remove someone from the whitelist of the minecraft server")
                .descriptionLocalizationsOrNull(Map.of("de", "Entfernt jemanden von der Whitelist des Minecraft Servers"))
                .defaultMemberPermissions(Long.toString(PermissionSet.of(Permission.MANAGE_MESSAGES).getRawValue()))
                .dmPermission(false)
                .addOption(ApplicationCommandOptionData.builder()
                        .name("username")
                        .nameLocalizationsOrNull(Map.of("de", "nutzername"))
                        .description("The ingame name of the player")
                        .descriptionLocalizationsOrNull(Map.of("de", "Der Minecraft Ingame-Nutzername"))
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .minLength(3).maxLength(16)
                        .required(true)
                        .build())
                .build();
    }

    @Override
    public Publisher<Void> run(CloudBotManager manager, String label, ChatInputInteractionEvent event, User user, Translator i18n) {
        String username = event.getOption("username")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElseThrow();

        McApiUtil.McProfile profile = McApiUtil.loadProfile(username);
        if (!manager.getStorage().getWhitelist().containsKey(profile.getUniqueId())) {
            return event.reply(i18n.apply("command.whitelist-remove.not-whitelisted",
                    MarkdownEscape.escape(profile.getUsername()))).withEphemeral(true);
        }

        manager.updateStorage(storage -> storage.getWhitelist().remove(profile.getUniqueId()));
        return event.reply(i18n.apply("command.whitelist-remove.success",
                MarkdownEscape.escape(profile.getUsername()))).withEphemeral(true);
    }
}
