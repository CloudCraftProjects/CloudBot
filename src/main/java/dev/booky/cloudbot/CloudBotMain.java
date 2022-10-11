package dev.booky.cloudbot;
// Created by booky10 in CloudBot (15:48 10.10.22)

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public class CloudBotMain extends JavaPlugin {

    private CloudBotManager manager;

    @Override
    public void onLoad() {
        this.manager = new CloudBotManager(this, super.getDataFolder().toPath());
        new Metrics(this, 16636);
    }

    @Override
    public void onEnable() {
        this.manager.reloadConfig();
        this.manager.startBot();
    }

    @Override
    public void onDisable() {
        this.manager.saveConfig();
    }
}
