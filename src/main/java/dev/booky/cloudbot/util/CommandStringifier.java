package dev.booky.cloudbot.util;
// Created by booky10 in CloudBot (19:01 12.10.22)

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;

import java.util.Collection;

public final class CommandStringifier {

    public static String stringify(ChatInputInteractionEvent event) {
        StringBuilder builder = new StringBuilder("`/");
        builder.append(MarkdownEscape.escape(event.getCommandName()));

        stringify0(builder, event.getOptions());
        return builder.append('`').toString();
    }

    private static void stringify0(StringBuilder builder, Collection<ApplicationCommandInteractionOption> options) {
        for (ApplicationCommandInteractionOption option : options) {
            builder.append(' ');
            stringify0(builder, option);
        }
    }

    private static void stringify0(StringBuilder builder, ApplicationCommandInteractionOption option) {
        builder.append(MarkdownEscape.escape(option.getName())).append(':');
        switch (option.getType()) {
            case STRING -> {
                String value = option.getValue().map(ApplicationCommandInteractionOptionValue::asString).orElse("null");
                builder.append('\'').append(MarkdownEscape.escape(value)).append('\'');
            }
            case BOOLEAN -> builder.append(option.getValue()
                    .map(ApplicationCommandInteractionOptionValue::asBoolean)
                    .orElse(false));
            case NUMBER -> builder.append(option.getValue()
                    .map(ApplicationCommandInteractionOptionValue::asDouble)
                    .orElse(0d));
            case INTEGER -> builder.append(option.getValue()
                    .map(ApplicationCommandInteractionOptionValue::asLong)
                    .orElse(0L));
            case USER, ROLE, CHANNEL, MENTIONABLE -> {
                String value = option.getValue().map(ApplicationCommandInteractionOptionValue::asSnowflake).map(Snowflake::asString).orElse("null");
                builder.append(value).append('[').append(option.getType()).append(']');
            }
            case SUB_COMMAND, SUB_COMMAND_GROUP -> {
                builder.deleteCharAt(builder.length() - 1);
                stringify0(builder, option.getOptions());
            }
            default -> builder.append(option.getType().name()).append("[UNSUPPORTED]");
        }
    }
}
