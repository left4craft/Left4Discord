package com.github.captnsisko.left4discord.tasks;

import java.awt.Color;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.github.captnsisko.left4discord.Main;
import com.github.captnsisko.left4discord.util.Constants;
import com.github.captnsisko.left4discord.util.DatabaseManager;

import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

public class MuteTask extends TimerTask {
	@Override
	public void run() {
		System.out.println("Running mute task...");
		try {
			Statement sta = DatabaseManager.get().createStatement();
			Set<String> uuids = new HashSet<String>();
			ResultSet r = sta.executeQuery("SELECT * FROM litebans_mutes WHERE active=1;");
			while(r.next()) {
				if(r.getLong("until") > System.currentTimeMillis() || r.getLong("until") < 0) uuids.add(r.getString("uuid"));
			}
			r = sta.executeQuery("SELECT * FROM litebans_bans WHERE active=1;");
			while(r.next()) {
				if(r.getLong("until") > System.currentTimeMillis() || r.getLong("until") < 0) uuids.add(r.getString("uuid"));
			}
			System.out.println("Found muted/banned uuids");
			//uuids.forEach(s -> System.out.println(s));

			ArrayList<Long> muted = new ArrayList<Long>();
			ArrayList<Long> currentlyMuted = new ArrayList<Long>();
			for(String uuid : uuids) {
				PreparedStatement prepared = DatabaseManager.get().prepareStatement("SELECT discordID from discord_users WHERE UUID=UNHEX(?)");
				prepared.setString(1, uuid.replaceAll("-", ""));
				r = prepared.executeQuery();
				if(r.next()) {
					muted.add(r.getLong(1));	
				} else {
					//System.out.println("Couldn't find discord user for uuid " + uuid);
				}
			}
			Main.getAPI().getRoleById(587112191950585856l).get().getUsers().forEach(u -> currentlyMuted.add(u.getId()));
			currentlyMuted.forEach(l -> System.out.println(l));
			for(long id : muted) {
				if(!currentlyMuted.contains(id)) {
					try {
						User u = Main.getAPI().getServerById(Constants.SERVER).get().getMemberById(id).get();
						if(Main.mute(u)) {
							EmbedBuilder embed = new EmbedBuilder();
							embed.setAuthor(u);
							embed.addField("Muted", u.getNicknameMentionTag() + ", you have been muted on Discord because you were muted or banned in-game. "
									+ "This is the only channel in which you are permitted to speak. "
									+ "After you are unmuted or unbanned, it may take up to 5 minutes for your Discord role to update.");
							embed.addField("Reason", "Use \"!lookup " + u.getNicknameMentionTag() + "\" to see your punishment reason and history.");
							embed.addField("Appeal", "Depending on your offense, you may be able to appeal your punishment at https://left4.cf/appeal");
							embed.setColor(new Color(200, 0, 0));
							embed.setFooter(Main.FOOTER_TEXT);
							Main.getAPI().getChannelById(Constants.MUTEDCHANNEL).get().asServerTextChannel().get().sendMessage(embed);
							CompletableFuture<Message> m = Main.getAPI().getChannelById(Constants.MUTEDCHANNEL).get().asServerTextChannel().get().sendMessage(u.getNicknameMentionTag() + "'s Punishment History:");
							new LookupTask(
								m,
								id,
								u.getDisplayName(Main.getAPI().getServerById(Constants.SERVER).get())).run();
						}
					} catch (NoSuchElementException e) {
						//System.out.println("Could not find muted user " + id + ", they may have left the server.");
					}
				}
			}
			for(long id : currentlyMuted) {
				if(!muted.contains(id)) {
					try {
						User u = Main.getAPI().getUserById(id).get();
						Main.unmute(u);
						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor(u);
						embed.addField("Unmuted", u.getNicknameMentionTag() + ", you have been unmuted because your in-game punishment expired. "
								+ "Please ensure you read and follow the rules to avoid this in the future. "
								+ "Your Discord role has been removed for now. It will be updated when you reconnect to the server.");
						embed.setColor(new Color(76, 175, 80));
						embed.setFooter(Constants.FOOTER_TEXT);
						Main.getAPI().getChannelById(Constants.MUTEDCHANNEL).get().asServerTextChannel().get().sendMessage(embed);
						embed.removeAllFields();
						embed.addField("Unmuted", "You have been unmuted because your in-game punishment expired. "
								+ "Please ensure you read and follow the rules to avoid this in the future. "
								+ "Your Discord role has been removed for now. It will be updated when you reconnect to the server.");
						u.sendMessage(embed);
					} catch (InterruptedException | ExecutionException e) {
						//System.out.println("Could not unmute user " + id + ", they may have left the server.");
					}

				}
			}

		} catch (SQLException e) {
			System.out.println("Error connecting to SQL for mute task!");
			e.printStackTrace();
		}

	}
}
