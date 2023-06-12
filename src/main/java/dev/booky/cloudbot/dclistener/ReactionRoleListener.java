package dev.booky.cloudbot.dclistener;
// Created by booky10 in CloudBot (15:09 12.06.23)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.events.DcEventHandler;
import dev.booky.cloudbot.events.DcListener;
import dev.booky.cloudbot.events.custom.MainGuildDataReloadEvent;
import dev.booky.cloudbot.storage.CloudBotConfig;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveAllEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.reaction.Reaction;
import discord4j.core.object.reaction.ReactionEmoji;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Predicate;

public final class ReactionRoleListener implements DcListener {

    private final CloudBotManager manager;

    public ReactionRoleListener(CloudBotManager manager) {
        this.manager = manager;
    }

    public Mono<Void> reloadReactionRoles(Guild mainGuild) {
        return Mono.defer(() -> {
            Mono<Void> mono = Mono.empty();
            for (Map.Entry<Long, CloudBotConfig.ReactionRole> entry : this.manager.getConfig().getReactionRoles().entrySet()) {
                ReactionEmoji.Unicode emoji = ReactionEmoji.unicode(entry.getValue().getEmoji());
                mono = mono.and(mainGuild.getChannelById(Snowflake.of(entry.getValue().getChannelId()))
                        .filter(channel -> channel instanceof TextChannel)
                        .map(channel -> (TextChannel) channel)
                        .flatMap(channel -> channel.getMessageById(Snowflake.of(entry.getKey())))
                        .filter(message -> message.getReactions().stream()
                                .map(Reaction::getEmoji).noneMatch(Predicate.isEqual(emoji)))
                        .flatMap(message -> message.addReaction(emoji))).then();
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
        CloudBotConfig.ReactionRole role = this.manager.getConfig()
                .getReactionRoles().get(event.getMessageId().asLong());
        if (role != null) {
            return event.getMember()
                    .map(member -> member.addRole(Snowflake.of(role.getRoleId())))
                    .orElseGet(Mono::empty);
        }
        return Mono.empty();
    }

    @DcEventHandler
    public Mono<Void> onReactionRemove(ReactionRemoveEvent event) {
        CloudBotConfig.ReactionRole role = this.manager.getConfig()
                .getReactionRoles().get(event.getMessageId().asLong());
        if (role == null) {
            return Mono.empty();
        }

        if (event.getUserId().equals(event.getClient().getSelfId())) {
            // an admin removed our reaction, add it back!
            return event.getGuild().flatMap(this::reloadReactionRoles);
        }

        return event.getGuildId()
                .map(guildId -> event.getClient().getGuildById(guildId)
                        .flatMap(guild -> guild.getMemberById(event.getUserId()))
                        .flatMap(member -> member.removeRole(Snowflake.of(role.getRoleId()))))
                .orElseGet(Mono::empty);
    }

    @DcEventHandler
    public Mono<Void> onReactionRemoveAll(ReactionRemoveAllEvent event) {
        CloudBotConfig.ReactionRole role = this.manager.getConfig()
                .getReactionRoles().get(event.getMessageId().asLong());
        if (role != null) {
            // updates and re-reacts to the message
            return event.getGuild().flatMap(this::reloadReactionRoles);
        }
        return Mono.empty();
    }
}
