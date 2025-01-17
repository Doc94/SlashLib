package dev.hc224.slashlib;

import dev.hc224.slashlib.commands.BaseCommand;
import dev.hc224.slashlib.commands.generic.GenericChatCommand;
import dev.hc224.slashlib.commands.generic.GenericMessageCommand;
import dev.hc224.slashlib.commands.generic.GenericUserCommand;
import dev.hc224.slashlib.context.*;
import discord4j.common.util.Snowflake;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.discordjson.Id;
import discord4j.discordjson.json.ApplicationCommandData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.service.ApplicationService;
import reactor.util.Logger;
import reactor.util.Loggers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Logic to interact with Discord and create/modify/delete guild and global commands.
 * Accessible through {@link GenericSlashLib#getCommandRegister()}
 *
 * @param <IC> The {@link Class} used for {@link ChatContext}
 * @param <IB> The {@link Class} used for {@link ChatContextBuilder}
 * @param <UC> The {@link Class} used for {@link UserContext}
 * @param <UB> The {@link Class} used for {@link UserContextBuilder}
 * @param <MC> The {@link Class} used for {@link MessageContext}
 * @param <MB> The {@link Class} used for {@link MessageContextBuilder}
 */
public class CommandRegister<
        IC extends ChatContext, IB extends ChatContextBuilder,
        UC extends UserContext, UB extends UserContextBuilder,
        MC extends MessageContext, MB extends MessageContextBuilder
        > {
    private static final Logger logger = Loggers.getLogger(CommandRegister.class);

    // Internal Command Structure used to lookup commands
    private final CommandStructure<IC, IB, UC, UB, MC, MB> commandStructure;
    // The provider of guild command state, used when registering/validating guild commands with discord
    private final GuildCommandStateProvider guildCommandStateProvider;

    protected CommandRegister(GuildCommandStateProvider guildCommandStateProvider) {
        this.commandStructure = new CommandStructure<>();
        this.guildCommandStateProvider = guildCommandStateProvider;
    }

    /**
     * Create a new CommandRegister with lists of commands for chat/user/message guild and global commands.
     *
     * @param globalChatCommands list of global chat commands which will be later registered
     * @param globalUserCommands list of global user commands which will be later registered
     * @param globalMessageCommands list of global message commands which will be later registered
     * @param guildChatCommands list of guild chat commands which will be referenced when registering guild commands
     * @param guildUserCommands list of guild user commands which will be referenced when registering guild commands
     * @param guildMessageCommands list of guild message commands which will be referenced when registering message commands
     * @param guildCommandStateProvider the provider of guild command states used to determine if a guild will have a particular guild command
     * @return a new CommandRegister instance
     */
    static <IC extends ChatContext, IB extends ChatContextBuilder,
            UC extends UserContext, UB extends UserContextBuilder,
            MC extends MessageContext, MB extends MessageContextBuilder
            > CommandRegister<IC, IB, UC, UB, MC, MB>
            create(List<GenericChatCommand<IC, IB>> globalChatCommands,
                   List<GenericUserCommand<UC, UB>> globalUserCommands,
                   List<GenericMessageCommand<MC, MB>> globalMessageCommands,
                   List<GenericChatCommand<IC, IB>> guildChatCommands,
                   List<GenericUserCommand<UC, UB>> guildUserCommands,
                   List<GenericMessageCommand<MC, MB>> guildMessageCommands,
                   GuildCommandStateProvider guildCommandStateProvider) {
        logger.debug("Creating CommandRegister");
        CommandRegister<IC, IB, UC, UB, MC, MB> commandRegister = new CommandRegister<>(guildCommandStateProvider);
        for (GenericChatCommand<IC, IB> command : globalChatCommands) {
            logger.debug("Adding Global Chat Command: " + command.getName());
            commandRegister.commandStructure.addGlobalChatCommand(command);
        }
        for (GenericUserCommand<UC, UB> command : globalUserCommands) {
            logger.debug("Adding Global User Command: " + command.getName());
            commandRegister.commandStructure.addGlobalUserCommand(command);
        }
        for (GenericMessageCommand<MC, MB> command : globalMessageCommands) {
            logger.debug("Adding Global Message Command: " + command.getName());
            commandRegister.commandStructure.addGlobalMessageCommand(command);
        }
        for (GenericChatCommand<IC, IB> command : guildChatCommands) {
            logger.debug("Adding Guild Chat Command: " + command.getName());
            commandRegister.commandStructure.addGuildChatCommand(command);
        }
        for (GenericUserCommand<UC, UB> command : guildUserCommands) {
            logger.debug("Adding Guild User Command: " + command.getName());
            commandRegister.commandStructure.addGuildUserCommand(command);
        }
        for (GenericMessageCommand<MC, MB> command : guildMessageCommands) {
            logger.debug("Adding Guild Message Command: " + command.getName());
            commandRegister.commandStructure.addGuildMessageCommand(command);
        }

        logger.debug("CommandRegister created");
        return commandRegister;
    }

    /**
     * Synchronize all global application commands (chat/user/message) with Discord.
     *
     * We merge all requests into a single list as Discord treats them all the same.
     *
     * @param applicationService the bots {@link ApplicationService}
     * @param applicationId the bots Application ID, must match the service
     * @return the number of application commands created/modified/deleted
     * @throws IllegalStateException when Discord doesn't return an interaction type and when
     *                               an unknown interaction type is received
     *                               (bot has an interaction type registered this library didn't create)
     */
    public int registerGlobalCommands(ApplicationService applicationService, long applicationId) {
        logger.debug("Registering application commands with Discord");

        // Since Chat, User, and Message commands can have name collisions we need to allow for that
        //  possibility by spitting them into multiple containers
        Map<String, ApplicationCommandData> discordGlobalChatCommands = new HashMap<>();
        Map<String, ApplicationCommandData> discordGlobalUserCommands = new HashMap<>();
        Map<String, ApplicationCommandData> discordGlobalMessageCommands = new HashMap<>();

        // Get the application commands and put them into their own maps based on type
        // Also ensure we understand all types
        applicationService
            .getGlobalApplicationCommands(applicationId)
            .doOnNext(acd -> {
                if (!acd.type().isAbsent()) {
                    if (acd.type().get() == ApplicationCommand.Type.CHAT_INPUT.getValue()) {
                        logger.debug("Received Global 'CHAT_INPUT' application command with name: " + acd.name());
                        discordGlobalChatCommands.put(acd.name(), acd);
                    } else if (acd.type().get() == ApplicationCommand.Type.USER.getValue()) {
                        logger.debug("Received Global '      USER' application command with name: " + acd.name());
                        discordGlobalUserCommands.put(acd.name(), acd);
                    } else if (acd.type().get() == ApplicationCommand.Type.MESSAGE.getValue()) {
                        logger.debug("Received Global '   MESSAGE' application command with name: " + acd.name());
                        discordGlobalMessageCommands.put(acd.name(), acd);
                    } else {
                        // This should never reasonably occur but in the event it does we do not want to continue
                        //  with updating the commands as an unknown interaction was registered externally.
                        throw new IllegalStateException("Unknown interaction type (" + acd.type().get() + ") with name: " + acd.name());
                    }
                } else {
                    // Thanks Discord, as a solution, do not call this logic until Discord fixes this.
                    // Unfortunately that must be implemented on the user's side.
                    throw new IllegalStateException("Discord did not return a type for an interaction with name: " + acd.name());
                }
            })
            .blockLast();

        // finally, validate the local application commands with the registered ones
        int totalChanges = 0;
        totalChanges += validateGlobalCommands(applicationService, applicationId, discordGlobalChatCommands, commandStructure.getGlobalChatCommands());
        totalChanges += validateGlobalCommands(applicationService, applicationId, discordGlobalUserCommands, commandStructure.getGlobalUserCommands());
        totalChanges += validateGlobalCommands(applicationService, applicationId, discordGlobalMessageCommands, commandStructure.getGlobalMessageCommands());

        logger.info("Created/Updated/Deleted " + totalChanges + " global application commands");
        return totalChanges;
    }

    /**
     * Synchronize all global application commands (chat/user/message) with Discord.
     *
     * We merge all requests into a single list as Discord treats them all the same.
     *
     * @param applicationService the bots {@link ApplicationService}
     * @param applicationId the bots Application ID, must match the service
     * @param guildIds the {@link Snowflake} ID of each guild to register commands for
     * @return the number of guilds which had their application commands created/modified/deleted
     * @throws IllegalStateException when Discord doesn't return an interaction type and when
     *                               an unknown interaction type is received
     *                               (bot has an interaction type registered this library didn't create)
     */
    public int registerGuildCommands(ApplicationService applicationService, long applicationId, List<Long> guildIds) {
        logger.debug("Registering guild application commands with Discord for " + guildIds.size() + " guilds.");

        int totalChanges = 0;
        for (long guildId : guildIds) {
            // Since Chat, User, and Message commands can have name collisions we need to allow for that
            //  possibility by spitting them into multiple containers
            Map<String, ApplicationCommandData> discordGuildChatCommands = new HashMap<>();
            Map<String, ApplicationCommandData> discordGuildUserCommands = new HashMap<>();
            Map<String, ApplicationCommandData> discordGuildMessageCommands = new HashMap<>();

            // Get the application commands and put them into their own maps based on type
            // Also ensure we understand all types
            applicationService
                .getGuildApplicationCommands(applicationId, guildId)
                .doOnNext(acd -> {
                    if (!acd.type().isAbsent()) {
                        if (acd.type().get() == ApplicationCommand.Type.CHAT_INPUT.getValue()) {
                            logger.debug("Received Guild 'CHAT_INPUT' application command with name: " + acd.name());
                            discordGuildChatCommands.put(acd.name(), acd);
                        } else if (acd.type().get() == ApplicationCommand.Type.USER.getValue()) {
                            logger.debug("Received Guild '      USER' application command with name: " + acd.name());
                            discordGuildUserCommands.put(acd.name(), acd);
                        } else if (acd.type().get() == ApplicationCommand.Type.MESSAGE.getValue()) {
                            logger.debug("Received Guild '   MESSAGE' application command with name: " + acd.name());
                            discordGuildMessageCommands.put(acd.name(), acd);
                        } else {
                            // This should never reasonably occur but in the event it does we do not want to continue
                            //  with updating the commands as an unknown interaction was registered externally.
                            throw new IllegalStateException("Unknown interaction type (" + acd.type().get() + ") with name: " + acd.name());
                        }
                    } else {
                        // Thanks Discord, as a solution, do not call this logic until Discord fixes this.
                        // Unfortunately that must be implemented on the user's side.
                        throw new IllegalStateException("Discord did not return a type for an interaction with name: " + acd.name());
                    }
                })
                .blockLast();

            // finally, validate the local application commands with the registered ones
            int localChanges = 0;
            localChanges += validateGuildCommands(
                    applicationService,
                    applicationId,
                    guildId,
                    discordGuildChatCommands,
                    commandStructure.getGuildChatCommands(),
                    guildCommandStateProvider.getGuildChatCommands(Snowflake.of(guildId)));
            localChanges += validateGuildCommands(
                    applicationService,
                    applicationId,
                    guildId,
                    discordGuildUserCommands,
                    commandStructure.getGuildUserCommands(),
                    guildCommandStateProvider.getGuildUserCommands(Snowflake.of(guildId)));
            localChanges += validateGuildCommands(
                    applicationService,
                    applicationId,
                    guildId,
                    discordGuildMessageCommands,
                    commandStructure.getGuildMessageCommands(),
                    guildCommandStateProvider.getGuildMessageCommands(Snowflake.of(guildId)));
            logger.info("Created/Updated/Deleted " + totalChanges + " guild application commands for guild " + guildId);
            if (localChanges > 0) totalChanges++;
        }

        logger.info("Created/Updated/Deleted " + totalChanges + " guild application commands");
        return totalChanges;
    }

    /*
     * Bulk Override all commands with the local state. This *should* use the server-side logic
     *  to diff and update changed commands. Which *should* be equivalent to
     *  {@link CommandRegister#registerGlobalCommands(ApplicationService, long)}
     *
     * As of 2022-03-02 the docs to not state this is the case. So this method will not be added, for now.
     * https://discord.com/developers/docs/interactions/application-commands#bulk-overwrite-global-application-commands
     *
     * Not much testing has been done with this, but is made available for a "hard reset" to be done.
     *
     * @param applicationService the bots {@link ApplicationService}
     * @param applicationId the bots Application ID, must match the service
     * @return the ApplicationCommandData returned by Discord
     * @throws IllegalStateException when Discord doesn't return an interaction type and when
     *                               an unknown interaction type is received
     *                               (bot has an interaction type registered this library didn't create)
     */
//    public Flux<ApplicationCommandData> registerGlobalCommandsBulkOverride(ApplicationService applicationService, long applicationId) {
//        return Flux.merge(
//            Flux.fromIterable(commandStructure.getGlobalChatCommands().values()),
//            Flux.fromIterable(commandStructure.getGlobalUserCommands().values()),
//            Flux.fromIterable(commandStructure.getGlobalMessageCommands().values()))
//                .map(BaseCommand::asRequest)
//                .collectList()
//                .flatMapMany(requestList -> applicationService.bulkOverwriteGlobalApplicationCommand(applicationId, requestList));
//    }

    /**
     * Validate two lists of commands, this is generified logic that is shared between Chat/User/Message commands
     * The provided maps should be of the same TYPE of command. This is not checked.
     *
     * @param applicationService the bots application service, used to create/modify/delete commands
     * @param applicationId the bots application id for the application service
     * @param registeredCommands the commands registered with discord
     * @param localCommands the commands created locally
     * @return the number of commands changed
     */
    @SuppressWarnings("ConstantConditions") // The ID of the ApplicationCommandData will be present
    private <B extends BaseCommand> int validateGlobalCommands(ApplicationService applicationService,
                                                               long applicationId,
                                                               Map<String, ApplicationCommandData> registeredCommands,
                                                               Map<String, B> localCommands) {
        int changes = 0;
        // Create/Update commands
        for (BaseCommand localCommand : localCommands.values()) {
            ApplicationCommandRequest request = localCommand.asRequest();
            // Command doesn't exist discord side, create it
            if (registeredCommands.get(request.name()) == null) {
                logger.info("Creating Global " + ApplicationCommand.Type.of(request.type().get()) + " Command: " + request.name());
                ApplicationCommandData acd = applicationService.createGlobalApplicationCommand(applicationId, request).block();
                assignGuildCommandId(localCommand, acd.id());
                changes++;
                continue;
            }
            // Command exists discord side, check if it's equal to the bot command
            ApplicationCommandData discordCmd = registeredCommands.get(request.name());
            if (!commandDataEqualsRequest(discordCmd, request)) {
                logger.info("Updating Global " + ApplicationCommand.Type.of(request.type().get()) + " Command: " + discordCmd.name());
                ApplicationCommandData acd = applicationService.modifyGlobalApplicationCommand(applicationId, discordCmd.id().asLong(), request).block();
                assignGuildCommandId(localCommand, acd.id());
                changes++;
            } else {
                assignGuildCommandId(localCommand, registeredCommands.get(request.name()).id());
            }
        }

        // Delete removed commands
        for (ApplicationCommandData discordCmd : registeredCommands.values()) {
            if (!localCommands.containsKey(discordCmd.name())) {
                logger.info("Deleting Global " + ApplicationCommand.Type.of(discordCmd.type().get()) + " Command: " + discordCmd.name());
                applicationService.deleteGlobalApplicationCommand(applicationId, discordCmd.id().asLong()).block();
                changes++;
            }
        }

        return changes;
    }

    /**
     * Validate two lists of commands, this is generified logic that is shared between Chat/User/Message commands
     * The provided maps should be of the same TYPE of command. This is not checked.
     *
     * @param applicationService the bots application service, used to create/modify/delete commands
     * @param applicationId the bots application id for the application service
     * @param guildId the guild id of the commands being validated
     * @param registeredCommands the commands registered with discord
     * @param localCommands the commands created locally
     * @param allowedCommands the commands the guild is allowed to have
     * @return the number of commands changed
     */
    private <B extends BaseCommand> int validateGuildCommands(ApplicationService applicationService,
                                                              long applicationId,
                                                              long guildId,
                                                              Map<String, ApplicationCommandData> registeredCommands,
                                                              Map<String, B> localCommands,
                                                              Set<String> allowedCommands) {
        int changes = 0;
        // Create/Update commands
        for (BaseCommand localCommand : localCommands.values()) {
            // Don't create/update commands the guild isn't allowed to have
            if (!allowedCommands.contains(localCommand.getName())) {
                continue;
            }

            ApplicationCommandRequest request = localCommand.asRequest();
            // Command doesn't exist discord side, create it
            if (registeredCommands.get(request.name()) == null) {
                logger.info("Creating Guild (" + guildId + ") " + ApplicationCommand.Type.of(request.type().get()) + " Command: " + request.name());
                applicationService.createGuildApplicationCommand(applicationId, guildId, request).block();
                changes++;
                continue;
            }
            // Command exists discord side, check if it's equal to the bot command
            ApplicationCommandData discordCmd = registeredCommands.get(request.name());
            if (!commandDataEqualsRequest(discordCmd, request)) {
                logger.info("Updating Guild (" + guildId + ") " + ApplicationCommand.Type.of(request.type().get()) + " Command: " + discordCmd.name());
                applicationService.modifyGuildApplicationCommand(applicationId, guildId, discordCmd.id().asLong(), request).block();
                changes++;
            } else {
                assignGuildCommandId(localCommand, registeredCommands.get(request.name()).id());
            }
        }

        // Delete removed or disallowed commands
        for (ApplicationCommandData discordCmd : registeredCommands.values()) {
            if (!localCommands.containsKey(discordCmd.name()) || !allowedCommands.contains(discordCmd.name())) {
                logger.info("Deleting Guild (" + guildId + ") " + ApplicationCommand.Type.of(discordCmd.type().get()) + " Command: " + discordCmd.name());
                applicationService.deleteGuildApplicationCommand(applicationId, guildId, discordCmd.id().asLong()).block();
                changes++;
            }
        }

        return changes;
    }

    /**
     * Create an entry in the {@link CommandStructure} mapping a global commands ID to it's class.
     *
     * As the logic for registering global commands is generified, and it doesn't make sense to just return
     *  existing/created/updated commands, we check the class type of the created/modified command to create the
     *  correct mapping.
     *
     * @param command the command to map by its id
     * @param id the id of the command
     */
    @SuppressWarnings("unchecked") // No errors observed when casting in testing
    private void assignGuildCommandId(BaseCommand command, Id id) {
        if (command instanceof GenericChatCommand) {
            commandStructure.addGlobalChatCommand((GenericChatCommand<IC, IB>) command, Snowflake.of(id));
        } else if (command instanceof GenericUserCommand) {
            commandStructure.addGlobalUserCommand((GenericUserCommand<UC, UB>) command, Snowflake.of(id));
        } else if (command instanceof GenericMessageCommand) {
            commandStructure.addGlobalMessageCommand((GenericMessageCommand<MC, MB>) command, Snowflake.of(id));
        }
    }

    /**
     * Check if a slash commands data returned by Discord matches the local slash command request.
     * The names of each parameter should be the same.
     *
     * @param acd the {@link ApplicationCommandData} returned by Discord for a slash command
     * @param acr the {@link ApplicationCommandRequest} created for the same slash command locally
     * @return true if the local slash command is equal to the one registered on Discord
     */
    private boolean commandDataEqualsRequest(ApplicationCommandData acd, ApplicationCommandRequest acr) {
        if (!(acr.name().equals(acd.name())
            && acr.description().toOptional().map(desc -> desc.equals(acd.description())).orElse(false)
            && defaultPermissionEquals(acr.defaultPermission(), acd.defaultPermission()) )) {
            return false;
        }

        if (!acd.options().isAbsent() && !acr.options().isAbsent()) { // Both have options
            return acd.options().get().equals(acr.options().get());
        } else { // If both don't have options return true, otherwise false
            return acd.options().isAbsent() && acr.options().isAbsent();
        }
    }

    /**
     * The default permission must be set in the {@link ApplicationCommandRequest} but doesn't have to be present
     * in a {@link ApplicationCommandData}. This checks for equivalency between the two.
     *
     * @param p1 a Possible representing the default permission value
     * @param p2 a Possible representing the default permission value
     * @return true if the two possibles are equivalent in the context of being set for the default permission
     */
    private boolean defaultPermissionEquals(Possible<Boolean> p1, Possible<Boolean> p2) {
        // The data may not have the permission value present due to behavior as of D4J v3.2.0-RC2
        // By default, D4J will set the requests' default permission to true

        // Each half will return true if the value itself is true or if it is not present (two states for true)
        //  it will return false only when the value itself is false
        // In total this statement will return true only if the values equate to the default permission being true,
        //  or both values are false
        return p1.toOptional().orElse(true) == p2.toOptional().orElse(true);
    }

    public CommandStructure<IC, IB, UC, UB, MC, MB> getCommandStructure() { return commandStructure; }
    public GuildCommandStateProvider getGuildCommandStateProvider() { return guildCommandStateProvider; }
}
