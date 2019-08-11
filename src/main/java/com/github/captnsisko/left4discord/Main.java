package com.github.captnsisko.left4discord;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.Channel;
//import org.javacord.api.entity.channel.ServerVoiceChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.Messageable;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.event.server.member.ServerMemberJoinEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class Main {
	private static String WEBHOOK_URL;
	private static Connection connection;
	private static DiscordApi api;
	private static HashMap<String, Role> roles;
	private static Server server;
	private static final long EXPIRE_TIME = 1800000l; // expire time in milliseconds
	private static SecureRandom rng;
	public static final long SERVER = 424571587413540874l;
	public static final long MINECRAFTCHANNEL = 424870757860900865l;
	private static final long BOTCHANNEL = 424889411499458561l;
	private static final long STAFFROLE = 424867647381831690l;
	private static final long PMCHANNEL = 526562171472183296l;
	public static final long MUTEDCHANNEL = 587122816797769788l;
	public static final long MUTEDROLE = 587112191950585856l;

	public static final String FOOTER_TEXT = "Left4Chat 2.3 | Written by Captain_Sisko";

	public static ArrayList<Long> triviaUsers;

	public static void main(String[] args) {
		triviaUsers = new ArrayList<Long>();
		String TOKEN = null;
		String host = null;
		String database = null;
		String port = null;
		String user = null;
		String pass = null;
		System.out.println("Reading config files...");
		try {
			Scanner tokenReader = new Scanner(new File("token.txt"));
			if(tokenReader.hasNextLine()) {
				TOKEN = tokenReader.nextLine();
				WEBHOOK_URL = tokenReader.nextLine();
			} else {
				System.out.println("token.txt is empty!");
			}
			tokenReader.close();

			Scanner sqlReader = new Scanner(new File("sql.txt"));
			
			if(sqlReader.hasNextLine()) {
				host = sqlReader.nextLine().split("=")[1];
				database = sqlReader.nextLine().split("=")[1];
				port = sqlReader.nextLine().split("=")[1];
				user = sqlReader.nextLine().split("=")[1];
				pass = sqlReader.nextLine().split("=")[1];
			} else {
				System.out.println("sql.txt is empty!");
			}
			sqlReader.close();

			Scanner webhookReader = new Scanner(new File("webhook.txt"));
			
			if(webhookReader.hasNextLine()) {
				WEBHOOK_URL = webhookReader.nextLine();
				System.out.println(WEBHOOK_URL);
			} else {
				System.out.println("webhook.txt is empty!");
			}
			webhookReader.close();
		} catch (FileNotFoundException e) {
			System.out.println("Could not find token.txt! Is it in the same directory as this jar file?");
		}

		System.out.println("Connecting to mySQL...");

		try {
			Class.forName("com.mysql.jdbc.Driver");
			connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database
					+ "?autoReconnect=true&verifyServerCertificate=false&useSSL=true", user, pass);
		} catch (SQLException | ClassNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
		}

		rng = new SecureRandom();
		System.out.println("Starting Left4Chat Discord bot...");

		api = new DiscordApiBuilder().setToken(TOKEN).login().join();
		subscribe();
		System.out.println("Redis listeners enabled!");
		// new DiscordApiBuilder().setToken(TOKEN).login().thenAccept(api -> {
		// System.out.println("Message listeners enabled!");

		api.addMessageCreateListener(event -> {
			if (event instanceof MessageCreateEvent) {
				MessageCreateEvent msg = (MessageCreateEvent) event;
				if (msg.getChannel().getId() == MINECRAFTCHANNEL && msg.getMessageAuthor().isRegularUser()
						&& !msg.getMessageContent().toLowerCase().startsWith("!mute")
						&& !msg.getMessageContent().toLowerCase().startsWith("!unmute")) {
					User author = msg.getMessageAuthor().asUser().get();
					Jedis r = new Jedis();
					if (msg.getMessageContent().length() > 256) {
						msg.getChannel().sendMessage(
								author.getMentionTag() + " Chat message not sent because the length is >256!");
					} else if (msg.getMessageContent().equalsIgnoreCase("list")) {
						String[] players = r.get("minecraft.players").split(",");
						for (int i = 0; i < players.length; i++)
							players[i] = players[i].split(" ")[0];
						new Timer().schedule(new TimerTask() {
							@Override
							public void run() {
								msg.deleteMessage();
							}
						}, 500);

						String pString = "```Players: " + players.length + "\n";
						pString += String.join(", ", players) + "```";
						msg.getChannel().sendMessage(pString);
					} else {
						String content = msg.getMessageContent();
						String role = author.getRoles(msg.getServer().get())
								.get(author.getRoles(msg.getServer().get()).size() - 1).getName();

						if (!content.isEmpty()) {
							content = content.replace("&", "& ");
							content = content.replace("\n", " ");
							content = "&3[Discord&r" + role + "&3]&r " + author.getDisplayName(msg.getServer().get())
									+ " &3&l»&r " + content;
							r.publish("minecraft.chat.global.in", content);
						}

						for (MessageAttachment a : msg.getMessageAttachments()) {
							r.publish("minecraft.chat.global.in",
									"&3[Discord&r" + role + "&3]&r " + author.getDisplayName(msg.getServer().get())
											+ " &3&l»&r " + a.getUrl().toExternalForm());
						}
					}
					r.close();
				} else if (msg.isPrivateMessage() && msg.getMessageAuthor().isRegularUser()) {
					User u = msg.getMessageAuthor().asUser().get();
					u.sendMessage("Go in-game and type `/discord " + updateJson(u.getIdAsString())
							+ "` to sync your account.\n"
							+ "This code expires in 30 minutes. Your previous codes are now invalid.");
				} else if (msg.getMessageAuthor().isRegularUser()) {
					String content = msg.getMessageContent();
					if (msg.getChannel().getId() == BOTCHANNEL && content.toLowerCase().startsWith("!help")) {
						EmbedBuilder embed = new EmbedBuilder();
						embed.addField("Purpose",
								"This bot was written by Captain_Sisko to synchronize ranks, usernames, chat messages, and punishments between "
										+ "Minecraft and Discord. It has additional features to enhance the user experience on the Left4Craft Discord server.");
						embed.addField("Commands",
								"`!help` - Displays this help menu\n"
										+ "`!lookup @player` - looks up a player's punishment history\n"
										+ "`!trivia` - Generates a trivia question you can answer for in-game rewards\n"
										+ "`!mute <player> [time] <reason>` - Mutes a player (staff only)\n"
										+ "`!unmute <player>` - Unmutes a player (staff only)");
						embed.setColor(new Color(76, 175, 80));
						embed.setFooter(FOOTER_TEXT);
						msg.getChannel().asTextChannel().get().sendMessage(embed);
					} else if ((msg.getChannel().getId() == BOTCHANNEL || msg.getChannel().getId() == MUTEDCHANNEL)
							&& content.toLowerCase().startsWith("!lookup")) {
						String[] parts = content.split(" ");
						TextChannel channel = msg.getChannel().asTextChannel().get();
						if (parts.length < 2) {
							EmbedBuilder embed = new EmbedBuilder();
							embed.addField("Error", "Usage: `!lookup @player`");
							embed.setColor(new Color(200, 0, 0));
							embed.setFooter(Main.FOOTER_TEXT);
							channel.sendMessage(embed);
						} else {
							String pString = parts[1];
							System.out.println(pString);
							if (!pString.startsWith("<@")) {
								EmbedBuilder embed = new EmbedBuilder();
								embed.addField("Error", "Invalid tag");
								embed.setColor(new Color(200, 0, 0));
								embed.setFooter(Main.FOOTER_TEXT);
								channel.sendMessage(embed);
							} else {
								try {
									int startIndex = 0;
									while (!"1234567890".contains("" + pString.charAt(startIndex)))
										startIndex++;
									long id = Long.parseLong(pString.substring(startIndex, pString.length() - 1));
									User u = api.getUserById(id).get();
									CompletableFuture<Message> m = channel
											.sendMessage("Looking up Discord user " + id + "...");
									new Thread(new PunishmentEmbed(connection, m, id,
											u.getDisplayName(api.getServerById(SERVER).get()))).run();

								} catch (NumberFormatException | InterruptedException | ExecutionException e) {
									EmbedBuilder embed = new EmbedBuilder();
									embed.addField("Error", "Invalid tag");
									embed.setColor(new Color(200, 0, 0));
									embed.setFooter(Main.FOOTER_TEXT);
									channel.sendMessage(embed);
								}
							}
						}
					} else if (msg.getChannel().getId() == BOTCHANNEL && content.toLowerCase().startsWith("!trivia")) {
						long id = msg.getMessageAuthor().asUser().get().getId();
						if (!isUserTrivia(id)) {
							addTriviaUser(id);
							new TriviaTask(msg.getMessageAuthor().asUser().get(),
									msg.getChannel().asServerTextChannel().get()).run();
						} else {
							EmbedBuilder embed = new EmbedBuilder();
							embed.addField("Error", "You already have a trivia question!");
							embed.setColor(new Color(200, 0, 0));
							embed.setFooter(Main.FOOTER_TEXT);
							msg.getChannel().sendMessage(embed);
						}
					} else if (content.toLowerCase().startsWith("!mute")) {
						User author = msg.getMessageAuthor().asUser().get();
						if (author.getRoles(api.getServerById(SERVER).get()).contains(roles.get("staff"))) {
							String[] parts = content.split(" ");
							if (content.length() > 200) {
								EmbedBuilder embed = new EmbedBuilder();
								embed.addField("Error", "Please limit your mute command to 200 characters or less.");
								embed.setColor(new Color(200, 0, 0));
								embed.setFooter(Main.FOOTER_TEXT);
								msg.getChannel().sendMessage(embed);
							} else if (parts.length < 3) {
								EmbedBuilder embed = new EmbedBuilder();
								embed.addField("Error", "Usage: `!mute <player> [time] <reason>`");
								embed.setColor(new Color(200, 0, 0));
								embed.setFooter(Main.FOOTER_TEXT);
								msg.getChannel().sendMessage(embed);
							} else if (parts.length == 3 && parts[2]
									.matches("[1-9]+(?:\\.\\d+)?\\s*[s|sec|seconds|m|min|minutes|h|hours|d|days]")) {
								EmbedBuilder embed = new EmbedBuilder();
								embed.addField("Error", "You must specify a reason for punishment.");
								embed.setColor(new Color(200, 0, 0));
								embed.setFooter(Main.FOOTER_TEXT);
								msg.getChannel().sendMessage(embed);
							} else {
								try {
									PreparedStatement sta = connection
											.prepareStatement("SELECT uuid FROM litebans_history WHERE name = ?");
									sta.setString(1, parts[1]);
									ResultSet r = sta.executeQuery();
									if (r.next()) {
										Jedis j = new Jedis();
										j.publish("minecraft.console.hub.in", content.substring(1) + " via Discord by "
												+ author.getDisplayName(msg.getServer().get()));
										j.close();
										EmbedBuilder embed = new EmbedBuilder();
										embed.addField("Success", parts[1] + " was muted.");
										embed.setColor(new Color(76, 175, 80));
										embed.setFooter(Main.FOOTER_TEXT);
										msg.getChannel().sendMessage(embed);
									} else {
										EmbedBuilder embed = new EmbedBuilder();
										embed.addField("Error", "Player " + parts[1] + " was not found.");
										embed.setColor(new Color(200, 0, 0));
										embed.setFooter(Main.FOOTER_TEXT);
										msg.getChannel().sendMessage(embed);
									}
								} catch (SQLException e) {
									e.printStackTrace();
								}
							}

						} else {
							EmbedBuilder embed = new EmbedBuilder();
							embed.addField("Error",
									"You don't have permission to do that, " + author.getMentionTag() + "!");
							embed.setColor(new Color(200, 0, 0));
							embed.setFooter(Main.FOOTER_TEXT);
							msg.getChannel().sendMessage(embed);
						}
					} else if (content.toLowerCase().startsWith("!unmute")) {
						User author = msg.getMessageAuthor().asUser().get();
						if (author.getRoles(api.getServerById(SERVER).get()).contains(roles.get("staff"))) {
							String[] parts = content.split(" ");
							if (content.length() > 30) {
								EmbedBuilder embed = new EmbedBuilder();
								embed.addField("Error", "Please limit your unmute command to 30 characters or less.");
								embed.setColor(new Color(200, 0, 0));
								embed.setFooter(Main.FOOTER_TEXT);
								msg.getChannel().sendMessage(embed);
							} else if (parts.length < 2) {
								EmbedBuilder embed = new EmbedBuilder();
								embed.addField("Error", "Usage: `!unmute <player>`");
								embed.setColor(new Color(200, 0, 0));
								embed.setFooter(Main.FOOTER_TEXT);
								msg.getChannel().sendMessage(embed);
							} else {
								try {
									PreparedStatement sta = connection
											.prepareStatement("SELECT uuid FROM litebans_history WHERE name = ?");
									sta.setString(1, parts[1]);
									ResultSet r = sta.executeQuery();
									if (r.next()) {
										Jedis j = new Jedis();
										j.publish("minecraft.console.hub.in", content.substring(1));
										j.close();
										EmbedBuilder embed = new EmbedBuilder();
										embed.addField("Success", parts[1] + " was unmuted.");
										embed.setColor(new Color(76, 175, 80));
										embed.setFooter(Main.FOOTER_TEXT);
										msg.getChannel().sendMessage(embed);
									} else {
										EmbedBuilder embed = new EmbedBuilder();
										embed.addField("Error", "Player " + parts[1] + " was not found.");
										embed.setColor(new Color(200, 0, 0));
										embed.setFooter(Main.FOOTER_TEXT);
										msg.getChannel().sendMessage(embed);
									}
								} catch (SQLException e) {
									e.printStackTrace();
								}
							}
						} else {
							EmbedBuilder embed = new EmbedBuilder();
							embed.addField("Error",
									"You don't have permission to do that, " + author.getMentionTag() + "!");
							embed.setColor(new Color(200, 0, 0));
							embed.setFooter(Main.FOOTER_TEXT);
							msg.getChannel().sendMessage(embed);
						}
					}
				}
			}
		});
		api.addServerMemberJoinListener(event -> {
			if (event instanceof ServerMemberJoinEvent) {
				ServerMemberJoinEvent join = (ServerMemberJoinEvent) event;
				User u = join.getUser();
				u.sendMessage("Welcome to the server!\nGo in-game and type `/discord " + updateJson(u.getIdAsString())
						+ "` to sync your account.\n"
						+ "This code expires in 30 minutes. Your previous codes are now invalid.");
			}
		});
		System.out.println("Discord listeners enabled!");

		server = api.getServerById(SERVER).get();
		roles = new HashMap<String, Role>();
		try {
			for (Role r : server.getRoles()) {
				System.out.println(r.getName());
				roles.put(r.getName().toLowerCase(), r);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Got roles: " + roles.toString());



		Timer t = new Timer();
		t.scheduleAtFixedRate(new ExpireTask(api), 0, 60000l);
		t.scheduleAtFixedRate(new KeepAliveTask(connection), 0, 600000l);
		t.scheduleAtFixedRate(new MuteTask(), 0, 300000l);
		System.out.println("Enabled expired key remover");

		String unlinkedUsers = "";
		System.out.println("Finding unlinked users...");
		Collection<User> members = api.getServerById(SERVER).get().getMembers();
		for (User u : members) {
			Server server = api.getServerById(SERVER).get();
			if(u.getRoles(server).size() > 1) {
				try {
					PreparedStatement sta = connection.prepareStatement("SELECT * FROM discord_users WHERE discordID=?");
					sta.setLong(1, u.getId());
					if(!sta.executeQuery().next()) {
						unlinkedUsers += u.getDisplayName(server) + "\n";
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
		System.out.println(unlinkedUsers);

		// });

	}

	private static synchronized String updateJson(String id) {
		Jedis j = new Jedis();
		String json = j.get("discord.synccodes");
		String code = "ERROR GENERATING CODE";
		ObjectMapper mapper = new ObjectMapper();
		try {
			@SuppressWarnings("unchecked")
			Map<String, String> codes = mapper.readValue(json, Map.class);
			for (String key : codes.keySet()) {
				if (codes.get(key).split("~")[0].equals(id)) {
					codes.remove(key);
					System.out.println("Removing old code for " + id);
				}
			}
			// MessageDigest digest = MessageDigest.getInstance("SHA-256");
			// byte[] hash = digest.digest((id + SALT).getBytes(StandardCharsets.UTF_8));
			code = org.apache.commons.codec.digest.DigestUtils.sha256Hex(id + rng.nextLong());
			code = code.substring(1, 10);
			System.out.println("Code for " + id + ": " + code);
			long expires = System.currentTimeMillis() + EXPIRE_TIME;
			System.out.println("Expire time for " + id + ": " + expires);
			codes.put(code, id + "~" + expires);
			j.set("discord.synccodes", mapper.writeValueAsString(codes));
		} catch (IOException e) {
			e.printStackTrace();
		}
		j.close();
		return code;
	}

	private static JedisPubSub subscribe() {
		final JedisPubSub jedisPubSub = new JedisPubSub() {
			Timer t = new Timer();

			public void onMessage(String channel, String message) {
				if (channel.equals("minecraft.chat.global.out")) {
					System.out.println(message);
					if (message.startsWith(":") || message.startsWith("<")) {
						Channel c = api.getChannelById(MINECRAFTCHANNEL).get();
						if (c instanceof Messageable) {
							((Messageable) c).sendMessage(message);
						}
						t.schedule(new PlayercountTask(api), 5000l);
					} else {
						String[] parts = message.split("\\*\\*");
						if (parts.length >= 3) {
							String name = parts[2];
							String content = String.join("\\**", Arrays.copyOfRange(parts, 3, parts.length));
							content = content.replaceAll("(?i)@everyone", "@_everyone");
							content = content.replaceAll("(?i)@here", "@_here");
							String avatar = "https://crafatar.com/avatars/" + parts[1];
							try {
								Unirest.post(WEBHOOK_URL).field("username", name).field("avatar_url", avatar)
										.field("content", content).asJson();
							} catch (UnirestException e) {
								e.printStackTrace();
							}
							if (content.toLowerCase().contains("@staff")) {
								content = content.replaceAll("(?i)@staff",
										api.getRoleById(STAFFROLE).get().getMentionTag());
								Channel c = api.getChannelById(MINECRAFTCHANNEL).get();
								if (c instanceof Messageable) {
									((Messageable) c).sendMessage("**" + name + "** " + content);
								}
							}

						}
					}
				} else if (channel.equals("minecraft.chat.messages")) {
					System.out.println(message);
					String[] parts = message.split(",");
					message = "**" + parts[0] + " -> " + parts[1] + "** ";
					for (int i = 2; i < parts.length; i++)
						message += parts[i] + ",";
					message = message.substring(0, message.length() - 1);
					message = message.replaceAll("(?i)@everyone", "@_everyone");
					message = message.replaceAll("(?i)@here", "@_here");
					Channel c = api.getChannelById(PMCHANNEL).get();
					if (c instanceof Messageable) {
						((Messageable) c).sendMessage(message);
					}
				} else if (channel.equals("discord.botcommands")) {
					System.out.println(message);
					String[] parts = message.split(" ");
					if (parts[0].equals("setuser")) {
						long id = Long.valueOf(parts[1]);
						String name = parts[2];
						try {
							api.getUserById(id).get().updateNickname(server, name);
							System.out.println("Set nickname of " + id + " to " + name);
						} catch (InterruptedException | ExecutionException e) {
							e.printStackTrace();
						}
					} else if (parts[0].equals("setgroup")) {
						long id = Long.valueOf(parts[1]);
						try {
							User user = api.getUserById(id).get();
							Role role = roles.get(parts[2].toLowerCase());
							if (role != null && user != null && !isMuted(user)
									&& !user.getRoles(server).contains(role)) {
								for (Role r : user.getRoles(server))
									if (r.getId() != MUTEDROLE)
										user.removeRole(r);
								user.addRole(role);
								if (parts[2].equalsIgnoreCase("Owner") || parts[2].equalsIgnoreCase("Admin")
										|| parts[2].equalsIgnoreCase("Moderator")
										|| parts[2].equalsIgnoreCase("Helper")) {
									user.addRole(roles.get("staff"));
									System.out.println("Adding staff role...");
								}
								user.sendMessage("Your in-game rank of `" + parts[2]
										+ "` has been applied to your Discord account.");
								System.out.println("Successfully set role!");
							} else if (role == null) {
								System.out.println("Could not find role named " + parts[2].toLowerCase());
							} else if (user == null) {
								System.out.println("Could find not user " + parts[1]);
							} else {
								System.out.println("User already has the correct role.");
							}
						} catch (InterruptedException | ExecutionException e) {
							e.printStackTrace();
						}
					} else if (parts[0].equals("unlink")) {
						long id = Long.valueOf(parts[1]);
						long id2 = Long.valueOf(parts[2]);

						try {
							User user = api.getUserById(id).get();
							if (id != id2) {
								for (Role r : user.getRoles(server))
									user.removeRole(r);
								user.sendMessage(
										"Your account has been demoted on Discord because you linked another account from in game.\n"
												+ "If this was not you, your Minecraft account may have been compromised.\n"
												+ "New ID: `" + id2 + "`");
								System.out.println("User notified of account unlink.");
								User newUser = api.getUserById(id2).get();
								user.sendMessage("New user: `" + newUser.getDiscriminatedName() + "`");
								System.out.println("User notified of the new account.");
							} else {
								user.sendMessage(
										"This Discord account has already been linked to your in-game account.");
							}
						} catch (InterruptedException | ExecutionException e) {
							System.out.println("Error during unlink command. One of the users likely left the server.");
							// e.printStackTrace();
						}

					} else {
						System.out.println("Unknown command: " + parts[0]);
					}

				} else if (channel.equals("minecraft.chat.mute")) {
					new MuteTask().run();
				}
			}
		};
		new Thread(new Runnable() {
			public void run() {
				try {
					Jedis jedis = new Jedis();
					jedis.subscribe(jedisPubSub, new String[] { "minecraft.chat.global.out", "minecraft.chat.messages",
							"discord.botcommands", "minecraft.chat.mute" });
					System.out.println("Subscriber closed!");
					jedis.quit();
					jedis.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, "subscriberThread").start();
		return jedisPubSub;
	}

	public static boolean isMuted(User u) {
		return u.getRoles(server).contains(roles.get("muted"));
	}

	public static boolean mute(User u) {
		try {
			u.addRole(roles.get("muted")).get();
		} catch (InterruptedException | ExecutionException e) {
			return false;
		}
		for (Role r : u.getRoles(server))
			if (r.getId() != MUTEDROLE)
				u.removeRole(r);

		return true;
	}

	public static void unmute(User u) {
		u.removeRole(roles.get("muted"));
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

	public static Connection getSQL() {
		return connection;
	}

	public static DiscordApi getAPI() {
		return api;
	}

	public static HashMap<String, Role> getRoles() {
		return roles;
	}

}