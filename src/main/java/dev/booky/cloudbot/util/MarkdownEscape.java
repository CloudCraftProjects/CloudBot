package dev.booky.cloudbot.util;
// Created by booky10 in CloudBot (21:49 12.10.22)

import java.util.regex.Pattern;

public final class MarkdownEscape {

    private static final Pattern MD_ESCAPE = Pattern.compile("([_*~`>])");

    public static String escape(String string) {
        return MD_ESCAPE.matcher(string).replaceAll("\\\\$1");
    }
}
