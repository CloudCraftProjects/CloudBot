package dev.booky.cloudbot.dclistener;
// Created by booky10 in CloudBot (13:59 12.06.23)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.events.DcEventHandler;
import dev.booky.cloudbot.events.DcListener;
import dev.booky.cloudbot.events.custom.MainGuildDataReloadEvent;
import discord4j.core.event.domain.InviteCreateEvent;
import discord4j.core.event.domain.InviteDeleteEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.object.ExtendedInvite;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class InviteListener implements DcListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("CloudBot");

    private final List<ExtendedInvite> currentInvites = new ArrayList<>();
    private final CloudBotManager manager;

    public InviteListener(CloudBotManager manager) {
        this.manager = manager;
    }

    public Mono<List<ExtendedInvite>> reloadInvites(Guild mainGuild) {
        return mainGuild.getInvites().collectList()
                .doOnSuccess(invites -> {
                    synchronized (this.currentInvites) {
                        this.currentInvites.clear();
                        if (invites != null) {
                            this.currentInvites.addAll(invites);
                        }
                    }
                });
    }

    @DcEventHandler
    public Mono<Void> onDataReload(MainGuildDataReloadEvent event) {
        return event.getMainGuild()
                .map(this::reloadInvites)
                .map(Mono::then)
                .orElseGet(Mono::empty);
    }

    @DcEventHandler
    public Mono<Void> onInviteCreate(InviteCreateEvent event) {
        if (!event.getGuildId()
                .map(id -> id.asLong() == this.manager.getConfig().getMainGuildId())
                .orElse(false)) {
            // only track invites for main guild
            return Mono.empty();
        }

        // this works, don't judge it
        return event.getClient().getRestClient().getInviteService()
                .getInvite(event.getCode())
                .map(data -> new ExtendedInvite(event.getClient(), data))
                .doOnSuccess(invite -> {
                    if (invite == null) {
                        return;
                    }
                    synchronized (this.currentInvites) {
                        this.currentInvites.add(invite);
                    }
                })
                .then();
    }

    @DcEventHandler
    public Mono<Void> onInviteDelete(InviteDeleteEvent event) {
        if (!event.getGuildId()
                .map(id -> id.asLong() == this.manager.getConfig().getMainGuildId())
                .orElse(false)) {
            // only track invites for main guild
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            synchronized (this.currentInvites) {
                this.currentInvites.removeIf(invite -> invite.getCode().equals(event.getCode()));
            }
        });
    }

    @DcEventHandler
    public Mono<Void> onMemberJoin(MemberJoinEvent event) {
        Guild mainGuild = this.manager.getMainGuild();
        if (mainGuild == null || event.getGuildId().asLong() != this.manager.getConfig().getMainGuildId()) {
            // only track invites for main guild, if it is available
            return Mono.empty();
        }

        // filter out expired invites, they don't count
        List<ExtendedInvite> invites;
        synchronized (this.currentInvites) {
            invites = this.currentInvites.stream()
                    .filter(invite -> invite.getExpiration()
                            .map(expiration -> expiration.isBefore(Instant.now()))
                            .orElse(true))
                    .toList();
        }

        return this.reloadInvites(mainGuild)
                .flatMap(newInvites -> {
                    Map<String, ExtendedInvite> newCodes = newInvites.stream()
                            .collect(Collectors.toUnmodifiableMap(ExtendedInvite::getCode, Function.identity()));

                    // look for invites which had one use remaining and are now gone
                    Optional<ExtendedInvite> usedInvite = invites.stream()
                            .filter(invite -> invite.getMaxUses() > 0)
                            .filter(invite -> invite.getMaxUses() - invite.getUses() == 1)
                            .filter(invite -> !newCodes.containsKey(invite.getCode()))
                            .findAny();

                    if (usedInvite.isEmpty()) {
                        // fallback to searching where an invitation's use count was incremented by 1
                        usedInvite = invites.stream()
                                .filter(invite -> newCodes.containsKey(invite.getCode()))
                                .filter(invite -> invite.getUses() + 1 == newCodes.get(invite.getCode()).getUses())
                                .findAny();
                    }

                    return Mono.justOrEmpty(usedInvite);
                })
                .flatMap(invite -> {
                    Member member = event.getMember();
                    String inviteUrl = "https://discord.gg/%s".formatted(invite.getCode());
                    LOGGER.info("{} was invited by {}", member.getTag(), inviteUrl);

                    TextChannel logChannel = this.manager.getLogChannel();
                    if (logChannel == null) {
                        return Mono.empty();
                    }

                    String desc = "**%s** (`%s`)\n".formatted(member.getTag(), member.getId()) +
                            "Invite: " + inviteUrl + invite.getInviter()
                            .map(inviter -> "\n  Created by **%s** (`%s`)"
                                    .formatted(inviter.getTag(), inviter.getId()))
                            .orElse("");

                    EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder()
                            .description(desc)
                            .color(Color.BLUE);

                    invite.getExpiration().ifPresent(expiresAt ->
                            embed.footer("Expires", null)
                                    .timestamp(expiresAt));

                    return logChannel.createMessage()
                            .withEmbeds(embed.build());
                })
                .then();
    }
}
