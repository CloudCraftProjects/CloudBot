package dev.booky.cloudbot;
// Created by booky10 in CloudBot (16:04 10.10.22)

import dev.booky.cloudbot.commands.BotCommand;
import dev.booky.cloudbot.commands.ListCommand;
import dev.booky.cloudbot.commands.PluginsCommand;
import dev.booky.cloudbot.commands.WhitelistCommand;
import dev.booky.cloudbot.commands.WhitelistRemoveCommand;
import dev.booky.cloudbot.i18n.TranslationManager;
import dev.booky.cloudbot.i18n.Translator;
import dev.booky.cloudbot.storage.CloudBotConfig;
import dev.booky.cloudbot.storage.CloudBotStorage;
import dev.booky.cloudbot.storage.ConfigLoader;
import dev.booky.cloudbot.storage.ConfigLoader.FileType;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.User;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private final TranslationManager i18n;
    private GatewayDiscordClient gateway;

    private CloudBotStorage storage;
    private CloudBotConfig config;
    private boolean isDirty = false;

    private final Path storagePath;
    private final Path configPath;

    public CloudBotManager(Plugin plugin, Path configDir) {
        this.i18n = new TranslationManager(plugin);
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
        this.i18n.reload();
    }

    public void saveStorages() {
        ConfigLoader.saveObject(this.configPath, this.getConfig(), FileType.YAML);
        ConfigLoader.saveObject(this.storagePath, this.getStorage(), FileType.JSON);
    }

    public void startBot() {
        DiscordClient client = DiscordClientBuilder.create(this.getConfig().getToken())
                .setDefaultAllowedMentions(AllowedMentions.suppressAll())
                .build();

        Set<BotCommand> commands = Set.of(
                new PluginsCommand(),
                new ListCommand(),
                new WhitelistCommand(),
                new WhitelistRemoveCommand());

        Mono<Void> login = client.gateway().setEnabledIntents(IntentSet.none()).withGateway(gateway -> {
            this.gateway = gateway;

            long appId = gateway.getRestClient().getApplicationId().blockOptional().orElseThrow();
            Map<String, BotCommand> commandMap = new HashMap<>(commands.size());
            for (BotCommand command : commands) {
                ApplicationCommandRequest req = command.provideCommandData();
                commandMap.put(req.name(), command);

                gateway.getRestClient().getApplicationService()
                        .createGlobalApplicationCommand(appId, req).block();
            }

            return gateway.on(ChatInputInteractionEvent.class, event -> {
                BotCommand command = commandMap.get(event.getCommandName());
                if (command == null) {
                    return event.reply(this.i18n.translate("command.not-found", event)).withEphemeral(true);
                }

                Translator translator = (key, args) -> this.i18n.translate(key, event, args);
                User user = event.getInteraction().getUser();

                return command.run(this, event.getCommandName(), event, user, translator);
            });
        });

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> login.block());
    }

    public void shutdownBot() {
        if (this.gateway != null) {
            this.gateway.logout().block();
        }
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
