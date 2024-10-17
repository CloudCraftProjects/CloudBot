package dev.booky.cloudbot.util;
// Created by booky10 in CloudBot (03:12 17.10.2024)

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@NullMarked
public final class FloodgateUtil {

    private static final boolean FLOODGATE;

    static {
        boolean floodgate = false;
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgate = true;
        } catch (ClassNotFoundException ignored) {
        }
        FLOODGATE = floodgate;
    }

    private FloodgateUtil() {
    }

    public static OptionalLong getBedrockId(UUID playerId) {
        return FLOODGATE ? Handler.getBedrockId(playerId) : OptionalLong.empty();
    }

    public static CompletableFuture<@Nullable Long> getXuid(String gamertag) {
        return FLOODGATE ? Handler.getXuid(gamertag) : CompletableFuture.completedFuture(null);
    }

    public static UUID createJavaUniqueId(long xuid) {
        return FLOODGATE ? Handler.createJavaUniqueId(xuid) : new UUID(0L, xuid);
    }

    private static final class Handler {

        private Handler() {
        }

        public static OptionalLong getBedrockId(UUID playerId) {
            FloodgatePlayer player = FloodgateApi.getInstance().getPlayer(playerId);
            if (player != null) {
                return OptionalLong.of(Long.parseLong(player.getXuid()));
            }
            return OptionalLong.empty();
        }

        public static CompletableFuture<Long> getXuid(String gamertag) {
            return FloodgateApi.getInstance().getXuidFor(gamertag);
        }

        public static UUID createJavaUniqueId(long xuid) {
            return FloodgateApi.getInstance().createJavaPlayerId(xuid);
        }
    }
}
