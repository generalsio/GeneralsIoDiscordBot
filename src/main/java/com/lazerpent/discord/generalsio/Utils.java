package com.lazerpent.discord.generalsio;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Utils is a bunch of utility functions, mainly used for error messages or messages which are very simple
 */
public class Utils {
    /**
     * Prints a generic error message with a provided error message (which is put in the embed description)
     *
     * @param msg          Message object containing the user who tried this command
     * @param errorMessage The specific error message to print in the embed description
     */
    public static MessageEmbed error(@NotNull Message msg, @NotNull String errorMessage) {
        return new EmbedBuilder().setTitle("Error").setDescription(errorMessage)
                .setFooter(msg.getAuthor().getAsTag(), msg.getAuthor().getAvatarUrl())
                .setColor(Constants.Colors.ERROR) // Red
                .setTimestamp(Instant.now()).build();
    }

    /**
     * Prints a generic success message with a provided message (which is put in the embed description)
     *
     * @param msg     Message object containing the user who completed this command
     * @param message The specific message to print in the embed description
     */
    public static MessageEmbed complete(@NotNull Message msg, @NotNull String message) {
        return new EmbedBuilder().setTitle("Action Completed")
                .setDescription(message)
                .setFooter(msg.getAuthor().getAsTag(), msg.getAuthor().getAvatarUrl())
                .setColor(new Color(9, 191, 5)) // Green
                .setTimestamp(Instant.now()).build();
    }

    public static String encodeURI(String data) {
        try {
            return URLEncoder.encode(data, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        } catch (Exception err) {
            return data;
        }
    }
}
