package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (20:36 13.06.23)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import reactor.core.publisher.Mono;

import java.util.Map;

public final class ReloadConfigCommand extends AbstractBotCommand {

    public ReloadConfigCommand(CloudBotManager manager) {
        super(manager, "reload-config");
    }

    @Override
    protected void buildRequest(ImmutableApplicationCommandRequest.Builder builder) {
        builder
                .description("Reloads the bot configuration from disk")
                .descriptionLocalizationsOrNull(Map.of("de", "Lade die Bot Konfiguration neu"))
                .defaultMemberPermissions(Long.toString(PermissionSet.of(Permission.ADMINISTRATOR).getRawValue()))
                .dmPermission(false);
    }

    @Override
    public Mono<Void> run(ChatInputInteractionEvent event, User user, Translator i18n) {
        return event.deferReply().withEphemeral(true).then()
                .and(Mono.defer(() -> {
                    this.manager.saveStorages(); // shouldn't save config, doesn't get modified anywhere
                    return this.manager.reloadStorages(event);
                })).then()
                .and(event.createFollowup()
                        .withContent(i18n.apply("command.reload-config")))
                .then();
    }
}
