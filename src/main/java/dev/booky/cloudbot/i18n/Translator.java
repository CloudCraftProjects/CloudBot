package dev.booky.cloudbot.i18n;
// Created by booky10 in CloudBot (16:50 12.10.22)

@FunctionalInterface
public interface Translator {

    String apply(String key, Object... args);
}
