package com.github.captnsisko.left4discord.Commands;

import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.user.User;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;

public class SyncCommand extends Command {
    private static final long EXPIRE_TIME = 1800000l; // expire time for sync codes in milliseconds
    private static final SecureRandom rng = new SecureRandom();

    public SyncCommand() {
        super();
    }

    @Override
    protected boolean isCalled(Message msg) {
        return msg.isPrivateMessage() && msg.getAuthor().isRegularUser();
    }

    @Override
    protected void execute(Message msg) {
        msg.getAuthor().asUser().ifPresent(u -> {
            sendSyncMessage(u);
        });
    }

    public static void sendSyncMessage(User u) {
        u.sendMessage("Go in-game and type `/discord " + updateJson(u.getIdAsString()) + "` to sync your account.\n"
                + "This code expires in 30 minutes. Your previous codes are now invalid.");
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

}