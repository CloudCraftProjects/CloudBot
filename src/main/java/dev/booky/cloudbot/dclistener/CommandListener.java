package dev.booky.cloudbot.dclistener;
// Created by booky10 in CloudBot (13:21 12.06.23)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.commands.AbstractBotCommand;
import dev.booky.cloudbot.commands.ExecuteCommand;
import dev.booky.cloudbot.commands.ListCommand;
import dev.booky.cloudbot.commands.MessageCommand;
import dev.booky.cloudbot.commands.PingCommand;
import dev.booky.cloudbot.commands.PluginsCommand;
import dev.booky.cloudbot.commands.ReloadConfigCommand;
import dev.booky.cloudbot.commands.TeamMembersCommand;
import dev.booky.cloudbot.commands.TpsCommand;
import dev.booky.cloudbot.commands.UserInfoCommand;
import dev.booky.cloudbot.commands.WhitelistCommand;
import dev.booky.cloudbot.commands.WhitelistRemoveCommand;
import dev.booky.cloudbot.events.DcEventHandler;
import dev.booky.cloudbot.events.DcListener;
import dev.booky.cloudbot.events.custom.MainGuildDataReloadEvent;
import dev.booky.cloudbot.i18n.Translator;
import dev.booky.cloudbot.storage.CloudBotConfig;
import dev.booky.cloudbot.util.CommandStringifier;
import dev.booky.cloudbot.util.MarkdownEscape;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.service.ApplicationService;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CommandListener implements DcListener {

    private final CloudBotManager manager;
    private final Map<String, AbstractBotCommand> commands;

    public CommandListener(CloudBotManager manager) {
        this.manager = manager;

        this.commands = Stream.of(
                        new ExecuteCommand(manager),
                        new ListCommand(manager),
                        new MessageCommand(manager),
                        new PingCommand(manager),
                        new PluginsCommand(manager),
                        new ReloadConfigCommand(manager),
                        new TeamMembersCommand(manager),
                        new TpsCommand(manager),
                        new UserInfoCommand(manager),
                        new WhitelistCommand(manager),
                        new WhitelistRemoveCommand(manager)
                ).filter(AbstractBotCommand::shouldRegister)
                .collect(Collectors.toUnmodifiableMap(AbstractBotCommand::getLabel, Function.identity()));

        this.commands.values().stream()
                .filter(command -> command instanceof DcListener)
                .map(command -> (DcListener) command)
                .forEach(manager.getEventManager()::register);
    }

    public Mono<Void> reloadCustomCommands(long appId, Guild mainGuild) {
        ApplicationService appService = mainGuild.getClient().getRestClient().getApplicationService();
        return appService.bulkOverwriteGuildApplicationCommand(appId, mainGuild.getId().asLong(),
                        this.manager.getConfig().getCustomCommands().entrySet().stream()
                                .map(entry -> entry.getValue().buildRequest(entry.getKey())).toList())
                .collectList().then();
    }

    public Mono<Void> reloadGlobalCommands(long appId, GatewayDiscordClient gateway) {
        List<ApplicationCommandRequest> requests = this.commands.values().stream()
                .map(AbstractBotCommand::buildRequest).toList();

        ApplicationService appService = gateway.getRestClient().getApplicationService();
        return appService.bulkOverwriteGlobalApplicationCommand(appId, requests).collectList().then();
    }

    @DcEventHandler
    public Mono<Void> onDataReload(MainGuildDataReloadEvent event) {
        return event.getClient().getRestClient().getApplicationId()
                // reload global commands
                .flatMap(appId -> this.reloadGlobalCommands(appId, event.getClient()).then()
                        .then().and(event.getMainGuild()
                                // reload custom commands, only present on the main guild
                                .map(guild -> this.reloadCustomCommands(appId, guild))
                                .orElseGet(Mono::empty)));
    }

    @DcEventHandler
    public Mono<Void> onCommandInteraction(ChatInputInteractionEvent event) {
        User user = event.getInteraction().getUser();
        CompletableFuture<Message> logMessage = new CompletableFuture<>();

        GuildMessageChannel logChannel = this.manager.getLogChannel();
        if (logChannel != null) {
            Optional<Snowflake> guildId = event.getInteraction().getGuildId();
            String location = guildId.map(snowflake -> "Guild: `" + snowflake.asString() + "`\n" +
                            "Channel: `" + event.getInteraction().getChannelId().asString() + "`")
                    .orElseGet(() -> "Private Messages: `" + event.getInteraction().getChannelId().asString() + "`")
                    + "\n";

            String desc = "**" + MarkdownEscape.escape(user.getTag()) + "** (`" + user.getId().asString() + ")`\n" +
                    location + "> " + CommandStringifier.stringify(event);

            logChannel.createMessage().withEmbeds(EmbedCreateSpec.builder()
                            .description(desc).color(Color.of(0xA9F90F))
                            .timestamp(Instant.now()).footer(user.getTag(), user.getAvatarUrl())
                            .build())
                    .subscribe(logMessage::complete);
        }

        try {
            Translator i18n = this.manager.createTranslator(event.getInteraction());
            {
                AbstractBotCommand command = this.commands.get(event.getCommandName());
                if (command != null) {
                    return command.run(event, user, i18n)
                            .onErrorResume(throwable -> this.handleException(throwable, event, logMessage));
                }
            }

            {
                CloudBotConfig.CustomCommand command = this.manager.getConfig().getCustomCommands().get(event.getCommandName());
                if (command != null) {
                    return command.run(event)
                            .onErrorResume(throwable -> this.handleException(throwable, event, logMessage));
                }
            }

            return event.reply(i18n.apply("command.not-found")).withEphemeral(true);
        } catch (Throwable throwable) {
            return this.handleException(throwable, event, logMessage);
        }
    }

    public Mono<Void> handleException(Throwable throwable,
                                      ChatInputInteractionEvent event,
                                      CompletableFuture<Message> logMessage) {
        throwable.printStackTrace();

        GuildMessageChannel logChannel = this.manager.getLogChannel();
        if (logChannel == null) {
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
            logChannel.createMessage()
                    .withEmbeds(EmbedCreateSpec.builder()
                            .description("```" + stacktrace + "```")
                            .color(Color.of(0xCE3C1E))
                            .timestamp(Instant.now())
                            .footer(user.getTag(), user.getAvatarUrl())
                            .build())
                    .withMessageReference(msg.getId())
                    .subscribe();
        });

        // we sadly don't know if a reply has already been deferred, so have to ignore errors :(
        event.deferReply().withEphemeral(true)
                .onErrorResume(error -> Mono.empty()).subscribe();

        Translator i18n = this.manager.createTranslator(event.getInteraction());
        return event.createFollowup(i18n.apply("command.errored", event,
                "`" + MarkdownEscape.codeEscape(throwable.toString()) + "`")).then();
    }
}
