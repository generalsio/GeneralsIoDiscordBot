package com.lazerpent.discord.generalsio;

import com.lazerpent.discord.generalsio.Constants;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.internal.utils.tuple.Pair;

import java.awt.*;
import java.util.HashMap;
import java.util.Objects;

public class RoleHandler {
    private static final HashMap<Pair<Long, Long>, Long> rateLimit = new HashMap<>();

    static void reactionAdd(GuildMessageReactionAddEvent event) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(event.getGuild().getIdLong());

        if (!event.getUser().isBot() && event.getMessageIdLong() == GUILD_INFO.rolesMessage) {
            event.getReaction().removeReaction(event.getUser()).queue();

            if (Database.getGeneralsName(event.getUser().getIdLong()) == null) {
                event.getUser().openPrivateChannel().queue(c -> c.sendMessage(
                        new EmbedBuilder()
                                .setTitle("Unknown generals.io Username")
                                .setDescription("To add roles, you must register your generals.io username." +
                                        " \nUse ``!addname username`` to register.\nExample: ```!addname MyName321```")
                                .setColor(new Color(123, 11, 11)).build()).queue());
                return;
            }            
            // Get which role corresponds to the reaction
            Utils.Mode mode = Utils.Mode.fromString(event.getReactionEmote().getName());
            Role r = event.getGuild().getRoleById(GUILD_INFO.roles.get(mode));

            // Check the rate limit
            Long value = rateLimit.get(Pair.of(event.getUser().getIdLong(), r.getIdLong()));
            long time = System.currentTimeMillis();
            if (value != null && value > time) {
                return;
            }
            rateLimit.put(Pair.of(event.getUser().getIdLong(), r.getIdLong()), time + 3000); // 3 second delay


            // If they already have the role, remove it. If they did not have it, add it
            if (event.getMember().getRoles().contains(r)) {
                event.getGuild().removeRoleFromMember(event.getMember(), r).queue();

                event.getUser().openPrivateChannel().queue(c -> c.sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("Role Removed")
                        .setColor(Color.RED)
                        .setDescription("You have been removed from the " + r.getName() + " role. ").build()).queue());
            } else {
                event.getGuild().addRoleToMember(event.getMember(), r).queue();

                event.getUser().openPrivateChannel().queue(c -> c.sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("Role Added")
                        .setColor(Color.GREEN)
                        .setDescription("You have been added to the " + r.getName() + " role. ").build()).queue());
            }
        }
    }

    static void setup(Message msg) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

        StringBuilder sb = new StringBuilder();
        for (Utils.Mode value: Utils.Mode.values()) {
            sb.append(msg.getGuild().getEmotesByName(value.toString(), false).get(0).getAsMention() + " - <@&" + GUILD_INFO.roles.get(value) + ">\n");
        }

        Message m = msg.getChannel().sendMessageEmbeds(new EmbedBuilder().setTitle("Generals.io Role Selector")
                .setDescription("To select one or more roles, simply react with the role you would like to add or remove. \n\nEach role has a specific channel dedicated to that game mode. " +
                        "You can also ping all players with the role using **!ping** in that game mode's channel.\n\nWant the <@&788259902254088192> role? DM or ping <@356517795791503393>. The tester role is pinged when <@356517795791503393> is testing a beta version on the BOT server.\n\n" +
                        sb.toString()
                ).setFooter("You must wait 3 seconds between adding a role and removing it.")
                .setThumbnail(msg.getGuild().getIconUrl()).build()).complete();

        for (Utils.Mode value: Utils.Mode.values()) {
            m.addReaction(msg.getGuild().getEmotesByName(value.toString(), false).get(0)).queue();
        }
    }

    private static final HashMap<Long, Long> lastPinged = new HashMap<>();

    static void ping(Message msg) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

        Utils.Mode mode = GUILD_INFO.channelToMode(msg.getChannel().getIdLong());
        if (mode == null) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "**!ping** can only be used in a game mode channel")).queue();
            return;
        }

        Role role = msg.getGuild().getRoleById(GUILD_INFO.roles.get(mode));

        Long last = lastPinged.get(role.getIdLong());
        if (last != null && last > System.currentTimeMillis()) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Each role can only be pinged once every 10 minutes")).queue();
            return;
        }
        msg.getChannel().sendMessage(role.getAsMention() + " (Pinged By " + Objects.requireNonNull(msg.getMember()).getAsMention() + ")").queue();
        lastPinged.put(role.getIdLong(), System.currentTimeMillis() + 1000 * 60 * 10);
    }
}
