package dev.booky.cloudbot;
// Created by booky10 in CloudBot (16:04 10.10.22)

import dev.booky.cloudbot.storage.CloudBotConfig;
import dev.booky.cloudbot.storage.CloudBotStorage;
import dev.booky.cloudbot.storage.ConfigLoader;
import dev.booky.cloudbot.storage.ConfigLoader.FileType;
import dev.booky.cloudbot.util.McApiUtil;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.AllowedMentions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

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
    private GatewayDiscordClient gateway;

    private CloudBotStorage storage;
    private CloudBotConfig config;
    private boolean isDirty = false;

    private final Path storagePath;
    private final Path configPath;

    public CloudBotManager(Plugin plugin, Path configDir) {
        this.plugin = plugin;
        this.configPath = configDir.resolve("config.yml");
        this.storagePath = configDir.resolve("storage.json");
    }

    public static Component getPrefix() {
        return PREFIX;
    }

    public void updateConfig(Consumer<CloudBotConfig> consumer) {
        consumer.accept(this.getConfig());
        this.isDirty = true;
    }

    public void updateStorage(Consumer<CloudBotStorage> consumer) {
        consumer.accept(this.getStorage());
        this.isDirty = true;
    }

    public void reloadStorages() {
        this.config = ConfigLoader.loadObject(this.configPath, CloudBotConfig.class, FileType.YAML);
        this.storage = ConfigLoader.loadObject(this.storagePath, CloudBotStorage.class, FileType.JSON);
    }

    public void saveStorages() {
        ConfigLoader.saveObject(this.configPath, this.getConfig(), FileType.YAML);
        ConfigLoader.saveObject(this.storagePath, this.getStorage(), FileType.JSON);
    }

    public void startBot() {
        DiscordClient client = DiscordClientBuilder.create(this.getConfig().getToken())
                .setDefaultAllowedMentions(AllowedMentions.suppressAll())
                .build();

        ApplicationCommandRequest whitelistCommand = ApplicationCommandRequest.builder()
                .name("whitelist")
                .description("Whitelists you on the Minecraft Server")
                .descriptionLocalizationsOrNull(Map.of("de", "Whitelisted dich auf dem Minecraft Server"))
                .addOption(ApplicationCommandOptionData.builder()
                        .name("username")
                        .nameLocalizationsOrNull(Map.of("de", "nutzername"))
                        .description("Your Minecraft ingame name")
                        .descriptionLocalizationsOrNull(Map.of("de", "Dein Minecraft Ingame-Name"))
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .minLength(3).maxLength(16)
                        .required(true)
                        .build())
                .build();

        Mono<Void> login = client.gateway().setEnabledIntents(IntentSet.none()).withGateway(gateway -> {
            this.gateway = gateway;

            long appId = gateway.getRestClient().getApplicationId().blockOptional().orElseThrow();
            gateway.getRestClient().getApplicationService()
                    .createGuildApplicationCommand(appId, 737751273163718668L, whitelistCommand)
                    .block();

            return gateway.on(ChatInputInteractionEvent.class, event -> switch (event.getCommandName()) {
                case "whitelist" -> {
                    String username = event.getOption("username")
                            .flatMap(ApplicationCommandInteractionOption::getValue)
                            .map(ApplicationCommandInteractionOptionValue::asString)
                            .orElseThrow();

                    try {
                        UUID uniqueId = McApiUtil.getUniqueId(username);
                        yield event.reply("'" + username + "' -> " + uniqueId);
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                        yield event.reply("<a:alert:785547764389117983> Error: `" + throwable + "`").withEphemeral(true);
                    }
                }
                default -> event.reply("404 <a:help:770734169344442378>").withEphemeral(true);
            });
        });

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> login.block());
    }

    public CloudBotConfig getConfig() {
        return Objects.requireNonNull(this.config, "Config has not been loaded yet");
    }


    public CloudBotStorage getStorage() {
        return Objects.requireNonNull(this.storage, "Storage has not been loaded yet");
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public boolean isDirty() {
        return isDirty;
    }
}
