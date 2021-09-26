package com.lazerpent.discord.generalsio.bot.commands;

import com.lazerpent.discord.generalsio.bot.Commands;
import com.lazerpent.discord.generalsio.bot.Commands.Category;
import com.lazerpent.discord.generalsio.bot.Commands.Command;
import com.lazerpent.discord.generalsio.bot.Commands.CommandParameter;
import com.lazerpent.discord.generalsio.bot.Commands.Optional;
import com.lazerpent.discord.generalsio.bot.Constants;
import com.lazerpent.discord.generalsio.bot.Database;
import com.lazerpent.discord.generalsio.bot.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Category(name = "Users")
public class Users {
    private static boolean nickWupey = false;

    public static boolean getNickWupey() {
        return nickWupey;
    }

    @Command(name = "user", perms = Constants.Perms.USER, desc = "Show Discord user for a generals username")
    public static Object handleUserGenerals(@NotNull SlashCommandEvent cmd,
                                            @CommandParameter(name = "username", desc = "The generals username")
                                                    String username) {
        Database.User user = Database.User.fromUsername(username);
        if (user == null) {
            return Utils.error(cmd, username + " is not registered to any discord" +
                                    " user");
        }

        return embedUser(user);
    }

    @Command(name = "profile", perms = Constants.Perms.USER, desc = "Show generals username for the given " +
                                                                              "discord " +
                                                                              "user, or the message author")
    public static Object handleUserDiscord(@NotNull SlashCommandEvent cmd,
                                           @CommandParameter(name = "user", desc = "The user mentioned on Discord")
                                           @Optional Member mention) {
        Database.User user;
        user = Database.User.fromId(Objects.requireNonNullElseGet(mention, cmd::getMember).getIdLong());

        if (user == null) {
            return Utils.error(cmd, "<@" + mention.getIdLong() + "> has not registered their generals.io user");
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

    @Command(name = "addname", desc = "Register generals.io username")
    public static Object handleAddName(@NotNull SlashCommandEvent cmd,
                                       @CommandParameter(name = "username", desc = "The generals.io username")
                                               String username) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(cmd.getGuild().getIdLong());

        if (Database.User.fromId(cmd.getMember().getIdLong()) != null) {
            return Utils.error(cmd, "You're already registered as **" + username +
                                    "**. Ask a <@&" + GUILD_INFO.moderatorRole + "> " +
                                    "to change your username.");
        }

        Database.User existingUser = Database.User.fromUsername(username);
        if (existingUser != null) {
            if (cmd.getGuild().retrieveBanById(existingUser.discordId()).complete() != null) {
                return Utils.error(cmd, "**" + username + "** is banned");
            }

            Member m = cmd.getGuild().getMemberById(existingUser.discordId());
            if (m != null) {
                return Utils.error(cmd, "**" + username + "** is already registered " +
                                        "to " + m.getAsMention());
            }
        }

        Database.addDiscordGenerals(cmd.getMember().getIdLong(), username);
        return new EmbedBuilder().setTitle("Username Added").setColor(Constants.Colors.SUCCESS)
                .setDescription(Objects.requireNonNull(cmd.getMember()).getAsMention() + " is now generals.io user " +
                                "**" + username + "**").build();
    }

    @Command(name = "nickwupey", desc = "Bully Wuped", perms = Constants.Perms.MOD)
    public static Object handleNickWupey(@NotNull SlashCommandEvent cmd) {
        if (cmd.getMember().getIdLong() == 175430325755838464L) {
            return Utils.error(cmd, "You can't bully yourself!");
        }

        nickWupey = !nickWupey;
        if (nickWupey) {
            cmd.getGuild().modifyNickname(Objects.requireNonNull(cmd.getGuild().retrieveMemberById(175430325755838464L).complete()), "Wupey").queue();
        }

        return new EmbedBuilder().setTitle("Auto-Nick").setDescription("...is " + nickWupey).build();
    }

    @Command(name = "setname", desc = "Change generals.io username of user", perms =
            Constants.Perms.MOD)
    public static Message handleSetName(@NotNull SlashCommandEvent cmd,
                                        @CommandParameter(name = "user", desc = "The mention for the user")
                                                Member member,
                                        @CommandParameter(name = "username", desc = "The generals.io username")
                                                    String username) {
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