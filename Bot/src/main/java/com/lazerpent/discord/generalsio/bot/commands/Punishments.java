package com.lazerpent.discord.generalsio.bot.commands;

import com.lazerpent.discord.generalsio.bot.Commands.Category;
import com.lazerpent.discord.generalsio.bot.Commands.Command;
import com.lazerpent.discord.generalsio.bot.Commands.CommandParameter;
import com.lazerpent.discord.generalsio.bot.Constants;
import com.lazerpent.discord.generalsio.bot.Database;
import com.lazerpent.discord.generalsio.bot.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Punishment handler
 * This whole system requires moderator permission to use
 * <p>
 * Allows for moderators to add usernames to a "punishment" list, or a "disable" list. All usernames on the disabled
 * list
 * are also on the punishment list (since by disabling, punish should also be run).
 * <p>
 * A moderator can then pull up a list of the command to run to punish/disable the users. After running a command, the
 * moderator can select to clear the list and start over
 */
@Category(name = "Moderation")
public class Punishments {
    /**
     * Allows a moderator to add a list of usernames to the punishment list
     * <p>
     * Run as: punish username1 "username 2" username3 ...
     *
     * @param cmd  SlashCommandEvent object from moderator (permission determined prior to call)
     * @param names List of players delimited by spaces (or using quotes for multiple words)
     */
    @Command(name = "punish", desc = "Add user to the punishment list",
            perms = Constants.Perms.MOD)
    public static void handlePunish(@NotNull SlashCommandEvent cmd,
                                    @CommandParameter(name = "names",
                                            desc = "List of players delimited by spaces" +
                                                    " (or using angle brackets for multiple words)") String names) {
        Matcher match = Pattern.compile("(?<=<)[^<>]+(?=>)|[^<>\\s]+").matcher(names);
        Arrays.stream(match.results().map(MatchResult::group).toArray(String[]::new)).forEach(Database::addPunishment);
        Utils.replySuccess(cmd, "Added players to punishment list.");
    }

    /**
     * Allows a moderator to add a list of usernames to the disabled list
     * <p>
     * Run as: disable username1 "username 2" username3 ...
     *
     * @param cmd  Message object from moderator (permission determined prior to call)
     * @param names List of players delimited by spaces (or using quotes for multiple words)
     */
    @Command(name = "disable", desc = "Add user to the disable list",
            perms = Constants.Perms.MOD)
    public static void handleDisable(@NotNull SlashCommandEvent cmd,
                                             @CommandParameter(name = "names",
                                                     desc = "List of players delimited by spaces " +
                                                             "(or using angle brackets for multiple words)") String names) {
        Matcher match = Pattern.compile("(?<=<)[^<>]+(?=>)|[^<>\\s]+").matcher(names);
        Arrays.stream(match.results().map(MatchResult::group).toArray(String[]::new)).forEach(Database::addDisable);
        Utils.replySuccess(cmd, "Added players to disable list.");
    }


    /**
     * Returns a list of the commands to run in order to punish/disable users previously added
     * <p>
     * Run as: cmd
     *
     * @param cmd SlashCommandEvent object from moderator (permission determined prior to call)
     */
    @Command(name = "getpunishcommand", perms = Constants.Perms.MOD, desc = "Gets a list of commands to run to punish players")
    public static void handleGetCommands(@NotNull SlashCommandEvent cmd) {
        List<String> punish = new LinkedList<>(), disable = new LinkedList<>();
        Database.getPunishments(punish, disable);

        final EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("In-Game Player Punishments").setColor(Color.RED);
        String disableString = "";

        if (!disable.isEmpty()) {
            disableString = " \"" + String.join("\" \"", disable) + "\"";
            embedBuilder.addField("Disable", "```bash\nnode scripts/disable_player.js" + disableString + "```", false);
        }
        if (!punish.isEmpty()) {
            embedBuilder.addField("Punishment", "```bash\nnode scripts/fully_punish_player.js \"" +
                                                String.join("\" \"", punish) + "\"" + disableString + "```",
                    false);
        }
        MessageBuilder builder = new MessageBuilder();
        if (embedBuilder.getFields().isEmpty()) {
            embedBuilder.setDescription("There are no commands to run.");
        } else {
            builder.setActionRows(ActionRow.of(List.of(
                    Button.success("punish-finish", "Commands Run"),
                    Button.danger("punish-cancel", "Cancel"))));
        }
        builder.setEmbeds(embedBuilder.build());

        cmd.reply(builder.build()).queue();
    }

    /**
     * Button click handler for punishment buttons. All buttons used start with "punish-", which is validated prior
     * to calling this method. Handles two options, "Commands Run" (clear list) or "Cancel" (do nothing)
     *
     * @param event ButtonClickEvent directly from JDA
     */
    public static void onButtonClick(ButtonClickEvent event) {
        if (2 > Constants.Perms.get(Objects.requireNonNull(event.getMember()))) {
            return;
        }
        if (event.getComponentId().endsWith("cancel")) {
            event.getInteraction().editMessageEmbeds(
                            new EmbedBuilder(Objects.requireNonNull(event.getMessage()).getEmbeds().get(0))
                                    .setTitle(":x: In-Game Player Punishments").build())
                    .setActionRows(Collections.emptyList()).queue();
            return;
        }
        event.getInteraction().editMessageEmbeds(
                        new EmbedBuilder(Objects.requireNonNull(event.getMessage()).getEmbeds().get(0))
                                .setTitle(":white_check_mark: In-Game Player Punishments").build())
                .setActionRows(Collections.emptyList()).queue();
        Database.clearPunishments();
    }
}
