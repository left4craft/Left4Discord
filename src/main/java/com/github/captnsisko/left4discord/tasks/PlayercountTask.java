package com.github.captnsisko.left4discord.tasks;

import java.util.TimerTask;

import com.github.captnsisko.left4discord.util.Constants;

import org.javacord.api.DiscordApi;

import redis.clients.jedis.Jedis;

public class PlayercountTask extends TimerTask {
	private DiscordApi api;
	
	public PlayercountTask(DiscordApi api) {
		this.api = api;
	}
	
	@Override
	public void run() {
		Jedis j = new Jedis();
		String[] pList = j.get("minecraft.players").split(",");	
		int pCount = 0;
		if (!pList[0].isEmpty()) {
			pCount = pList.length;
		}
		api.getChannelById(Constants.MINECRAFTCHANNEL).get().asServerTextChannel().get().updateTopic("Chat with players who are in-game. Players online:  " + pCount);
		j.close();

	}
}
