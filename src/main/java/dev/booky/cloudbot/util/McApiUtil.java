package dev.booky.cloudbot.util;
// Created by booky10 in CloudBot (19:20 11.10.22)

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class McApiUtil {

    public static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{1,16}");

    private static final URI NAME_URI = URI.create("https://api.mojang.com/users/profiles/minecraft/");
    private static final URI UUID_URI = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/");

    private static final LoadingCache<String, Optional<McProfile>> NAME_PROFILE_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build(McApiUtil::loadProfile0);
    private static final LoadingCache<UUID, Optional<McProfile>> UUID_PROFILE_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build(McApiUtil::loadProfile0);

    public static Optional<McProfile> loadProfile(UUID uniqueId) {
        return UUID_PROFILE_CACHE.get(uniqueId);
    }

    public static Optional<McProfile> loadProfile(String username) {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Illegal minecraft username '" + username + "'");
        }

        return NAME_PROFILE_CACHE.get(username.toLowerCase(Locale.ROOT));
    }

    private static Optional<McProfile> loadProfile0(UUID uniqueId) {
        HttpRequest req = HttpRequest.newBuilder(UUID_URI.resolve(uniqueId.toString())).build();
        HttpResponse<String> resp = HTTP.sendAsync(req, BodyHandlers.ofString()).join();
        if (resp.statusCode() == 204) {
            return Optional.empty(); // unknown player
        } else if (resp.statusCode() != 200) {
            throw new IllegalStateException("Server returned unexpected response:\n" + resp);
        }

        JsonObject jsonResp = GSON.fromJson(resp.body(), JsonObject.class);
        if (!jsonResp.has("name")) {
            throw new IllegalStateException("Server returned unknown response:\n" + jsonResp);
        }

        String username = jsonResp.get("name").getAsString();
        Optional<McProfile> profile = Optional.of(new McProfile(username, uniqueId));
        NAME_PROFILE_CACHE.put(username, profile);
        return profile;
    }

    private static Optional<McProfile> loadProfile0(String username) {
        HttpRequest req = HttpRequest.newBuilder(NAME_URI.resolve(username)).build();
        HttpResponse<String> resp = HTTP.sendAsync(req, BodyHandlers.ofString()).join();
        if (resp.statusCode() == 404) {
            return Optional.empty(); // unknown player
        } else if (resp.statusCode() != 200) {
            throw new IllegalStateException("Server returned unexpected response:\n" + resp);
        }

        JsonObject jsonResp = GSON.fromJson(resp.body(), JsonObject.class);
        if (!jsonResp.has("id") || !jsonResp.has("name")) {
            throw new IllegalStateException("Server returned unknown response:\n" + jsonResp);
        }

        UUID uniqueId = FastUuidSansHyphens.parseUuid(jsonResp.get("id").getAsString());
        String realUsername = jsonResp.get("name").getAsString();

        Optional<McProfile> profile = Optional.of(new McProfile(realUsername, uniqueId));
        UUID_PROFILE_CACHE.put(uniqueId, profile);
        return profile;
    }

    public static class McProfile {

        private final String username;
        private final UUID uniqueId;

        public McProfile(String username, UUID uniqueId) {
            this.username = username;
            this.uniqueId = uniqueId;
        }

        public PlayerProfile createBukkit() {
            return Bukkit.createProfile(this.uniqueId, this.username);
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
