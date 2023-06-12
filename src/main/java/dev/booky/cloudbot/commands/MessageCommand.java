package dev.booky.cloudbot.commands;
// Created by booky10 in CloudBot (00:35 12.06.23)

import dev.booky.cloudbot.CloudBotManager;
import dev.booky.cloudbot.events.DcEventHandler;
import dev.booky.cloudbot.events.DcListener;
import dev.booky.cloudbot.i18n.Translator;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.Embed;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.core.spec.MessageCreateMono;
import discord4j.core.spec.MessageEditMono;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.EmbedData;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import net.kyori.adventure.text.format.TextColor;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class MessageCommand extends AbstractBotCommand implements DcListener {

    private static final Permission PERMISSION = Permission.ADMINISTRATOR;

    private static final String SELECTION_ID = "message-selection";
    private static final String EDIT_ID = "edit";
    private static final String SEND_ID = "send";

    private static final String EDIT_MESSAGE_ID = "edit-message";
    private static final String EM_CONTENT_ID = "content";

    private static final String EDIT_EMBED_ID = "edit-embed";
    private static final String EE_MSG_CONTENT_ID = "content";
    private static final String EE_TITLE_ID = "title";
    private static final String EE_DESCRIPTION_ID = "description";
    private static final String EE_COLOR_ID = "color";

    private static final String CHANNEL_ID = "channel";
    private static final String MESSAGE_ID = "message";

    public MessageCommand(CloudBotManager manager) {
        super(manager, "message");
    }

    @Override
    protected void buildRequest(ImmutableApplicationCommandRequest.Builder builder) {
        builder
                .description("Shows a message editor")
                .descriptionLocalizationsOrNull(Map.of("de", "Zeigt dir einen Nachrichten-Editor"))
                .defaultMemberPermissions(Long.toString(PermissionSet.of(PERMISSION).getRawValue()))
                .dmPermission(false)
                .addOption(ApplicationCommandOptionData.builder()
                        .name("type")
                        .nameLocalizationsOrNull(Map.of("de", "typ"))
                        .description("The message type to edit")
                        .descriptionLocalizationsOrNull(Map.of("de", "Der zu bearbeitende Nachrichten-Typ"))
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .choices(ApplicationCommandOptionChoiceData.builder()
                                        .name("Message")
                                        .nameLocalizationsOrNull(Map.of("de", "Nachricht"))
                                        .value("message").build(),
                                ApplicationCommandOptionChoiceData.builder()
                                        .name("Embed")
                                        .nameLocalizationsOrNull(Map.of("de", "Embed"))
                                        .value("embed").build())
                        .required(false)
                        .build());
    }

    @Override
    public Mono<Void> run(ChatInputInteractionEvent event, User user, Translator i18n) {
        Optional<String> optType = event.getOption("type")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        if (optType.isEmpty()) {
            return this.runSelection(event, i18n);
        }

        String type = optType.get();
        if (type.equals("message")) {
            return this.runMessageMenu(event, i18n);
        }
        if (type.equals("embed")) {
            return this.runEmbedMenu(event, i18n);
        }
        throw new IllegalStateException("Illegal type specified: " + type);
    }

    @DcEventHandler
    public Mono<Void> onSelectMenuInteract(SelectMenuInteractionEvent event) {
        if (!SELECTION_ID.equals(event.getCustomId())) {
            return Mono.empty();
        }

        Translator translator = this.manager.createTranslator(event.getInteraction());
        return this.withPermission(event.getInteraction(), () -> {
            if (event.getValues().contains("message")) {
                return this.runMessageMenu(event, translator);
            }
            if (event.getValues().contains("embed")) {
                return this.runEmbedMenu(event, translator);
            }
            throw new IllegalStateException("Illegal type selection: " + event.getValues());
        }, PERMISSION);
    }

    private Mono<Void> runSelection(ChatInputInteractionEvent event, Translator i18n) {
        SelectMenu selectMenu = SelectMenu.of(SELECTION_ID,
                        SelectMenu.Option.of(i18n.apply("command.message.selection.message.name"), "message")
                                .withDescription(i18n.apply("command.message.selection.message.desc"))
                                .withEmoji(ReactionEmoji.unicode("\uD83D\uDCE7")),
                        SelectMenu.Option.of(i18n.apply("command.message.selection.embed.name"), "embed")
                                .withDescription(i18n.apply("command.message.selection.embed.desc"))
                                .withEmoji(ReactionEmoji.unicode("\uD83D\uDCDC")))
                .withPlaceholder(i18n.apply("command.message.selection.hint"));
        return event.reply()
                .withEphemeral(true)
                .withComponents(ActionRow.of(selectMenu));
    }

    private Button getEditButton(Translator i18n) {
        return Button.primary(EDIT_ID, ReactionEmoji.unicode("\u270F\uFE0F"),
                i18n.apply("command.message.edit"));
    }

    private Button getSendButton(Translator i18n) {
        return Button.primary(SEND_ID, ReactionEmoji.unicode("\uD83D\uDCE8"),
                i18n.apply("command.message.send"));
    }

    private Mono<Void> runMessageMenu(DeferrableInteractionEvent event, Translator i18n) {
        return event.reply(i18n.apply("command.message.no-content"))
                .withComponents(ActionRow.of(this.getEditButton(i18n), this.getSendButton(i18n)));
    }

    private Mono<Void> runEmbedMenu(DeferrableInteractionEvent event, Translator i18n) {
        return event.reply()
                .withComponents(ActionRow.of(this.getEditButton(i18n), this.getSendButton(i18n)))
                .withEmbeds(EmbedCreateSpec.builder()
                        .description(i18n.apply("command.message.no-content"))
                        .color(Color.of(0x27292E))
                        .build());
    }

    public InteractionPresentModalSpec createMessageEditModal(Translator i18n, Message message) {
        return InteractionPresentModalSpec.builder()
                .title(i18n.apply("command.message.message.modal-title"))
                .customId(EDIT_MESSAGE_ID)
                .addComponent(ActionRow.of(TextInput.paragraph(EM_CONTENT_ID,
                                i18n.apply("command.message.message.content"),
                                1, 2048)
                        .prefilled(message.getContent())
                        .required(true)))
                .build();
    }

    public InteractionPresentModalSpec createEmbedEditModal(Translator i18n, Message message, Embed embed) {
        TextInput titleInput = TextInput.small(EE_TITLE_ID,
                        i18n.apply("command.message.embed.title"),
                        0, 128)
                .required(false)
                .prefilled("");
        if (embed.getTitle().isPresent()) {
            titleInput = titleInput.prefilled(embed.getTitle().get());
        }

        TextInput contentInput = TextInput.paragraph(EE_DESCRIPTION_ID,
                        i18n.apply("command.message.embed.description"),
                        1, 4000)
                .required(true)
                .prefilled(i18n.apply("command.message.no-content"));
        if (embed.getDescription().isPresent()) {
            contentInput = contentInput.prefilled(embed.getDescription().get());
        }

        TextInput colorInput = TextInput.small(EE_COLOR_ID,
                        i18n.apply("command.message.embed.color"),
                        1 + 3, 1 + 6)
                .required(true)
                .prefilled("#27292E");
        if (embed.getColor().isPresent()) {
            String colorStr = TextColor.color(embed.getColor().get().getRGB()).asHexString();
            colorInput = colorInput.prefilled(colorStr.toUpperCase(Locale.ROOT));
        }

        return InteractionPresentModalSpec.builder()
                .title(i18n.apply("command.message.embed.modal-title"))
                .customId(EDIT_EMBED_ID)
                .addComponent(ActionRow.of(TextInput.paragraph(EE_MSG_CONTENT_ID,
                                i18n.apply("command.message.embed.msg-content"),
                                0, 2048)
                        .required(false)
                        .prefilled(message.getContent())))
                .addComponent(ActionRow.of(titleInput))
                .addComponent(ActionRow.of(contentInput))
                .addComponent(ActionRow.of(colorInput))
                .build();
    }

    public InteractionPresentModalSpec createSendingModal(Translator i18n) {
        return InteractionPresentModalSpec.builder()
                .title(i18n.apply("command.message.send.modal-title"))
                .customId(SEND_ID)
//                .addComponent(ActionRow.of(SelectMenu.ofChannel(CHANNEL_ID, Channel.Type.GUILD_TEXT))) // TODO: what the fuck?
                .addComponent(ActionRow.of(TextInput.small(CHANNEL_ID,
                                i18n.apply("command.message.send.channel-id"))
                        .required(true)))
                .addComponent(ActionRow.of(TextInput.small(MESSAGE_ID,
                                i18n.apply("command.message.send.message-id"))
                        .required(false)))
                .build();
    }

    @DcEventHandler
    public Mono<Void> onButtonInteract(ButtonInteractionEvent event) {
        if (EDIT_ID.equals(event.getCustomId())) {
            return Mono.defer(() -> {
                Translator i18n = this.manager.createTranslator(event.getInteraction());
                Message message = event.getMessage().orElseThrow();

                InteractionPresentModalSpec modalSpec;
                if (message.getEmbeds().isEmpty()) {
                    // simple message modal
                    modalSpec = this.createMessageEditModal(i18n, message);
                } else {
                    // more complex embed creation modal
                    Embed embed = message.getEmbeds().get(0);
                    modalSpec = this.createEmbedEditModal(i18n, message, embed);
                }

                return this.withPermission(event.getInteraction(),
                        () -> event.presentModal(modalSpec), PERMISSION);
            });
        }

        if (SEND_ID.equals(event.getCustomId())) {
            return Mono.defer(() -> {
                Translator i18n = this.manager.createTranslator(event.getInteraction());
                return this.withPermission(event.getInteraction(),
                        () -> event.presentModal(this.createSendingModal(i18n)), PERMISSION);
            });
        }
        return Mono.empty();
    }

    public Map<String, Optional<String>> extractInputs(ModalSubmitInteractionEvent event) {
        return event.getComponents().stream()
                .filter(component -> component instanceof ActionRow)
                .map(component -> (ActionRow) component)
                .flatMap(row -> row.getChildren().stream())
                .filter(child -> child instanceof TextInput)
                .map(child -> (TextInput) child)
                .collect(Collectors.toUnmodifiableMap(
                        TextInput::getCustomId, TextInput::getValue));
    }

    public Mono<Void> submitEditMessage(ModalSubmitInteractionEvent event) {
        Message message = event.getMessage().orElseThrow();
        return Mono.fromSupplier(() -> this.extractInputs(event))
                .flatMap(inputs -> {
                    Optional<String> content = inputs.get(EM_CONTENT_ID);
                    return message.edit().withContentOrNull(content.orElse(null));
                })
                .then();
    }

    public Mono<Void> submitEditEmbed(ModalSubmitInteractionEvent event) {
        Message message = event.getMessage().orElseThrow();
        return Mono.fromSupplier(() -> this.extractInputs(event))
                .flatMap(inputs -> {
                    EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();

                    String content = inputs.getOrDefault(EE_MSG_CONTENT_ID, Optional.empty())
                            .filter(Predicate.not(StringUtils::isBlank))
                            .orElse(null);
                    inputs.getOrDefault(EE_TITLE_ID, Optional.empty())
                            .ifPresent(embed::title);
                    inputs.getOrDefault(EE_DESCRIPTION_ID, Optional.empty())
                            .ifPresent(embed::description);
                    inputs.getOrDefault(EE_COLOR_ID, Optional.empty())
                            .flatMap(colorStr -> Optional.ofNullable(TextColor.fromCSSHexString(colorStr)))
                            .map(color -> Color.of(color.value()))
                            .ifPresent(embed::color);

                    return message.edit()
                            .withContentOrNull(content)
                            .withEmbeds(embed.build());
                })
                .then();
    }

    public Mono<Void> submitSend(ModalSubmitInteractionEvent event) {
        Message message = event.getMessage().orElseThrow();
        return Mono.fromSupplier(() -> this.extractInputs(event)).flatMap(inputs -> {
            long channelId = inputs.getOrDefault(CHANNEL_ID, Optional.empty())
                    .map(str -> str.replaceAll("\\D+", ""))
                    .filter(Predicate.not(StringUtils::isBlank))
                    .map(Long::parseLong)
                    .orElseGet(() -> message.getChannelId().asLong());
            Optional<Long> messageId = inputs.getOrDefault(MESSAGE_ID, Optional.empty())
                    .map(str -> str.replaceAll("\\D+", ""))
                    .filter(Predicate.not(StringUtils::isBlank))
                    .map(Long::parseLong);

            return message.getGuild()
                    .flatMap(guild -> guild.getChannelById(Snowflake.of(channelId)))
                    .filter(channel -> channel instanceof TextChannel)
                    .map(channel -> (TextChannel) channel)
                    .flatMap(channel -> {
                        if (messageId.isEmpty()) {
                            MessageCreateMono creator = channel.createMessage();
                            if (!StringUtils.isBlank(message.getContent())) {
                                creator = creator.withContent(message.getContent());
                            }
                            return creator.withEmbeds(message.getEmbeds().stream()
                                            .map(this::partialCopy).toList())
                                    .then();
                        }

                        return channel.getMessageById(Snowflake.of(messageId.get()))
                                .flatMap(targetMsg -> {
                                    MessageEditMono editor = targetMsg.edit();
                                    if (!StringUtils.isBlank(message.getContent())) {
                                        editor = editor.withContentOrNull(message.getContent());
                                    } else {
                                        editor = editor.withContentOrNull(null);
                                    }
                                    return editor.withEmbedsOrNull(message.getEmbeds().stream()
                                            .map(this::partialCopy).toList());
                                })
                                .then();
                    });
        }).then();
    }

    private EmbedCreateSpec partialCopy(Embed embed) {
        EmbedData data = embed.getData();
        Possible<Color> color = Possible.absent();
        if (!data.color().isAbsent()) {
            color = Possible.of(Color.of(data.color().get()));
        }

        return EmbedCreateSpec.builder()
                .title(data.title())
                .description(data.description())
                .color(color)
                .build();
    }

    @DcEventHandler
    public Mono<Void> onModalSubmit(ModalSubmitInteractionEvent event) {
        return Mono.defer(() -> {
            if (EDIT_MESSAGE_ID.equals(event.getCustomId())) {
                return this.withPermission(event.getInteraction(),
                        () -> this.submitEditMessage(event), Permission.ADMINISTRATOR);
            }

            if (EDIT_EMBED_ID.equals(event.getCustomId())) {
                return this.withPermission(event.getInteraction(),
                        () -> this.submitEditEmbed(event), Permission.ADMINISTRATOR);
            }

            if (SEND_ID.equals(event.getCustomId())) {
                return this.withPermission(event.getInteraction(),
                        () -> this.submitSend(event), Permission.ADMINISTRATOR);
            }

            return Mono.empty();
        }).flatMap($ -> event.deferEdit());
    }
}
