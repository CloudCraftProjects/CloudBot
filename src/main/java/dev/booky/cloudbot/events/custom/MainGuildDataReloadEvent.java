package dev.booky.cloudbot.events.custom;
// Created by booky10 in CloudBot (14:01 12.06.23)

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Guild;
import discord4j.gateway.ShardInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class MainGuildDataReloadEvent extends Event {

    @Nullable
    private final Guild mainGuild;

    public MainGuildDataReloadEvent(GatewayDiscordClient gateway, ShardInfo shardInfo, @Nullable Guild mainGuild) {
        super(gateway, shardInfo);
        this.mainGuild = mainGuild;
    }

    public Optional<Guild> getMainGuild() {
        return Optional.ofNullable(this.mainGuild);
    }

    @Override
    public String toString() {
        return "MainGuildDataReloadEvent{mainGuild=" + this.mainGuild + '}';
    }
}
