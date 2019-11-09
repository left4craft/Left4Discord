package com.github.captnsisko.left4discord;

import java.awt.Color;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.concurrent.ExecutionException;

import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.message.Messageable;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.event.server.member.ServerMemberJoinEvent;

import com.github.captnsisko.left4discord.Commands.ChatCommand;
import com.github.captnsisko.left4discord.Commands.Command;
import com.github.captnsisko.left4discord.Commands.HelpCommand;
import com.github.captnsisko.left4discord.Commands.LookupCommand;
import com.github.captnsisko.left4discord.Commands.MuteCommand;
import com.github.captnsisko.left4discord.Commands.RealnameCommand;
import com.github.captnsisko.left4discord.Commands.SyncCommand;
import com.github.captnsisko.left4discord.Commands.TriviaCommand;
import com.github.captnsisko.left4discord.Commands.UnmuteCommand;
import com.github.captnsisko.left4discord.Util.DatabaseManager;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class Main {
	private static String WEBHOOK_URL;
	private static DiscordApi api;
	private static HashMap<String, Role> roles;
	private static Server server;
	public static final long SERVER = 424571587413540874l;
	public static final long MINECRAFTCHANNEL = 424870757860900865l;
	private static final long BOTCHANNEL = 424889411499458561l;
	private static final long STAFFROLE = 424867647381831690l;
	private static final long PMCHANNEL = 526562171472183296l;
	public static final long MUTEDCHANNEL = 587122816797769788l;
	public static final long MUTEDROLE = 587112191950585856l;

	public static final String FOOTER_TEXT = "Left4Chat 2.4 | Written by Captain_Sisko";

	public static void main(String[] args) {
		String TOKEN = null;
		try {
			Scanner tokenReader = new Scanner(new File("token.txt"));
			if (tokenReader.hasNextLine()) {
				TOKEN = tokenReader.nextLine();
				WEBHOOK_URL = tokenReader.nextLine();
			} else {
				System.out.println("token.txt is empty!");
			}
			tokenReader.close();

			Scanner webhookReader = new Scanner(new File("webhook.txt"));

			if (webhookReader.hasNextLine()) {
				WEBHOOK_URL = webhookReader.nextLine();
				System.out.println(WEBHOOK_URL);
			} else {
				System.out.println("webhook.txt is empty!");
			}
			webhookReader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("Starting Left4Chat Discord bot...");

		api = new DiscordApiBuilder().setToken(TOKEN).login().join();
		api.updateActivity(ActivityType.PLAYING, "Minecraft on left4craft.org");
		subscribe();
		System.out.println("Redis listeners enabled!");


		// Put all commands into a list
		List<Command> commands = new ArrayList<Command>();
		commands.add(new ChatCommand());
		commands.add(new SyncCommand());
		commands.add(new LookupCommand());
		commands.add(new TriviaCommand());
		commands.add(new RealnameCommand());
		commands.add(new MuteCommand());
		commands.add(new UnmuteCommand());
		commands.add(new HelpCommand(commands.toArray(new Command[commands.size()])));

		api.addMessageCreateListener(event -> {
			if (event instanceof MessageCreateEvent) {
				MessageCreateEvent msg = (MessageCreateEvent) event;
				commands.forEach(c -> c.executeIfCalled(msg.getMessage()));

				if (msg.getMessageAuthor().isRegularUser()) {
					String content = msg.getMessageContent();
					/*if (msg.getChannel().getId() == BOTCHANNEL && content.toLowerCase().startsWith("!help")) {
						EmbedBuilder embed = new EmbedBuilder();
						embed.addField("Purpose",
								"This bot was written by Captain_Sisko to synchronize ranks, usernames, chat messages, and punishments between "
										+ "Minecraft and Discord. It has additional features to enhance the user experience on the Left4Craft Discord server.");
						embed.addField("Commands",
								"`!help` - Displays this help menu\n"
										+ "`!lookup @player` - looks up a player's punishment history\n"
										+ "`!realname @player` - looks up a player's in-game username and uuid\n"
										+ "`!trivia` - Generates a trivia question you can answer for in-game rewards\n"
										+ "`!mute <player> [time] <reason>` - Mutes a player (staff only)\n"
										+ "`!unmute <player>` - Unmutes a player (staff only)");
						embed.setColor(new Color(76, 175, 80));
						embed.setFooter(FOOTER_TEXT);
						msg.getChannel().asTextChannel().get().sendMessage(embed);
					}
					*/
				}
			}
		});
		api.addServerMemberJoinListener(event -> {
			if (event instanceof ServerMemberJoinEvent) {
				ServerMemberJoinEvent join = (ServerMemberJoinEvent) event;
				join.getUser().sendMessage("Welcome to Left4Craft!");
				SyncCommand.sendSyncMessage(join.getUser());
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
		//t.scheduleAtFixedRate(new KeepAliveTask(DatabaseManager.get()), 0, 600000l);
		t.scheduleAtFixedRate(new MuteTask(), 0, 300000l);
		System.out.println("Enabled expired key remover");

		String unlinkedUsers = "";
		System.out.println("Finding unlinked users...");
		Collection<User> members = api.getServerById(SERVER).get().getMembers();
		for (User u : members) {
			Server server = api.getServerById(SERVER).get();
			if (u.getRoles(server).size() > 1) {
				try {
					PreparedStatement sta = DatabaseManager.get()
							.prepareStatement("SELECT * FROM discord_users WHERE discordID=?");
					sta.setLong(1, u.getId());
					if (!sta.executeQuery().next()) {
						unlinkedUsers += u.getDisplayName(server) + "\n";
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
		System.out.println(unlinkedUsers);
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

	public static DiscordApi getAPI() {
		return api;
	}

	public static HashMap<String, Role> getRoles() {
		return roles;
	}

}