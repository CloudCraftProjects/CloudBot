package dev.booky.cloudbot.dclistener;
// Created by booky10 in CloudBot (15:09 12.06.23)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.events.DcEventHandler;
import dev.booky.cloudbot.events.DcListener;
import dev.booky.cloudbot.events.custom.MainGuildDataReloadEvent;
import dev.booky.cloudbot.storage.CloudBotConfig;
import dev.booky.cloudbot.storage.MessageRef;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveAllEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import discord4j.core.object.emoji.Emoji;
import discord4j.core.object.emoji.UnicodeEmoji;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.reaction.Reaction;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class ReactionRoleListener implements DcListener {

    private final CloudBotManager manager;

    public ReactionRoleListener(CloudBotManager manager) {
        this.manager = manager;
    }

    public Mono<Void> reloadReactionRoles(Guild mainGuild) {
        return Mono.defer(() -> {
            Mono<Void> mono = Mono.empty();
            for (Map.Entry<MessageRef, Set<CloudBotConfig.ReactionRole>> entry : this.manager.getConfig().getReactionRoles().entrySet()) {
                mono = mono.and(entry.getKey().getMessage(mainGuild).flatMap(message -> {
                    Mono<Void> reactionMono = Mono.empty();
                    for (CloudBotConfig.ReactionRole role : entry.getValue()) {
                        UnicodeEmoji emoji = Emoji.unicode(role.getEmoji());
                        if (message.getReactions().stream().map(Reaction::getEmoji).noneMatch(Predicate.isEqual(emoji))) {
                            reactionMono = reactionMono.and(message.addReaction(emoji).then());
                        }
                    }
                    return reactionMono;
                }).then());
            }
            return mono;
        });
    }

    @DcEventHandler
    public Mono<Void> onDataReload(MainGuildDataReloadEvent event) {
        return event.getMainGuild()
                .map(this::reloadReactionRoles)
                .orElseGet(Mono::empty);
    }

    @DcEventHandler
    public Mono<Void> onReactionAdd(ReactionAddEvent event) {
        MessageRef messageRef = MessageRef.of(event.getChannelId(), event.getMessageId());
        Set<CloudBotConfig.ReactionRole> roles = this.manager.getConfig().getReactionRoles().get(messageRef);
        if (roles != null && !roles.isEmpty()) {
            return event.getMember()
                    .map(member -> {
                        Mono<Void> mono = Mono.empty();
                        for (CloudBotConfig.ReactionRole role : roles) {
                            if (role.getEmoji().equals(event.getEmoji().asFormat())) {
                                mono = mono.and(member.addRole(Snowflake.of(role.getRoleId()))).then();
                            }
                        }
                        return mono;
                    })
                    .orElseGet(Mono::empty);
        }
        return Mono.empty();
    }

    @DcEventHandler
    public Mono<Void> onReactionRemove(ReactionRemoveEvent event) {
        MessageRef messageRef = MessageRef.of(event.getChannelId(), event.getMessageId());
        Set<CloudBotConfig.ReactionRole> roles = this.manager.getConfig().getReactionRoles().get(messageRef);
        if (roles == null || roles.isEmpty()) {
            return Mono.empty();
        }

        if (event.getUserId().equals(event.getClient().getSelfId())) {
            // an admin removed our reaction, add it back!
            return event.getGuild().flatMap(this::reloadReactionRoles);
        }

        return event.getGuildId()
                .map(guildId -> event.getClient().getGuildById(guildId)
                        .flatMap(guild -> guild.getMemberById(event.getUserId()))
                        .flatMap(member -> {
                            Mono<Void> mono = Mono.empty();
                            for (CloudBotConfig.ReactionRole role : roles) {
                                if (role.getEmoji().equals(event.getEmoji().asFormat())) {
                                    mono = mono.and(member.removeRole(Snowflake.of(role.getRoleId()))).then();
                                }
                            }
                            return mono;
                        }))
                .orElseGet(Mono::empty);
    }

    @DcEventHandler
    public Mono<Void> onReactionRemoveAll(ReactionRemoveAllEvent event) {
        MessageRef messageRef = MessageRef.of(event.getChannelId(), event.getMessageId());
        Set<CloudBotConfig.ReactionRole> roles = this.manager.getConfig().getReactionRoles().get(messageRef);
        if (roles != null && !roles.isEmpty()) {
            // updates and re-reacts to the message
            return event.getGuild().flatMap(this::reloadReactionRoles);
        }
        return Mono.empty();
    }
}
