package dev.booky.cloudbot;
// Created by booky10 in CloudBot (15:48 10.10.22)

import dev.booky.cloudbot.listener.LoginListener;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CloudBotMain extends JavaPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("CloudBot");

    private CloudBotManager manager;

    @Override
    public void onLoad() {
        this.manager = new CloudBotManager(this, super.getDataFolder().toPath());
        new Metrics(this, 16636);
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
            if (this.manager.isDirty()) {
                this.manager.saveStorages();
            }
        }, 20, 5 * 60 * 20);
    }

    @Override
    public void onDisable() {
        this.manager.saveStorages();
        this.manager.shutdownBot();
    }
}
