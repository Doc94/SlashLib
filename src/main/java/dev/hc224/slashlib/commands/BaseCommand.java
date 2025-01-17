package dev.hc224.slashlib.commands;

import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import reactor.util.annotation.Nullable;

/**
 * A class which represents all types of Slash Commands. This class should not be directly extended.
 */
public abstract class BaseCommand {
    // Command Name
    private final String name;
    // Command Description
    private final String description;
    // never null: The type of command this is (chat, user, message)
    private final ApplicationCommand.Type commandType;
    // If anyone can use this command by default
    private boolean defaultPermission;
    // Permissions needed by the bot
    private PermissionSet botPermissions;
    // Permissions needed by the calling user
    private PermissionSet userPermissions;
    // If the command can be used in DMs
    private boolean usableInDMs;

    protected BaseCommand(String name,
                          String description,
                          @Nullable ApplicationCommandOption.Type type,
                          ApplicationCommand.Type commandType) {
        this.name = name;
        this.description = description;
        this.commandType = commandType;

        this.defaultPermission = true;
        this.botPermissions = PermissionSet.none();
        this.userPermissions = PermissionSet.none();
        this.usableInDMs = false;
    }

    public abstract ApplicationCommandRequest asRequest();

    /**
     * @return a starting {@link ImmutableApplicationCommandRequest.Builder} with common properties
     */
    protected ImmutableApplicationCommandRequest.Builder buildBaseRequest() {
        ImmutableApplicationCommandRequest.Builder builder = ApplicationCommandRequest.builder()
                .type(this.getCommandType().getValue())
                .name(this.getName())
                .description(this.getDescription());
        if (!this.isDefaultPermission()) { // Avoid the need to check false against an absent possible
            builder.defaultPermission(false);
        }
        return builder;
    }

    /**
     * Set that this command requires a permission to be set for users to call it.
     */
    protected void setDefaultPermissionFalse() {
        this.defaultPermission = false;
    }

    /**
     * Set that this command can be used in DMs.
     */
    protected void setUsableInDMs() {
        this.usableInDMs = true;
    }

    /**
     * Set the permissions the bot needs to execute this command.
     *
     * @param permissions a unique list of Discord permissions
     */
    protected void setBotPermissions(Permission... permissions) {
        this.botPermissions = PermissionSet.of(permissions);
    }

    /**
     * Set the permissions the calling user needs to execute this command.
     *
     * @param permissions a unique list of Discord permissions
     */
    protected void setUserPermissions(Permission... permissions) {
        this.userPermissions = PermissionSet.of(permissions);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public ApplicationCommand.Type getCommandType() { return commandType; }
    public boolean isDefaultPermission() { return defaultPermission; }
    public PermissionSet getBotPermissions() { return botPermissions; }
    public PermissionSet getUserPermissions() { return userPermissions; }
    public boolean isUsableInDMs() { return usableInDMs; }
}
