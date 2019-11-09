package com.github.captnsisko.left4discord.commands;

import java.util.Timer;
import java.util.TimerTask;

import com.github.captnsisko.left4discord.util.Constants;

import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.user.User;

import redis.clients.jedis.Jedis;

public class ChatCommand extends Command {
    public ChatCommand() {
        super();
    }

    @Override
    protected boolean isCalled(Message msg) {
        String content = msg.getContent().toLowerCase();
        return msg.getChannel().getId() == Constants.MINECRAFTCHANNEL && msg.getAuthor().isRegularUser()
                && !content.startsWith("!mute") && !content.startsWith("!unmute");
    }

    @Override
    protected void execute(Message msg) {
        User author = msg.getAuthor().asUser().get();
        Jedis r = new Jedis();
        if (msg.getContent().length() > 256) {
            msg.getChannel().sendMessage(author.getMentionTag() + " Chat message not sent because the length is >256!");
        } else if (msg.getContent().equalsIgnoreCase("list")) {
            String[] players = r.get("minecraft.players").split(",");
            for (int i = 0; i < players.length; i++)
                players[i] = players[i].split(" ")[0];
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    msg.delete();
                }
            }, 500);

            String pString = "```Players: " + (players[0].isEmpty() ? 0 : players.length) + "\n";
            pString += String.join(", ", players) + "```";
            msg.getChannel().sendMessage(pString);
        } else {
            String content = msg.getContent();
            String role = author.getRoles(msg.getServer().get()).get(author.getRoles(msg.getServer().get()).size() - 1)
                    .getName();

            if (!content.isEmpty()) {
                content = content.replace("&", "& ");
                content = content.replace("\n", " ");
                content = "&3[Discord&r" + role + "&3]&r " + author.getDisplayName(msg.getServer().get()) + " &3&l»&r "
                        + content;
                r.publish("minecraft.chat.global.in", content);
            }

            for (MessageAttachment a : msg.getAttachments()) {
                r.publish("minecraft.chat.global.in", "&3[Discord&r" + role + "&3]&r "
                        + author.getDisplayName(msg.getServer().get()) + " &3&l»&r " + a.getUrl().toExternalForm());
            }
        }
        r.close();
    }
}