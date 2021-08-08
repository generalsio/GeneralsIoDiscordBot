package com.lazerpent.discord.generalsio.commands;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.jetbrains.annotations.NotNull;

import com.lazerpent.discord.generalsio.*;
import com.lazerpent.discord.generalsio.Commands.Category;
import com.lazerpent.discord.generalsio.Commands.Command;
import com.lazerpent.discord.generalsio.Database.Hill.Challenge;
import com.lazerpent.discord.generalsio.ReplayStatistics.ReplayResult;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;

@Category(cat = "hill", name = "GoTH/AoTH")
public class Hill {
    private static class ChallengeData {
        public long[] opp;
        public int length;
        public long timestamp;
        public Message challengeMsg;

        public ChallengeData(long[] opp, int length, long timestamp, Message challengeMsg) {
            this.opp = opp;
            this.length = length;
            this.timestamp = timestamp;
            this.challengeMsg = challengeMsg;
        }
    }

    public static final int CONCURRENT_CHALLENGE_LIMIT = 3;
    public static List<Hill.ChallengeData> curGothChallenges = new ArrayList<>();
    public static List<Hill.ChallengeData> curAothChallenges = new ArrayList<>();
    public static long tempGoth;
    public static long tempAoth1;
    public static long tempAoth2;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    public static void init() {
        EXECUTOR.execute(() -> {
            while (true) {
                checkGothScores();
                try {
                    Thread.sleep(90_000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        EXECUTOR.execute(() -> {
            while (true) {
                checkAothScores();
                try {
                    Thread.sleep(120_000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static void checkGothScores() {
        Database.Hill.Challenge[] terms = Database.Hill.lastTerms(Constants.Hill.GoTH, 1);
        long goth = tempGoth;
        if (terms.length != 0)
            goth = terms[0].opp[0];
        if (goth != 0) {
            String gothName = Database.getGeneralsName(goth);
            List<String> replayIDs = new ArrayList<>();
            List<ReplayResult> replays = ReplayStatistics.getReplays(gothName, 1000);
            Collections.reverse(replays);
            synchronized (curGothChallenges) {
                boolean shouldClear = false;
                for (Hill.ChallengeData cdata : curGothChallenges) {
                    String opponent = Database.getGeneralsName(cdata.opp[0]);

                    Database.Hill.Challenge c = new Database.Hill.Challenge();
                    c.timestamp = cdata.timestamp;
                    c.type = Constants.Hill.GoTH;
                    c.opp = cdata.opp;
                    c.scoreInc = 0;
                    c.scoreOpp = 0;
                    for (ReplayResult replay : replays) {
                        if (c.scoreInc == (cdata.length + 1) / 2 || c.scoreOpp == (cdata.length + 1) / 2) {
                            break;
                        }
                        if (replay.started < cdata.timestamp) {
                            continue;
                        }
                        if (replay.ranking.length == 2 && replay.hasPlayer(opponent) && replay.hasPlayer(gothName)) {
                            if (replay.ranking[0].name.equals(gothName)) {
                                c.scoreInc += 1;
                            } else {
                                c.scoreOpp += 1;
                            }
                            replayIDs.add(replay.id);
                        }
                    }
                    c.replays = replayIDs.toArray(new String[0]);
                    if (c.scoreInc == (cdata.length + 1) / 2 || c.scoreOpp == (cdata.length + 1) / 2) {
                        cdata.challengeMsg.replyEmbeds(logScore(cdata.challengeMsg.getGuild(), c,
                                Constants.Hill.GoTH)).queue();
                        if (c.scoreOpp > c.scoreInc) {
                            shouldClear = true;
                            break;
                        }
                    }
                }
                if (shouldClear) {
                    curGothChallenges.clear();
                }
            }
        }
    }

    private static void checkAothScores() {
        Database.Hill.Challenge[] terms = Database.Hill.lastTerms(Constants.Hill.AoTH, 1);
        long aoth1 = tempAoth1;
        long aoth2 = tempAoth2;
        if (terms.length != 0) {
            aoth1 = terms[0].opp[0];
            aoth2 = terms[0].opp[1];
        }
        if (aoth1 != 0 && aoth2 != 0) {
            String aothName1 = Database.getGeneralsName(aoth1);
            String aothName2 = Database.getGeneralsName(aoth2);
            List<String> replayIDs = new ArrayList<>();
            List<ReplayStatistics.ReplayResult> replays = ReplayStatistics.getReplays(aothName1, 1000);
            Collections.reverse(replays);
            Map<ReplayStatistics.ReplayResult, Replay> replayMap = ReplayStatistics.convertReplayFiles(replays);
            synchronized (curAothChallenges) {
                boolean shouldClear = false;
                for (Hill.ChallengeData cdata : curAothChallenges) {
                    String opponent1 = Database.getGeneralsName(cdata.opp[0]);
                    String opponent2 = Database.getGeneralsName(cdata.opp[1]);

                    Database.Hill.Challenge c = new Database.Hill.Challenge();
                    c.timestamp = cdata.timestamp;
                    c.type = Constants.Hill.GoTH;
                    c.opp = cdata.opp;
                    c.scoreInc = 0;
                    c.scoreOpp = 0;
                    for (ReplayStatistics.ReplayResult replay : replays) {
                        if (c.scoreInc == (cdata.length + 1) / 2 || c.scoreOpp == (cdata.length + 1) / 2) {
                            break;
                        }
                        if (replay.started < cdata.timestamp) {
                            continue;
                        }
                        Replay replayFile = replayMap.get(replay);
                        if (replay.ranking.length == 4
                            && replay.hasPlayer(opponent1)
                            && replay.hasPlayer(opponent2)
                            && replay.hasPlayer(aothName1)
                            && replay.hasPlayer(aothName2)
                            && replayFile.teams != null) {
                            boolean hasAothTeam = false;
                            boolean hasOppTeam = false;
                            boolean hasCrossTeam = false;
                            for (int a = 0; a < replayFile.teams.length; a++) {
                                for (int b = a + 1; b < replayFile.teams.length; b++) {
                                    if (replayFile.teams[a] == replayFile.teams[b]) {
                                        String u1 = replayFile.usernames[a];
                                        String u2 = replayFile.usernames[b];
                                        if (u1.equals(aothName1) && u2.equals(aothName2)
                                            || u1.equals(aothName2) && u2.equals(aothName1)) {
                                            hasAothTeam = true;
                                        } else if (u1.equals(opponent1) && u2.equals(opponent2)
                                                   || u1.equals(opponent2) && u2.equals(opponent1)) {
                                            hasOppTeam = true;
                                        } else {
                                            hasCrossTeam = true;
                                        }
                                    }
                                }
                            }
                            if (!hasAothTeam || !hasOppTeam || hasCrossTeam) {
                                continue;
                            }
                            if (replay.ranking[0].name.equals(aothName1) || replay.ranking[0].name.equals(aothName2)) {
                                c.scoreInc += 1;
                            } else {
                                c.scoreOpp += 1;
                            }
                            replayIDs.add(replay.id);
                        }
                    }
                    c.replays = replayIDs.toArray(new String[0]);
                    if (c.scoreInc == (cdata.length + 1) / 2 || c.scoreOpp == (cdata.length + 1) / 2) {
                        cdata.challengeMsg.replyEmbeds(logScore(cdata.challengeMsg.getGuild(), c,
                                Constants.Hill.AoTH)).queue();
                        if (c.scoreOpp > c.scoreInc) {
                            shouldClear = true;
                            break;
                        }
                    }
                }
                if (shouldClear) {
                    curAothChallenges.clear();
                }
            }
        }
    }

    private static MessageEmbed scoreEmbed(Database.Hill.Challenge c, Constants.Hill mode) {
        long oppMember = c.opp[0];
        String oppName = Database.getGeneralsName(oppMember);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("GoTH Results")
                .setColor(Constants.Colors.PRIMARY)
                .setDescription((c.scoreInc > c.scoreOpp ? "**" + c.scoreInc
                                                           + "**-" + c.scoreOpp :
                        c.scoreInc + "-**" + c.scoreOpp + "**")
                                + " vs " + oppName + "(<@" + oppMember + ">)");

        if (c.replays.length != 0) {
            StringBuilder sb = new StringBuilder();
            for (String replay : c.replays) {
                sb.append("https://generals.io/replays/" + replay + "\n");
            }

            embed.addField("Replays", sb.toString(), false);
        }

        return embed.build();
    }

    // Logs the results of a new GoTH challenge. Updates the database, updates the guild roles, and returns a
    // MessageEmbed.
    private static MessageEmbed logScore(Guild guild, Database.Hill.Challenge c, Constants.Hill mode) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(guild.getIdLong());

        if (c.scoreInc < c.scoreOpp) {
            Database.Hill.Challenge[] terms = Database.Hill.lastTerms(mode, 1);
            // remove GoTH role from incumbent
            long inc1 = 0;
            long inc2 = 0;
            if (mode == Constants.Hill.AoTH) {
                inc1 = tempAoth1;
                inc2 = tempAoth2;
            } else {
                inc1 = tempGoth;
            }
            if (terms.length != 0) {
                inc1 = terms[0].opp[0];
                if (mode == Constants.Hill.AoTH) {
                    inc2 = terms[0].opp[1];
                }
            }
            if (inc1 != 0) {
                guild.removeRoleFromMember(inc1, guild.getRoleById(GUILD_INFO.hillRoles.get(mode))).queue();
                if (mode == Constants.Hill.AoTH) {
                    guild.removeRoleFromMember(inc2, guild.getRoleById(GUILD_INFO.hillRoles.get(mode))).queue();
                }
            }

            // add GoTH role to new message
            guild.addRoleToMember(c.opp[0], guild.getRoleById(GUILD_INFO.hillRoles.get(mode))).queue();
            if (mode == Constants.Hill.AoTH) {
                guild.addRoleToMember(c.opp[1],
                        guild.getRoleById(GUILD_INFO.hillRoles.get(mode))).queue();
            }
        }

        Database.Hill.add(c);

        return scoreEmbed(c, mode);
    }

    @Command(name = {"gothdel", "gothdelete"}, args = {"id"}, desc = "Delete completed GoTH challenge", perms =
            Constants.Perms.MOD)
    public static void handleGothDel(@NotNull Commands self, @NotNull Message msg, String[] args) {
        // TODO: allow removal of single replay from challenge + reviving challenges
        if (args.length <= 1) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must return length")).queue();
            return;
        }

        if (args[1].charAt(0) != 'C') {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must delete challenges")).queue();
            return;
        }

        try {
            long timestamp = Long.parseLong(args[1].substring(1));
            Database.Hill.delete(timestamp);
            msg.getChannel().sendMessageEmbeds(Utils.success(msg, "Challenge Deleted", "If `C" + timestamp + "` " +
                                                                                       "existed, it was deleted")).queue();
        } catch (NumberFormatException e) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Invalid challenge ID")).queue();
            return;
        }
    }
    
    @Command(name = {"goth"}, args = {"id?"}, desc = "Show information about the given term or challenge", perms
            = Constants.Perms.USER)
    public static void handleGoth(@NotNull Commands self, @NotNull Message msg, String[] args) {
        if (args.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify a subcommand.")).queue();
            return;
        }
        if (args[1].equalsIgnoreCase("top")) {
            showHallOfFame(self, msg, args);
        }
    }

    @Command(name = {"challenge"}, args = {"goth | aoth", "bestof", "@partner?"}, desc = "Challenge the current " +
                                                                                         "GoTH or AoTH.", perms =
            Constants.Perms.USER)
    public static void handleChallenge(@NotNull Commands self, @NotNull Message msg, String[] args) { // TODO:
        // test challenges
        if (args.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify GoTH or AoTH.")).queue();
            return;
        }
        Constants.Hill mode;
        switch (args[1].toLowerCase()) {
            case "goth":
                mode = Constants.Hill.GoTH;
                break;
            case "aoth":
                mode = Constants.Hill.AoTH;
                break;
            default:
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify GoTH or AoTH.")).queue();
                return;
        }
        if (args.length < 3) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify number of games in set (best of x " +
                                                                "games).")).queue();
            return;
        }
        if (args[2].length() < 2 || !args[2].substring(0, 2).equalsIgnoreCase("bo")) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "\"bestof\" argument should be in format bo_ (ex." +
                                                                " bo3).")).queue();
            return;
        }
        int bestof = 0;
        try {
            bestof = Integer.parseInt(args[2].substring(2));
        } catch (NumberFormatException e) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify integer for number of games in set" +
                                                                ".")).queue();
            return;
        }
        if (bestof <= 0) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify positive number for number of games" +
                                                                " in set.")).queue();
            return;
        }
        if (bestof % 2 == 0) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify odd number for number of games in " +
                                                                "set.")).queue();
            return;
        }
        if (mode == Constants.Hill.AoTH && msg.getMentionedMembers().size() != 1) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify one partner to challenge AoTH.")).queue();
            return;
        }
        String partner = null;
        long partnerId = 0;
        if (mode == Constants.Hill.AoTH) {
            partnerId = msg.getMentionedMembers().get(0).getIdLong();
            partner = Database.getGeneralsName(partnerId);
            if (partnerId == msg.getAuthor().getIdLong()) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You can't partner with yourself, you silly, " +
                                                                    "lonely fool.")).queue();
                return;
            }
        }
        String username = Database.getGeneralsName(msg.getAuthor().getIdLong());
        if (partner == null && mode == Constants.Hill.AoTH) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Partner must register generals.io username to " +
                                                                "challenge AoTH.")).queue();
            return;
        }
        if (username == null) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must register generals.io username to challenge" +
                                                                ".")).queue();
            return;
        }
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());
        if (msg.getMember().getRoles().contains(msg.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode)))) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You can't challenge yourself.")).queue();
            return;
        }
        Database.Hill.Challenge[] terms = Database.Hill.lastTerms(mode, 1);
        if (terms.length == 0
            && ((mode == Constants.Hill.GoTH && tempGoth == 0)
                || (mode == Constants.Hill.AoTH && tempAoth1 == 0))) {
            switch (mode) {
                case GoTH:
                    tempGoth = msg.getAuthor().getIdLong();
                    msg.getGuild().addRoleToMember(tempGoth,
                            msg.getGuild().getRoleById(GUILD_INFO.hillRoles.get(Constants.Hill.GoTH))).queue();
                    msg.getChannel().sendMessageEmbeds(new EmbedBuilder()
                            .setTitle("Temporary GoTH set")
                            .setDescription(username + " is the temporary GoTH. They will not be recorded in the " +
                                            "hall of fame until they win a challenge.")
                            .build()).queue();
                    break;
                case AoTH:
                    tempAoth1 = msg.getAuthor().getIdLong();
                    tempAoth2 = partnerId;
                    msg.getGuild().addRoleToMember(tempAoth1,
                            msg.getGuild().getRoleById(GUILD_INFO.hillRoles.get(Constants.Hill.AoTH))).queue();
                    msg.getGuild().addRoleToMember(tempAoth2,
                            msg.getGuild().getRoleById(GUILD_INFO.hillRoles.get(Constants.Hill.AoTH))).queue();
                    msg.getChannel().sendMessageEmbeds(new EmbedBuilder()
                            .setTitle("Temporary AoTH set")
                            .setDescription(username + " and " + partner + " are the temporary AoTHs. They will " +
                                            "not be recorded in the hall of fame until they win a challenge.")
                            .build()).queue();
                    break;
            }
        } else {
            switch (mode) {
                case GoTH:
                    msg.getChannel().sendMessage(new MessageBuilder().append("<@&" + GUILD_INFO.hillRoles.get(Constants.Hill.GoTH) + ">")
                            .setEmbeds(new EmbedBuilder()
                                    .setColor(Constants.Colors.PRIMARY)
                                    .setTitle("New GoTH Challenge")
                                    .setDescription(username + " (" + msg.getAuthor().getAsMention()
                                                    + ") challenges you, best of " + bestof
                                                    + ". Do you accept?")
                                    .build())
                            .setActionRows(ActionRow.of(List.of(
                                    Button.success("goth-accept-" + msg.getAuthor().getIdLong() + "-" + bestof,
                                            "Accept"),
                                    Button.danger("goth-reject-" + msg.getAuthor().getIdLong(), "Reject")
                            )))
                            .build()).queue();
                    break;
                case AoTH:
                    msg.getChannel().sendMessage(new MessageBuilder().append("<@&" + GUILD_INFO.hillRoles.get(Constants.Hill.AoTH) + ">")
                            .setEmbeds(new EmbedBuilder()
                                    .setColor(Constants.Colors.PRIMARY)
                                    .setTitle("New AoTH Challenge")
                                    .setDescription(username + " (" + msg.getAuthor().getAsMention()
                                                    + ") and " + partner + " (<@" + partnerId + ">)"
                                                    + " challenge you, best of " + bestof
                                                    + ". Do you accept?")
                                    .build())
                            .setActionRows(ActionRow.of(List.of(
                                    Button.success("aoth-accept-" + msg.getAuthor().getIdLong()
                                                   + "-" + partnerId + "-" + bestof, "Accept"),
                                    Button.danger("aoth-reject-" + msg.getAuthor().getIdLong()
                                                  + "-" + partnerId, "Reject")
                            )))
                            .build()).queue();
                    break;
            }
        }
    }

    public static void handleButtonClick(ButtonClickEvent event) {
        if (event.getComponentId().startsWith("goth-accept") || event.getComponentId().startsWith("aoth-accept")) {
            final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(event.getGuild().getIdLong());
            String[] idArgs = event.getComponentId().split("-");
            Constants.Hill mode = switch(idArgs[0].toLowerCase()) {
            case "goth" -> Constants.Hill.GoTH;
            case "aoth" -> Constants.Hill.AoTH;
            default -> throw new IllegalArgumentException("Unexpected value: " + idArgs[0].toLowerCase());
            };
            if (!event.getMember().getRoles().contains(event.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode)))) {
                return;
            }
            List<ChallengeData> curChallenges = switch(mode) {
            case GoTH -> curGothChallenges;
            case AoTH -> curAothChallenges;
            };
            synchronized (curChallenges) {
                if (curChallenges.size() >= CONCURRENT_CHALLENGE_LIMIT) {
                    event.getChannel().sendMessageEmbeds(Utils.error(event.getMessage(),
                            "You can only accept " + CONCURRENT_CHALLENGE_LIMIT + " challenges at once.")).queue();
                    return;
                }
                long[] challenger = Arrays.stream(idArgs)
                        .skip(2)
                        .limit(mode.teamSize)
                        .mapToLong(Long::parseLong)
                        .sorted()
                        .toArray();
                int bestof = Integer.parseInt(idArgs[3]);
                String challengerName = getOpponentName(challenger, false);
                event.getMessage().editMessage(new MessageBuilder(event.getMessage()).setActionRows().build()).queue();
                if (challengerName == null) {
                    event.getChannel().sendMessageEmbeds(Utils.error(event.getMessage(), "Challenge could not be " +
                                                                                         "accepted: challenger " +
                                                                                         "doesn't exist.")).queue();
                    return;
                }
                Hill.ChallengeData challenge = new ChallengeData(challenger, bestof,
                        Instant.now().getEpochSecond() * 1000, null);
                curChallenges.add(challenge);
                String ping = Arrays.stream(challenger).mapToObj(c -> "<@" + c + ">")
                        .collect(Collectors.joining(" "));
                Challenge[] xoths = Database.Hill.firstTerms(mode, Integer.MAX_VALUE);
                long[] incumbent = xoths[xoths.length - 1].opp;
                String incumbentName = getOpponentName(incumbent, false);
                int challengeIdx = 0;
                for(int a = 0; a < xoths.length; a++) {
                    if(Arrays.equals(xoths[a].opp, incumbent)) {
                        challengeIdx += Database.Hill.getWithOpponent(mode, xoths[a].timestamp + 1,
                                (a == xoths.length - 1 ? Long.MAX_VALUE : xoths[a + 1].timestamp),
                                challenger).length;
                    }
                }
                event.reply(new MessageBuilder().append(ping)
                        .setEmbeds(new EmbedBuilder()
                                .setColor(Constants.Colors.PRIMARY)
                                .setTitle(mode.toString() + " Challenge Accepted", 
                                        "https://generals.io/games/" + mode.toString().toLowerCase())
                                .setDescription("**"
                                        + incumbentName + " vs. " + challengerName
                                        + (challengeIdx == 0 ? "" : (" #" + (challengeIdx + 1)))
                                        + "**")
                                .appendDescription("\nBest of " + bestof)
                                .build())
                        .setActionRows(ActionRow.of(List.of(
                                Button.link("https://generals.io/games/" 
                                        + mode.toString().toLowerCase(), "Play"),
                                Button.link("https://generals.io/games/" 
                                        + mode.toString().toLowerCase() + "?spectate=true",
                                        "Spectate")
                        )))
                        .build()).queue((inx) -> {
                    inx.retrieveOriginal().queue(m -> {
                        challenge.challengeMsg = m;
                    });
                });
            }
        } else if (event.getComponentId().startsWith("goth-reject") || event.getComponentId().startsWith("aoth-reject")) {
            final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(event.getGuild().getIdLong());
            if (!event.getMember().getRoles().contains(event.getGuild().getRoleById(GUILD_INFO.hillRoles.get(Constants.Hill.GoTH)))) {
                return;
            }
            String[] idArgs = event.getComponentId().split("-");
            Constants.Hill mode = switch(idArgs[0].toLowerCase()) {
            case "goth" -> Constants.Hill.GoTH;
            case "aoth" -> Constants.Hill.AoTH;
            default -> throw new IllegalArgumentException("Unexpected value: " + idArgs[0].toLowerCase());
            };
            long[] challenger = Arrays.stream(idArgs)
                    .skip(2)
                    .limit(mode.teamSize)
                    .mapToLong(Long::parseLong)
                    .sorted()
                    .toArray();
            String challengerName = getOpponentName(challenger, false);
            event.getMessage().editMessage(new MessageBuilder(event.getMessage()).setActionRows().build()).queue();
            String ping = Arrays.stream(challenger).mapToObj(c -> "<@" + c + ">")
                    .collect(Collectors.joining(" "));
            event.reply(new MessageBuilder().append(ping)
                    .setEmbeds(new EmbedBuilder()
                            .setColor(Constants.Colors.ERROR)
                            .setTitle(mode.toString() + " Challenge Rejected")
                            .setDescription("The " + mode.toString() + " didn't want to play against "
                                            + challengerName + ".")
                            .build())
                    .build()).queue();
        }
    }

    public static void showHallOfFame(@NotNull Commands self, @NotNull Message msg, String[] args) {
        if (args.length > 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Too many arguments provided.")).queue();
            return;
        }
        Constants.Hill mode = switch(args[0].toLowerCase()) {
        case "goth" -> Constants.Hill.GoTH;
        case "aoth" -> Constants.Hill.AoTH;
        default -> throw new IllegalArgumentException("Unexpected value: " + args[0].toLowerCase());
        };
        Challenge[] xoths = Database.Hill.firstTerms(mode, Integer.MAX_VALUE);
        EmbedBuilder hofEmbed = new EmbedBuilder()
                .setTitle(mode.toString() + " Hall of Fame");
        if (xoths.length == 0) {
            hofEmbed.setDescription(String.format("No %ss yet! Be the first by "
                    + "accepting and winning a challenge (!challenge).",
                    mode.toString()));
        } else {
            for (int a = 0; a < xoths.length; a++) {
                String xothName = getOpponentName(xoths[a].opp, true);
                hofEmbed.appendDescription("\n#" + (a + 1) + ": "
                                           + xothName);
                int terms = Database.Hill.get(mode, xoths[a].timestamp,
                        a + 1 < xoths.length ? xoths[a + 1].timestamp : Long.MAX_VALUE).length;
                String startDate = Instant.ofEpochMilli(xoths[a].timestamp).toString();
                if (startDate.indexOf('T') != -1) {
                    startDate = startDate.substring(0, startDate.indexOf('T'));
                }
                if (a == xoths.length - 1) {
                    String suffix = switch (terms) {
                    case 1 -> "st";
                    case 2 -> "nd";
                    case 3 -> "rd";
                    default -> "th";
                    };
                    hofEmbed.appendDescription(String.format("\n    %d%s term, started %s (current)",
                            terms,
                            suffix,
                            startDate));
                } else {
                    terms--;
                    String endDate = Instant.ofEpochMilli(xoths[a + 1].timestamp).toString();
                    if (endDate.indexOf('T') != -1) {
                        endDate = endDate.substring(0, endDate.indexOf('T'));
                    }
                    hofEmbed.appendDescription(String.format("\n    %d term%s, %s to %s",
                            terms,
                            (terms > 1 ? "s" : ""),
                            startDate,
                            endDate));
                }
            }
        }
        msg.getChannel().sendMessageEmbeds(hofEmbed.build()).queue();
    }

    public static void showGothRecord(@NotNull Commands self, @NotNull Message msg, String[] args) {
        // TODO: implement !goth results

    }
    
    public static String getOpponentName(long[] opp, boolean includeMentions) {
        if(opp.length == 1) {
            if(includeMentions) {
                return String.format("%s (<@%d>)",
                        StringUtils.defaultIfEmpty(Database.getGeneralsName(opp[0]), "Unknown"),
                        opp[0]);
            } else {
                return StringUtils.defaultIfEmpty(Database.getGeneralsName(opp[0]), "Unknown");
            }
        } else if(opp.length == 2) {
            if(includeMentions) {
                return String.format("%s (<@%d>) and %s (<@%d>)",
                        StringUtils.defaultIfEmpty(Database.getGeneralsName(opp[0]), "Unknown"),
                        opp[0],
                        StringUtils.defaultIfEmpty(Database.getGeneralsName(opp[1]), "Unknown"),
                        opp[1]);
            } else {
                return String.format("%s and %s",
                        StringUtils.defaultIfEmpty(Database.getGeneralsName(opp[0]), "Unknown"),
                        StringUtils.defaultIfEmpty(Database.getGeneralsName(opp[1]), "Unknown"));
            }
        } else {
            throw new IllegalArgumentException("Invalid challenge opponent data: " 
                    + Arrays.toString(opp));
        }
    }
}