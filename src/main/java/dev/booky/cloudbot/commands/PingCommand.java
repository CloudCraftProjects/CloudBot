package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (02:47 11.06.23)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

import java.util.Map;

public class PingCommand implements BotCommand {

    @Override
    public ApplicationCommandRequest provideCommandData() {
        return ApplicationCommandRequest.builder()
                .name("ping")
                .description("Checks and prints the latency to the discord api")
                .descriptionLocalizationsOrNull(Map.of("de", "Überprüft und gibt die Latenz zur Discord API aus"))
                .dmPermission(true)
                .build();
    }

    @Override
    public Mono<Void> run(CloudBotManager manager, String label, ChatInputInteractionEvent event, User user, Translator i18n) {
        long start = System.currentTimeMillis();
        return event.reply()
                .withEphemeral(true)
                .withEmbeds(EmbedCreateSpec.builder()
                        .color(Color.CYAN)
                        .description(i18n.apply("command.ping.waiting", user.getMention()))
                        .build())
                .then(Mono.defer(() -> {
                    long ping = System.currentTimeMillis() - start;
                    return event.editReply()
                            .withEmbeds(EmbedCreateSpec.builder()
                                    .color(Color.CYAN)
                                    .description(i18n.apply("command.ping.result", user.getMention(), ping))
                                    .build());
                }))
                .then();
    }
}
