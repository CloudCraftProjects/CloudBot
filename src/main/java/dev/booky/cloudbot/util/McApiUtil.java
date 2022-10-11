package dev.booky.cloudbot.util;
// Created by booky10 in CloudBot (19:20 11.10.22)

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class McApiUtil {

    private static final URI BASE_URI = URI.create("https://api.mojang.com/users/profiles/minecraft/");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{3,16}");

    private static final LoadingCache<String, McProfile> PROFILE_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build(McApiUtil::loadProfile0);

    public static McProfile loadProfile(String username) {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Illegal minecraft username '" + username + "'");
        }

        return PROFILE_CACHE.get(username.toLowerCase(Locale.ROOT));
    }

    private static McProfile loadProfile0(String username) {
        String response = HttpUtil.getJoin(BASE_URI.resolve(username), HttpResponse.BodyHandlers.ofString());
        if (response.isEmpty()) { // Player does not exist
            throw new IllegalArgumentException("Player '" + username + "' does not exist");
        }

        JsonObject jsonResp = GSON.fromJson(response, JsonObject.class);
        if (!jsonResp.has("id") || !jsonResp.has("name")) {
            throw new IllegalStateException("Server returned unknown response:\n" + jsonResp);
        }

        UUID uniqueId = FastUuidSansHyphens.parseUuid(jsonResp.get("id").getAsString());
        String realUsername = jsonResp.get("name").getAsString();
        return new McProfile(realUsername, uniqueId);
    }

    public static class McProfile {

        private final String username;
        private final UUID uniqueId;

        public McProfile(String username, UUID uniqueId) {
            this.username = username;
            this.uniqueId = uniqueId;
        }

        public String getUsername() {
            return this.username;
        }

        public UUID getUniqueId() {
            return this.uniqueId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof McProfile mcProfile)) return false;
            if (!this.username.equals(mcProfile.username)) return false;
            return this.uniqueId.equals(mcProfile.uniqueId);
        }

        @Override
        public int hashCode() {
            int result = this.username.hashCode();
            result = 31 * result + this.uniqueId.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "'" + this.username + "'";
        }
    }
}
