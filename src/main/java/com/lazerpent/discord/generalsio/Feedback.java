package com.lazerpent.discord.generalsio;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class Feedback {
    private static final long REPORT_CHANNEL = 709631419303067679L;


    private final HashMap<User, Report> reports = new HashMap<>();

    void report(Message msg, String[] command) {
        if (command.length != 1) {
            switch (command[1]) {
                default:
                    msg.getChannel().sendMessage(Utils.error(msg, Objects.requireNonNull(msg.getMember()).getAsMention()
                            + " Unknown command report::" + command[1])).queue();
                    return;
                case "help":
                    //TODO report help
                    return;
                case "cancel":
                    String message = Objects.requireNonNull(msg.getMember()).getAsMention() + " ";
                    if (reports.remove(msg.getAuthor()) == null) {
                        message += "You did not have a report in progress.";
                    } else {
                        message += "Report canceled.";
                    }
                    msg.getChannel().sendMessage(message).queue();
                    return;
                case "restart":
                    reports.remove(msg.getAuthor());
                    break;
            }
        }

        if (reports.containsKey(msg.getAuthor())) {
            msg.getChannel().sendMessage(Utils.error(msg, Objects.requireNonNull(msg.getMember()).getAsMention()
                    + " You already have a report in progress. Please finish it, or use ``" + Commands.PREFIX + "report restart`` to cancel it, and start a new one.")).queue();
            return;
        }

        PrivateChannel privateChannel;
        privateChannel = msg.getAuthor().openPrivateChannel().complete();

        try {
            privateChannel.sendMessage("Thank you for starting a report. Please state the category of your report: MULTIPLE ACCOUNTS, BAD MANNERS, OTHER").complete();
            msg.delete().queue();
        } catch (ErrorResponseException e) {
            msg.getChannel().sendMessage(Objects.requireNonNull(msg.getMember()).getAsMention() + " I am unable to send you private messages.").queue();
            return;
        }
        Report report = new Report();
        report.member = msg.getMember();
        reports.put(msg.getAuthor(), report);
    }

    void privateMessage(Message msg) {
        Report report = reports.get(msg.getAuthor());
        if (report == null) {
            return;
        }
        String content = msg.getContentDisplay();
        switch (report.state) {
            case AWAITING_CATEGORY:
                try {
                    report.type = ReportType.valueOf(content.replaceAll(" ", "_").toUpperCase());
                    msg.getPrivateChannel().sendMessage("Please list all players associated with this report. If more than one, separate by commas.").queue();
                    report.state = ReportState.AWAITING_NAMES;
                } catch (IllegalArgumentException ignored) {
                    msg.getPrivateChannel().sendMessage("Invalid response: Must be MULTIPLE ACCOUNTS, BAD MANNERS, OTHER").queue();
                }
                break;
            case AWAITING_NAMES:
                report.setRelatedUsers(content);
                msg.getPrivateChannel().sendMessage("Now, please include any generals.io replay links that are associated with this report. When you are finished (or have none), please type ``done``. You can include up to 5 replay links.").queue();
                report.state = ReportState.AWAITING_LINKS;
                break;
            case AWAITING_LINKS:
                if (content.equalsIgnoreCase("done")) {
                    msg.getPrivateChannel().sendMessage("Now please include any screenshots (just upload them to discord) that are associated with this report. When you are finished (or have none), please type ``done``. You can include up to 3 screenshots.").queue();
                    report.state = ReportState.AWAITING_SCREENSHOTS;
                    break;
                }
                if (!content.contains("generals.io/replays/")) {
                    msg.getPrivateChannel().sendMessage("The link must be a generals.io replay link. Please try again").queue();
                    break;
                }
                if (report.links.size() > 5) {
                    msg.getPrivateChannel().sendMessage("You can only have 5 links").queue();
                    break;
                }
                report.links.add(content);
                msg.getPrivateChannel().sendMessage("Link recorded. Please continue to send links, or type ``done``.").queue();
                break;
            case AWAITING_SCREENSHOTS:
                if (content.equalsIgnoreCase("done")) {
                    msg.getPrivateChannel().sendMessage("Finally, please include a description for this report. Keep your description short, but include as much detail as possible.").queue();
                    report.state = ReportState.AWAITING_DESCRIPTION;
                    break;
                }
                if (msg.getAttachments().size() == 0) {
                    msg.getPrivateChannel().sendMessage("You must upload your screenshot as an attachment to discord.").queue();
                    break;
                }
                for (Message.Attachment attachment : msg.getAttachments()) {
                    if (!attachment.isImage()) {
                        msg.getPrivateChannel().sendMessage("You can only upload images.").queue();
                        return;
                    }
                }
                if (report.attachments.size() + msg.getAttachments().size() > 3) {
                    msg.getPrivateChannel().sendMessage("You can only attach up to 3 images.").queue();
                    break;
                }
                report.attachments.addAll(msg.getAttachments());
                msg.getPrivateChannel().sendMessage("Attachment recorded. Please continue to send attachments, or type ``done``.").queue();
                break;

            case AWAITING_DESCRIPTION:
                if (content.length() >= 500) {
                    msg.getPrivateChannel().sendMessage("Please keep your description under 500 characters. You currently are using " + content.length() + " characters.").queue();
                    break;
                }
                report.description = content;
                msg.getPrivateChannel().sendMessage("Thank you for your report.").queue();
                report.state = ReportState.COMPLETE;
                finishedReport(report);
                break;
        }
    }

    private void finishedReport(Report report) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(StringUtils.capitalize(report.type.toString().toLowerCase().replaceAll("_", " ")) + " report");
        builder.setDescription(report.description);
        final StringBuilder userBuilder = new StringBuilder();
        report.relatedUsers.forEach(s -> userBuilder.append(s).append("\n"));
        builder.addField("Related user(s)", userBuilder.toString(), false);
        final StringBuilder linkBuilder = new StringBuilder();
        if (report.links.size() == 0) {
            linkBuilder.append("None");
        }
        report.links.forEach(s -> linkBuilder.append(s).append("\n"));
        builder.addField("Replay Links (if any)", linkBuilder.toString(), false);
        final StringBuilder attachmentBuilder = new StringBuilder();
        if (report.attachments.size() == 0) {
            attachmentBuilder.append("None");
        }
        report.attachments.forEach(s -> attachmentBuilder.append(s.getUrl()).append("\n"));
        builder.addField("Attachment links (if any)", attachmentBuilder.toString(), false);

        builder.setAuthor(report.member.getEffectiveName(), null, report.member.getUser().getEffectiveAvatarUrl());

        MessageBuilder messageBuilder = new MessageBuilder();
        messageBuilder.setEmbed(builder.build());
        Objects.requireNonNull(report.member.getGuild().getTextChannelById(REPORT_CHANNEL)).sendMessage(messageBuilder.build()).queue();
    }

    private static class Report {
        Member member;
        ReportState state = ReportState.AWAITING_CATEGORY;
        ReportType type;
        final ArrayList<String> relatedUsers = new ArrayList<>();
        final ArrayList<String> links = new ArrayList<>();
        final ArrayList<Message.Attachment> attachments = new ArrayList<>();
        String description;

        public void setRelatedUsers(String usersByComma) {
            for (String s : usersByComma.split(",")) {
                relatedUsers.add(s.strip());
            }
        }
    }

    @SuppressWarnings({"unused", "RedundantSuppression"})
    private enum ReportType {
        MULTIPLE_ACCOUNTS, BAD_MANNERS, OTHER
    }

    private enum ReportState {
        AWAITING_CATEGORY, AWAITING_NAMES, AWAITING_LINKS, AWAITING_SCREENSHOTS, AWAITING_DESCRIPTION, COMPLETE
    }
}
