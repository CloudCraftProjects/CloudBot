package dev.booky.cloudbot.util;
// Created by booky10 in CloudBot (19:01 12.10.22)

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;

import java.util.Collection;
import java.util.regex.Pattern;

public final class CommandStringifier {

    private static final Pattern MD_ESCAPE = Pattern.compile("([_*~`>])");

    public static String stringify(ChatInputInteractionEvent event) {
        StringBuilder builder = new StringBuilder("`/" + event.getCommandName());
        stringify0(builder, event.getOptions());
        return builder.append("`").toString();
    }

    private static String escapeMarkdown(String string) {
        return MD_ESCAPE.matcher(string).replaceAll("\\\\$1");
    }

    private static void stringify0(StringBuilder builder, Collection<ApplicationCommandInteractionOption> options) {
        for (ApplicationCommandInteractionOption option : options) {
            builder.append(' ');
            stringify0(builder, option);
        }
    }

    private static void stringify0(StringBuilder builder, ApplicationCommandInteractionOption option) {
        switch (option.getType()) {
            case STRING -> {
                String value = option.getValue().map(ApplicationCommandInteractionOptionValue::asString).orElse("null");
                builder.append(option.getName()).append(":'").append(escapeMarkdown(value)).append('\'');
            }
            case BOOLEAN -> {
                boolean value = option.getValue().map(ApplicationCommandInteractionOptionValue::asBoolean).orElse(false);
                builder.append(option.getName()).append(':').append(value);
            }
            case NUMBER -> {
                double value = option.getValue().map(ApplicationCommandInteractionOptionValue::asDouble).orElse(0d);
                builder.append(option.getName()).append(':').append(value);
            }
            case INTEGER -> {
                long value = option.getValue().map(ApplicationCommandInteractionOptionValue::asLong).orElse(0L);
                builder.append(option.getName()).append(':').append(value);
            }
            case USER, ROLE, CHANNEL, MENTIONABLE -> {
                String value = option.getValue().map(ApplicationCommandInteractionOptionValue::asSnowflake).map(Snowflake::asString).orElse("null");
                builder.append(option.getName()).append(':').append(value).append('[').append(option.getType()).append(']');
            }
            case SUB_COMMAND, SUB_COMMAND_GROUP -> {
                builder.append(option.getName());
                stringify0(builder, option.getOptions());
            }
            default -> {
                String value = option.getType().name() + "[UNSUPPORTED]";
                builder.append(option.getName()).append(':').append(value);
            }
        }
    }
}
