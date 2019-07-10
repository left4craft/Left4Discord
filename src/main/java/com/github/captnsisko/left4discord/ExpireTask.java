package com.github.captnsisko.left4discord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.user.User;

import com.fasterxml.jackson.databind.ObjectMapper;

import redis.clients.jedis.Jedis;

public class ExpireTask extends TimerTask {
	private DiscordApi api;
	
	public ExpireTask(DiscordApi api) {
		this.api = api;
	}
	
	@Override
	public void run() {
		Jedis j = new Jedis();
		String json = j.get("discord.synccodes");	
		ObjectMapper mapper = new ObjectMapper();
		try {
			@SuppressWarnings("unchecked")
			Map<String, String> codes = mapper.readValue(json, Map.class);
			ArrayList<String> delKeys= new ArrayList<String>();
			for (String key : codes.keySet()) {
				String[] values = codes.get(key).split("~");
				if(Long.valueOf(values[1]) < System.currentTimeMillis()) {
					long id = Long.valueOf(values[0]);
					User u = api.getUserById(id).get();
					u.sendMessage("Your code has expired. Reply to this message for a new code.");
					delKeys.add(key);
					System.out.println("Removing expired code for " + id);
				}
			}
			for (String k : delKeys) codes.remove(k);
			j.set("discord.synccodes", mapper.writeValueAsString(codes));
		} catch (IOException | InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		j.close();

	}
}
