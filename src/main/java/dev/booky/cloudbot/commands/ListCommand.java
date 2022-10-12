package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (17:19 12.10.22)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Color;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.reactivestreams.Publisher;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ListCommand implements BotCommand {

    @Override
    public ApplicationCommandRequest provideCommandData() {
        return ApplicationCommandRequest.builder()
                .name("list")
                .description("Lists all players which are currently on the minecraft server")
                .descriptionLocalizationsOrNull(Map.of("de", "Listet alle Spieler auf, die aktuell auf dem Minecraft Server sind"))
                .dmPermission(true)
                .build();
    }

    @Override
    public Publisher<Void> run(CloudBotManager manager, String label, ChatInputInteractionEvent event, User user, Translator i18n) {
        Set<Player> players = new HashSet<>(Bukkit.getOnlinePlayers());
        players.removeIf(player -> player.hasMetadata("vanished"));

        StringBuilder builder = new StringBuilder();
        for (Player player : players) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(player.getName().replace("_", "\\_"));
        }

        String titleKey = "command.list." + (players.size() == 1 ? "singular" : "plural");

        // Max player count is about 4096/(16+2)=227.56, with every playername having 16 characters
        // before discord throws an error, but idrc.
        return event.reply().withEphemeral(true)
                .withEmbeds(EmbedCreateSpec.builder()
                        .title(i18n.apply(titleKey, players.size()))
                        .description(builder.toString())
                        .color(Color.CYAN)
                        .build());
    }
}
