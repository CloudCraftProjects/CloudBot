package dev.booky.cloudbot.dclistener;
// Created by booky10 in CloudBot (14:35 12.06.23)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.events.DcEventHandler;
import dev.booky.cloudbot.events.DcListener;
import dev.booky.cloudbot.events.custom.MainGuildDataReloadEvent;
import dev.booky.cloudbot.storage.CloudBotConfig;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.core.spec.TextChannelEditMono;
import discord4j.core.spec.VoiceChannelEditMono;
import reactor.core.publisher.Mono;

import java.util.function.Predicate;

public final class MemberCounterListener implements DcListener {

    private final CloudBotManager manager;
    private GuildChannel memberCounterChannel;

    public MemberCounterListener(CloudBotManager manager) {
        this.manager = manager;
    }

    public Mono<GuildChannel> reloadMemberCounterChannel(Guild mainGuild) {
        return Mono.defer(() -> {
                    long channelId = this.manager.getConfig().getMemberCounter().getChannelId();
                    if (channelId != -1L) {
                        return mainGuild.getChannelById(Snowflake.of(channelId));
                    }
                    return Mono.empty();
                })
                .doOnSuccess(channel -> {
                    this.memberCounterChannel = channel;
                    this.updateMemberCounter(channel).subscribe();
                });
    }

    private Mono<Void> updateMemberCounter(GuildChannel channel) {
        // only voice + text channels are supported
        if (channel == null || (channel.getType() != Channel.Type.GUILD_VOICE
                && channel.getType() != Channel.Type.GUILD_TEXT)) {
            return Mono.empty();
        }

        CloudBotConfig.MemberCounter counterCfg = this.manager.getConfig().getMemberCounter();
        return channel.getGuild()
                .flatMap(guild -> {
                    if (counterCfg.isExcludeBots()) {
                        // need to fetch all members and then count the non-bots,
                        // can't do this without requesting a lot
                        return guild.getMembers()
                                .filter(Predicate.not(Member::isBot))
                                .count();
                    }
                    return Mono.just(guild.getMemberCount());
                })
                .map(count -> counterCfg.getFormat().formatted(count.intValue()))
                .filter(name -> !channel.getName().equals(name))
                .flatMap(name -> {
                    if (channel.getType() == Channel.Type.GUILD_VOICE) {
                        return VoiceChannelEditMono.of((VoiceChannel) channel).withName(name);
                    }
                    if (channel.getType() == Channel.Type.GUILD_TEXT) {
                        return TextChannelEditMono.of((TextChannel) channel).withName(name);
                    }
                    throw new AssertionError();
                })
                .then();
    }

    @DcEventHandler
    public Mono<Void> onDataReload(MainGuildDataReloadEvent event) {
        return event.getMainGuild()
                .map(this::reloadMemberCounterChannel)
                .map(Mono::then)
                .orElseGet(Mono::empty);
    }

    @DcEventHandler
    public Mono<Void> onMemberJoin(MemberJoinEvent event) {
        if (event.getGuildId().asLong() == this.manager.getConfig().getMainGuildId()) {
            return this.updateMemberCounter(this.memberCounterChannel).then();
        }
        return Mono.empty();
    }

    @DcEventHandler
    public Mono<Void> onMemberLeave(MemberLeaveEvent event) {
        if (event.getGuildId().asLong() == this.manager.getConfig().getMainGuildId()) {
            return this.updateMemberCounter(this.memberCounterChannel).then();
        }
        return Mono.empty();
    }
}
