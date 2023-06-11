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
import discord4j.core.event.domain.InviteCreateEvent;
import discord4j.core.event.domain.InviteDeleteEvent;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.ExtendedInvite;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.TextChannelEditMono;
import discord4j.core.spec.VoiceChannelEditMono;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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

    private final List<ExtendedInvite> currentInvites = new ArrayList<>();

    private GatewayDiscordClient gateway;
    private Guild mainGuild;
    private TextChannel logChannel;
    private GuildChannel memberCounterChannel;

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
                    .and(this.reloadMainGuildData()).then()
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

    public Mono<Guild> getOrLoadMainGuild() {
        return Mono.defer(() -> {
            if (this.mainGuild == null) {
                return this.reloadMainGuild();
            }
            return Mono.just(this.mainGuild);
        });
    }

    public Mono<List<ExtendedInvite>> reloadInvites() {
        return this.getOrLoadMainGuild()
                .flatMap(guild -> guild.getInvites().collectList())
                .doOnSuccess(invites -> {
                    synchronized (this.currentInvites) {
                        this.currentInvites.clear();
                        if (invites != null) {
                            this.currentInvites.addAll(invites);
                        }
                    }
                });
    }

    public Mono<TextChannel> reloadLogChannel() {
        return this.getOrLoadMainGuild()
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

    public Mono<GuildChannel> reloadMemberCounterChannel() {
        return this.getOrLoadMainGuild()
                .flatMap(guild -> {
                    long channelId = this.getConfig().getMemberCounter().getChannelId();
                    if (channelId != -1L) {
                        return guild.getChannelById(Snowflake.of(channelId));
                    }
                    return Mono.empty();
                })
                .doOnSuccess(channel -> {
                    this.memberCounterChannel = channel;
                    this.updateMemberCounter(channel).subscribe();
                });
    }

    public Mono<Void> reloadCustomCommands() {
        return this.getOrLoadMainGuild()
                .flatMap(guild -> {
                    long appId = guild.getClient().getRestClient().getApplicationId().blockOptional().orElseThrow();
                    ApplicationService appService = guild.getClient().getRestClient().getApplicationService();

                    return appService.bulkOverwriteGuildApplicationCommand(appId, guild.getId().asLong(),
                                    this.getConfig().getCustomCommands().entrySet().stream()
                                            .map(entry -> entry.getValue().buildRequest(entry.getKey())).toList())
                            .collectList().then();
                });
    }

    public Mono<Void> reloadMainGuildData() {
        return this.reloadLogChannel().then()
                .and(this.reloadMemberCounterChannel()).then()
                .and(this.reloadInvites()).then()
                .and(this.reloadCustomCommands()).then();
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
        commands.clear();

        Mono<Void> login = client.gateway()
                .setEnabledIntents(IntentSet.of(Intent.GUILD_MEMBERS))
                .withGateway(gateway -> {
                    this.gateway = gateway;

                    return this.reloadMainGuildData().then()
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
                return this.reloadMainGuildData();
            }
            return Mono.empty();
        }).then().and(gateway.on(GuildDeleteEvent.class, event -> {
            if (event.getGuildId().asLong() == this.getConfig().getMainGuildId()) {
                synchronized (this.currentInvites) {
                    this.currentInvites.clear();
                }

                this.mainGuild = null;
                return this.reloadMainGuildData();
            }
            return Mono.empty();
        })).then().and(gateway.on(InviteCreateEvent.class, event -> {
            if (!event.getGuildId().map(id -> id.asLong() == this.getConfig().getMainGuildId()).orElse(false)) {
                return Mono.empty();
            }

            // this works, don't judge it
            return event.getClient().getRestClient().getInviteService()
                    .getInvite(event.getCode())
                    .map(data -> new ExtendedInvite(event.getClient(), data))
                    .doOnSuccess(invite -> {
                        if (invite == null) {
                            return;
                        }
                        synchronized (this.currentInvites) {
                            this.currentInvites.add(invite);
                        }
                    });
        })).then().and(gateway.on(InviteDeleteEvent.class, event -> {
            if (!event.getGuildId().map(id -> id.asLong() == this.getConfig().getMainGuildId()).orElse(false)) {
                return Mono.empty();
            }
            synchronized (this.currentInvites) {
                this.currentInvites.removeIf(invite -> invite.getCode().equals(event.getCode()));
            }
            return Mono.empty();
        })).then().and(gateway.on(MemberJoinEvent.class, event -> {
            if (event.getGuildId().asLong() == this.getConfig().getMainGuildId()) {
                return this.sendRandomMessage(event.getMember(), this.getConfig().getJoinMessages()).then()
                        .and(this.checkInvites(event.getMember())).then()
                        .and(this.updateMemberCounter(this.memberCounterChannel)).then();
            }
            return Mono.empty();
        })).then().and(gateway.on(MemberLeaveEvent.class, event -> {
            if (event.getGuildId().asLong() == this.getConfig().getMainGuildId()) {
                return this.sendRandomMessage(event.getMember().orElse(null), this.getConfig().getLeaveMessages()).then()
                        .and(this.updateMemberCounter(this.memberCounterChannel)).then();
            }
            return Mono.empty();
        })).then().and(gateway.on(ChatInputInteractionEvent.class, event -> {
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

            try {
                {
                    AbstractBotCommand command = commandMap.get(event.getCommandName());
                    if (command != null) {
                        Translator translator = (key, args) -> this.i18n.translate(key, event, args);
                        return command.run(event, user, translator)
                                .onErrorResume(throwable -> this.handleException(throwable, event, logMessage));
                    }
                }

                {
                    CloudBotConfig.CustomCommand command = this.getConfig().getCustomCommands().get(event.getCommandName());
                    if (command != null) {
                        return command.run(event)
                                .onErrorResume(throwable -> this.handleException(throwable, event, logMessage));
                    }
                }

                return event.reply(this.i18n.translate("command.not-found", event)).withEphemeral(true);
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

    private Mono<Void> sendRandomMessage(@Nullable Member member, CloudBotConfig.RandomMessages msgCfg) {
        if (member == null || this.mainGuild == null || msgCfg.getChannelId() == -1L) {
            return Mono.empty();
        }

        String msg = msgCfg.getMessage();
        if (msg == null) {
            return Mono.empty();
        }

        return this.mainGuild.getChannelById(Snowflake.of(msgCfg.getChannelId()))
                .filter(channel -> channel instanceof TextChannel)
                .map(channel -> (TextChannel) channel)
                .flatMap(channel -> channel.createMessage(msg.formatted(member.getMention()))
                        .withAllowedMentions(AllowedMentions.builder().allowUser(member.getId()).build()))
                .then();
    }

    private Mono<Void> checkInvites(Member member) {
        if (this.mainGuild == null) {
            return Mono.empty();
        }

        List<ExtendedInvite> invites;
        synchronized (this.currentInvites) {
            invites = this.currentInvites.stream()
                    // filter out expired invites, they don't count
                    .filter(invite -> invite.getExpiration()
                            .map(expiration -> expiration.isBefore(Instant.now()))
                            .orElse(true))
                    .toList();
        }

        return this.reloadInvites()
                .flatMap(newInvites -> {
                    Map<String, ExtendedInvite> newCodes = newInvites.stream()
                            .collect(Collectors.toUnmodifiableMap(ExtendedInvite::getCode, Function.identity()));

                    // look for invites which had one use remaining and are now gone
                    Optional<ExtendedInvite> usedInvite = invites.stream()
                            .filter(invite -> invite.getMaxUses() > 0)
                            .filter(invite -> invite.getMaxUses() - invite.getUses() == 1)
                            .filter(invite -> !newCodes.containsKey(invite.getCode()))
                            .findAny();

                    if (usedInvite.isEmpty()) {
                        // fallback to searching where an invitation's use count was incremented by 1
                        usedInvite = invites.stream()
                                .filter(invite -> newCodes.containsKey(invite.getCode()))
                                .filter(invite -> invite.getUses() + 1 == newCodes.get(invite.getCode()).getUses())
                                .findAny();
                    }

                    return Mono.justOrEmpty(usedInvite);
                })
                .flatMap(invite -> {
                    String inviteUrl = "https://discord.gg/%s".formatted(invite.getCode());
                    this.plugin.getSLF4JLogger().info("{} was invited by {}", member.getTag(), inviteUrl);

                    if (this.logChannel == null) {
                        return Mono.empty();
                    }

                    String desc = "**%s** (`%s`)\n".formatted(member.getTag(), member.getId()) +
                            "Invite: " + inviteUrl + invite.getInviter()
                            .map(inviter -> "\n  Created by **%s** (`%s`)"
                                    .formatted(inviter.getTag(), inviter.getId()))
                            .orElse("");

                    EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder()
                            .description(desc)
                            .color(Color.BLUE);

                    invite.getExpiration().ifPresent(expiresAt ->
                            embed.footer("Expires", null)
                                    .timestamp(expiresAt));

                    return this.logChannel.createMessage()
                            .withEmbeds(embed.build());
                })
                .then();
    }

    private Mono<Void> updateMemberCounter(GuildChannel channel) {
        // only voice + text channels are supported
        if (channel == null || (channel.getType() != Channel.Type.GUILD_VOICE
                && channel.getType() != Channel.Type.GUILD_TEXT)) {
            return Mono.empty();
        }

        CloudBotConfig.MemberCounter counterCfg = this.getConfig().getMemberCounter();
        return channel.getGuild()
                .flatMap(guild -> {
                    if (counterCfg.isExcludeBots()) {
                        // need to fetch all members and then count the non-bots,
                        // can't do this without requesting a lot
                        return guild.getMembers()
                                .filter(Predicate.not(Member::isBot))
                                .count();
                    }
                    return Mono.just(guild.getMemberCount());
                })
                .map(count -> counterCfg.getFormat().formatted(count.intValue()))
                .filter(name -> !channel.getName().equals(name))
                .flatMap(name -> {
                    if (channel.getType() == Channel.Type.GUILD_VOICE) {
                        return VoiceChannelEditMono.of((VoiceChannel) channel).withName(name);
                    }
                    if (channel.getType() == Channel.Type.GUILD_TEXT) {
                        return TextChannelEditMono.of((TextChannel) channel).withName(name);
                    }
                    throw new AssertionError();
                })
                .then();
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

    public @Nullable Guild getMainGuild() {
        return this.mainGuild;
    }

    public @Nullable TextChannel getLogChannel() {
        return this.logChannel;
    }

    public @Nullable GuildChannel getMemberCounterChannel() {
        return this.memberCounterChannel;
    }

    public Plugin getPlugin() {
        return this.plugin;
    }

    public boolean isDirty() {
        return this.isDirty;
    }
}
