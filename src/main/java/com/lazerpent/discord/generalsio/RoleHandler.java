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
                                .setColor(Constants.Colors.ERROR).build()).queue());
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
                        .setColor(Constants.Colors.ERROR)
                        .setDescription("You have been removed from the " + r.getName() + " role").build()).queue());
            } else {
                event.getGuild().addRoleToMember(event.getMember(), r).queue();

                event.getUser().openPrivateChannel().queue(c -> c.sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("Role Added")
                        .setColor(Constants.Colors.SUCCESS)
                        .setDescription("You have been added to the " + r.getName() + " role").build()).queue());
            }
        }
    }

    private static final HashMap<Utils.Mode, Long> lastPinged = new HashMap<>();

    public static boolean tryPing(Utils.Mode mode) {
        Long last = lastPinged.get(mode);
        if (last != null && System.currentTimeMillis() < last + 1000 * 60 * 10) {
            return false;
        }

        lastPinged.put(mode, System.currentTimeMillis());

        return true;
    }
}
