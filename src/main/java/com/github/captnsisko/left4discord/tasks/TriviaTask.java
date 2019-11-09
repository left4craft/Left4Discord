package com.github.captnsisko.left4discord.tasks;


import java.awt.Color;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.reaction.ReactionAddEvent;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.captnsisko.left4discord.commands.TriviaCommand;
import com.github.captnsisko.left4discord.util.Constants;
import com.github.captnsisko.left4discord.util.DatabaseManager;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.vdurmont.emoji.EmojiParser;

import redis.clients.jedis.Jedis;

public class TriviaTask implements Runnable {
	private boolean completed;
	private User user;
	private TextChannel channel;

	public TriviaTask(User user, TextChannel channel) {
		this.user = user;
		this.channel = channel;
		completed = false;
	}

	@Override
	public void run() {
		try {
			JSONObject json = Unirest.get("https://opentdb.com/api.php?amount=1&category=15&type=multiple").asJson().getBody().getObject();
			json = json.getJSONArray("results").getJSONObject(0);
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(new Color(76, 175, 80));

			String question = fixString(json.getString("question"));
			String correct = fixString(json.getString("correct_answer"));
			String difficulty = fixString(json.getString("difficulty"));
			ArrayList<String> answers = new ArrayList<String>();
			json.getJSONArray("incorrect_answers").forEach(o -> {
				String s = (String) o;
				if(answers.size() < 3) answers.add(fixString(s));
			});
			answers.add(correct);
			Collections.shuffle(answers);
			String answerStr = "";
			for(int i = 0; i < answers.size(); i++) {
				answerStr += "abcd".charAt(i) + ") " + answers.get(i) + "\n";
			}
			//embed.addField("Instructions", "You have 30 seconds to answer the question by reacting with the correct letter choice");
			embed.setAuthor(user);
			embed.addField(question, answerStr);
			embed.setFooter(Constants.FOOTER_TEXT);
			try {
				Message msg = channel.sendMessage(embed).get();
				String[] choices = {EmojiParser.parseToUnicode(":regional_indicator_symbol_a:"),
						EmojiParser.parseToUnicode(":regional_indicator_symbol_b:"),
						EmojiParser.parseToUnicode(":regional_indicator_symbol_c:"),
						EmojiParser.parseToUnicode(":regional_indicator_symbol_d:")};

				msg.addReactions(choices);
				msg.addReactionAddListener(e -> {
					if (e instanceof ReactionAddEvent && !completed) {
						ReactionAddEvent react = (ReactionAddEvent) e;
						if(react.getUser().getId() == user.getId()) {
							for(int i = 0; i < 4; i++) {
								if(choices[i].equals(react.getEmoji().asUnicodeEmoji().get())) {
									this.completed = true;
									EmbedBuilder result = new EmbedBuilder();
									result.setAuthor(user);
									if(answers.get(i).equals(correct)) {
										result.setColor(new Color(76, 175, 80));
										result.addField("Correct", "You answered the question correctly!");
										Jedis j = new Jedis();
										String rewardsStr = j.get("discord.triviareward");
										ObjectMapper mapper = new ObjectMapper();
										try {
											@SuppressWarnings("unchecked")
											Map<String, String> codes = mapper.readValue(rewardsStr, Map.class);
											if (codes.get(user.getIdAsString()) == null || Long.parseLong(codes.get(user.getIdAsString())) < System.currentTimeMillis()) {
												codes.put(user.getIdAsString(), Long.toString(System.currentTimeMillis() + 3600000));
												result.addField("Reward", reward(user.getId(), difficulty));
											} else {
												long minutes = (Long.parseLong(codes.get(user.getIdAsString())) - System.currentTimeMillis()) / 60000;
												result.addField("Reward", "You must wait another " + minutes + " minutes to recieve a reward.");
											}
											j.set("discord.triviareward", mapper.writeValueAsString(codes));
										} catch (IOException e1) {
											e1.printStackTrace();
										}
										j.close();
									} else {
										result.setColor(new Color(200, 0, 0));
										result.addField("Incorrect", "You answered the question incorrectly!");
										result.addField("Your Answer", answers.get(i));
									}
									result.addField("Correct Answer", correct);
									result.addField("Difficulty", difficulty);
									result.setFooter(Constants.FOOTER_TEXT);
									channel.sendMessage(result);
									msg.removeAllReactions();
									TriviaCommand.removeTriviaUser(user.getId());
								}
							}
						}
					} else {
						System.out.println(completed);
					}
				}).removeAfter(29, TimeUnit.SECONDS);
				Timer t = new Timer();
				t.schedule(new outOfTimeTask(this, msg, user, correct, difficulty), 30000);
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}

			//@SuppressWarnings("unchecked")
			//Map<String, String> codes = mapper.readValue(json, Map.class);
		} catch (UnirestException | JSONException e) {
			System.out.println("Error getting trivia question!");
			e.printStackTrace();
			EmbedBuilder embed = new EmbedBuilder();
			embed.addField("Error", "Could not reach trivia server");
			embed.setColor(new Color(200, 0, 0));
			embed.setFooter(Constants.FOOTER_TEXT);
			channel.sendMessage(embed);
		} 
	}

	private String fixString(String str) {
		return Jsoup.parse(str).text();
	}

	public boolean isCompleted() {
		return completed;
	}

	private String reward(long id, String difficulty) {
		try {
			PreparedStatement statement = DatabaseManager.get().prepareStatement("SELECT HEX(UUID) FROM discord_users WHERE discordID = ?");
			statement.setLong(1, id);
			ResultSet r = statement.executeQuery();
			if (r.next()) {
				String uuid = r.getString(1);
				uuid = uuid.toLowerCase();
				uuid = new StringBuilder(uuid).insert(8, '-').insert(13, '-').insert(18, '-').insert(23, '-').toString();
				statement = DatabaseManager.get().prepareStatement("SELECT name FROM litebans_history WHERE uuid=? ORDER BY date DESC;");
				statement.setString(1, uuid);
				r = statement.executeQuery();
				if (r.next()) {
					Jedis j = new Jedis();
					String user = r.getString(1);
					System.out.println("Found username for " + uuid + ": " + user);
					if(difficulty.equals("easy")) {
						j.publish("minecraft.console.survival.in", "eco give " + user + " 50");
						j.close();
						return "You have been rewarded $50 for answering an easy question!";
					} else if (difficulty.equals("medium")) {
						j.publish("minecraft.console.survival.in", "eco give " + user + " 100");
						j.close();
						return "You have been rewarded $100 for answering a medium question!";
					} else if (difficulty.equals("hard")) {
						j.publish("minecraft.console.survival.in", "eco give " + user + " 200");
						j.close();
						return "You have been rewarded $200 for answering a hard question!";
					}
					j.close();
				} else {
					return "Could not find username for uuid " + uuid + "!";
				}
			} else {
				return "Discord account " + id + " is not currently linked to a Minecraft account!";
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return "Error establishing database connection!";
		}

		return "Error evaluating question difficulty!";
	}
}
