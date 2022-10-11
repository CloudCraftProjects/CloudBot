package dev.booky.cloudbot;
// Created by booky10 in CloudBot (16:04 10.10.22)

import dev.booky.cloudbot.config.ConfigLoader;
import dev.booky.cloudbot.util.CloudBotConfig;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.AllowedMentions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.Objects;

public class CloudBotManager {

    // <gray>[<gradient:#d4d4d4:#8fe3cd>CloudBot</gradient>]</gray><space>
    private static final Component PREFIX = Component.text()
            .append(Component.text('[', NamedTextColor.GRAY))
            .append(Component.text('C', TextColor.color(0xd4d4d4)))
            .append(Component.text('l', TextColor.color(0xcbd6d3)))
            .append(Component.text('o', TextColor.color(0xc3d8d2)))
            .append(Component.text('u', TextColor.color(0xbadad1)))
            .append(Component.text('d', TextColor.color(0xb2dcd1)))
            .append(Component.text('B', TextColor.color(0xa9ddd0)))
            .append(Component.text('o', TextColor.color(0xa0dfcf)))
            .append(Component.text('t', TextColor.color(0x98e1ce)))
            .append(Component.text(']', NamedTextColor.GRAY))
            .append(Component.space()).build();

    private final Plugin plugin;
    private final Path configPath;

    private GatewayDiscordClient gateway;
    private CloudBotConfig config;

    public CloudBotManager(Plugin plugin, Path configDir) {
        this.plugin = plugin;
        this.configPath = configDir.resolve("config.yml");
    }

    public static Component getPrefix() {
        return PREFIX;
    }

    public void reloadConfig() {
        this.config = ConfigLoader.loadObject(this.configPath, CloudBotConfig.class);
    }

    public void saveConfig() {
        ConfigLoader.saveObject(this.configPath, this.getConfig());
    }

    public void startBot() {
        DiscordClient client = DiscordClientBuilder.create(this.getConfig().getToken())
                .setDefaultAllowedMentions(AllowedMentions.suppressAll())
                .build();

        Mono<Void> login = client.gateway().setEnabledIntents(IntentSet.none()).withGateway(gateway -> {
            this.gateway = gateway;

            return gateway.on(ChatInputInteractionEvent.class, event -> switch (event.getCommandName()) {
                default -> event.reply("404 <a:help:770734169344442378>").withEphemeral(true);
            });
        });

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> login.block());
    }

    public CloudBotConfig getConfig() {
        return Objects.requireNonNull(this.config, "Config has not been loaded yet");
    }

    public Plugin getPlugin() {
        return plugin;
    }
}
