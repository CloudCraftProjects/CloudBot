package dev.booky.cloudbot.dclistener;
// Created by booky10 in CloudBot (14:19 12.06.23)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.events.DcEventHandler;
import dev.booky.cloudbot.events.DcListener;
import dev.booky.cloudbot.storage.CloudBotConfig;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.rest.util.AllowedMentions;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

public final class JoinLeaveMessageListener implements DcListener {

    private final CloudBotManager manager;

    public JoinLeaveMessageListener(CloudBotManager manager) {
        this.manager = manager;
    }

    public Mono<Void> sendRandomMessage(Member member, CloudBotConfig.RandomMessages msgCfg) {
        if (member.isBot() && !msgCfg.isAllowBots()) {
            return Mono.empty();
        }

        Guild mainGuild = this.manager.getMainGuild();
        if (mainGuild == null || msgCfg.getChannelId() == -1L) {
            return Mono.empty();
        }

        Map<String, ?> props = Map.of(
                "displayname", member.getGlobalName().orElse(member.getTag()),
                "username", member.getTag(),
                "mention", member.getMention(),
                "avatar", member.getEffectiveAvatarUrl(),
                "join_time", member.getJoinTime().map(Instant::getEpochSecond).orElse(0L));
        String msg = msgCfg.getMessage(props);
        if (msg == null) {
            return Mono.empty();
        }

        return mainGuild.getChannelById(Snowflake.of(msgCfg.getChannelId()))
                .filter(channel -> channel instanceof GuildMessageChannel)
                .map(channel -> (GuildMessageChannel) channel)
                .flatMap(channel -> channel.createMessage(msg)
                        .withAllowedMentions(AllowedMentions.builder().allowUser(member.getId()).build()))
                .then();
    }

    @DcEventHandler
    public Mono<Void> onMemberJoin(MemberJoinEvent event) {
        if (event.getGuildId().asLong() == this.manager.getConfig().getMainGuildId()) {
            return this.sendRandomMessage(event.getMember(), this.manager.getConfig().getJoinMessages());
        }
        return Mono.empty();
    }

    @DcEventHandler
    public Mono<Void> onMemberLeave(MemberLeaveEvent event) {
        if (event.getGuildId().asLong() == this.manager.getConfig().getMainGuildId()) {
            return event.getMember()
                    .map(member -> this.sendRandomMessage(member, this.manager.getConfig().getLeaveMessages()))
                    .orElseGet(Mono::empty);
        }
        return Mono.empty();
    }
}
