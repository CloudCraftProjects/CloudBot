package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (18:02 12.10.22)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ApplicationCommandRequest;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.reactivestreams.Publisher;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class PluginsCommand implements BotCommand {

    @Override
    public ApplicationCommandRequest provideCommandData() {
        return ApplicationCommandRequest.builder()
                .name("plugins")
                .description("Lists the plugins currently active on the server")
                .descriptionLocalizationsOrNull(Map.of("de", "Listet die aktuell aktivierten Plugins auf dem Server auf"))
                .dmPermission(true)
                .build();
    }

    @Override
    public Publisher<Void> run(CloudBotManager manager, String label, ChatInputInteractionEvent event, User user, Translator i18n) {
        Set<Plugin> plugins = new HashSet<>(List.of(Bukkit.getPluginManager().getPlugins()));
        plugins.removeIf(Predicate.not(Plugin::isEnabled));

        StringBuilder builder = new StringBuilder();
        builder.append("Plugins (").append(plugins.size()).append("): ");

        boolean firstPlugin = true;
        for (Plugin plugin : plugins) {
            if (firstPlugin) {
                firstPlugin = false;
            } else {
                builder.append(", ");
            }

            if (plugin.getDescription().getWebsite() != null) {
                builder.append('[').append(plugin.getName()).append("](");
                builder.append(plugin.getDescription().getWebsite()).append(")");
            } else {
                builder.append(plugin.getName());
            }

            builder.append(" (`").append(plugin.getDescription().getVersion()).append("`)");
        }

        return event.reply(builder.toString()).withEphemeral(true);
    }
}
