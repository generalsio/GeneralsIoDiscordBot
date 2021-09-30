package com.lazerpent.discord.generalsio.bot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Utils is a bunch of utility functions, mainly used for error messages or messages which are very simple
 */
public class Utils {
    /**
     * Creates a generic error message with a provided error message (which is put in the embed description)
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
     * Creates a generic error message for slash commands with a provided error message (which is put in the embed description)
     *
     * @param cmd          SlashCommandEvent object containing the user who tried this command
     * @param errorMessage The specific error message to print in the embed description
     */
    public static MessageEmbed error(@NotNull SlashCommandEvent cmd, @NotNull String errorMessage) {
        return new EmbedBuilder().setTitle("Error").setDescription(errorMessage)
                .setFooter(cmd.getMember().getUser().getAsTag(), cmd.getMember().getUser().getAvatarUrl())
                .setColor(Constants.Colors.ERROR) // Red
                .setTimestamp(Instant.now()).build();
    }

    /**
     * Creates a generic error message with a provided error message (which is put in the embed description)
     *
     * @param msg Message object containing the user who tried this command
     */
    public static MessageEmbed error(@NotNull Message msg, @NotNull String title, @NotNull String description) {
        return new EmbedBuilder().setTitle(title).setDescription(description)
                .setFooter(msg.getAuthor().getAsTag(), msg.getAuthor().getAvatarUrl())
                .setColor(Constants.Colors.ERROR) // Red
                .setTimestamp(Instant.now()).build();
    }

    /**
     * Creates a generic error message for slash commands with a provided error message (which is put in the embed description)
     *
     * @param cmd SlashCommandEvent object containing the user who tried this command
     */
    public static MessageEmbed error(@NotNull SlashCommandEvent cmd, @NotNull String title, @NotNull String description) {
        return new EmbedBuilder().setTitle(title).setDescription(description)
                .setFooter(cmd.getMember().getUser().getAsTag(), cmd.getMember().getUser().getAvatarUrl())
                .setColor(Constants.Colors.ERROR) // Red
                .setTimestamp(Instant.now()).build();
    }


    /**
     * Creates a generic success message with a provided message (which is put in the embed description)
     *
     * @param msg Message object containing the user who completed this command
     */
    public static MessageEmbed success(@NotNull Message msg, @NotNull String description) {
        return new EmbedBuilder().setTitle("Success")
                .setDescription(description)
                .setFooter(msg.getAuthor().getAsTag(), msg.getAuthor().getAvatarUrl())
                .setColor(Constants.Colors.SUCCESS) // Green
                .setTimestamp(Instant.now()).build();
    }

    /**
     * Creates a generic success message for slash commands with a provided message (which is put in the embed description)
     *
     * @param cmd SlashCommandEvent object containing the user who completed this command
     */
    public static MessageEmbed success(@NotNull SlashCommandEvent cmd, @NotNull String description) {
        return new EmbedBuilder().setTitle("Success")
                .setDescription(description)
                .setFooter(cmd.getMember().getUser().getAsTag(), cmd.getMember().getUser().getAvatarUrl())
                .setColor(Constants.Colors.SUCCESS) // Green
                .setTimestamp(Instant.now()).build();
    }

    public static void replyError(@NotNull SlashCommandEvent cmd, String description) {
        cmd.replyEmbeds(error(cmd, description)).queue();
    }

    public static void replySuccess(@NotNull SlashCommandEvent cmd, String description) {
        cmd.replyEmbeds(success(cmd, description)).queue();
    }

    public static String encodeURI(String data) {
        return URLEncoder.encode(data, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    }
}
