package dev.booky.cloudbot;
// Created by booky10 in CloudBot (15:48 10.10.22)

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public class CloudBotMain extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Hello World!");
        new Metrics(this, 16636);
    }
}
