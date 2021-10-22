package com.lazerpent.discord.generalsio.bot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.internal.utils.tuple.Pair;

import java.util.HashMap;
import java.util.Objects;

public class RoleHandler {
    private static final HashMap<Pair<Long, Long>, Long> rateLimit = new HashMap<>();

    private static final HashMap<Constants.Mode, Long> lastPinged = new HashMap<>();

    static void reactionAdd(GuildMessageReactionAddEvent event) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(event.getGuild().getIdLong());

        if (!event.getUser().isBot() && event.getMessageIdLong() == GUILD_INFO.rolesMessage) {
            event.getReaction().removeReaction(event.getUser()).queue();

            if (Database.getGeneralsName(event.getUser().getIdLong()) == null) {
                event.getUser().openPrivateChannel().queue(c -> c.sendMessageEmbeds(
                        new EmbedBuilder()
                                .setTitle("Unknown generals.io Username")
                                .setDescription("""
                                        To add roles, you must register your generals.io username.\s
                                        Use ``/addname username`` to register.
                                        Example: ```/addname MyName321```""")
                                .setColor(Constants.Colors.ERROR).build()).queue());
                return;
            }
            // Get which role corresponds to the reaction
            Constants.Mode mode = Constants.Mode.fromString(event.getReactionEmote().getName());
            Role r;
            if (mode == null && event.getReactionEmote().getName().equals("\uD83C\uDFC6")) {
                r = event.getGuild().getRoleById(GUILD_INFO.eventRole);
            } else {
                final Long id = GUILD_INFO.roles.get(mode);
                r = event.getGuild().getRoleById(id);
            }
            // Check the rate limit
            Long value = rateLimit.get(Pair.of(event.getUser().getIdLong(), Objects.requireNonNull(r).getIdLong()));
            long time = System.currentTimeMillis();
            if (value != null && value > time) {
                return;
            }
            rateLimit.put(Pair.of(event.getUser().getIdLong(), r.getIdLong()), time + 3000); // 3-second delay

            Role finalR = r;
            // If they already have the role, remove it. If they did not have it, add it
            if (event.getMember().getRoles().contains(r)) {
                event.getGuild().removeRoleFromMember(event.getMember(), r).queue();

                event.getUser().openPrivateChannel().queue(c -> c.sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("Role Removed")
                        .setColor(Constants.Colors.ERROR)
                        .setDescription("You have been removed from the " + finalR.getName() + " role").build()).queue());
            } else {
                event.getGuild().addRoleToMember(event.getMember(), r).queue();

                event.getUser().openPrivateChannel().queue(c -> c.sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("Role Added")
                        .setColor(Constants.Colors.SUCCESS)
                        .setDescription("You have been added to the " + finalR.getName() + " role").build()).queue());
            }
        }
    }

    public static boolean tryPing(Constants.Mode mode) {
        Long last = lastPinged.get(mode);
        if (last != null && System.currentTimeMillis() < last + 1000 * 60 * 10) {
            return false;
        }

        lastPinged.put(mode, System.currentTimeMillis());

        return true;
    }
}
