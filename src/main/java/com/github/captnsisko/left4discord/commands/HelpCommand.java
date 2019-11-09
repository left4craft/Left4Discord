package com.github.captnsisko.left4discord.commands;

import java.awt.Color;

import com.github.captnsisko.left4discord.util.Constants;

import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;

public class HelpCommand extends Command {
    private final static String keyword = "help";
    private final static String description = "Displays this help menu";
    private final static String usage = "help";
    private Command[] commands;

    public HelpCommand(Command... commands) {
        super(keyword, description, usage);
        this.commands = commands;
    }

    @Override
    protected boolean isCalled(Message msg) {
        return msg.getChannel().getId() == Constants.BOTCHANNEL
                && msg.getContent().toLowerCase().startsWith(Constants.PREFIX + keyword);
    }

    @Override
    protected void execute(Message msg) {
        EmbedBuilder embed = new EmbedBuilder();
        String commandStr = "";
        for (Command cmd : commands) {
            if (cmd.visibleInHelp) {
                commandStr += "`" + Constants.PREFIX + cmd.usage + "`" + " - " + cmd.description + "\n";
            }
        }
        commandStr = commandStr.trim();

        embed.addField("Purpose",
                "This bot was written by Captain_Sisko to synchronize ranks, usernames, chat messages, and punishments between "
                        + "Minecraft and Discord. It has additional features to enhance the user experience on the Left4Craft Discord server.");
        embed.addField("Commands", commandStr);
        embed.setColor(new Color(76, 175, 80));
        embed.setFooter(Constants.FOOTER_TEXT);
        msg.getChannel().asTextChannel().get().sendMessage(embed);
    }
}