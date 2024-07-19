package dev.booky.cloudbot.util;
// Created by booky10 in CloudBot (16:45 19.07.2024)

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import discord4j.common.ReactorResources;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.discordjson.Id;
import discord4j.rest.RestResources;
import discord4j.rest.route.Routes;
import reactor.core.publisher.Mono;

@JsonDeserialize
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class ApplicationIdUtil {

    private Id id;

    private ApplicationIdUtil() {
    }

    public static Mono<Long> requestApplicationId(GatewayDiscordClient gateway) {
        // manually do rest request, see https://github.com/Discord4J/Discord4J/issues/1243
        RestResources resources = gateway.getRestClient().getRestResources();
        ReactorResources reactorRes = resources.getReactorResources();
        return Routes.APPLICATION_INFO_GET.newRequest()
                .exchange(resources.getRouter()).mono()
                .flatMap(res -> res.bodyToMono(ApplicationIdUtil.class))
                .publishOn(reactorRes.getBlockingTaskScheduler())
                .map(info -> Snowflake.asLong(info.id));
    }
}
