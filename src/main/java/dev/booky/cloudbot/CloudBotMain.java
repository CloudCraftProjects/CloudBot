package dev.booky.cloudbot;
// Created by booky10 in CloudBot (15:48 10.10.22)

import dev.booky.cloudbot.listener.LoginListener;
import discord4j.core.GatewayDiscordClient;
import discord4j.gateway.ShardInfo;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CloudBotMain extends JavaPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("CloudBot");

    private CloudBotManager manager;

    @Override
    public void onLoad() {
        new Metrics(this, 16636);

        this.manager = new CloudBotManager(this, super.getDataFolder().toPath());
        Bukkit.getServicesManager().register(CloudBotManager.class, this.manager, this, ServicePriority.Normal);
    }

    @Override
    public void onEnable() {
        this.manager.reloadStorages()
                .filter($ -> {
                    if (this.manager.getConfig().getToken() == null) {
                        LOGGER.error("No token specified in configuration, can't start bot");
                        return false;
                    }
                    return true;
                }).then()
                .and(this.manager.startBot()).subscribe();

        Bukkit.getPluginManager().registerEvents(new LoginListener(this.manager), this);

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            this.manager.saveStorages();

            // update presence
            GatewayDiscordClient gateway = this.manager.getGateway();
            if (gateway != null) {
                int shardCount = gateway.getGatewayClientGroup().getShardCount();
                for (int i = 0; i < shardCount; i++) {
                    ShardInfo shardInfo = ShardInfo.create(i, shardCount);
                    gateway.updatePresence(this.manager.getConfig().buildPresence(shardInfo));
                }
            }
        }, 20, 30 * 20);
    }

    @Override
    public void onDisable() {
        this.manager.saveStorages();
        this.manager.shutdownBot();
    }
}
