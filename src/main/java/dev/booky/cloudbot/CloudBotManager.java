package dev.booky.cloudbot;
// Created by booky10 in CloudBot (16:04 10.10.22)

import dev.booky.cloudbot.dclistener.CommandListener;
import dev.booky.cloudbot.dclistener.InviteListener;
import dev.booky.cloudbot.dclistener.JoinLeaveMessageListener;
import dev.booky.cloudbot.dclistener.MemberCounterListener;
import dev.booky.cloudbot.dclistener.ReactionRoleListener;
import dev.booky.cloudbot.events.DcEventHandler;
import dev.booky.cloudbot.events.DcEventManager;
import dev.booky.cloudbot.events.DcListener;
import dev.booky.cloudbot.events.custom.MainGuildDataReloadEvent;
import dev.booky.cloudbot.i18n.TranslationManager;
import dev.booky.cloudbot.i18n.Translator;
import dev.booky.cloudbot.storage.CloudBotConfig;
import dev.booky.cloudbot.storage.CloudBotStorage;
import dev.booky.cloudbot.storage.ColorSerializer;
import dev.booky.cloudbot.storage.MessageRef;
import dev.booky.cloudbot.storage.MessageRefSerializer;
import dev.booky.cloudcore.config.ConfigurateLoader;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.gateway.ShardInfo;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.Color;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.gson.GsonConfigurationLoader;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

public final class CloudBotManager implements DcListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("CloudBot");

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

    private static final TypeSerializerCollection SERIALIZERS = TypeSerializerCollection.builder()
            .register(Color.class, ColorSerializer.INSTANCE)
            .register(MessageRef.class, MessageRefSerializer.INSTANCE)
            .build();
    private static final ConfigurateLoader<?, ?> GSON_LOADER = ConfigurateLoader.loader(GsonConfigurationLoader::builder)
            .withAllDefaultSerializers().withSerializers(SERIALIZERS).build();
    private static final ConfigurateLoader<?, ?> YAML_LOADER = ConfigurateLoader.yamlLoader()
            .withAllDefaultSerializers().withSerializers(SERIALIZERS).build();

    private final TranslationManager i18n;
    private final Plugin plugin;

    private GatewayDiscordClient gateway;
    private Guild mainGuild;
    private GuildMessageChannel logChannel;

    private final DcEventManager eventManager = new DcEventManager();

    private final Object diskLock = new Object();
    private CloudBotStorage storage;
    private boolean dirtyStorage = false;
    private CloudBotConfig config;
    private boolean dirtyConfig = false;

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
        try {
            consumer.accept(this.getConfig());
        } finally {
            this.dirtyConfig = true;
        }
    }

    public void updateStorage(Consumer<CloudBotStorage> consumer) {
        try {
            consumer.accept(this.getStorage());
        } finally {
            this.dirtyStorage = true;
        }
    }

    public Mono<Void> reloadStorages() {
        return this.reloadStorages(null, null);
    }

    public Mono<Void> reloadStorages(Event cause) {
        return this.reloadStorages(cause.getClient(), cause.getShardInfo());
    }

    public Mono<Void> reloadStorages(@Nullable GatewayDiscordClient gateway, @Nullable ShardInfo shardInfo) {
        return Mono.defer(() -> {
            synchronized (this.diskLock) {
                this.config = YAML_LOADER.loadObject(this.configPath, CloudBotConfig.class);
                this.storage = GSON_LOADER.loadObject(this.storagePath, CloudBotStorage.class);
                this.i18n.reload();
            }

            if (gateway != null && shardInfo != null) {
                return this.reloadMainGuildData(gateway, shardInfo);
            }
            return Mono.empty();
        });
    }

    public void saveStorages() {
        synchronized (this.diskLock) {
            if (this.dirtyConfig) {
                YAML_LOADER.saveObject(this.configPath, this.getConfig(), CloudBotConfig.class);
            }
            if (this.dirtyStorage) {
                GSON_LOADER.saveObject(this.storagePath, this.getStorage(), CloudBotStorage.class);
            }
        }
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

    public Mono<Void> reloadMainGuildData(Event cause) {
        return this.reloadMainGuildData(cause.getClient(), cause.getShardInfo());
    }

    public Mono<Void> reloadMainGuildData(GatewayDiscordClient gateway, ShardInfo shardInfo) {
        return this.reloadMainGuild().flatMap(guild -> this.reloadMainGuildData(guild, gateway, shardInfo));
    }

    public Mono<Void> reloadMainGuildData(Guild mainGuild, GatewayDiscordClient gateway, ShardInfo shardInfo) {
        return this.eventManager.invoke(new MainGuildDataReloadEvent(gateway, shardInfo, mainGuild));
    }

    public Mono<Void> startBot() {
        return Mono.defer(() -> DiscordClientBuilder.create(this.getConfig().getToken())
                .setDefaultAllowedMentions(AllowedMentions.suppressAll())
                .build()
                .gateway()
                .setEnabledIntents(IntentSet.of(Intent.GUILD_MEMBERS, Intent.GUILD_INVITES, Intent.GUILD_MESSAGE_REACTIONS))
                .setInitialPresence(info -> this.getConfig().buildPresence(info))
                .withGateway(gateway -> {
                    this.gateway = gateway;
                    return this.registerEvents(gateway).then()
                            // no one will notice...
                            .and(this.reloadMainGuildData(gateway, null))
                            .doFinally(type -> LOGGER.info("Finished gateway bootstrapping: {}", type));
                }));
    }

    @DcEventHandler
    public Mono<Void> onGuildCreate(GuildCreateEvent event) {
        if (event.getGuild().getId().asLong() == this.getConfig().getMainGuildId()) {
            this.mainGuild = event.getGuild();
            return this.reloadMainGuildData(event);
        }
        return Mono.empty();
    }

    @DcEventHandler
    public Mono<Void> onGuildDelete(GuildDeleteEvent event) {
        if (event.getGuildId().asLong() == this.getConfig().getMainGuildId()) {
            this.mainGuild = null;
            return this.reloadMainGuildData(event);
        }
        return Mono.empty();
    }

    @DcEventHandler
    public Mono<Void> onDataReload(MainGuildDataReloadEvent event) {
        return event.getMainGuild()
                .map(guild -> {
                    long channelId = this.getConfig().getLogChannelId();
                    if (channelId != -1L) {
                        return guild.getChannelById(Snowflake.of(channelId))
                                .filter(channel -> channel instanceof GuildMessageChannel)
                                .map(channel -> (GuildMessageChannel) channel)
                                .doOnSuccess(channel -> this.logChannel = channel)
                                .then();
                    }
                    return Mono.<Void>empty();
                })
                .orElseGet(Mono::empty);
    }

    private Mono<Void> registerEvents(GatewayDiscordClient gateway) {
        this.eventManager.register(this);
        this.eventManager.register(new CommandListener(this));
        if (this.getConfig().isTrackInvites()) {
            this.eventManager.register(new InviteListener(this));
        }
        this.eventManager.register(new JoinLeaveMessageListener(this));
        this.eventManager.register(new MemberCounterListener(this));
        this.eventManager.register(new ReactionRoleListener(this));

        return gateway.on(Event.class, this.eventManager::invoke).then();
    }

    public Translator createTranslator(Interaction interaction) {
        return (key, args) -> this.i18n.translate(key, interaction.getUserLocale(), args);
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
        return this.plugin;
    }

    public @Nullable GatewayDiscordClient getGateway() {
        return this.gateway;
    }

    public @Nullable Guild getMainGuild() {
        return this.mainGuild;
    }

    public @Nullable GuildMessageChannel getLogChannel() {
        return this.logChannel;
    }

    public DcEventManager getEventManager() {
        return this.eventManager;
    }
}
