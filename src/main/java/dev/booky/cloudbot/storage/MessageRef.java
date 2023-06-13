package dev.booky.cloudbot.storage;
// Created by booky10 in CloudBot (15:52 13.06.23)

import com.google.common.base.Preconditions;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.TextChannel;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

public final class MessageRef {

    private final long channelId;
    private final long messageId;

    private MessageRef(long channelId, long messageId) {
        this.channelId = channelId;
        this.messageId = messageId;
    }

    public static MessageRef of(String string) throws IllegalArgumentException {
        String[] split = StringUtils.split(string, '-');
        Preconditions.checkArgument(split.length == 2, "Invalid message reference provided: %s", string);

        try {
            return of(Long.parseLong(split[0]), Long.parseLong(split[1]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    public static MessageRef of(Snowflake channelId, Snowflake messageId) {
        return of(channelId.asLong(), messageId.asLong());
    }

    public static MessageRef of(long channelId, long messageId) {
        return new MessageRef(channelId, messageId);
    }

    private Mono<TextChannel> toText(Channel channel) {
        if (channel instanceof TextChannel) {
            return Mono.just((TextChannel) channel);
        }
        return Mono.empty();
    }

    public Mono<TextChannel> getChannel(GatewayDiscordClient gateway) {
        return gateway.getChannelById(Snowflake.of(this.channelId)).flatMap(this::toText);
    }

    public Mono<TextChannel> getChannel(Guild guild) {
        return guild.getChannelById(Snowflake.of(this.channelId)).flatMap(this::toText);
    }

    public Mono<Message> getMessage(GatewayDiscordClient gateway) {
        return this.getChannel(gateway).flatMap(channel -> channel.getMessageById(Snowflake.of(this.messageId)));
    }

    public Mono<Message> getMessage(Guild guild) {
        return this.getChannel(guild).flatMap(channel -> channel.getMessageById(Snowflake.of(this.messageId)));
    }

    long getChannelId() {
        return this.channelId;
    }

    long getMessageId() {
        return this.messageId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MessageRef that)) return false;
        if (channelId != that.channelId) return false;
        return messageId == that.messageId;
    }

    @Override
    public int hashCode() {
        int result = (int) (channelId ^ (channelId >>> 32));
        result = 31 * result + (int) (messageId ^ (messageId >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "MessageRef{" + this.channelId + '-' + this.messageId + '}';
    }
}
