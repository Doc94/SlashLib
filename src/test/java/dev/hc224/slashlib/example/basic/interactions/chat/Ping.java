package dev.hc224.slashlib.example.basic.interactions.chat;

import dev.hc224.slashlib.commands.standard.TopCommand;
import dev.hc224.slashlib.context.ChatContext;
import reactor.core.publisher.Mono;

/**
 * A simple example chat input interaction at the top level which simply responds to the user with "Pong!".
 */
public class Ping extends TopCommand {
    /**
     * Create a new instance of this class, the constructor is where details about the interaction are set.
     */
    public Ping() {
        // Pass the name and description to the superclass constructor
        super("ping", "get a pong!");
        setUsableInDMs();
    }

    /**
     * This is the method that will be called when a user invokes this interaction.
     * The context is returned for usage in custom event receivers.
     *
     * @param context a {@link ChatContext} provided by SlashLib with some data provided about the interaction.
     * @return the same context provided
     */
    @Override
    public Mono<ChatContext> executeChat(ChatContext context) {
        return context.getEvent().reply("Pong!").thenReturn(context);
        // If for some reason you want to return empty, do `.then(Mono.empty())` instead.
    }
}
