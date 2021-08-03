package com.lazerpent.discord.generalsio;

import com.lazerpent.discord.generalsio.Database;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.restaction.order.CategoryOrderAction;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ApplicationHandler {
    static boolean cancelapply(Message msg) {
        msg.delete().queue();

        // Make sure it is an application, and it is their channel
        if (!(Objects.requireNonNull(msg.getCategory()).getIdLong() == 794725980300247080L
                && Objects.equals(msg.getTextChannel().getTopic(), msg.getAuthor().getId()))) {
            return true;
        }

        // Check if already closed
        if (msg.getChannel().getName().startsWith("closed")) {
            return true;
        }

        CategoryOrderAction action = msg.getCategory().modifyTextChannelPositions();
        List<GuildChannel> currentOrder = action.getCurrentOrder();
        for (int i = 0; i < currentOrder.size(); i++) {
            GuildChannel guildChannel = currentOrder.get(i);
            if (guildChannel.getIdLong() == msg.getChannel().getIdLong()) {
                if (i != currentOrder.size() - 1) {
                    action.selectPosition(i).moveDown(currentOrder.size() - 1).queue();
                }
                guildChannel.getManager().setName("closed-" + guildChannel.getName()).queue();
                guildChannel.getManager().removePermissionOverride(Objects.requireNonNull(msg.getMember())).queue();
                msg.getChannel().sendMessage("Application Closed").queue();
                break;
            }
        }
        return true;
    }

    static boolean apply(Message msg) {
        msg.delete().queue();

        Category category = msg.getGuild().getCategoryById(794725980300247080L);

        // Get all the application channels
        List<TextChannel> channels =
                Objects.requireNonNull(category).getTextChannels();

        // Check if they already have an application channel
        Optional<TextChannel> channel =
                channels.stream().filter(c -> Objects.equals(c.getTopic(), msg.getAuthor().getId())).findFirst();

        // If they already have a channel, ping them there, and end the command
        if (channel.isPresent()) {
            TextChannel c = channel.get();
            if (c.getName().startsWith("closed")) {
                // Re-open application, then ping
                c.getManager().putPermissionOverride(
                        Objects.requireNonNull(msg.getMember()),
                        Collections.singleton(Permission.VIEW_CHANNEL),
                        null).queue();
                c.getManager().setName(c.getName().substring(7)).queue();
                c.sendMessage("Application Re-opened").queue();
            }
            channel.get().sendMessage(
                    Objects.requireNonNull(msg.getMember()).getAsMention()).queue(m -> m.delete().queue());
            return true;
        }

        // Create a channel, then send the message
        TextChannel c = category.createTextChannel(
                Objects.requireNonNull(msg.getMember()).getEffectiveName() + "-application").complete();

        c.getManager().putPermissionOverride(msg.getMember(), Collections.singleton(Permission.VIEW_CHANNEL), null).queue();
        c.getManager().setTopic(msg.getAuthor().getId()).queue();
        c.sendMessage(new MessageBuilder().append(msg.getMember()).append(" has been apart of this guild for ")
                .append(ChronoUnit.DAYS.between(msg.getMember().getTimeJoined().toInstant(), Instant.now()))
                .append(" days. ").append(msg.getMember()).append("'s Generals.io username is ").append(Database.getGeneralsName(msg.getAuthor().getIdLong())).setEmbed(
                        new EmbedBuilder().setTitle("Generals.io Moderation Application")
                                .setColor(Color.BLUE)
                                .setDescription("Thank you for your interest in applying to the generals.io Moderation Team. We are looking for 1 - 2 amazing applicants to join us. \n" +
                                        "We will take into account how long you have been in the generals.io community (both in-game and discord), how active you have been, and other similar information. " +
                                        "**Note: We do require that all applicants be at least 18 years of age.**")
                                .addField("Application Process",
                                        "First, please answer the following prompts / questions, including as much detail as possible: " +
                                                "```1. Why do you want to be apart of the Generals.io Moderation Team?" +
                                                "\n2. What can you bring (and have you brought) to generals.io?" +
                                                "\n3. Why would you be a good fit for this role?" +
                                                "\n4. What is your time zone?```" +
                                                "There is no length restriction, make your answers as long or short as you need." +
                                                "\n" +
                                                "\nAfter you answer these questions, just wait. Once we have reviewed your application, we will reach out to you.", false)
                                .addField("Application Expectations", "Please complete the above question, truthfully and honestly, with as much detail as possible." +
                                        "\nPlease do not ping any current staff members in this channel." +
                                        "\nDo not inquire into the status of your application; someone will reach out to you when your application has been reviewed.", false)
                                .addField("Cancel your Application", "To cancel your application, simply do **!cancelapply** in this channel. You may reopen the application at any time with **!apply**", true)
                                .setFooter("You may edit your response, however we will not necessarily re-read it or see that it has been edited.")
                                .build()).build()).queue();
        return true;
    }
}
