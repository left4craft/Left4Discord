package com.github.captnsisko.left4discord.tasks;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.github.captnsisko.left4discord.util.Constants;
import com.github.captnsisko.left4discord.util.DatabaseManager;

import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import redis.clients.jedis.Jedis;

public class LookupTask implements Runnable {
	Connection conn;
	CompletableFuture<Message> msg;
	long id;
	String name;

	public LookupTask(CompletableFuture<Message> msg, long id, String name) {
		this.msg = msg;
		this.id = id;
		this.name = name;
	}

	@Override
	public void run() {
		//System.out.println("[DEBUG] RUNNING PUNISHMENTEMBED");
		try {
			conn = DatabaseManager.get();
			PreparedStatement statement = conn.prepareStatement("SELECT HEX(UUID) FROM discord_users WHERE discordID= ?");
			statement.setLong(1, id);
			ResultSet r = statement.executeQuery();
			if (r.next()) {
				//System.out.println("[DEBUG] Found uuid for " + id);
				String uuid = r.getString(1);
				uuid = uuid.toLowerCase();
				uuid = new StringBuilder(uuid).insert(8, '-').insert(13, '-').insert(18, '-').insert(23, '-').toString();
				ArrayList<String> punishments = new ArrayList<String>();

				statement = conn.prepareStatement("SELECT * FROM litebans_warnings WHERE uuid=?");
				statement.setString(1, uuid);
				r = statement.executeQuery();
				while(r.next()) {
					String s = r.getString("time") + "|%%|";
					s += "*" + (r.getBoolean("active") ? "Active" : "Inactive") + "* " + r.getString("banned_by_name") + " warned for " + r.getString("reason");
					s += " on `" + toDate(r.getLong("time")) + "`";
					if(!r.getBoolean("silent")) punishments.add(s);

				}
				statement = conn.prepareStatement("SELECT * FROM litebans_kicks WHERE uuid = ?");
				statement.setString(1, uuid);
				r = statement.executeQuery();
				while(r.next()) {
					String s = r.getString("time") + "|%%|";
					s += "*" + (r.getBoolean("active") ? "Active" : "Inactive") + "* " + r.getString("banned_by_name") + " kicked for " + r.getString("reason");
					s += " on `" + toDate(r.getLong("time")) + "`";
					if(!r.getBoolean("silent")) punishments.add(s);

				}
				
				boolean muted = false;
				statement = conn.prepareStatement("SELECT * FROM litebans_mutes WHERE uuid = ?");
				statement.setString(1, uuid);
				r = statement.executeQuery();
				while(r.next()) {
					boolean active = ((r.getLong("until") > System.currentTimeMillis() || r.getLong("until") < 0) && r.getBoolean("active"));
					String s = r.getString("time") + "|%%|";
					s += "*" + (active ? "Active" : "Inactive") + "* " + r.getString("banned_by_name") + " muted for " + r.getString("reason");
					s += " on `" + toDate(r.getLong("time")) + "`";
					s += " until `" + toDate(r.getLong("until")) + "`";
					if(!r.getBoolean("silent")) punishments.add(s);
					if (active) muted = true;
				}
				
				boolean banned = false;
				statement = conn.prepareStatement("SELECT * FROM litebans_bans WHERE uuid = ?");
				statement.setString(1, uuid);
				r = statement.executeQuery();
				while(r.next()) {
					boolean active = ((r.getLong("until") > System.currentTimeMillis() || r.getLong("until") < 0) && r.getBoolean("active"));
					String s = r.getString("time") + "|%%|";
					s += "*" + (active ? "Active" : "Inactive") + "* " + r.getString("banned_by_name") + " banned for " + r.getString("reason");
					s += " on `" + toDate(r.getLong("time")) + "`";
					s += " until `" + toDate(r.getLong("until")) + "`";
					if(!r.getBoolean("silent")) punishments.add(s);
					if (active) banned = true;
				}
				String list = "";
				if (punishments.size() == 0) {
					list = "No punishments found";
				} else {
					Collections.sort(punishments);
					Collections.reverse(punishments);
					List<String> punList = punishments.subList(0, Math.min(punishments.size(), 5));
					for (String punishment  : punList) {
						list += punishment.split("\\|%%\\|")[1] + "\n\n";
					}
				}
				Jedis redis = new Jedis();
				String[] players = redis.get("minecraft.players").split(",");
				redis.close();
				boolean online = false;
				for (String player : players) {
					if (player.split(" ")[1].equalsIgnoreCase(uuid)) online = true;
				}
				
				EmbedBuilder embed= new EmbedBuilder();
				embed.setTitle("Player Information");
				embed.setDescription("Click name for detailed punishment history");
				embed.setAuthor(name, "https://web.left4craft.org/bans/history.php?uuid=" + uuid, "https://crafatar.com/avatars/" + uuid);
				embed.addField("Online (Minecraft)", online ? "Yes" : "No", true);
				embed.addField("Muted", muted ? "Yes" : "No", true);
				embed.addField("Banned", banned ? "Yes" : "No", true);
				embed.addField("UUID", uuid, false);
				embed.addField("Punishments", list, false);
				embed.setColor(new Color(76, 175, 80));
				embed.setFooter(Constants.FOOTER_TEXT);
				
				msg.get().edit(embed);
			} else {
				EmbedBuilder embed = new EmbedBuilder();
				embed.addField("Error", "Discord account " + id + " is not currently linked to a Minecraft account.");
				embed.setColor(new Color(200, 0, 0));
				embed.setFooter(Constants.FOOTER_TEXT);
				msg.get().edit(embed);
			}
		} catch (SQLException | InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}

	}
	private String toDate(long time) {
		if (time < 0) return "the end of time";
		Date date = new Date(time);
		SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d yyyy, h:mm a", Locale.ENGLISH);
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		return sdf.format(date) + " UTC";
	}
}
