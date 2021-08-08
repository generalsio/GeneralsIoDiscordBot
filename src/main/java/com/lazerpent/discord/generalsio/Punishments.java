package com.lazerpent.discord.generalsio;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Punishment handler
 * This whole system requires moderator permission to use
 * <p>
 * Allows for moderators to add usernames to a "punishment" list, or a "disable" list. All usernames on the disable list
 * are also on the punishment list (since by disabling, punish should also be ran).
 * <p>
 * A moderator can then pull up a list of the command to run to punish/disable the users. After running a command, the
 * moderator can select to clear the list and start over
 */
public class Punishments {
    /**
     * Allows a moderator to add a list of usernames to the punishment list
     * <p>
     * Run as: punish username1 "username 2" username3 ...
     *
     * @param msg  Message object from moderator (permission determined prior to call)
     * @param args List of players delimited by spaces (or using quotes for multiple words)
     */
    public static void punish(@NotNull Message msg, @NotNull String[] args) {
        List<String> names = validateInput(msg, args);
        if (names == null) return;

        names.forEach(Database::addPunishment);
        msg.getChannel().sendMessageEmbeds(Utils.success(msg, "Added players to punishment list.")).queue();
    }

    /**
     * Allows a moderator to add a list of usernames to the disable list
     * <p>
     * Run as: disable username1 "username 2" username3 ...
     *
     * @param msg  Message object from moderator (permission determined prior to call)
     * @param args List of players delimited by spaces (or using quotes for multiple words)
     */
    public static void disable(@NotNull Message msg, @NotNull String[] args) {
        List<String> names = validateInput(msg, args);
        if (names == null) return;

        names.forEach(Database::addDisable);
        msg.getChannel().sendMessageEmbeds(Utils.success(msg, "Added players to disable list.")).queue();
    }


    /**
     * Returns a list of the commands to run in order to punish/disable users previously added
     * <p>
     * Run as: cmd
     *
     * @param msg Message object from moderator (permission determined prior to call)
     */
    public static void getCommands(@NotNull Message msg) {
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

        msg.getChannel().sendMessage(builder.build()).queue();
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

    /**
     * Validates the moderator's input of usernames by comparing them against max length (18)
     *
     * @param msg  Message object
     * @param args Username list delimited by spaces or quotes
     * @return List of each username
     */
    private static List<String> validateInput(@NotNull Message msg, @NotNull String[] args) {
        if (args.length == 1) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must provide at least one username.")).queue();
            return null;
        }
        String argString = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Matcher match = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(argString);
        List<String> names = match.results().map(MatchResult::group).map(s -> s.replaceAll("\"", "").trim())
                .collect(Collectors.toCollection(ArrayList::new));

        if (names.stream().anyMatch(n -> n.length() > 18)) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Provided username is too long.")).queue();
            return null;
        }
        return names;
    }
}
