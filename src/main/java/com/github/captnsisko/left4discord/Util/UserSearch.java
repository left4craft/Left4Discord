package com.github.captnsisko.left4discord.Util;

import java.awt.Color;
import java.util.concurrent.ExecutionException;

import com.github.captnsisko.left4discord.Main;

import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

public class UserSearch {
    public static User getDiscordUser(String search, TextChannel channel) {
        if (!search.startsWith("<@")) {
            EmbedBuilder embed = new EmbedBuilder();
            embed.addField("Error", "Invalid tag");
            embed.setColor(new Color(200, 0, 0));
            embed.setFooter(Main.FOOTER_TEXT);
            channel.sendMessage(embed);
        } else {
            try {
                int startIndex = 0;
                while (!"1234567890".contains("" + search.charAt(startIndex)))
                    startIndex++;
                long id = Long.parseLong(search.substring(startIndex, search.length() - 1));
                User u = Main.getAPI().getUserById(id).get();
                return u;
            } catch (NumberFormatException | InterruptedException | ExecutionException e) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.addField("Error", "Invalid tag");
                embed.setColor(new Color(200, 0, 0));
                embed.setFooter(Main.FOOTER_TEXT);
                channel.sendMessage(embed);
            }
        }
        return null;
    }
}