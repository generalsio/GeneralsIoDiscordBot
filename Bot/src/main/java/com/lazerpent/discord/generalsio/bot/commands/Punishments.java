package com.lazerpent.discord.generalsio.bot.commands;

import com.lazerpent.discord.generalsio.bot.Commands.Category;
import com.lazerpent.discord.generalsio.bot.Commands.Command;
import com.lazerpent.discord.generalsio.bot.Constants;
import com.lazerpent.discord.generalsio.bot.Database;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    public enum ReportState {
        PENDING,
        ACCEPTED,
        CLOSED,
        NEEDS_EDIT,
        INVALID,
    }

    private static Message createBotReportMessage(Message originalReport) {
        return new MessageBuilder().setEmbeds(new EmbedBuilder()
                        .setTitle("Report by " + originalReport.getAuthor().getName())
                        .setDescription(originalReport.getContentRaw())
                        .build())
                .setActionRows(ActionRow.of(List.of(
                                Button.success("punish-punish-" + originalReport.getIdLong(),
                                        "Punish"),
                                Button.success("punish-disable-" + originalReport.getIdLong(),
                                        "Disable"))),
                        ActionRow.of(List.of(
                                Button.danger("punish-more-evidence-" + originalReport.getIdLong(),
                                        "More Evidence"),
                                Button.danger("punish-bad-format-" + originalReport.getIdLong(),
                                        "Bad Format"),
                                Button.danger("punish-reject-" + originalReport.getIdLong(),
                                        "Reject"))))
                .build();
    }

    /**
     * Takes a message with a report and sends it to the report record channel.
     *
     * @param message The message with the report.
     */
    public static void handleReport(Message message) {
        Guild g = message.getGuild();
        TextChannel recordChannel = g.getTextChannelById(Constants.GUILD_INFO.get(g.getIdLong()).reportRecordChannel);
        recordChannel.sendMessage(createBotReportMessage(message)).queue((e) -> {
            Database.updateReport(message.getIdLong(), e.getIdLong());
        });
    }

    /**
     * Takes a report message that was edited and updates the report record channel accordingly.
     * If the report message in question was marked as needing more evidence, then the bot will reply
     * to its message with the original report. Otherwise, the bot will just edit its original message
     * to include the updated report.
     * <p>
     * If the edited message was not previously recorded, the bot will handle it like a new report message.
     *
     * @param message The edited report message.
     */
    public static void handleReportEdit(Message message) {
        if (Database.isReportRecorded(message.getIdLong())) {
            ReportState curState = Database.getReportState(message.getIdLong());
            if (curState != ReportState.PENDING && curState != ReportState.NEEDS_EDIT) {
                return;
            }
            Guild g = message.getGuild();
            TextChannel recordChannel = g.getTextChannelById(Constants.GUILD_INFO.get(g.getIdLong()).reportRecordChannel);
            Message originalBotMessage = recordChannel.retrieveMessageById(Database.getBotMessage(message.getIdLong())).complete();
            if (curState == ReportState.PENDING) {
                originalBotMessage.editMessage(createBotReportMessage(message)).queue();
            } else {
                Database.setReportState(message.getIdLong(), ReportState.PENDING);
                originalBotMessage.reply(createBotReportMessage(message)).queue();
            }
        } else {
            handleReport(message);
        }
    }

    private static String escapeName(String name) {
        return name.replaceAll("'", "'\\\\''");
    }

    /**
     * Returns a list of the commands to run in order to punish/disable users previously added
     * <p>
     * Run as: cmd
     *
     * @param cmd SlashCommandEvent object from moderator (permission determined prior to call)
     */
    @Command(name = "getpunishcommand", perms = Constants.Perms.MOD, desc = "Gets a list of commands to run to punish" +
            " players")
    public static void handleGetCommands(@NotNull SlashCommandEvent cmd) {
        List<String> punish = new LinkedList<>(), disable = new LinkedList<>();
        Database.getPunishments(punish, disable);
        punish = punish.stream().map(Punishments::escapeName).collect(Collectors.toList());
        disable = disable.stream().map(Punishments::escapeName).collect(Collectors.toList());

        final EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("In-Game Player Punishments").setColor(Color.RED);
        String disableString = "";

        if (!disable.isEmpty()) {
            disableString = " '" + String.join("' '", disable) + "'";
            embedBuilder.addField("Disable", "```bash\nnode scripts/disable_player.js" + disableString + "```", false);
        }
        if (!punish.isEmpty()) {
            embedBuilder.addField("Punishment", "```bash\nnode scripts/fully_punish_player.js '" +
                            String.join("' '", punish) + "'" + disableString + "```",
                    false);
        }
        MessageBuilder builder = new MessageBuilder();
        if (embedBuilder.getFields().isEmpty()) {
            embedBuilder.setDescription("There are no commands to run.");
        } else {
            builder.setActionRows(ActionRow.of(List.of(
                    Button.success("punish-finish", "Mark commands as ran"))));
        }
        builder.setEmbeds(embedBuilder.build());

        cmd.reply(builder.build()).queue();
    }

    private static List<String> getNames(String messageRaw) {
        String[] words = messageRaw.split("[<>\\s]+");
        String profilePrefix = "https://generals.io/profiles/";
        List<String> names = new ArrayList<>();
        for (String word : words) {
            if (word.startsWith(profilePrefix)) {
                try {
                    names.add(URLDecoder.decode(word.substring(profilePrefix.length()), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace(); // TODO: handle this better
                }
            }
        }
        return names;
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
        Guild g = event.getGuild();
        TextChannel cheatersChannel = g.getTextChannelById(Constants.GUILD_INFO.get(g.getIdLong()).reportCheatersChannel);
        if (event.getComponentId().startsWith("punish-punish-")) {
            long originalID = Long.parseLong(event.getComponentId().substring("punish-punish-".length()));
            Message originalReport = cheatersChannel.retrieveMessageById(originalID).complete();
            List<String> names = getNames(originalReport.getContentRaw());
            for (String name : names) {
                Database.addPunishment(name);
            }
            originalReport.addReaction("\uD83D\uDC4D").queue();
            Database.setReportState(originalID, ReportState.ACCEPTED);
            event.getInteraction().editComponents(Collections.emptyList()).queue();
        } else if (event.getComponentId().startsWith("punish-disable-")) {
            long originalID = Long.parseLong(event.getComponentId().substring("punish-disable-".length()));
            Message originalReport = cheatersChannel.retrieveMessageById(originalID).complete();
            List<String> names = getNames(originalReport.getContentRaw());
            for (String name : names) {
                Database.addDisable(name);
            }
            originalReport.addReaction("\uD83D\uDC4D").queue();
            Database.setReportState(originalID, ReportState.ACCEPTED);
            event.getInteraction().editComponents(Collections.emptyList()).queue();
        } else if (event.getComponentId().startsWith("punish-more-evidence-")) {
            long originalID = Long.parseLong(event.getComponentId().substring("punish-more-evidence-".length()));
            Message originalReport = cheatersChannel.retrieveMessageById(originalID).complete();
            originalReport.reply(new MessageBuilder().setContent("Your report needs more evidence. Edit it to add evidence.").build()).queue();
            Database.setReportState(originalID, ReportState.NEEDS_EDIT);
            event.getInteraction().editComponents(Collections.emptyList()).queue();
        } else if (event.getComponentId().startsWith("punish-bad-format-")) {
            long originalID = Long.parseLong(event.getComponentId().substring("punish-bad-format-".length()));
            Message originalReport = cheatersChannel.retrieveMessageById(originalID).complete();
            originalReport.reply(new MessageBuilder().setContent("Your report needs to be reformatted (ex. adding links to the cheaters' profiles). Edit the original message to reformat it.").build()).queue();
            Database.setReportState(originalID, ReportState.NEEDS_EDIT);
            event.getInteraction().editComponents(Collections.emptyList()).queue();
        } else if (event.getComponentId().startsWith("punish-reject-")) {
            long originalID = Long.parseLong(event.getComponentId().substring("punish-reject-".length()));
            Message originalReport = cheatersChannel.retrieveMessageById(originalID).complete();
            originalReport.reply(new MessageBuilder().setContent("Your report has been rejected. Please make a new report instead of editing this report.").build()).queue();
            Database.setReportState(originalID, ReportState.CLOSED);
            event.getInteraction().editComponents(Collections.emptyList()).queue();
        } else if (event.getComponentId().equals("punish-finish")) {
            event.getInteraction().editMessageEmbeds(
                            new EmbedBuilder(Objects.requireNonNull(event.getMessage()).getEmbeds().get(0))
                                    .setTitle(":white_check_mark: In-Game Player Punishments").build())
                    .setActionRows(Collections.emptyList()).queue();
            Database.clearPunishments();
        }
    }
}
