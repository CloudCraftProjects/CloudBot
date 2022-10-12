package dev.booky.cloudbot.listener;
// Created by booky10 in CloudBot (17:39 12.10.22)

import dev.booky.cloudbot.CloudBotManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.UUID;

public class LoginListener implements Listener {

    private final CloudBotManager manager;

    public LoginListener(CloudBotManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        if (!this.manager.getConfig().isWhitelistActive()) {
            return;
        }

        UUID uniqueId = event.getPlayer().getUniqueId();
        if (this.manager.getStorage().getWhitelist().containsKey(uniqueId)) {
            return;
        }

        String inviteLink = this.manager.getConfig().getInviteLink();
        String message = "Please join our discord";
        if (inviteLink != null) {
            message += ": " + inviteLink;
        }

        event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, Component.text(message, NamedTextColor.RED));
    }
}
