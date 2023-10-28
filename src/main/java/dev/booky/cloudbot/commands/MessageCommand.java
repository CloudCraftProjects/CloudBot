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
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.core.spec.MessageCreateMono;
import discord4j.core.spec.MessageEditMono;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
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
    private static final String SELECT_MESSAGE = "message";
    private static final String SELECT_EMBED = "embed";

    private static final String EDIT_ID = "edit";
    private static final String IMPORT_ID = "import";
    private static final String SEND_ID = "send";

    private static final String EDIT_MESSAGE_ID = "edit-message";
    private static final String EM_CONTENT_ID = "content";

    private static final String EDIT_EMBED_ID = "edit-embed";
    private static final String EE_MSG_CONTENT_ID = "content";
    private static final String EE_TITLE_ID = "title";
    private static final String EE_DESCRIPTION_ID = "description";
    private static final String EE_COLOR_ID = "color";
    private static final String EE_IMAGE_ID = "image";

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
                                        .value(SELECT_MESSAGE).build(),
                                ApplicationCommandOptionChoiceData.builder()
                                        .name("Embed")
                                        .nameLocalizationsOrNull(Map.of("de", "Embed"))
                                        .value(SELECT_EMBED).build())
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
        if (type.equals(SELECT_MESSAGE)) {
            return this.runMessageMenu(event, i18n);
        }
        if (type.equals(SELECT_EMBED)) {
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
            if (event.getValues().contains(SELECT_MESSAGE)) {
                return this.runMessageMenu(event, translator);
            }
            if (event.getValues().contains(SELECT_EMBED)) {
                return this.runEmbedMenu(event, translator);
            }
            throw new IllegalStateException("Illegal type selection: " + event.getValues());
        }, PERMISSION);
    }

    private Mono<Void> runSelection(ChatInputInteractionEvent event, Translator i18n) {
        SelectMenu selectMenu = SelectMenu.of(SELECTION_ID,
                        SelectMenu.Option.of(i18n.apply("command.message.selection.message.name"), SELECT_MESSAGE)
                                .withDescription(i18n.apply("command.message.selection.message.desc"))
                                .withEmoji(ReactionEmoji.unicode("\uD83D\uDCE7")),
                        SelectMenu.Option.of(i18n.apply("command.message.selection.embed.name"), SELECT_EMBED)
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

    private Button getImportButton(Translator i18n) {
        return Button.primary(IMPORT_ID, ReactionEmoji.unicode("\u2B07\uFE0F"),
                i18n.apply("command.message.import"));
    }

    private Button getSendButton(Translator i18n) {
        return Button.success(SEND_ID, ReactionEmoji.unicode("\uD83D\uDCE8"),
                i18n.apply("command.message.send"));
    }

    private Mono<Void> runMessageMenu(DeferrableInteractionEvent event, Translator i18n) {
        return event.reply(i18n.apply("command.message.no-content"))
                .withComponents(ActionRow.of(this.getEditButton(i18n),
                        this.getImportButton(i18n), this.getSendButton(i18n)));
    }

    private Mono<Void> runEmbedMenu(DeferrableInteractionEvent event, Translator i18n) {
        return event.reply()
                .withEmbeds(EmbedCreateSpec.builder()
                        .description(i18n.apply("command.message.no-content"))
                        .color(Color.of(0x27292E))
                        .build())
                .withComponents(ActionRow.of(this.getEditButton(i18n),
                        this.getImportButton(i18n), this.getSendButton(i18n)));
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

        TextInput imageInput = TextInput.small(EE_IMAGE_ID,
                i18n.apply("command.message.embed.image"));
        if (embed.getImage().isPresent()) {
            String imageUrl = embed.getImage().map(Embed.Image::getUrl).orElseThrow();
            imageInput = imageInput.prefilled(imageUrl);
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
                .addComponent(ActionRow.of(imageInput))
                .addComponent(ActionRow.of(colorInput))
                .build();
    }

    public InteractionPresentModalSpec createImportModal(Translator i18n) {
        return InteractionPresentModalSpec.builder()
                .title(i18n.apply("command.message.import.modal-title"))
                .customId(IMPORT_ID)
                .addComponent(ActionRow.of(TextInput.small(CHANNEL_ID,
                                i18n.apply("command.message.import.channel-id"))
                        .required(false)))
                .addComponent(ActionRow.of(TextInput.small(MESSAGE_ID,
                                i18n.apply("command.message.import.message-id"))
                        .required(true)))
                .build();
    }

    public InteractionPresentModalSpec createSendingModal(Translator i18n) {
        return InteractionPresentModalSpec.builder()
                .title(i18n.apply("command.message.send.modal-title"))
                .customId(SEND_ID)
                .addComponent(ActionRow.of(TextInput.small(CHANNEL_ID,
                                i18n.apply("command.message.send.channel-id"))
                        .required(false)))
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

        if (IMPORT_ID.equals(event.getCustomId())) {
            return Mono.defer(() -> {
                Translator i18n = this.manager.createTranslator(event.getInteraction());
                return this.withPermission(event.getInteraction(),
                        () -> event.presentModal(this.createImportModal(i18n)), PERMISSION);
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

    public Map.Entry<Long, Optional<Long>> extractMessageRefInputs(ModalSubmitInteractionEvent event) {
        Map<String, Optional<String>> inputs = this.extractInputs(event);
        Optional<String> rawChannelId = inputs.getOrDefault(CHANNEL_ID, Optional.empty());
        Optional<String> rawMessageId = inputs.getOrDefault(MESSAGE_ID, Optional.empty());

        Optional<Long> optChannelId = Optional.empty();
        Optional<Long> optMessageId = Optional.empty();
        if (rawMessageId.isPresent()) {
            String[] split = StringUtils.split(rawMessageId.get(), '-');
            if (split.length == 1) {
                optMessageId = this.parseId(split[0]);
            } else if (split.length == 2) {
                optChannelId = this.parseId(split[0]);
                optMessageId = this.parseId(split[1]);
            }
        }

        if (optChannelId.isEmpty()) {
            optChannelId = rawChannelId.flatMap(this::parseId);
        }

        Message message = event.getMessage().orElseThrow();
        long channelId = optChannelId.orElseGet(() -> message.getChannelId().asLong());
        return Map.entry(channelId, optMessageId);
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
                    inputs.getOrDefault(EE_IMAGE_ID, Optional.empty())
                            .ifPresent(embed::image);
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

    public Mono<Void> submitImport(ModalSubmitInteractionEvent event) {
        Message message = event.getMessage().orElseThrow();
        return Mono.fromSupplier(() -> this.extractMessageRefInputs(event))
                .filter(inputs -> inputs.getValue().isPresent())
                .flatMap(inputs -> {
                    long messageId = inputs.getValue().orElseThrow(AssertionError::new);
                    return message.getGuild()
                            .flatMap(guild -> guild.getChannelById(Snowflake.of(inputs.getKey())))
                            .filter(channel -> channel instanceof GuildMessageChannel)
                            .map(channel -> (GuildMessageChannel) channel)
                            .flatMap(channel -> channel.getMessageById(Snowflake.of(messageId)))
                            .flatMap(targetMessage -> message.edit()
                                    .withContentOrNull(targetMessage.getContent())
                                    .withEmbedsOrNull(targetMessage.getEmbeds().stream()
                                            .map(this::toCreateSpec).toList()));
                })
                .then();
    }

    private Optional<Long> parseId(String input) {
        return Optional.of(input.replaceAll("\\D+", ""))
                .filter(Predicate.not(StringUtils::isBlank))
                .map(Long::parseLong);
    }

    public Mono<Void> submitSend(ModalSubmitInteractionEvent event) {
        Message message = event.getMessage().orElseThrow();
        return Mono.fromSupplier(() -> this.extractMessageRefInputs(event))
                .flatMap(inputs -> message.getGuild()
                        .flatMap(guild -> guild.getChannelById(Snowflake.of(inputs.getKey())))
                        .filter(channel -> channel instanceof GuildMessageChannel)
                        .map(channel -> (GuildMessageChannel) channel)
                        .flatMap(channel -> {
                            if (inputs.getValue().isEmpty()) {
                                MessageCreateMono creator = channel.createMessage();
                                if (!StringUtils.isBlank(message.getContent())) {
                                    creator = creator.withContent(message.getContent());
                                }
                                return creator.withEmbeds(message.getEmbeds().stream()
                                                .map(this::toCreateSpec).toList())
                                        .then();
                            }

                            return channel.getMessageById(Snowflake.of(inputs.getValue().get()))
                                    .flatMap(targetMsg -> {
                                        MessageEditMono editor = targetMsg.edit();
                                        if (!StringUtils.isBlank(message.getContent())) {
                                            editor = editor.withContentOrNull(message.getContent());
                                        } else {
                                            editor = editor.withContentOrNull(null);
                                        }
                                        return editor.withEmbedsOrNull(message.getEmbeds().stream()
                                                .map(this::toCreateSpec).toList());
                                    });
                        })).then();
    }

    private EmbedCreateSpec toCreateSpec(Embed embed) {
        EmbedCreateSpec.Builder builder = EmbedCreateSpec.builder();

        embed.getTitle().ifPresent(builder::title);
        embed.getDescription().ifPresent(builder::description);
        embed.getColor().ifPresent(builder::color);
        embed.getTimestamp().ifPresent(builder::timestamp);
        embed.getThumbnail().map(Embed.Thumbnail::getUrl).ifPresent(builder::thumbnail);
        embed.getImage().map(Embed.Image::getUrl).ifPresent(builder::image);
        embed.getUrl().ifPresent(builder::url);

        embed.getAuthor().ifPresent(author -> builder.author(author.getName().orElseThrow(),
                author.getUrl().orElse(null), author.getIconUrl().orElse(null)));
        embed.getFooter().ifPresent(footer -> builder.footer(footer.getText(), footer.getIconUrl().orElse(null)));

        for (Embed.Field field : embed.getFields()) {
            builder.addFields(EmbedCreateFields.Field.of(field.getName(),
                    field.getValue(), field.isInline()));
        }

        return builder.build();
    }

    @DcEventHandler
    public Mono<Void> onModalSubmit(ModalSubmitInteractionEvent event) {
        if (EDIT_MESSAGE_ID.equals(event.getCustomId())) {
            return event.deferEdit().then().and(this.withPermission(event.getInteraction(),
                    () -> this.submitEditMessage(event), Permission.ADMINISTRATOR));
        }

        if (EDIT_EMBED_ID.equals(event.getCustomId())) {
            return event.deferEdit().then().and(this.withPermission(event.getInteraction(),
                    () -> this.submitEditEmbed(event), Permission.ADMINISTRATOR));
        }

        if (IMPORT_ID.equals(event.getCustomId())) {
            return event.deferEdit().then().and(this.withPermission(event.getInteraction(),
                    () -> this.submitImport(event), Permission.ADMINISTRATOR));
        }

        if (SEND_ID.equals(event.getCustomId())) {
            return event.deferEdit().then().and(this.withPermission(event.getInteraction(),
                    () -> this.submitSend(event), Permission.ADMINISTRATOR));
        }

        return Mono.empty();
    }
}
