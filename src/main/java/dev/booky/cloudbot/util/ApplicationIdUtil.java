package dev.booky.cloudbot.util;
// Created by booky10 in CloudBot (16:45 19.07.2024)

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.discordjson.Id;
import discord4j.rest.RestClient;
import discord4j.rest.RestResources;
import discord4j.rest.route.Routes;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.time.Duration;

@JsonDeserialize
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class ApplicationIdUtil {

    private Id id;

    private ApplicationIdUtil() {
    }

    public static void replaceApplicationIdMono(GatewayDiscordClient gateway) {
        Mono<Long> mono = requestApplicationId(gateway).cache(
                __ -> Duration.ofMillis(Long.MAX_VALUE),
                __ -> Duration.ZERO, () -> Duration.ZERO);

        try {
            Field field = RestClient.class.getDeclaredField("applicationIdMono");
            field.setAccessible(true);
            field.set(gateway.getRestClient(), mono);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Error while replacing application id mono with workaround");
        }
    }

    public static Mono<Long> requestApplicationId(GatewayDiscordClient gateway) {
        // manually do rest request, see https://github.com/Discord4J/Discord4J/issues/1243
        RestResources resources = gateway.getRestClient().getRestResources();
        return Routes.APPLICATION_INFO_GET.newRequest()
                .exchange(resources.getRouter())
                .bodyToMono(ApplicationIdUtil.class)
                .map(info -> Snowflake.asLong(info.id));
    }
}
