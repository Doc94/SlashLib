package dev.hc224.slashlib.example.basic.interactions.chat;

import dev.hc224.slashlib.commands.standard.TopCommand;
import dev.hc224.slashlib.context.ChatContext;
import dev.hc224.slashlib.context.ChatContextBuilder;
import dev.hc224.slashlib.context.ContextBuilder;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

import java.sql.Date;
import java.text.SimpleDateFormat;

/**
 * An example command which can be called from a private channel (DM) or a guild to display some information
 *  about the bot account.
 *
 * This uses the require/request methods on the {@link ContextBuilder}
 *  to collect and determine what information to include in the response.
 */
public class About extends TopCommand {
    private static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss zzz");

    public About() {
        super("status", "Show some information about the bot");
        setUsableInDMs();
    }

    @Override
    public Mono<ChatContext> executeChat(ChatContext context) {
        return Mono.just(context).map(this::executeChatBlocking)
            .flatMap(embed -> context.getEvent().reply(InteractionApplicationCommandCallbackSpec.builder().addEmbed(embed).build()))
            .thenReturn(context);
    }

    // We require and check for the bot user and member respectively
    // The join time should always be present.
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private EmbedCreateSpec executeChatBlocking(ChatContext context) {
        EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();

        embed.author(context.getBotUser().get().getUsername(), null, context.getBotUser().get().getAvatarUrl());
        embed.addField(
            "Bot Created At",
            formatter.format(Date.from(context.getBotUser().get().getId().getTimestamp())),
            false);

        // A check for `context.getBotMember().isPresent()` could be used in place here.
        // The doesAllRequestedDataExist is provided as an easy way to check if *all* requested data is present.
        if (context.doesAllRequestedDataExist()) {
            embed.addField(
                "Bot Joined At",
                formatter.format(Date.from(context.getBotMember().get().getJoinTime().get())),
                false);
        }

        embed.color(Color.SUBMARINE);

        return embed.build();
    }

    @Override
    public ChatContextBuilder setRequestData(ChatContextBuilder contextBuilder) {
        return (ChatContextBuilder) contextBuilder.requireBotUser().requestBotMember();
    }
}
