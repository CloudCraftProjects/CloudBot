package dev.booky.cloudbot.i18n;
// Created by booky10 in CloudBot (23:24 11.10.22)

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

// Pretty much copy-pasted from adventure, licensed under MIT
public class TranslationRegistry {

    private static final Pattern SINGLE_QUOTE_PATTERN = Pattern.compile("'");

    private final Map<String, Translation> translations = new ConcurrentHashMap<>();
    private final Locale defaultLocale = Locale.ENGLISH;

    public void register(String key, Locale locale, MessageFormat format) {
        this.translations.computeIfAbsent(key, Translation::new).register(locale, format);
    }

    public void registerAll(Locale locale, ResourceBundle bundle, boolean escapeSingleQuotes) {
        for (String key : bundle.keySet()) {
            String format = bundle.getString(key);
            if (escapeSingleQuotes) {
                format = SINGLE_QUOTE_PATTERN.matcher(format).replaceAll("''");
            }
            this.register(key, locale, new MessageFormat(format, locale));
        }
    }

    public void unregister(String key) {
        this.translations.remove(key);
    }

    public MessageFormat translate(String key, Locale locale) {
        Translation translation = this.translations.get(key);
        if (translation == null) {
            return null;
        }

        return translation.translate(locale);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TranslationRegistry that)) return false;
        if (!translations.equals(that.translations)) return false;
        return defaultLocale.equals(that.defaultLocale);
    }

    @Override
    public int hashCode() {
        int result = translations.hashCode();
        result = 31 * result + defaultLocale.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "TranslationRegistry{translations=" + translations + ", defaultLocale=" + defaultLocale + '}';
    }

    public class Translation {

        private final String key;
        private final Map<Locale, MessageFormat> formats = new ConcurrentHashMap<>();

        public Translation(String key) {
            this.key = key;
        }

        public void register(Locale locale, MessageFormat format) {
            if (this.formats.putIfAbsent(locale, format) != null) {
                throw new IllegalArgumentException("Translation already registered: " + key + " (" + locale + ")");
            }
        }

        public MessageFormat translate(Locale locale) {
            MessageFormat format = this.formats.get(locale);
            if (format != null) {
                return format;
            }

            // try without country
            format = this.formats.get(Locale.forLanguageTag(locale.getLanguage()));
            if (format != null) {
                return format;
            }

            // try default locale
            return this.formats.get(TranslationRegistry.this.defaultLocale);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Translation that)) return false;
            if (!key.equals(that.key)) return false;
            return formats.equals(that.formats);
        }

        @Override
        public int hashCode() {
            int result = key.hashCode();
            result = 31 * result + formats.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Translation{key='" + key + '\'' + ", formats=" + formats + '}';
        }
    }
}
