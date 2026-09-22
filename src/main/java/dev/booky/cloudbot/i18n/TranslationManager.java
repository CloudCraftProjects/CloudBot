package dev.booky.cloudbot.i18n;
// Created by booky10 in CloudBot (23:24 11.10.22)

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import org.bukkit.plugin.Plugin;

import java.text.AttributedCharacterIterator;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

public final class TranslationManager {

    private final Plugin plugin;
    private TranslationRegistry registry;

    public TranslationManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public String translate(String key, ChatInputInteractionEvent event, Object... args) {
        return this.translate(key, event.getInteraction().getUserLocale(), args);
    }

    public String translate(String key, String lang, Object... args) {
        int splitIndex = lang.indexOf('-');
        if (splitIndex != -1) {
            lang = lang.substring(0, splitIndex);
        }

        Locale locale = Locale.forLanguageTag(lang);
        return this.translate(key, locale, args);
    }

    public String translate(String key, Locale locale, Object... args) {
        MessageFormat format = this.getRegistry().translate(key, locale);
        if (format == null) {
            return key;
        }

        if (args.length == 0) {
            return format.format(null, new StringBuffer(), null).toString();
        }

        Object[] nulls = new Object[args.length];
        StringBuffer sb = format.format(nulls, new StringBuffer(), null);
        AttributedCharacterIterator it = format.formatToCharacterIterator(nulls);

        StringBuilder builder = new StringBuilder();
        while (it.getIndex() < it.getEndIndex()) {
            Integer index = (Integer) it.getAttribute(MessageFormat.Field.ARGUMENT);
            int end = it.getRunLimit();

            if (index != null) {
                builder.append(args[index]);
            } else {
                builder.append(sb.substring(it.getIndex(), end));
            }
            it.setIndex(end);
        }

        return builder.toString();
    }

    public void reload() {
        TranslationRegistry registry = new TranslationRegistry();
        this.registerBundle(registry, Locale.ENGLISH);
        this.registerBundle(registry, Locale.GERMAN);
        this.registry = registry;
    }

    private void registerBundle(TranslationRegistry registry, Locale locale) {
        String baseName = this.plugin.getPluginMeta().getName().toLowerCase(Locale.ROOT);
        ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale);
        registry.registerAll(locale, bundle, true);
    }

    public TranslationRegistry getRegistry() {
        return Objects.requireNonNull(this.registry, "Translations not loaded yet");
    }
}
