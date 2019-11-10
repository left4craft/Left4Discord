package com.github.captnsisko.left4discord.tasks;

import java.util.TimerTask;

import com.github.captnsisko.left4discord.Main;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.user.User;

import redis.clients.jedis.Jedis;

public class WebconsoleTask extends TimerTask {
	private DiscordApi api;

	public WebconsoleTask(DiscordApi api) {
		this.api = api;
	}
	
	@Override
	public void run() {
		Jedis j = new Jedis();
		String admins = "";
		for(User u : Main.getRoles().get("admin").getUsers()) {
			admins += u.getIdAsString() + ",";
		}
		for(User u : Main.getRoles().get("owner").getUsers()) {
			admins += u.getIdAsString() + ",";
		}
		admins = admins.substring(0, admins.length()-1);
		System.out.println("Verified admins: " + admins);
		j.set("webconsole.users", admins);
		j.close();
	}
}
