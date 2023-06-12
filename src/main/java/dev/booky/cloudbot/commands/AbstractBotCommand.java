package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (16:39 12.10.22)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import reactor.core.publisher.Mono;

public abstract class AbstractBotCommand {

    protected final CloudBotManager manager;
    protected final String label;

    public AbstractBotCommand(CloudBotManager manager, String label) {
        this.manager = manager;
        this.label = label;
    }

    protected abstract void buildRequest(ImmutableApplicationCommandRequest.Builder builder);

    public abstract Mono<Void> run(ChatInputInteractionEvent event, User user, Translator i18n);

    public Mono<Void> handleEvent(Event rawEvent) {
        return Mono.empty();
    }

    public ApplicationCommandRequest buildRequest() {
        ImmutableApplicationCommandRequest.Builder builder =
                ApplicationCommandRequest.builder().name(this.getLabel());
        this.buildRequest(builder);
        return builder.build();
    }

    public String getLabel() {
        return this.label;
    }
}
