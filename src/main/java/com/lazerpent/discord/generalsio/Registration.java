package com.lazerpent.discord.generalsio;

import com.lazerpent.discord.generalsio.Database;
import com.lazerpent.discord.generalsio.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Registration {
    static void addName(Message msg, String[] command) {
        if (command.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must include your generals.io username.")).queue();
            return;
        }
        String username;
        if ((username = Database.getGeneralsName(Objects.requireNonNull(msg.getMember()).getIdLong())) != null) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You are already registered as " + username + ".")).queue();
            return;
        }

        //Get the generals.io username, skipping the messaged name
        username = Arrays.stream(command).skip(1).collect(Collectors.joining(" "));


        long l;
        if ((l = Database.getDiscordId(username)) != -1) {
            if (msg.getGuild().retrieveBanById(l).complete() != null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, username + " is currently banned from this guild.")).queue();
                return;
            }
            Member m = msg.getGuild().getMemberById(l);
            if (m != null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, username + " is already registered to " + m.getAsMention() + ".")).queue();
                return;
            }
        }

        Database.addDiscordGenerals(Objects.requireNonNull(msg.getMember()).getIdLong(), username);
        msg.getChannel().sendMessageEmbeds(new EmbedBuilder().setTitle("Registered").setColor(Color.GREEN.darker()).setDescription(msg.getMember().getAsMention() + " Saved you as generals.io user " + username + ".").build()).queue();
    }

    static void lookup(Message msg, String[] command) {
        long discordId;
        String name;
        if (command.length == 1) {
            discordId = msg.getAuthor().getIdLong();
            name = Database.getGeneralsName(discordId);
            if (name == null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, Objects.requireNonNull(msg.getMember()).getAsMention() + " has not registered their generals.io user.")).queue();
                return;
            }
        } else {
            List<Member> members = msg.getMentionedMembers();
            if (members.size() == 0) {
                name = Arrays.stream(command).skip(1).collect(Collectors.joining(" "));
                discordId = Database.getDiscordId(name);
                if (discordId == -1) {
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg, name + " is not registered to any discord user.")).queue();
                    return;
                }
            } else if (members.size() != 1) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You can only lookup one profile at a time.")).queue();
                return;
            } else {
                discordId = members.get(0).getIdLong();
                name = Database.getGeneralsName(discordId);
                if (name == null) {
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg, members.get(0).getAsMention() + " has not registered their generals.io user.")).queue();
                    return;
                }
            }
        }
        msg.getChannel().sendMessageEmbeds(new EmbedBuilder().setTitle("User Profile").setColor(new Color(138, 43, 226))
                .setDescription(name + " is registered to " + Objects.requireNonNull(msg.getGuild().getMemberById(discordId)).getAsMention()).build()).queue();
    }

    static void set(Message msg, String[] command) {
        if (msg.getMentionedMembers().size() == 0) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must mention the member to update.")).queue();
            return;
        }

        if (command.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must provide the generals.io username to update.")).queue();
            return;
        }

        Member m = msg.getMentionedMembers().get(0);
        String tmp = m.getEffectiveName();
        int a = tmp.length();
        tmp = tmp.replaceAll(" ", "");
        a -= tmp.length();
        String name = Arrays.stream(command).skip(2 + a).collect(Collectors.joining(" "));

        if (Database.noMatch(m.getIdLong(), name)) {
            Database.addDiscordGenerals(m.getIdLong(), name);
        } else {
            Database.updateDiscordGenerals(m.getIdLong(), m.getIdLong(), name);
        }
        msg.getChannel().sendMessageEmbeds(new EmbedBuilder().setTitle("User Update").setColor(new Color(15, 243, 235))
                .setDescription(name + " is updated to " + Objects.requireNonNull(m.getAsMention())).build()).queue();
    }
}
