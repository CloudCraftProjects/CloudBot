package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (17:12 31.10.22)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import reactor.core.publisher.Mono;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExecuteCommand implements BotCommand {

    private static final SimpleDateFormat LOG_PREFIX = new SimpleDateFormat("HH:mm:ss");

    @Override
    public ApplicationCommandRequest provideCommandData() {
        return ApplicationCommandRequest.builder()
                .name("execute")
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
                        .build())
                .build();
    }

    @Override
    public Mono<Void> run(CloudBotManager manager, String label, ChatInputInteractionEvent event, User user, Translator i18n) {
        String command = event.getOption("command")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElseThrow();

        event.reply("Executing command...").withEphemeral(true).subscribe();
        Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
            List<String> feedback = new ArrayList<>();
            AtomicBoolean dirty = new AtomicBoolean();

            Bukkit.dispatchCommand(Bukkit.createCommandSender(msg -> {
                String[] plainMsg = PlainTextComponentSerializer.plainText().serialize(msg).split("\n");
                String currentTime = "[" + LOG_PREFIX.format(new Date()) + "] ";

                for (String plainMsgPart : plainMsg) {
                    // nobody will know
                    plainMsgPart = plainMsgPart.replace("```", "´´´");
                    feedback.add(currentTime + plainMsgPart);
                }

                if (!dirty.getAndSet(true)) {
                    Bukkit.getScheduler().runTaskLaterAsynchronously(manager.getPlugin(), () ->
                            event.editReply("```\n" + String.join("\n", feedback) + "\n```")
                                    .block(), 20);
                }
            }), command);
        });
        return Mono.empty();
    }
}
