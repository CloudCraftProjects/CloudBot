package dev.booky.cloudbot.util;
// Created by booky10 in CloudBot (21:49 12.10.22)

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public final class MarkdownEscape {

    private static final Pattern MD_ESCAPE = Pattern.compile("([_*~`>])");

    @Contract("null -> null; !null -> !null")
    public static @Nullable String escape(@Nullable String string) {
        if (string == null) {
            return null;
        }
        return MD_ESCAPE.matcher(string).replaceAll("\\\\$1");
    }

    @Contract("null -> null; !null -> !null")
    public static @Nullable String codeEscape(@Nullable String string) {
        if (string == null) {
            return null;
        }
        return string.replace("`", "\\`");
    }
}
