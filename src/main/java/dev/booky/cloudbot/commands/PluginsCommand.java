package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (18:02 12.10.22)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import dev.booky.cloudbot.util.MarkdownEscape;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import reactor.core.publisher.Mono;

import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class PluginsCommand extends AbstractBotCommand {

    private static final String PLUGINS_FORMAT = "Plugins (%d): ";
    private static final String PLUGIN_WITH_SITE_FORMAT = "[%s](<%3$2s>) (`v%2$2s`)";
    private static final String PLUGIN_NO_SITE_FORMAT = "%s (`v%s`)";

    public PluginsCommand(CloudBotManager manager) {
        super(manager, "plugins");
    }

    @Override
    protected void buildRequest(ImmutableApplicationCommandRequest.Builder builder) {
        builder
                .description("Lists the plugins currently active on the server")
                .descriptionLocalizationsOrNull(Map.of("de", "Listet die aktuell aktivierten Plugins auf dem Server auf"))
                .dmPermission(true);
    }

    @Override
    public Mono<Void> run(ChatInputInteractionEvent event, User user, Translator i18n) {
        Set<Plugin> plugins = new HashSet<>(List.of(Bukkit.getPluginManager().getPlugins()));
        plugins.removeIf(Predicate.not(Plugin::isEnabled));

        StringBuilder builder = new StringBuilder();
        Formatter formatter = new Formatter(builder);
        formatter.format(PLUGINS_FORMAT, plugins.size());

        boolean firstPlugin = true;
        for (Plugin plugin : plugins) {
            if (firstPlugin) {
                firstPlugin = false;
            } else {
                builder.append(", ");
            }

            String name = MarkdownEscape.escape(plugin.getPluginMeta().getName());
            String version = MarkdownEscape.escape(plugin.getPluginMeta().getVersion());
            String website = MarkdownEscape.escape(plugin.getPluginMeta().getWebsite());

            String format = website != null ? PLUGIN_WITH_SITE_FORMAT : PLUGIN_NO_SITE_FORMAT;
            formatter.format(format, name, version, website);
        }

        return event.reply(builder.toString()).withEphemeral(true);
    }
}
