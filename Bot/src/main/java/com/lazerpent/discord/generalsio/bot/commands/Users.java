package com.lazerpent.discord.generalsio.bot.commands;

import com.lazerpent.discord.generalsio.bot.Commands;
import com.lazerpent.discord.generalsio.bot.Commands.Command;
import com.lazerpent.discord.generalsio.bot.Commands.Required;
import com.lazerpent.discord.generalsio.bot.Constants;
import com.lazerpent.discord.generalsio.bot.Database;
import com.lazerpent.discord.generalsio.bot.Utils;
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

    //    @Command(name = {"profile", "user"}, args = {"username? | @mention?"}, cat = "user", perms =
//            Constants.Perms.USER, desc = "Show username of user, or vice versa")
    public static void handleUser(@NotNull Message msg, String[] cmd) {
        long discordId;
        String name;

        if (cmd.length == 1) {
            discordId = msg.getAuthor().getIdLong();
            name = Database.getGeneralsName(discordId);
            if (name == null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg,
                        Objects.requireNonNull(msg.getMember()).getAsMention() + " has not registered their " +
                        "generals.io user")).queue();
                return;
            }
        } else {
            List<Member> members = msg.getMentionedMembers();
            if (members.size() == 0) {
                name = Arrays.stream(cmd).skip(1).collect(Collectors.joining(" "));
                discordId = Database.getDiscordId(name);
                if (discordId == -1) {
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg, name + " is not registered to any discord" +
                                                                        " user")).queue();
                    return;
                }
            } else if (members.size() != 1) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You can only lookup one user at a time")).queue();
                return;
            } else {
                discordId = members.get(0).getIdLong();
                name = Database.getGeneralsName(discordId);
                if (name == null) {
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg, members.get(0).getAsMention() + " has not" +
                                                                        " registered their generals.io user")).queue();
                    return;
                }
            }
        }

        msg.getChannel().sendMessage(
                new MessageBuilder()
                        .setEmbeds(new EmbedBuilder().setTitle("Profile", "https://generals" +
                                                                          ".io/profiles/" + Utils.encodeURI(name)).setColor(Constants.Colors.PRIMARY)
                                .appendDescription("**Username:** " + name)
                                .appendDescription("\n**Discord:** " + Objects.requireNonNull(msg.getGuild().getMemberById(discordId)).getAsMention())
                                .build())
                        .setActionRows(ActionRow.of(Button.link("https://generals.io/profiles/" + Utils.encodeURI(name), "Visit")))
                        .build()).queue();
    }

    @Command(name = {"addname"}, cat = "user", desc = "Register generals.io username")
    public static void handleAddName(@NotNull Message msg, @Required String username) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

        if (Database.getGeneralsName(Objects.requireNonNull(msg.getMember()).getIdLong()) != null) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You're already registered as **" + username +
                                                                "**. Ask a <@&" + GUILD_INFO.moderatorRole + "> " +
                                                                "to change your username.")).queue();
            return;
        }

        long l;
        if ((l = Database.getDiscordId(username)) != -1) {
            if (msg.getGuild().retrieveBanById(l).complete() != null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "**" + username + "** is banned")).queue();
                return;
            }
            Member m = msg.getGuild().getMemberById(l);
            if (m != null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "**" + username + "** is already registered " +
                                                                    "to " + m.getAsMention())).queue();
                return;
            }
        }

        Database.addDiscordGenerals(Objects.requireNonNull(msg.getMember()).getIdLong(), username);
        msg.getChannel().sendMessageEmbeds(new EmbedBuilder().setTitle("Username Added").setColor(Constants.Colors.SUCCESS)
                .setDescription(msg.getMember().getAsMention() + " is now generals.io user **" + username + "**").build()).queue();
    }

    @Command(name = {"nickwupey"}, cat = "user", desc = "Bully Wuped", perms = Constants.Perms.MOD)
    public static void handleNickWupey(@NotNull Message msg) {
        if (msg.getAuthor().getIdLong() == 175430325755838464L) {
            Utils.error(msg, "You can't bully yourself!");
            return;
        }
        nickWupey = !nickWupey;
        msg.getChannel().sendMessage("Switched auto-nick to " + nickWupey).queue();
        if (nickWupey) {
            msg.getGuild().modifyNickname(Objects.requireNonNull(msg.getGuild().retrieveMemberById(175430325755838464L).complete()), "Wupey").queue();
        }
    }

    @Command(name = {"setname"}, cat = "user", desc = "Change generals.io username of user", perms =
            Constants.Perms.MOD)
    public static void handleSetName(@NotNull Message msg, @Required Member member, @Required String username) {
        if (Database.noMatch(member.getIdLong(), username)) {
            Database.addDiscordGenerals(member.getIdLong(), username);
        } else {
            Database.updateDiscordGenerals(member.getIdLong(), member.getIdLong(), username);
        }
        msg.getChannel().sendMessage(new MessageBuilder()
                .setContent(member.getAsMention())
                .setEmbeds(
                        new EmbedBuilder().setTitle("Username Updated").setColor(Constants.Colors.SUCCESS)
                                .setDescription(member.getAsMention() + " is now generals.io user **" + username +
                                                "**").build()).build()).queue();
    }

}