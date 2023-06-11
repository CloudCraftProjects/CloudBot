package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (03:13 11.06.23)

import com.google.common.base.Preconditions;
import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.i18n.Translator;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.util.OrderUtil;
import discord4j.discordjson.json.ApplicationCommandRequest;
import reactor.core.publisher.Mono;

import java.util.Map;

public class TeamMembersCommand implements BotCommand {

    @Override
    public ApplicationCommandRequest provideCommandData() {
        return ApplicationCommandRequest.builder()
                .name("team")
                .description("Lists all members which are in the server team")
                .descriptionLocalizationsOrNull(Map.of("de", "Liste alle Mitglieder des Server-Teams auf"))
                .dmPermission(true)
                .build();
    }

    @Override
    public Mono<Void> run(CloudBotManager manager, String label, ChatInputInteractionEvent event, User user, Translator i18n) {
        Guild guild = manager.getMainGuild();
        Preconditions.checkState(guild != null, "No main guild configured");

        long teamRoleId = manager.getConfig().getTeamRoleId();
        Preconditions.checkState(teamRoleId != -1L, "No team role configured");

        Snowflake teamRoleSf = Snowflake.of(teamRoleId);
        return event.deferReply().withEphemeral(true).then(guild.getMembers()
                .filter(member -> member.getRoleIds().contains(teamRoleSf))
                .flatMap(mem -> mem.getRoles()
                        .filter(Role::isHoisted)
                        .sort(OrderUtil.ROLE_ORDER)
                        .last()
                        .map(role -> "- %s: `%s`".formatted(mem.getMention(), role.getName())))
                .collectList()
                .map(members -> String.join("\n", members))
                .flatMap(result -> event.createFollowup(result).withEphemeral(true))
                .then());
    }
}
