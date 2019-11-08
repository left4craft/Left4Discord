package com.github.captnsisko.left4discord.Commands;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.github.captnsisko.left4discord.TriviaTask;
import com.github.captnsisko.left4discord.Util.Constants;

import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;

public class TriviaCommand extends Command {
    private final static String keyword = "trivia";
    private final static String description = "Generates a trivia question you can answer for in-game rewards";
    private final static String usage = "trivia";

    private static List<Long> triviaUsers;

    public TriviaCommand() {
        super(keyword, description, usage);
        triviaUsers = new ArrayList<Long>();
    }

    @Override
    protected boolean isCalled(Message msg) {
        return msg.getChannel().getId() == Constants.BOTCHANNEL && msg.getContent().toLowerCase().startsWith("!trivia");
    }

    @Override
    protected void execute(Message msg) {
        long id = msg.getAuthor().getId();
        if (!isUserTrivia(id)) {
            addTriviaUser(id);
            new TriviaTask(msg.getAuthor().asUser().get(), msg.getChannel()).run();
        } else {
            EmbedBuilder embed = new EmbedBuilder();
            embed.addField("Error", "You already have a trivia question!");
            embed.setColor(new Color(200, 0, 0));
            embed.setFooter(Constants.FOOTER_TEXT);
            msg.getChannel().sendMessage(embed);
        }
    }

    public static synchronized void addTriviaUser(long id) {
        if (!triviaUsers.contains(id))
            triviaUsers.add(id);
    }

    public static synchronized void removeTriviaUser(long id) {
        if (triviaUsers.contains(id))
            triviaUsers.remove(id);
    }

    public static synchronized boolean isUserTrivia(long id) {
        return triviaUsers.contains(id);
    }
}