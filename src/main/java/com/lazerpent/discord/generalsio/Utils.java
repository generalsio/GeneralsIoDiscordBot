package com.lazerpent.discord.generalsio;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Instant;
import java.util.Objects;


/**
 * Utils is a bunch of utility functions, mainly used for error messages or messages which are very simple
 */
public class Utils {
    public static class Perms { 
        public final static int NONE = 0;
        public final static int USER = 1;
        public final static int MOD = 2;

        private Perms(){}

        /** @return whether said user has a given permission. */
        public static int get(@NotNull Member mbr) {
            if (mbr.hasPermission(Permission.MESSAGE_MANAGE) || mbr.getIdLong() == 356517795791503393L) {
                return Perms.MOD;
            } else if (Database.getGeneralsName(mbr.getIdLong()) != null) {
                return Perms.USER;
            } else {
                return Perms.NONE;
            }
        }
    }

    public static enum Mode {
        FFA, M1v1, M2v2, Custom;

        public static Mode fromString(String s) {
            switch (s) {
                case "ffa": return Mode.FFA;
                case "1v1": return Mode.M1v1;
                case "2v2": return Mode.M2v2;
                case "custom": return Mode.Custom;
            }
            return null;
        }

        public String toString() {
            switch (this) {
                case FFA: return "ffa";
                case M1v1: return "1v1";
                case M2v2: return "2v2";
                case Custom: return "custom";
            }
            return "";
        }
    }

    /**
     * Prints a generic error message with a provided error message (which is put in the embed description)
     *
     * @param msg Message object containing the user who tried this command
     * @param errorMessage The specific error message to print in the embed description
     */
    public static MessageEmbed error(@NotNull Message msg, @NotNull String errorMessage) {
        return new EmbedBuilder().setTitle("Error Occurred").setDescription(errorMessage)
                .setFooter("Command attempted by " + msg.getAuthor().getAsTag())
                .setColor(new Color(220, 21, 21)) // Red
                .setTimestamp(Instant.now()).build();
    }
    /**
     * Prints a generic success message with a provided message (which is put in the embed description)
     *
     * @param msg Message object containing the user who completed this command
     * @param message The specific message to print in the embed description
     */
    public static MessageEmbed complete(@NotNull Message msg, @NotNull String message) {
        return new EmbedBuilder().setTitle("Action Completed")
                .setDescription(message)
                .setFooter("Command run by " + msg.getAuthor().getAsTag())
                .setColor(new Color(9, 191, 5)) // Green
                .setTimestamp(Instant.now()).build();
    }
}
