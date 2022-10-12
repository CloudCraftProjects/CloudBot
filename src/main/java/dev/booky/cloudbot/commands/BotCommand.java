package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (16:39 12.10.22)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ApplicationCommandRequest;
import org.reactivestreams.Publisher;

public interface BotCommand {

    ApplicationCommandRequest provideCommandData();

    Publisher<Void> run(CloudBotManager manager, String label, ChatInputInteractionEvent event, User user, Translator i18n);
}
