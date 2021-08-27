package com.lazerpent.discord.generalsio.commands;

import com.lazerpent.discord.generalsio.Commands;
import com.lazerpent.discord.generalsio.Constants;
import com.lazerpent.discord.generalsio.Database;
import com.lazerpent.discord.generalsio.Utils;
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

    @Commands.Command(name = {"profile", "user"}, args = {"username? | @mention?"}, cat = "user", perms =
            Constants.Perms.USER, desc = "Show username of user, or vice versa")
    public static void handleUser(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
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

    @Commands.Command(name = {"addname"}, args = {"username"}, cat = "user", desc = "Register generals.io username")
    public static void handleAddName(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

        if (cmd.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Missing username")).queue();
            return;
        }
        String username;
        if ((username = Database.getGeneralsName(Objects.requireNonNull(msg.getMember()).getIdLong())) != null) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You're already registered as **" + username +
                                                                "**. Ask a <@&" + GUILD_INFO.moderatorRole + "> " +
                                                                "to change your username.")).queue();
            return;
        }

        //Get the generals.io username, skipping the messaged name
        username = Arrays.stream(cmd).skip(1).collect(Collectors.joining(" "));

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

    @Commands.Command(name = {"nickwupey"}, cat = "user", desc = "Bully Wuped", perms = Constants.Perms.MOD)
    public static void handleNickWupey(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
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

    @Commands.Command(name = {"setname"}, cat = "user", args = {"@mention", "username"}, desc = "Change generals.io " +
                                                                                                "username of user",
            perms
                    = Constants.Perms.MOD)
    public static void handleSetName(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        if (msg.getMentionedMembers().size() == 0) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must mention the member to update")).queue();
            return;
        }

        if (cmd.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must provide the generals.io username to " +
                                                                "update")).queue();
            return;
        }

        Member m = msg.getMentionedMembers().get(0);
        String tmp = m.getEffectiveName();
        int a = tmp.length();
        tmp = tmp.replaceAll(" ", "");
        a -= tmp.length();
        String name = Arrays.stream(cmd).skip(2 + a).collect(Collectors.joining(" "));

        if (Database.noMatch(m.getIdLong(), name)) {
            Database.addDiscordGenerals(m.getIdLong(), name);
        } else {
            Database.updateDiscordGenerals(m.getIdLong(), m.getIdLong(), name);
        }
        msg.getChannel().sendMessage(new MessageBuilder()
                .setContent(m.getAsMention())
                .setEmbeds(
                        new EmbedBuilder().setTitle("Username Updated").setColor(Constants.Colors.SUCCESS)
                                .setDescription(m.getAsMention() + " is now generals.io user **" + name + "**").build()).build()).queue();
    }

}