package dev.hc224.slashlib.commands.generic;

import dev.hc224.slashlib.commands.InvalidCommandLocationException;
import dev.hc224.slashlib.context.ChatContext;
import dev.hc224.slashlib.context.ChatContextBuilder;

/**
 * A Class representing a Group Command at the middle level.
 */
public abstract class GenericMidGroupCommand<IC extends ChatContext, IB extends ChatContextBuilder> extends GenericGroupCommand<IC, IB> {
    protected GenericMidGroupCommand(String name, String description) {
        super(name, description);
    }

    /**
     * Add a sub command to this group, must be a GenericSubCommand
     *
     * @param command the sub command to add to this group
     */
    @Override
    public void addSubCommand(GenericChatCommand<IC, IB> command) {
        if (!(command instanceof GenericSubCommand)) {
            throw new InvalidCommandLocationException(command, this, "GenericSubCommand");
        }
        super.addSubCommand(command);
    }

    /**
     * Throw an {@link IllegalStateException} when trying to set the default permission of a MidGroup as it does nothing.
     * @throws IllegalStateException when called
     */
    @Override
    protected void setDefaultPermissionFalse() {
        throw new IllegalStateException("Default Permission only works on GenericTopCommand or GenericTopGroupCommand! Command: " + this.getClass().getSimpleName());
    }
}
