package com.lazerpent.discord.generalsio.bot.commands;

import com.lazerpent.discord.generalsio.bot.Commands.Category;
import com.lazerpent.discord.generalsio.bot.Commands.Command;
import com.lazerpent.discord.generalsio.bot.Commands.CommandParameter;
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
    private static Long nickWupey = null;

    public static Long getNickWupey() {
        return nickWupey;
    }

    @Command(name = "user", perms = Constants.Perms.USER, desc = "Show Discord user for a generals username")
    public static void handleUserGenerals(@NotNull SlashCommandEvent cmd,
                                          @CommandParameter(name = "username",
                                                  desc = "The generals username") String username) {
        Database.User user = Database.User.fromUsername(username);
        if (user == null) {
            Utils.replyError(cmd, username + " is not registered to any Discord user.");
            return;
        }

        cmd.reply(embedUser(user)).queue();
    }

    @Command(name = "profile", perms = Constants.Perms.USER, desc = "Show generals username for the given " +
                                                                    "Discord " +
                                                                    "user, or the message author")
    public static void handleUserDiscord(@NotNull SlashCommandEvent cmd,
                                         @CommandParameter(name = "user",
                                                 desc = "The user mentioned on Discord",
                                                 optional = true) Member mention) {
        Database.User user;
        user = Database.User.fromId(Objects.requireNonNullElseGet(mention, cmd::getMember).getIdLong());

        if (user == null) {
            Utils.replyError(cmd, "<@" + mention.getIdLong() + "> has not registered their generals.io user");
            return;
        }

        cmd.reply(embedUser(user)).queue();
    }

    private static Message embedUser(Database.User user) {
        return new MessageBuilder()
                .setEmbeds(new EmbedBuilder().setTitle("Profile", "https://generals.io/profiles/" +
                                                                  Utils.encodeURI(user.username())).setColor(Constants.Colors.PRIMARY)
                        .appendDescription("**Username:** " + user.username())
                        .appendDescription("\n**Discord:** <@" + user.discordId() + ">")
                        .build())
                .setActionRows(ActionRow.of(Button.link("https://generals.io/profiles/" +
                                                        Utils.encodeURI(user.username()), "Visit")))
                .build();
    }

    @Command(name = "addname", desc = "Register generals.io username")
    public static void handleAddName(@NotNull SlashCommandEvent cmd,
                                     @CommandParameter(name = "username",
                                             desc = "The generals.io username") String username) {
        final Constants.GuildInfo GUILD_INFO =
                Constants.GUILD_INFO.get(Objects.requireNonNull(cmd.getGuild()).getIdLong());

        Database.User curEntry = Database.User.fromId(Objects.requireNonNull(cmd.getMember()).getIdLong());
        if (curEntry != null) {
            Utils.replyError(cmd, "You're already registered as **" + curEntry.username() +
                                  "**. Ask a <@&" + GUILD_INFO.moderatorRole + "> " +
                                  "to change your username.");
            return;
        }

        Database.User existingUser = Database.User.fromUsername(username);
        if (existingUser != null) {
            if (cmd.getGuild().retrieveBanById(existingUser.discordId()).complete() != null) {
                Utils.replyError(cmd, "**" + username + "** is banned");
                return;
            }

            Member m = cmd.getGuild().getMemberById(existingUser.discordId());
            if (m != null) {
                Utils.replyError(cmd, "**" + username + "** is already registered to " + m.getAsMention());
                return;
            }
        }

        Database.addDiscordGenerals(cmd.getMember().getIdLong(), username);
        cmd.replyEmbeds(new EmbedBuilder().setTitle("Username Added").setColor(Constants.Colors.SUCCESS)
                .setDescription(Objects.requireNonNull(cmd.getMember()).getAsMention() + " is now generals.io user " +
                                "**" + username + "**").build()).queue();
    }

    @Command(name = "nickwupey", desc = "Bully Wuped", perms = Constants.Perms.MOD)
    public static void handleNickWupey(@NotNull SlashCommandEvent cmd) {
        if (Objects.requireNonNull(cmd.getMember()).getIdLong() == 175430325755838464L) {
            Utils.replyError(cmd, "You can't bully yourself!");
            return;
        }

        if (nickWupey == null) {
            nickWupey = cmd.getUser().getIdLong();
            Objects.requireNonNull(cmd.getGuild()).modifyNickname(Objects.requireNonNull(cmd.getGuild().retrieveMemberById(175430325755838464L).complete()), "Wupey").queue();
        } else {
            if (nickWupey == 356517795791503393L & cmd.getUser().getIdLong() != nickWupey) {
                Utils.replyError(cmd, "Only Lazerpent can turn off nickwupey if Lazerpent turned it on");
                return;
            }
            nickWupey = null;
        }

        cmd.replyEmbeds(new EmbedBuilder().setTitle("Auto-Nick").setDescription("Switched to " + (nickWupey == null ?
                "disabled." : "enabled.")).build()).queue();
    }

    @Command(name = "setname", desc = "Change generals.io username of user", perms =
            Constants.Perms.MOD)
    public static void handleSetName(@NotNull SlashCommandEvent cmd,
                                     @CommandParameter(name = "user",
                                             desc = "The mention for the user") Member member,
                                     @CommandParameter(name = "username",
                                             desc = "The generals.io username") String username) {
        if (Database.noMatch(member.getIdLong(), username)) {
            Database.addDiscordGenerals(member.getIdLong(), username);
        } else {
            Database.updateDiscordGenerals(Database.getDiscordId(username), member.getIdLong(), username);
        }
        cmd.reply(new MessageBuilder()
                .setContent(member.getAsMention())
                .setEmbeds(
                        new EmbedBuilder().setTitle("Username Updated").setColor(Constants.Colors.SUCCESS)
                                .setDescription(member.getAsMention() + " is now generals.io user **" + username +
                                                "**").build()).build()).queue();
    }

}