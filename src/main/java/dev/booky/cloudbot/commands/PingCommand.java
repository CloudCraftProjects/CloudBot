package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (02:47 11.06.23)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public final class PingCommand extends AbstractBotCommand {

    public PingCommand(CloudBotManager manager) {
        super(manager, "ping");
    }

    @Override
    protected void buildRequest(ImmutableApplicationCommandRequest.Builder builder) {
        builder
                .description("Checks and prints the latency to the discord api")
                .descriptionLocalizationsOrNull(Map.of("de", "Überprüft und gibt die Latenz zur Discord API aus"))
                .dmPermission(true);
    }

    @Override
    public Mono<Void> run(ChatInputInteractionEvent event, User user, Translator i18n) {
        return event.reply()
                .withEphemeral(true)
                .withEmbeds(EmbedCreateSpec.builder()
                        .color(Color.CYAN)
                        .description(i18n.apply("command.ping.sent", user.getMention()))
                        .build())
                .flatMap($ -> event.editReply().withEmbeds(EmbedCreateSpec.builder()
                        .color(Color.CYAN)
                        .description(i18n.apply("command.ping.waiting", user.getMention()))
                        .build()))
                .flatMap(message -> event.editReply().withEmbeds(EmbedCreateSpec.builder()
                        .color(Color.CYAN)
                        .description(i18n.apply("command.ping.result", user.getMention(),
                                Duration.between(message.getEditedTimestamp().orElseThrow(), Instant.now()).toMillis()))
                        .build()))
                .then();
    }
}
