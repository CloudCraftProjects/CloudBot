package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (17:12 31.10.22)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import dev.booky.cloudbot.util.DiscordComponentRenderer;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import reactor.core.publisher.Mono;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExecuteCommand extends AbstractBotCommand {

    private static final SimpleDateFormat LOG_PREFIX = new SimpleDateFormat("HH:mm:ss");

    public ExecuteCommand(CloudBotManager manager) {
        super(manager, "execute");
    }

    @Override
    protected void buildRequest(ImmutableApplicationCommandRequest.Builder builder) {
        builder
                .description("Execute a console command")
                .descriptionLocalizationsOrNull(Map.of("de", "Führe einen Befehl in der Konsole aus"))
                .defaultMemberPermissions(Long.toString(PermissionSet.of(Permission.ADMINISTRATOR).getRawValue()))
                .dmPermission(false)
                .addOption(ApplicationCommandOptionData.builder()
                        .name("command")
                        .nameLocalizationsOrNull(Map.of("de", "befehl"))
                        .description("The command to execute")
                        .descriptionLocalizationsOrNull(Map.of("de", "Der Befehl, welcher ausgeführt werden soll"))
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(true)
                        .build());
    }

    @Override
    public Mono<Void> run(ChatInputInteractionEvent event, User user, Translator i18n) {
        String command = event.getOption("command")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElseThrow();

        event.reply(i18n.apply("command.execute.executing")).withEphemeral(true).subscribe();
        Bukkit.getScheduler().runTask(this.manager.getPlugin(), () -> {
            List<String> feedback = new ArrayList<>();
            AtomicBoolean updating = new AtomicBoolean();

            CommandSender[] sender = new CommandSender[1];
            sender[0] = Bukkit.createCommandSender(msg -> {
                Component translatedMessage = GlobalTranslator.render(msg, Locale.getDefault());
                String ansiMessage = DiscordComponentRenderer.render(translatedMessage);

                String[] lines = StringUtils.split(ansiMessage, '\n');
                String currentTime = "[" + LOG_PREFIX.format(new Date()) + "] ";

                for (String line : lines) {
                    // nobody will know
                    line = line.replace("```", "´´´");
                    synchronized (feedback) {
                        feedback.add(currentTime + line);
                    }
                }

                if (updating.compareAndSet(false, true)) {
                    Bukkit.getScheduler().runTaskLaterAsynchronously(this.manager.getPlugin(), () -> {
                        String content;
                        synchronized (feedback) {
                            content = String.join("\n", feedback);
                        }
                        updating.set(false);

                        event.editReply("```ansi\n" + content + "\n```").block();
                    }, 20);
                }
            });
            Bukkit.dispatchCommand(sender[0], command);
        });
        return Mono.empty();
    }
}
