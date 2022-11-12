package dev.booky.cloudbot;
// Created by booky10 in CloudBot (16:04 10.10.22)

import dev.booky.cloudbot.commands.BotCommand;
import dev.booky.cloudbot.commands.ExecuteCommand;
import dev.booky.cloudbot.commands.ListCommand;
import dev.booky.cloudbot.commands.PluginsCommand;
import dev.booky.cloudbot.commands.UserInfoCommand;
import dev.booky.cloudbot.commands.WhitelistCommand;
import dev.booky.cloudbot.commands.WhitelistRemoveCommand;
import dev.booky.cloudbot.i18n.TranslationManager;
import dev.booky.cloudbot.i18n.Translator;
import dev.booky.cloudbot.storage.CloudBotConfig;
import dev.booky.cloudbot.storage.CloudBotStorage;
import dev.booky.cloudbot.storage.ConfigLoader;
import dev.booky.cloudbot.storage.ConfigLoader.FileType;
import dev.booky.cloudbot.util.CommandStringifier;
import dev.booky.cloudbot.util.MarkdownEscape;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.Color;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    private final TranslationManager i18n;
    private final Plugin plugin;

    private GatewayDiscordClient gateway;
    private TextChannel logChannel;

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

        if (this.gateway != null) {
            this.reloadLogChannel(null);
        }
    }

    public void saveStorages() {
        ConfigLoader.saveObject(this.configPath, this.getConfig(), FileType.YAML);
        ConfigLoader.saveObject(this.storagePath, this.getStorage(), FileType.JSON);
    }

    public void reloadLogChannel(@Nullable Guild guild) {
        if (this.getConfig().getMainGuildId() == -1L) {
            return;
        }
        if (this.getConfig().getLogChannelId() == -1L) {
            return;
        }

        if (guild == null) {
            guild = this.gateway.getGuildById(Snowflake.of(this.getConfig().getMainGuildId())).blockOptional().orElse(null);
            if (guild == null) {
                return;
            }
        }

        this.logChannel = (TextChannel) guild.getChannelById(Snowflake.of(this.getConfig().getLogChannelId()))
                .blockOptional().filter(channel -> channel instanceof TextChannel).orElse(null);
    }

    public void startBot() {
        DiscordClient client = DiscordClientBuilder.create(this.getConfig().getToken())
                .setDefaultAllowedMentions(AllowedMentions.suppressAll())
                .build();

        Set<BotCommand> commands = Set.of(
                new ExecuteCommand(),
                new UserInfoCommand(),
                new PluginsCommand(),
                new ListCommand(),
                new WhitelistCommand(),
                new WhitelistRemoveCommand());

        Mono<Void> login = client.gateway().setEnabledIntents(IntentSet.none()).withGateway(gateway -> {
            this.gateway = gateway;
            this.reloadLogChannel(null);

            long appId = gateway.getRestClient().getApplicationId().blockOptional().orElseThrow();
            Map<String, BotCommand> commandMap = new HashMap<>(commands.size());
            for (BotCommand command : commands) {
                ApplicationCommandRequest req = command.provideCommandData();
                commandMap.put(req.name(), command);

                gateway.getRestClient().getApplicationService()
                        .createGlobalApplicationCommand(appId, req).block();
            }

            return gateway.on(GuildCreateEvent.class, event -> {
                if (event.getGuild().getId().asLong() != this.getConfig().getMainGuildId()) {
                    return Mono.empty();
                }

                this.reloadLogChannel(event.getGuild());
                return Mono.empty();
            }).then().and(gateway.on(ChatInputInteractionEvent.class, event -> {
                User user = event.getInteraction().getUser();
                if (this.logChannel != null) {
                    Optional<Snowflake> guildId = event.getInteraction().getGuildId();
                    String location = guildId.map(snowflake -> "" +
                                    "Guild: `" + snowflake.asString() + "`\n" +
                                    "Channel: `" + event.getInteraction().getChannelId().asString() + "`")
                            .orElseGet(() -> "Private Messages: `" + event.getInteraction().getChannelId().asString() + "`")
                            + "\n";

                    String desc = "**" + MarkdownEscape.escape(user.getTag()) + "** (`" + user.getId().asString() + ")`\n" +
                            location + "> " + CommandStringifier.stringify(event);

                    this.logChannel.createMessage().withEmbeds(EmbedCreateSpec.builder()
                                    .description(desc).color(Color.of(0xA9F90F))
                                    .timestamp(Instant.now()).footer(user.getTag(), user.getAvatarUrl())
                                    .build())
                            .subscribe();
                }

                BotCommand command = commandMap.get(event.getCommandName());
                if (command == null) {
                    return event.reply(this.i18n.translate("command.not-found", event)).withEphemeral(true);
                }

                Translator translator = (key, args) -> this.i18n.translate(key, event, args);
                return command.run(this, event.getCommandName(), event, user, translator);
            }).then());
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

    public TextChannel getLogChannel() {
        return this.logChannel;
    }

    public Plugin getPlugin() {
        return this.plugin;
    }

    public boolean isDirty() {
        return this.isDirty;
    }
}
