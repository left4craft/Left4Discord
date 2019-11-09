package com.github.captnsisko.left4discord.commands;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;

import com.github.captnsisko.left4discord.tasks.LookupTask;
import com.github.captnsisko.left4discord.util.Constants;
import com.github.captnsisko.left4discord.util.UserSearch;

import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

public class LookupCommand extends Command {
    private final static String keyword = "lookup";
    private final static String description = "Looks up a player's punishment history";
    private final static String usage = "lookup <@player>";

    public LookupCommand() {
        super(keyword, description, usage);
    }

    @Override
    protected boolean isCalled(Message msg) {
        return (msg.getChannel().getId() == Constants.BOTCHANNEL || msg.getChannel().getId() == Constants.MUTEDCHANNEL)
                && msg.getContent().toLowerCase().startsWith(Constants.PREFIX + keyword);
    }

    @Override
    protected void execute(Message msg) {
        String[] parts = msg.getContent().split(" ");
        if (parts.length < 2) {
            EmbedBuilder embed = new EmbedBuilder();
            embed.addField("Error", "Usage: `" + Constants.PREFIX + usage + "`");
            embed.setColor(new Color(200, 0, 0));
            embed.setFooter(Constants.FOOTER_TEXT);
            msg.getChannel().sendMessage(embed);
        } else {
            String pString = msg.getContent().split(" ")[1];
            User u = UserSearch.getDiscordUser(pString, msg.getChannel());
            if (u != null) {
                CompletableFuture<Message> m = msg.getChannel()
                        .sendMessage("Looking up Discord user " + u.getId() + "...");
                new Thread(new LookupTask(m, u.getId(), u.getDisplayName(msg.getServer().get()))).run();
            }
        }
    }

}