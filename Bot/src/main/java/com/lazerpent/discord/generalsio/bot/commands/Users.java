package com.lazerpent.discord.generalsio.bot.commands;

import com.lazerpent.discord.generalsio.bot.*;
import com.lazerpent.discord.generalsio.bot.Commands.Command;
import com.lazerpent.discord.generalsio.bot.Commands.Optional;
import com.lazerpent.discord.generalsio.bot.Commands.Selection;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Commands.Category(cat = "user", name = "Users")
public class Users {
    private static boolean nickWupey = false;

    public static boolean getNickWupey() {
        return nickWupey;
    }

    @Command(name = {"profile", "user"}, perms = Constants.Perms.USER, desc = "Show discord user for a generals username")
    public static Object handleUserGenerals(@NotNull Message msg, String username) {
        Database.User user = Database.User.fromUsername(username);
        if (user == null) {
            return Utils.error(msg, username + " is not registered to any discord" +
                                                                        " user");
        }

        return embedUser(user);
    }

    @Command(name = {"profile", "user"}, perms = Constants.Perms.USER, desc = "Show generals username for a discord user")
    public static Object handleUserDiscord(@NotNull Message msg, @Optional Member mention) {
        Database.User user;
        if (mention != null) {
            user = Database.User.fromId(mention.getIdLong());
        } else {
            user = Database.User.fromId(msg.getAuthor().getIdLong());
        }

        if (user == null) {
            return Utils.error(msg, "<@" + user.discordId() + "> has not" +
                                                                " registered their generals.io user");
        }

        return embedUser(user);
    }

    private static Message embedUser(Database.User user) {
        return
            new MessageBuilder()
                    .setEmbeds(new EmbedBuilder().setTitle("Profile", "https://generals" +
                                                                        ".io/profiles/" + Utils.encodeURI(user.username())).setColor(Constants.Colors.PRIMARY)
                            .appendDescription("**Username:** " + user.username())
                            .appendDescription("\n**Discord:** <@" + user.discordId() + ">")
                            .build())
                    .setActionRows(ActionRow.of(Button.link("https://generals.io/profiles/" + Utils.encodeURI(user.username()), "Visit")))
                    .build();
    }

    @Command(name = {"addname"}, cat = "user", desc = "Register generals.io username")
    public static Object handleAddName(@NotNull Message msg, String username) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

        if (Database.User.fromId(msg.getAuthor().getIdLong()) != null) {
            return Utils.error(msg, "You're already registered as **" + username +
                                                                "**. Ask a <@&" + GUILD_INFO.moderatorRole + "> " +
                                                                "to change your username.");
        }

        Database.User existingUser = Database.User.fromUsername(username);
        if (existingUser != null) {
            if (msg.getGuild().retrieveBanById(existingUser.discordId()) != null) {
                return Utils.error(msg, "**" + username + "** is banned");
            }

            Member m = msg.getGuild().getMemberById(existingUser.discordId());
            if (m != null) {
                return Utils.error(msg, "**" + username + "** is already registered " +
                                                                    "to " + m.getAsMention());
            }
        }

        Database.addDiscordGenerals(msg.getAuthor().getIdLong(), username);
        return new EmbedBuilder().setTitle("Username Added").setColor(Constants.Colors.SUCCESS)
                .setDescription(msg.getMember().getAsMention() + " is now generals.io user **" + username + "**").build();
    }

    @Command(name = {"nickwupey"}, cat = "user", desc = "Bully Wuped", perms = Constants.Perms.MOD)
    public static Object handleNickWupey(@NotNull Message msg) {
        if (msg.getAuthor().getIdLong() == 175430325755838464L) {
            return Utils.error(msg, "You can't bully yourself!");
        }

        nickWupey = !nickWupey;
        if (nickWupey) {
            msg.getGuild().modifyNickname(Objects.requireNonNull(msg.getGuild().retrieveMemberById(175430325755838464L).complete()), "Wupey").queue();
        }

        return new EmbedBuilder().setTitle("Auto-Nick").setDescription("...is " + nickWupey).build();
    }

    @Command(name = {"setname"}, cat = "user", desc = "Change generals.io username of user", perms =
            Constants.Perms.MOD)
    public static Message handleSetName(@NotNull Message msg, Member member, String username) {
        if (Database.noMatch(member.getIdLong(), username)) {
            Database.addDiscordGenerals(member.getIdLong(), username);
        } else {
            Database.updateDiscordGenerals(member.getIdLong(), member.getIdLong(), username);
        }
        return new MessageBuilder()
                .setContent(member.getAsMention())
                .setEmbeds(
                        new EmbedBuilder().setTitle("Username Updated").setColor(Constants.Colors.SUCCESS)
                                .setDescription(member.getAsMention() + " is now generals.io user **" + username +
                                                "**").build()).build();
    }

}