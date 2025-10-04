package dev.booky.cloudbot.util;
// Created by booky10 in CloudBot (17:55 04.10.2025)

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

@NullMarked
public final class LuckPermsUtil {

    private static final boolean LUCKPERMS;

    static {
        boolean luckperms = false;
        try {
            Class.forName("net.luckperms.api.LuckPerms");
            luckperms = true;
        } catch (ClassNotFoundException ignored) {
        }
        LUCKPERMS = luckperms;
    }

    private LuckPermsUtil() {
    }

    public static boolean hasPermission(UUID playerId, String permission, boolean fallback) {
        if (LUCKPERMS) {
            return Handler.hasPermission(playerId, permission, fallback);
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isPermissionSet(permission)) {
            return player.hasPermission(permission);
        }
        return fallback;
    }

    private static final class Handler {

        private Handler() {
        }

        public static boolean hasPermission(UUID playerId, String permission, boolean fallback) {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(playerId);
            if (user == null) {
                return fallback;
            }
            QueryOptions queryOpts = user.getQueryOptions();
            CachedPermissionData perms = user.getCachedData().getPermissionData(queryOpts);
            return switch (perms.checkPermission(permission)) {
                case TRUE -> true;
                case FALSE -> false;
                default -> fallback;
            };
        }
    }
}
