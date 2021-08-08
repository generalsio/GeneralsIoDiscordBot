package com.lazerpent.discord.generalsio.commands;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    public static void init() {
        EXECUTOR.execute(() -> {
            while (true) {
                checkScores(Constants.Hill.GoTH);
                try {
                    Thread.sleep(90_000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        EXECUTOR.execute(() -> {
            while (true) {
                checkScores(Constants.Hill.AoTH);
                try {
                    Thread.sleep(120_000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    private static void checkScores(Constants.Hill mode) {
        Database.Hill.Challenge[] terms = Database.Hill.lastTerms(Constants.Hill.GoTH, 1);
        List<ChallengeData> curChallenges = switch(mode) {
        case GoTH -> curGothChallenges;
        case AoTH -> curAothChallenges;
        };
        if (terms.length == 0 || curChallenges.size() == 0)
            return;
        long[] incumbent = terms[0].opp;
        List<String> replayIDs = new ArrayList<>();
        List<ReplayResult> replays = ReplayStatistics.getReplays(Database.getGeneralsName(incumbent[0]), 1000);
        Map<ReplayStatistics.ReplayResult, Replay> replayMap = null;
        if(mode == Constants.Hill.AoTH) {
            replayMap = ReplayStatistics.convertReplayFiles(replays);
        }
        Collections.reverse(replays);
        synchronized (curChallenges) {
            boolean shouldClear = false;
            for (int challengeIdx = curChallenges.size() - 1; challengeIdx >= 0; challengeIdx--) {
                Hill.ChallengeData cdata = curChallenges.get(challengeIdx);
                Database.Hill.Challenge c = new Database.Hill.Challenge();
                c.timestamp = cdata.timestamp;
                c.type = mode;
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
                    if (replay.ranking.length == 2 * mode.teamSize) {
                        boolean shouldSkip = false;
                        for(long challenger : cdata.opp) {
                            if(!replay.hasPlayer(Database.getGeneralsName(challenger))) {
                                shouldSkip = true;
                                break;
                            }
                        }
                        if(shouldSkip) continue;
                        for(long xoth : incumbent) {
                            if(!replay.hasPlayer(Database.getGeneralsName(xoth))) {
                                shouldSkip = true;
                                break;
                            }
                        }
                        if(shouldSkip) continue;
                        if(replayMap != null) {
                            Replay replayFile = replayMap.get(replay);
                            if(replayFile.teams == null) {
                                continue;
                            }
                            if(!replayFile.onSameTeam(Arrays.stream(cdata.opp)
                                    .mapToObj(Database::getGeneralsName)
                                    .toArray(String[]::new))
                                || !replayFile.onSameTeam(Arrays.stream(incumbent)
                                        .mapToObj(Database::getGeneralsName)
                                        .toArray(String[]::new))) {
                                continue;
                            }
                            if(replayFile.onSameTeam(Database.getGeneralsName(cdata.opp[0]), 
                                    Database.getGeneralsName(incumbent[0]))) {
                                continue;
                            }
                        }
                        boolean incWon = false;
                        for(long xoth : incumbent) {
                            if (replay.ranking[0].name.equals(Database.getGeneralsName(xoth))) {
                                incWon = true;
                                break;
                            }
                        }
                        if(incWon) {
                            c.scoreInc++;
                        } else {
                            c.scoreOpp++;
                        }
                        replayIDs.add(replay.id);
                    }
                }
                c.replays = replayIDs.toArray(new String[0]);
                if (c.scoreInc == (cdata.length + 1) / 2 || c.scoreOpp == (cdata.length + 1) / 2) {
                    curChallenges.remove(challengeIdx);
                    try {
                        cdata.challengeMsg.replyEmbeds(logScore(cdata.challengeMsg.getGuild(), c,
                                mode)).queue();
                    } catch(Exception e) {
                        cdata.challengeMsg.replyEmbeds(Utils.error(cdata.challengeMsg, e.getMessage()));
                    }
                    if (c.scoreOpp > c.scoreInc) {
                        shouldClear = true;
                        break;
                    }
                }
            }
            if (shouldClear) {
                curChallenges.clear();
            }
        }
    }

    private static MessageEmbed scoreEmbed(Database.Hill.Challenge c, Constants.Hill mode) {
        long[] oppMember = c.opp;
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(mode.toString() + " Results")
                .setColor(Constants.Colors.PRIMARY)
                .setDescription((c.scoreInc > c.scoreOpp ? "**" + c.scoreInc
                                                           + "**-" + c.scoreOpp :
                        c.scoreInc + "-**" + c.scoreOpp + "**")
                                + " vs " + getOpponentName(oppMember, true));

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
    private static MessageEmbed logScore(Guild guild, Database.Hill.Challenge c, Constants.Hill mode) throws Exception {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(guild.getIdLong());

        if (c.scoreInc < c.scoreOpp) {
            Database.Hill.Challenge[] terms = Database.Hill.lastTerms(mode, 1);
            if (terms.length == 0) {
                throw new Exception("Could not retrieve information for previous " + mode.toString() + ".");
            }
            long[] incumbent = terms[0].opp;
            for(long inc : incumbent) {
                guild.removeRoleFromMember(inc, guild.getRoleById(GUILD_INFO.hillRoles.get(mode))).queue();
            }
            for(long challenger : c.opp) {
                guild.addRoleToMember(challenger, guild.getRoleById(GUILD_INFO.hillRoles.get(mode))).queue();
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
    
    @Command(name = {"settemp"}, args = {"goth | aoth", "@player", "@partner?"}, desc = "", perms = Constants.Perms.MOD)
    public static void handleSetTemp(@NotNull Commands self, @NotNull Message msg, String[] args) {
        if (args.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify GoTH or AoTH.")).queue();
            return;
        }
        Constants.Hill mode = switch(args[1].toLowerCase()) {
        case "goth" -> Constants.Hill.GoTH;
        case "aoth" -> Constants.Hill.AoTH;
        default -> null;
        };
        if(mode == null) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify GoTH or AoTH.")).queue();
            return;
        }
        if(msg.getMentionedMembers().size() != mode.teamSize) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify " + mode.teamSize + " users.")).queue();
            return;
        }
        long[] tempXoth = msg.getMentionedMembers().stream()
                .mapToLong(Member::getIdLong)
                .sorted()
                .toArray();
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());
        for(Member formerXoth : msg.getGuild().getMembersWithRoles(msg.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode)))) {
            msg.getGuild().removeRoleFromMember(formerXoth,
                    msg.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode))).queue();
        }
        for(long challenger : tempXoth) {
            msg.getGuild().addRoleToMember(challenger,
                    msg.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode))).queue();
        }
        Challenge c = new Challenge();
        c.timestamp = Instant.now().toEpochMilli();
        c.type = mode;
        c.scoreInc = 0;
        c.scoreOpp = 1;
        c.opp = tempXoth;
        c.replays = new String[]{ "temp" };
        Database.Hill.add(c);
        msg.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("Temporary " + mode.toString() + " set")
                .setDescription(String.format("%s %s the temporary %s. They will not be recorded in the " +
                                "hall of fame until they win a challenge.",
                                getOpponentName(tempXoth, false),
                                (tempXoth.length == 1 ? "is" : "are"),
                                mode.toString()))
                .build()).queue();
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
        Constants.Hill mode = switch(args[1].toLowerCase()) {
        case "goth" -> Constants.Hill.GoTH;
        case "aoth" -> Constants.Hill.AoTH;
        default -> null;
        };
        if(mode == null) {
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
        if (mode == Constants.Hill.AoTH) {
            if(msg.getMentionedMembers().size() != 1) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify one partner to challenge AoTH.")).queue();
                return;
            } else if(msg.getMentionedMembers().get(0).getIdLong() == msg.getAuthor().getIdLong()) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You can't partner with yourself, you silly, " +
                        "lonely fool.")).queue();
                return;
            } else if (Database.getGeneralsName(msg.getMentionedMembers().get(0).getIdLong()) == null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Partner must register generals.io username to " +
                        "challenge AoTH.")).queue();
                return;
            }
        }
        long[] challenger = switch(mode) {
        case GoTH -> new long[]{ msg.getAuthor().getIdLong() };
        case AoTH -> new long[]{ msg.getAuthor().getIdLong(), msg.getMentionedMembers().get(0).getIdLong() };
        };
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());
        if (msg.getMember().getRoles().contains(msg.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode)))) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You can't challenge yourself.")).queue();
            return;
        }
        if (Database.Hill.lastTerms(mode, 1).length == 0) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "There is no sitting "
                    + mode.toString()
                    + ". Ask a mod to !settemp you to become the temporary "
                    + mode.toString() + ".")).queue();
        } else {
            msg.getChannel().sendMessage(new MessageBuilder().append("<@&" + GUILD_INFO.hillRoles.get(mode) + ">")
                    .setEmbeds(new EmbedBuilder()
                            .setColor(Constants.Colors.PRIMARY)
                            .setTitle("New " + mode.toString() + " Challenge")
                            .setDescription(String.format("%s challenge%s you, best of %d. Do you accept?",
                                    getOpponentName(challenger, true),
                                    (challenger.length == 1 ? "s" : ""),
                                    bestof))
                            .build())
                    .setActionRows(ActionRow.of(List.of(
                            Button.success(String.format("%s-accept-%s-%d",
                                    mode.toString().toLowerCase(),
                                    Arrays.stream(challenger).mapToObj(String::valueOf).collect(Collectors.joining("-")),
                                    bestof),
                                    "Accept"),
                            Button.success(String.format("%s-reject-%s",
                                    mode.toString().toLowerCase(),
                                    Arrays.stream(challenger).mapToObj(String::valueOf).collect(Collectors.joining("-"))),
                                    "Reject")
                    )))
                    .build()).queue();
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
                                        + incumbentName + " vs " + challengerName
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
    
    @Command(name = {"halloffame"}, args = {"goth | aoth"}, desc = "Show GoTH or AoTH hall of fame.", perms
            = Constants.Perms.USER)
    public static void handleHallOfFame(@NotNull Commands self, @NotNull Message msg, String[] args) {
        if (args.length != 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must provide 1 argument.")).queue();
            return;
        }
        Constants.Hill mode = switch(args[1].toLowerCase()) {
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
                int terms = Database.Hill.get(mode, xoths[a].timestamp,
                        a + 1 < xoths.length ? xoths[a + 1].timestamp : Long.MAX_VALUE).length;
                if(xoths[a].replays.length == 1 && xoths[a].replays[0].equals("temp")) {
                    if(a != 0) {
                        hofEmbed.appendDescription("\n" + mode.toString() + " replaced with " + xothName + ".");
                    }
                    terms--;
                }
                if(terms == 0) {
                    continue;
                }
                hofEmbed.appendDescription("\n#" + (a + 1) + ": " + xothName);
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

    @Command(name = {"viewrecord"}, args = {"goth | aoth", "index?"}, 
            desc = "Show the challenge history of the nth GoTH/AoTH, "
                    + "or the latest GoTH/AoTH if no index is provided.", 
            perms = Constants.Perms.USER)
    public static void handleViewRecord(@NotNull Commands self, @NotNull Message msg, String[] args) {
        if (args.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must provide at least 1 argument.")).queue();
            return;
        }
        Constants.Hill mode = switch(args[1].toLowerCase()) {
        case "goth" -> Constants.Hill.GoTH;
        case "aoth" -> Constants.Hill.AoTH;
        default -> null;
        };
        if(mode == null) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify GoTH or AoTH.")).queue();
            return;
        }
        int goalIdx = -1;
        if(args.length == 3) {
            try {
                goalIdx = Integer.parseInt(args[2]);
            } catch(NumberFormatException e) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must provide a number for GoTH/AoTH index.")).queue();
                return;
            }
            if(goalIdx <= 0) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must provide a positive number for GoTH/AoTH index.")).queue();
                return;
            }
        }
        Challenge[] xoths = Database.Hill.firstTerms(mode, Integer.MAX_VALUE);
        int curIdx = 0;
        boolean found = false;
        Challenge[] lastFound = null;
        for (int a = 0; a < xoths.length; a++) {
            String xothName = getOpponentName(xoths[a].opp, true);
            Challenge[] terms = Database.Hill.get(mode, xoths[a].timestamp,
                    a + 1 < xoths.length ? xoths[a + 1].timestamp : Long.MAX_VALUE);
            boolean wasTemp = false;
            if(xoths[a].replays.length == 1 && xoths[a].replays[0].equals("temp")) {
                wasTemp = true;
            }
            if(terms.length == (wasTemp ? 1 : 0)) {
                continue;
            }
            lastFound = terms;
            if(curIdx == goalIdx) {
                
            }
            curIdx++;
        }
        if(goalIdx == -1) {
            
        } else if(found) {
            
        } else {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Could not find challenges for " 
                    + mode.toString() + " #" + goalIdx + ".")).queue();
        }
    }
    
    private static MessageEmbed challengeRecordEmbed(Challenge[] terms, String name, int idx, Constants.Hill mode) {
        EmbedBuilder recordEmbed = new EmbedBuilder();
        recordEmbed.setTitle(String.format("Challenges taken by %s, %s #%d",
                name,
                mode.toString(),
                idx));
        for(Challenge term : terms) {
            recordEmbed.appendDescription(name + " vs " + getOpponentName(term.opp, false) 
                + ": " + term.scoreInc + "-" + term.scoreOpp);
        }
        return recordEmbed.build();
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