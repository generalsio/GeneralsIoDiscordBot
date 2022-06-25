package com.lazerpent.discord.generalsio.bot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.requests.restaction.order.CategoryOrderAction;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ApplicationHandler {
    public static void cancelapply(SlashCommandEvent event) {

        Category category = null;
        final long channelId = event.getChannel().getIdLong();

        outer:
        for (Category cat : Objects.requireNonNull(event.getGuild()).getCategories()) {
            for (TextChannel channel : cat.getTextChannels()) {
                if (channelId == channel.getIdLong()) {
                    category = cat;
                    break outer;
                }
            }
        }
        // Make sure it is an application, and it is their channel
        if (category == null || !(category.getIdLong() == Constants.GUILD_INFO.get(event.getGuild().getIdLong()).applicationCategory)
                                && Objects.equals(event.getTextChannel().getTopic(),
                Objects.requireNonNull(event.getMember()).getId()) || event.getChannel().getName().startsWith("closed"
        )) {
            event.reply("This command can only be used in your open application channel").setEphemeral(true).queue();
            return;
        }


        CategoryOrderAction action = category.modifyTextChannelPositions();
        List<GuildChannel> currentOrder = action.getCurrentOrder();
        for (int i = 0; i < currentOrder.size(); i++) {
            GuildChannel guildChannel = currentOrder.get(i);
            if (guildChannel.getIdLong() == event.getChannel().getIdLong()) {
                if (i != currentOrder.size() - 1) {
                    action.selectPosition(i).moveDown(currentOrder.size() - 1 - i).queue();
                }
                guildChannel.getManager().setName("closed-" + guildChannel.getName()).queue();
                guildChannel.getManager().removePermissionOverride(Objects.requireNonNull(event.getMember()))
                        .queue();
                event.reply("Application Closed").queue();
                break;
            }
        }
    }

    public static void apply(SlashCommandEvent event) {
        apply(Objects.requireNonNull(event.getGuild()), event.getMember());
        event.reply("Application Started").setEphemeral(true).queue();
    }

    private static void apply(Guild g, Member m) {
        Category category = g.getCategoryById(Constants.GUILD_INFO.get(g.getIdLong()).applicationCategory);

        // Get all the application channels
        List<TextChannel> channels =
                Objects.requireNonNull(category).getTextChannels();

        // Check if they already have an application channel
        Optional<TextChannel> channel =
                channels.stream().filter(c -> Objects.equals(c.getTopic(), m.getId())).findFirst();

        // If they already have a channel, ping them there, and end the command
        if (channel.isPresent()) {
            TextChannel c = channel.get();
            if (c.getName().startsWith("closed")) {
                // Re-open application, then ping
                c.getManager().putPermissionOverride(m,
                        Collections.singleton(Permission.VIEW_CHANNEL),
                        null).queue();
                c.getManager().setName(c.getName().substring(7)).queue();
                c.sendMessage("Application Re-opened").queue();
            }
            channel.get().sendMessage(m.getAsMention()).queue(msg -> msg.delete().queue());
            return;
        }

        // Create a channel, then send the message
        TextChannel c = category.createTextChannel(m.getEffectiveName() + "-application").complete();

        c.getManager().putPermissionOverride(m, Collections.singleton(Permission.VIEW_CHANNEL),
                null).queue();
        c.getManager().setTopic(m.getId()).queue();

        c.sendMessage(new MessageBuilder().append(m).append(" has been apart of this guild for ")
                .append(Utils.formatNumber(ChronoUnit.DAYS.between(m.getTimeJoined().toInstant(), Instant.now())))
                .append(" days. ").append(m.getAsMention()).append("'s Generals.io username is ").append(Database.getGeneralsName(m.getIdLong())).setEmbeds(
                        new EmbedBuilder().setTitle("Generals.io Moderation Application")
                                .setColor(Color.BLUE)
                                .setDescription("""
                                        Thank you for your interest in applying to the generals.io Moderation Team! We are looking for 1 - 2 amazing applicants to join us.
                                        We will take into account how long you have been in the generals.io community (both in-game and discord), how active you have been in our channels and other similar information.
                                        **Note: We do require that all applicants be at least 18 years of age.**
                                        """)
                                .addField("Application Process",
                                        """
                                                First, please answer the following prompts / questions, including as much detail as possible:
                                                ```
                                                1. Why do you want to be apart of the Generals.io Moderation Team?
                                                2. What can you bring (and have you brought) to generals.io?
                                                3. Why would you be a good fit for this role?
                                                4. What experience do you have moderating other servers (if any)?
                                                5. What is your time zone?
                                                ```
                                                *Note: You may address more than just above*
                                                There is no length restriction, make your answers as long or short as you need.

                                                After you answer these questions, just wait. Once we have reviewed your application, we will reach out to you.
                                                """, false)
                                .addField("Application Expectations", """
                                        Please complete the above question, truthfully and honestly, with as much detail as possible.
                                        Please do not ping any current staff members in this channel.
                                        Do not inquire into the status of your application; someone will reach out to you when your application has been reviewed.
                                        """, false)
                                .addField("Cancel your Application", "To cancel your application, simply do " +
                                                                     "**/cancelapply** in this channel. You may " +
                                                                     "reopen the application at any time with " +
                                                                     "**/apply**", true)
                                .setFooter("You may edit your response, however we will not necessarily re-read it or" +
                                           " see that it has been edited.")
                                .build()).build()).queue();
    }
}
