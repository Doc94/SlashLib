package dev.hc224.slashlib;

import dev.hc224.slashlib.commands.BaseCommand;
import dev.hc224.slashlib.context.*;
import discord4j.core.event.domain.interaction.*;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.PrivateChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

/**
 * A Local class which contains the results of a permissions check.
 */
class PermissionResult {

}

/**
 * Core logic and implementation for processing received interactions and executing the relevant command logic.
 *
 * @param <IC> The {@link Class} used for the {@link ChatContext}        provided to commands for execution
 * @param <IB> The {@link Class} used for the {@link ChatContextBuilder} provided to commands for requesting data
 * @param <UC> The {@link Class} used for the {@link UserContext}             provided to commands for execution
 * @param <UB> The {@link Class} used for the {@link UserContextBuilder}      provided to commands for requesting data
 * @param <MC> The {@link Class} used for the {@link MessageContext}          provided to commands for execution
 * @param <MB> The {@link Class} used for the {@link MessageContextBuilder}   provided to commands for requesting data
 */
public class GenericEventReceiverImpl<
        IC extends ChatContext, IB extends ChatContextBuilder,
        UC extends UserContext, UB extends UserContextBuilder,
        MC extends MessageContext, MB extends MessageContextBuilder
        > implements GenericEventReceiver<IC, UC, MC> {

    protected final GenericSlashLib<IC, IB, UC, UB, MC, MB> genericSlashLib;

    /**
     * Create a new instance with a reference to the existing {@link GenericSlashLib} instance.
     * Method is public for custom implementations to access, but should not be directly instantiated.
     *
     * @param genericSlashLib the in-use {@link SlashLib} instance
     */
    public GenericEventReceiverImpl(GenericSlashLib<IC, IB, UC, UB, MC, MB> genericSlashLib) {
        this.genericSlashLib = genericSlashLib;
    }

    /**
     * Base logic for checking permissions, needs to be this generic for {@link AutoCompleteInteractionEvent}
     *
     * @param event the event received for an interaction
     * @param baseCommand the command related to the interaction
     * @return a present and true Mono<Boolean> if the command can be used, empty otherwise
     */
    // I do not like the logic in this method, duplicated channel casts and filters
    private <E extends InteractionCreateEvent, B extends BaseCommand> Mono<Boolean> checkPermissions(E event, B baseCommand) {
        return event.getInteraction().getChannel()
            // Check guild permissions, this will go empty if in DMs
            .ofType(GuildChannel.class)
            // Check permissions if they are set
            .flatMap(gc -> Mono.zip(gc.getEffectivePermissions(event.getClient().getSelfId()), gc.getEffectivePermissions(event.getInteraction().getUser().getId())))
            .map(t2 -> t2.getT1().containsAll(baseCommand.getBotPermissions()) && t2.getT2().containsAll(baseCommand.getUserPermissions()))
            .filter(Boolean::booleanValue)
            // If guild perms failed, then check DM eligibility
            .switchIfEmpty(event.getInteraction().getChannel().ofType(PrivateChannel.class)
                .map(_pc -> baseCommand.isUsableInDMs()))
            .filter(Boolean::booleanValue);
    }

    /**
     * Create an error message to be sent back in response to the user when a command cannot be executed.
     *
     * @param baseCommand the command the user called
     * @return an ephemeral interaction reply with an embed
     */
    private <B extends BaseCommand> InteractionApplicationCommandCallbackSpec generateErrorMessage(B baseCommand) {
        EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();
        embed.color(Color.RED);
        embed.title("Cannot execute command!");
        embed.description((baseCommand.isUsableInDMs()) ? "This command is usable in DMs" : "This command is not usable in DMs");

        if (!baseCommand.getBotPermissions().isEmpty()) {
            embed.addField("Required Bot Permissions", "`" + baseCommand.getBotPermissions().asEnumSet() + "`", false);
        }
        if (!baseCommand.getUserPermissions().isEmpty()) {
            embed.addField("Required User Permissions", "`" + baseCommand.getUserPermissions().asEnumSet() + "`", false);
        }

        return InteractionApplicationCommandCallbackSpec.builder().addEmbed(embed.build()).ephemeral(true).build();
    }

    /**
     * Check that the bot has permissions to execute the called command. And that the command can be used in a
     *  {@link discord4j.core.object.entity.channel.PrivateChannel} if called from one.
     *
     * If one of the checks fails, the event is responded to and the chain goes empty. As such, no downstream operator
     *  should switch from empty without considering this.
     *
     * @param event the event produced from the called command
     * @param baseCommand the target command to execute
     * @return a present (and true) mono if command execution can continue, empty otherwise
     */
    private <E extends DeferrableInteractionEvent, B extends BaseCommand> Mono<Boolean> checkPermissionsAndReply(E event, B baseCommand) {
        return checkPermissions(event, baseCommand)
            // No perms or not usable in DMs, send silent error message and remain empty
            .switchIfEmpty(Mono.defer(() -> event.reply(generateErrorMessage(baseCommand)).then(Mono.empty())));
    }

    /**
     * Receive a CHAT_INPUT interaction, determine the command, check permissions, and execute.
     *
     * @param event the event produced from a user starting an interaction
     * @return empty if command wasn't callable, a present {@link ChatContext} from post command execution otherwise
     */
    @Override
    public Mono<IC> receiveChatInputInteractionEvent(ChatInputInteractionEvent event) {
        // We need the command interaction to get the options
        return Mono.justOrEmpty(event.getInteraction().getCommandInteraction())
            // Get the command, we use the helper method on the command Structure to get this as
            //  chat input commands care multi-level
            .flatMap(aci -> Mono.just(genericSlashLib.getCommandRegister().getCommandStructure().searchForChatCommand(aci))
                // Check bot permissions in guild
                .flatMap(pair -> checkPermissionsAndReply(event, pair.getKey())
                    // Have perms, create the builder and collect data
                    .flatMap(_bool -> {
                        try {
                            return pair.getKey()
                                .setRequestData(genericSlashLib.getChatInputContextBuilderConstructor().newInstance(event, aci, pair.getValue()))
                                .collectData();
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    })
                    .ofType(genericSlashLib.getChatInputContextBuilderClass())
                    .map(ChatContextBuilder::build)
                    .ofType(genericSlashLib.getChatInputContextClass())
                    // Call the command
                    .flatMap(context -> pair.getKey().executeChat(context))));
    }

    /**
     * Receive a USER interaction, determine the command, check permissions, and execute.
     *
     * @param event the event produced from a user starting an interaction
     * @return empty if command wasn't callable, a present {@link UserContext} from post command execution otherwise
     */
    @Override
    public Mono<UC> receiveUserInteractionEvent(UserInteractionEvent event) {
        // Since User Interactions are only top level we can just get our command by the name
        return Mono.just(genericSlashLib.getCommandRegister().getCommandStructure().searchForUserCommand(event))
            // Check bot permissions in guild
            .flatMap(userCommand -> checkPermissionsAndReply(event, userCommand)
                // Have perms, create the builder and collect data
                .flatMap(_bool -> {
                    try {
                        return userCommand.setRequestData(genericSlashLib.getUserContextBuilderConstructor().newInstance(event))
                            .collectData();
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .ofType(genericSlashLib.getUserContextBuilderClass())
                .map(UserContextBuilder::build)
                .ofType(genericSlashLib.getUserContextClass())
                // Call the command
                .flatMap(userCommand::executeUser));
    }

    /**
     * Receive a MESSAGE interaction, determine the command, check permissions, and execute.
     *
     * @param event the event produced from a user starting an interaction
     * @return empty if command wasn't callable, a present {@link MessageContext} from post command execution otherwise
     */
    @Override
    public Mono<MC> receiveMessageInteractionEvent(MessageInteractionEvent event) {
        // Since User Interactions are only top level we can just get our command by the name
        return Mono.just(genericSlashLib.getCommandRegister().getCommandStructure().searchForMessageCommand(event))
            // Check bot permissions in guild
            .flatMap(messageCommand -> checkPermissionsAndReply(event, messageCommand)
                // Have perms, create the builder and collect data
                .flatMap(_bool -> {
                    try {
                        return messageCommand.setRequestData(genericSlashLib.getMessageContextBuilderConstructor().newInstance(event))
                            .collectData();
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .ofType(genericSlashLib.getMessageContextBuilderClass())
                .map(MessageContextBuilder::build)
                .ofType(genericSlashLib.getMessageContextClass())
                // Call the command
                .flatMap(messageCommand::executeMessage));
    }

    /**
     * Receive an autocomplete event for a CHAT_INPUT command. The event is a convenience provided by D4J.
     * Permissions are checked for the bot and calling user as well, the latter for security reasons if a command
     *  is only restricted by a permissions check and not by Discord.
     *
     * @param event the event produced from a user entering options into a command
     * @return an empty Mono on command event completion
     */
    @Override
    public Mono<Void> receiveAutoCompleteEvent(ChatInputAutoCompleteEvent event) {
        // We need the command interaction to get the options
        return Mono.justOrEmpty(event.getInteraction().getCommandInteraction())
            // Get the command, we use the helper method on the command Structure to get this as
            //  chat input commands care multi-level
            .flatMap(aci -> Mono.just(genericSlashLib.getCommandRegister().getCommandStructure().searchForChatCommand(aci))
                // Check bot permissions in guild
                .flatMap(pair -> checkPermissions(event, pair.getKey())
                    // Have perms, call the autocomplete method for the command
                    .flatMap(_boolean -> pair.getKey().receiveAutoCompleteEvent(new AutoCompleteContext(event, aci, pair.getValue())))));
    }
}
