package com.github.captnsisko.left4discord.Commands;

import java.awt.Color;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.github.captnsisko.left4discord.Main;
import com.github.captnsisko.left4discord.Util.Constants;
import com.github.captnsisko.left4discord.Util.DatabaseManager;

import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

import redis.clients.jedis.Jedis;

public class UnmuteCommand extends Command {
    private final static String keyword = "unmute";
    private final static String description = "Unmutes a player (staff only)";
    private final static String usage = "unmute <player> [time] <reason>";

    public UnmuteCommand() {
        super(keyword, description, usage);
    }

    @Override
    protected boolean isCalled(Message msg) {
        return msg.getContent().toLowerCase().startsWith(Constants.PREFIX + keyword);
    }

    @Override
    protected void execute(Message msg) {
        User author = msg.getAuthor().asUser().orElse(null);
        if (author == null) return;
        if (author.getRoles(msg.getServer().get()).contains(Main.getRoles().get("staff"))) {
            String[] parts = msg.getContent().split(" ");
            if (msg.getContent().length() > 30) {
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
                    PreparedStatement sta = DatabaseManager.get()
                            .prepareStatement("SELECT uuid FROM litebans_history WHERE name = ?");
                    sta.setString(1, parts[1]);
                    ResultSet r = sta.executeQuery();
                    if (r.next()) {
                        Jedis j = new Jedis();
                        j.publish("minecraft.console.hub.in", msg.getContent().substring(1));
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
            embed.addField("Error", "You don't have permission to do that, " + author.getMentionTag() + "!");
            embed.setColor(new Color(200, 0, 0));
            embed.setFooter(Main.FOOTER_TEXT);
            msg.getChannel().sendMessage(embed);
        }
    }
}