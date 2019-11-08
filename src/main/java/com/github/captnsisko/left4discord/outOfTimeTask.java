package com.github.captnsisko.left4discord;

import java.awt.Color;
import java.util.TimerTask;

import com.github.captnsisko.left4discord.Commands.TriviaCommand;

import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

public class outOfTimeTask extends TimerTask {
	private TriviaTask task;
	private Message msg;
	private User user;
	private String correct;
	private String difficulty;

	public outOfTimeTask(TriviaTask task, Message msg, User user, String correct, String difficulty) {
		this.task = task;
		this.msg = msg;
		this.user = user;
		this.correct = correct;
		this.difficulty = difficulty;
	}

	@Override
	public void run() {
		TriviaCommand.removeTriviaUser(user.getId());
		if(!task.isCompleted()) {
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(new Color(200, 0, 0));
			embed.setAuthor(user);
			embed.addField("Out of Time", "You took longer than 30 seconds to answer the question!");
			embed.addField("Correct Answer", correct);
			embed.addField("Difficulty", difficulty);
			embed.setFooter(Main.FOOTER_TEXT);
			msg.getChannel().sendMessage(embed);
			msg.removeAllReactions();
		}
	}
}
