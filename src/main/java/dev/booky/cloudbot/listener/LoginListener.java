package dev.booky.cloudbot.listener;
// Created by booky10 in CloudBot (17:39 12.10.22)

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.util.FloodgateUtil;
import dev.booky.cloudbot.util.LuckPermsUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

public class LoginListener implements Listener {

    private final CloudBotManager manager;

    public LoginListener(CloudBotManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWhitelistCheck(ProfileWhitelistVerifyEvent event) {
        if (!event.isWhitelisted()) {
            return; // already kicked
        }
        if (!this.manager.getConfig().isWhitelistActive()) {
            return;
        }

        UUID playerId = Objects.requireNonNull(event.getPlayerProfile().getId());
        System.out.println(playerId);
        if (this.manager.getStorage().getWhitelist().containsKey(playerId)) {
            return; // java player is whitelisted, everything is fine
        }

        OptionalLong bedrockId = FloodgateUtil.getBedrockId(playerId);
        if (bedrockId.isPresent() && this.manager.getStorage().getBedrockWhitelist().containsKey(bedrockId.getAsLong())) {
            return; // bedrock player is whitelisted, everything is fine
        }

        if (LuckPermsUtil.hasPermission(playerId, "cloudbot.bypass-whitelist", false)) {
            return; // bypass permission set
        }

        String inviteLink = this.manager.getConfig().getInviteLink();
        String message = "You are not whitelisted on this server.";
        if (inviteLink != null) {
            message += "\nYou can join our discord using " + inviteLink + " for whitelisting yourself.";
        }

        event.setWhitelisted(false);
        event.kickMessage(Component.text(message, NamedTextColor.RED));
    }
}
