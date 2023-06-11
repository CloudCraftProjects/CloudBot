package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (16:30 26.11.22)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.util.Color;
import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.api.statistic.StatisticWindow.CpuUsage;
import me.lucko.spark.api.statistic.StatisticWindow.MillisPerTick;
import me.lucko.spark.api.statistic.StatisticWindow.TicksPerSecond;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;
import me.lucko.spark.api.statistic.types.DoubleStatistic;
import me.lucko.spark.api.statistic.types.GenericStatistic;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class TpsCommand extends AbstractBotCommand {

    public TpsCommand(CloudBotManager manager) {
        super(manager, "tps");
    }

    @Override
    protected void buildRequest(ImmutableApplicationCommandRequest.Builder builder) {
        builder
                .description("Shows you information about the current server performance")
                .descriptionLocalizationsOrNull(Map.of("de", "Zeigt dir die Server-Performance an"))
                .dmPermission(true);
    }

    @Override
    public Mono<Void> run(ChatInputInteractionEvent event, User user, Translator i18n) {
        Spark spark = SparkProvider.get();
        StringBuilder descBuilder = new StringBuilder();

        DoubleStatistic<TicksPerSecond> tps = Objects.requireNonNull(spark.tps());
        GenericStatistic<DoubleAverageInfo, MillisPerTick> mspt = Objects.requireNonNull(spark.mspt());
        DoubleStatistic<CpuUsage> cpu = Objects.requireNonNull(spark.cpuProcess());

        double tps10s = Math.round(tps.poll(TicksPerSecond.SECONDS_10) * 100d) / 100d;
        double tps1min = Math.round(tps.poll(TicksPerSecond.MINUTES_1) * 100d) / 100d;
        double tps5min = Math.round(tps.poll(TicksPerSecond.MINUTES_5) * 100d) / 100d;

        descBuilder
                .append("\n> **Server TPS**")
                .append("\n- `10s: ").append(tps10s).append(" TPS`")
                .append("\n- `1min: ").append(tps1min).append(" TPS`")
                .append("\n- `5min: ").append(tps5min).append(" TPS`\n");

        double mspt10s = Math.round(mspt.poll(MillisPerTick.SECONDS_10).percentile(0.9d) * 100d) / 100d;
        double mspt1min = Math.round(mspt.poll(MillisPerTick.MINUTES_1).percentile(0.9d) * 100d) / 100d;

        descBuilder
                .append("\n> **Server MSPT** (`90%`)")
                .append("\n- `10s: ").append(mspt10s).append("ms`")
                .append("\n- `1min: ").append(mspt1min).append("ms`\n");

        int cpu10s = (int) Math.round(cpu.poll(CpuUsage.SECONDS_10) * 100d);
        int cpu1min = (int) Math.round(cpu.poll(CpuUsage.MINUTES_1) * 100d);

        descBuilder
                .append("\n> **Process CPU Usage**")
                .append("\n- `10s: ").append(cpu10s).append("%`")
                .append("\n- `1min: ").append(cpu1min).append("%`\n");

        Color embedColor;
        if (tps10s >= 19d) { // 19-20 tps, good performance
            embedColor = Color.GREEN;
        } else if (tps10s >= 15d) { // 15-18.99 tps, still playable
            embedColor = Color.YELLOW;
        } else if (tps10s >= 12d) { // 12-14.99 tps, not so playable
            embedColor = Color.ORANGE;
        } else { // 0-11.99 tps, completely unplayable
            embedColor = Color.RED;
        }

        return event.reply()
                .withEphemeral(true)
                .withEmbeds(EmbedCreateSpec.builder()
                        .description(descBuilder.toString())
                        .timestamp(Instant.now())
                        .footer("\u26A1 Powered by Spark", null)
                        .color(embedColor)
                        .build());
    }
}
