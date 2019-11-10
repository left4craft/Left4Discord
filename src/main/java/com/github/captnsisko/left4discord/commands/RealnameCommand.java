package com.github.captnsisko.left4discord.commands;

import java.awt.Color;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.github.captnsisko.left4discord.Main;
import com.github.captnsisko.left4discord.util.Constants;
import com.github.captnsisko.left4discord.util.DatabaseManager;
import com.github.captnsisko.left4discord.util.UserSearch;

import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

public class RealnameCommand extends Command {
    private final static String keyword = "realname";
    private final static String description = "Looks up a player's in-game username and uuid";
    private final static String usage = "realname <@player>";

    public RealnameCommand() {
        super(keyword, description, usage);
    }

    @Override
    protected boolean isCalled(Message msg) {
        return msg.getChannel().getId() == Constants.BOTCHANNEL
                && msg.getContent().toLowerCase().startsWith(Constants.PREFIX + keyword);
    }

    @Override
    protected void execute(Message msg) {
        String[] parts = msg.getContent().split(" ");
        TextChannel channel = msg.getChannel();
        if (parts.length < 2) {
            EmbedBuilder embed = new EmbedBuilder();
            embed.addField("Error", "Usage: `!realname @player`");
            embed.setColor(new Color(200, 0, 0));
            embed.setFooter(Constants.FOOTER_TEXT);
            channel.sendMessage(embed);
        } else {
            User u = UserSearch.getDiscordUser(parts[1], channel);
            if (u != null) {
                long id = u.getId();
                try {
                    PreparedStatement sta = DatabaseManager.get()
                            .prepareStatement("SELECT HEX(uuid) FROM discord_users WHERE discordID = ?");
                    sta.setLong(1, id);
                    ResultSet rs = sta.executeQuery();
                    if (rs.next()) {
                        String uuid = rs.getString(1);
                        uuid = uuid.toLowerCase();
                        uuid = new StringBuilder(uuid).insert(8, '-').insert(13, '-').insert(18, '-').insert(23, '-')
                                .toString();
                        sta = DatabaseManager.get().prepareStatement("SELECT name FROM MCnicks.nicky WHERE uuid = ?");
                        sta.setString(1, uuid);
                        rs = sta.executeQuery();
                        if (rs.next()) {
                            String realName = rs.getString(1);
                            EmbedBuilder embed = new EmbedBuilder();
                            embed.setAuthor(realName, "https://web.left4craft.org/bans/history.php?uuid=" + uuid,
                                    "https://crafatar.com/avatars/" + uuid);
                            embed.addField("Nickname", u.getDisplayName(msg.getServer().get()), true);
                            embed.addField("Real Name", realName, true);
                            embed.addField("UUID", uuid);
                            embed.setColor(new Color(76, 175, 80));
                            embed.setFooter(Main.FOOTER_TEXT);
                            channel.sendMessage(embed);
                        } else {
                            EmbedBuilder embed = new EmbedBuilder();
                            embed.setAuthor(u.getDisplayName(msg.getServer().get()),
                                    "https://web.left4craft.org/bans/history.php?uuid=" + uuid,
                                    "https://crafatar.com/avatars/" + uuid);
                            embed.addField("Nickname", "No nickname", true);
                            embed.addField("Real Name", u.getDisplayName(msg.getServer().get()), true);
                            embed.addField("UUID", uuid);
                            embed.setColor(new Color(76, 175, 80));
                            embed.setFooter(Main.FOOTER_TEXT);
                            channel.sendMessage(embed);
                        }
                    } else {
                        EmbedBuilder embed = new EmbedBuilder();
                        embed.addField("Error",
                                "Discord account " + id + " is not currently linked to a Minecraft account.");
                        embed.setColor(new Color(200, 0, 0));
                        embed.setFooter(Main.FOOTER_TEXT);
                        channel.sendMessage(embed);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}