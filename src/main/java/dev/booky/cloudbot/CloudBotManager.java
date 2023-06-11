package dev.booky.cloudbot;
// Created by booky10 in CloudBot (16:04 10.10.22)

import dev.booky.cloudbot.commands.AbstractBotCommand;
import dev.booky.cloudbot.commands.ExecuteCommand;
import dev.booky.cloudbot.commands.ListCommand;
import dev.booky.cloudbot.commands.PingCommand;
import dev.booky.cloudbot.commands.PluginsCommand;
import dev.booky.cloudbot.commands.TeamMembersCommand;
import dev.booky.cloudbot.commands.TpsCommand;
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
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.service.ApplicationService;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.Color;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private Guild mainGuild;
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
            this.reloadMainGuild().then()
                    .and(this.reloadLogChannel())
                    .subscribe();
        }
    }

    public void saveStorages() {
        ConfigLoader.saveObject(this.configPath, this.getConfig(), FileType.YAML);
        ConfigLoader.saveObject(this.storagePath, this.getStorage(), FileType.JSON);
    }

    public Mono<Guild> reloadMainGuild() {
        return Mono.defer(() -> {
                    long guildId = this.getConfig().getMainGuildId();
                    if (guildId != -1L) {
                        return this.gateway.getGuildById(Snowflake.of(guildId));
                    }
                    return Mono.empty();
                })
                .doOnSuccess(guild -> this.mainGuild = guild);
    }

    public Mono<TextChannel> reloadLogChannel() {
        return Mono.defer(() -> {
                    if (this.mainGuild == null) {
                        return this.reloadMainGuild();
                    }
                    return Mono.just(this.mainGuild);
                })
                .flatMap(guild -> {
                    long channelId = this.getConfig().getLogChannelId();
                    if (channelId != -1L) {
                        return guild.getChannelById(Snowflake.of(channelId))
                                .filter(channel -> channel instanceof TextChannel)
                                .map(channel -> (TextChannel) channel);
                    }
                    return Mono.empty();
                })
                .doOnSuccess(channel -> this.logChannel = channel);
    }

    public void startBot() {
        DiscordClient client = DiscordClientBuilder.create(this.getConfig().getToken())
                .setDefaultAllowedMentions(AllowedMentions.suppressAll())
                .build();

        Set<AbstractBotCommand> commands = new HashSet<>();
        commands.add(new ExecuteCommand(this));
        commands.add(new ListCommand(this));
        commands.add(new PingCommand(this));
        commands.add(new PluginsCommand(this));
        commands.add(new TeamMembersCommand(this));
        commands.add(new UserInfoCommand(this));
        commands.add(new WhitelistCommand(this));
        commands.add(new WhitelistRemoveCommand(this));

        if (Bukkit.getPluginManager().getPlugin("spark") != null) {
            commands.add(new TpsCommand(this));
        }

        Mono<Void> login = client.gateway()
                .setEnabledIntents(IntentSet.of(Intent.GUILD_MEMBERS))
                .withGateway(gateway -> {
                    this.gateway = gateway;

                    return this.reloadLogChannel().then()
                            .and(Mono.defer(() -> {
                                long appId = gateway.getRestClient().getApplicationId().blockOptional().orElseThrow();
                                ApplicationService appService = gateway.getRestClient().getApplicationService();

                                this.plugin.getLogger().info("Registering " + commands.size() + " commands...");
                                return appService.bulkOverwriteGlobalApplicationCommand(appId,
                                                commands.stream().map(AbstractBotCommand::buildRequest).toList())
                                        .collectList().then();
                            })).then()
                            .and(Mono.defer(() -> {
                                this.plugin.getLogger().info("Finished startup, listening for events...");
                                return this.registerEvents(gateway, commands);
                            })).then();
                });

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> login.block());
    }

    private Mono<Void> registerEvents(GatewayDiscordClient gateway, Collection<AbstractBotCommand> commands) {
        Map<String, AbstractBotCommand> commandMap = commands.stream()
                .collect(Collectors.toUnmodifiableMap(AbstractBotCommand::getLabel, Function.identity()));

        return gateway.on(GuildCreateEvent.class, event -> {
            if (event.getGuild().getId().asLong() == this.getConfig().getMainGuildId()) {
                this.mainGuild = event.getGuild();
                return this.reloadLogChannel();
            }
            return Mono.empty();
        }).then().and(gateway.on(ChatInputInteractionEvent.class, event -> {
            User user = event.getInteraction().getUser();
            CompletableFuture<Message> logMessage = new CompletableFuture<>();

            if (this.logChannel != null) {
                Optional<Snowflake> guildId = event.getInteraction().getGuildId();
                String location = guildId.map(snowflake -> "Guild: `" + snowflake.asString() + "`\n" +
                                "Channel: `" + event.getInteraction().getChannelId().asString() + "`")
                        .orElseGet(() -> "Private Messages: `" + event.getInteraction().getChannelId().asString() + "`")
                        + "\n";

                String desc = "**" + MarkdownEscape.escape(user.getTag()) + "** (`" + user.getId().asString() + ")`\n" +
                        location + "> " + CommandStringifier.stringify(event);

                this.logChannel.createMessage().withEmbeds(EmbedCreateSpec.builder()
                                .description(desc).color(Color.of(0xA9F90F))
                                .timestamp(Instant.now()).footer(user.getTag(), user.getAvatarUrl())
                                .build())
                        .subscribe(logMessage::complete);
            }

            AbstractBotCommand command = commandMap.get(event.getCommandName());
            if (command == null) {
                return event.reply(this.i18n.translate("command.not-found", event)).withEphemeral(true);
            }

            try {
                Translator translator = (key, args) -> this.i18n.translate(key, event, args);
                return command.run(event, user, translator)
                        .onErrorResume(throwable -> this.handleException(throwable, event, logMessage));
            } catch (Throwable throwable) {
                return this.handleException(throwable, event, logMessage);
            }
        }).then());
    }

    private Mono<Void> handleException(Throwable throwable,
                                       ChatInputInteractionEvent event,
                                       CompletableFuture<Message> logMessage) {
        throwable.printStackTrace();
        if (this.logChannel == null) {
            return Mono.empty();
        }

        logMessage.thenAccept(msg -> {
            StringWriter strWriter = new StringWriter();
            try (PrintWriter writer = new PrintWriter(strWriter)) {
                throwable.printStackTrace(writer);
            }

            String stacktrace = strWriter.toString();
            int maxSize = 4096 - 3 * 2 /*code block markers*/;

            if (stacktrace.length() > maxSize) {
                stacktrace = stacktrace.substring(0, maxSize - 3 /*three dots*/) + "...";
            }

            User user = event.getInteraction().getUser();
            this.logChannel.createMessage()
                    .withEmbeds(EmbedCreateSpec.builder()
                            .description("```" + stacktrace + "```")
                            .color(Color.of(0xCE3C1E))
                            .timestamp(Instant.now())
                            .footer(user.getTag(), user.getAvatarUrl())
                            .build())
                    .withMessageReference(msg.getId())
                    .subscribe();
        });

        // We sadly don't know if a reply has already been defered, so have to ignore errors :(
        event.deferReply().withEphemeral(true)
                .onErrorResume(e -> Mono.empty()).subscribe();

        return event.createFollowup(this.i18n.translate("command.errored", event,
                "`" + MarkdownEscape.codeEscape(throwable.toString()) + "`")).then();
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

    public @Nullable TextChannel getLogChannel() {
        return this.logChannel;
    }

    public @Nullable Guild getMainGuild() {
        return this.mainGuild;
    }

    public Plugin getPlugin() {
        return this.plugin;
    }

    public boolean isDirty() {
        return this.isDirty;
    }
}
