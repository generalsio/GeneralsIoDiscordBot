package com.lazerpent.discord.generalsio.commands;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.*;

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
                .setColor(mode.color)
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
    private static MessageEmbed logScore(Guild guild, Database.Hill.Challenge c, Constants.Hill mode) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(guild.getIdLong());

        if (c.scoreInc < c.scoreOpp) {
            Database.Hill.Challenge[] terms = Database.Hill.lastTerms(mode, 1);
            if (terms.length != 0) {
                long[] incumbent = terms[0].opp;
                for(long inc : incumbent) {
                    guild.removeRoleFromMember(inc, guild.getRoleById(GUILD_INFO.hillRoles.get(mode))).queue();
                }
            }
            for(long challenger : c.opp) {
                guild.addRoleToMember(challenger, guild.getRoleById(GUILD_INFO.hillRoles.get(mode))).queue();
            }
        }

        Database.Hill.add(c);

        return scoreEmbed(c, mode);
    }

    @Command(name = {"hdel", "hdelete"}, args = {"goth | aoth", "xoth #", "challenge #"}, desc = "Delete completed AoTH/GoTH challenge", perms =
            Constants.Perms.MOD)
    public static void handleGothDel(@NotNull Commands self, @NotNull Message msg, String[] args) {
        // TODO: allow removal of single replay from challenge + reviving challenges
        if (args.length < 4) {
            msg.getChannel().sendMessageEmbeds(Utils.errorWrongArgs(msg, 4, args.length)).queue();
            return;
        }

        Constants.Hill mode = Constants.Hill.fromString(args[1]);
        if (mode == null) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify GoTH or AoTH")).queue();
            return;
        }

        int xothN;
        int challengeN;
        try {
            xothN = Integer.parseInt(args[2]);
            challengeN = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "trolololol " + e)).queue();
            return;
        }

        try {
            Challenge[] cs = Database.Hill.firstTerms(mode, xothN + 1);
            long timestamp1;
            long timestamp2 = Long.MAX_VALUE;
            if (cs.length == xothN) {
                timestamp1 = cs[xothN-1].timestamp;
            } else if (cs.length > xothN) {
                timestamp1 = cs[xothN-1].timestamp;
                timestamp2 = cs[xothN].timestamp;
            } else {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, mode.name() + " #" + xothN + " does not exist")).queue();
                return;
            }
            Challenge[] challenges = Database.Hill.get(mode, timestamp1,timestamp2);
            if (challenges.length < challengeN) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, mode.name() + " #" + xothN + "'s challenge #" + challengeN + " does not exist")).queue();
                return;
            }
            Database.Hill.delete(challenges[challengeN].timestamp);
            msg.getChannel().sendMessageEmbeds(Utils.success(msg, "Challenge Deleted")).queue();
        } catch (NumberFormatException e) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Invalid challenge ID")).queue();
            return;
        }
    }
    
    @Command(name = {"hset", "hreplace"}, args = {"goth | aoth", "@player", "@partner?"}, desc = "Replace GoTH or AoTH", perms = Constants.Perms.MOD)
    public static void handleReplace(@NotNull Commands self, @NotNull Message msg, String[] args) {
        if (args.length < 3) {
            msg.getChannel().sendMessageEmbeds(Utils.errorWrongArgs(msg, 3, args.length)).queue();
            return;
        }
        Constants.Hill mode = Constants.Hill.fromString(args[1]);
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
        c.replays = new String[]{};
        Database.Hill.add(c);

        msg.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setColor(mode.color)
                .setTitle("Temporary " + mode.name() + " set")
                .setDescription(String.format("%s %s the temporary %s. They will not be recorded in the " +
                                "hall of fame until they win a challenge.",
                                getOpponentName(tempXoth, false),
                                (tempXoth.length == 1 ? "is" : "are"),
                                mode.toString()))
                .build()).queue();
    }

    @Command(name = {"challenge", "hchallenge"}, args = {"goth | aoth", "bestof", "@partner?"}, desc = "Challenge the current " +
                                                                                         "GoTH or AoTH.", perms =
            Constants.Perms.USER)
    public static void handleChallenge(@NotNull Commands self, @NotNull Message msg, String[] args) { // TODO:
        // gather arguments
        if (args.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.errorWrongArgs(msg, 2, args.length)).queue();
            return;
        }
        Constants.Hill mode = Constants.Hill.fromString(args[1]);
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
        
        long[] partners = msg.getMentionedMembers().stream().mapToLong(x -> x.getIdLong()).toArray();
        String[] partnerNames = Arrays.stream(partners).mapToObj(Database::getGeneralsName).collect(Collectors.toList()).toArray(new String[0]);
        if (partners.length + 1 != mode.teamSize) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify " + (mode.teamSize - 1) + " partner to challenge AoTH.")).queue();
        }
        for (int i = 0; i < partners.length; i++) {
            if (partners[i] == msg.getAuthor().getIdLong()) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You can't partner with yourself, you silly, " +
                        "lonely fool.")).queue();
                return;
            }
            if (partnerNames[i] == null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "<@" + partners[i] + "> must register generals.io username to " +
                        "challenge AoTH.")).queue();
                return;
            }
        }

        long[] challengers = LongStream.concat(LongStream.of(msg.getAuthor().getIdLong()), Arrays.stream(partners)).toArray();

        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

        for (long challenger: challengers) {
            if (msg.getGuild().getMemberById(challenger).getRoles().contains(msg.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode)))) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "<@" + challenger + "> is already a " + mode.name())).queue();
                return;
            }
        }

        if (Database.Hill.lastTerms(mode, 1).length == 0) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "There is no sitting "
                    + mode.name()
                    + ". Ask a mod to !hreplace you to become the temporary "
                    + mode.name() + ".")).queue();
        } else {
            msg.getChannel().sendMessage(new MessageBuilder().append("<@&" + GUILD_INFO.hillRoles.get(mode) + ">")
                    .setEmbeds(new EmbedBuilder()
                            .setColor(mode.color)
                            .setTitle("New " + mode.name() + " Challenge")
                            .setDescription(String.format("%s challenge%s you, best of %d. Do you accept?",
                                    getOpponentName(challengers, true),
                                    (challengers.length == 1 ? "s" : ""),
                                    bestof))
                            .build())
                    .setActionRows(ActionRow.of(List.of(
                            Button.success(String.format("%s-accept-%d-%s",
                                    mode.name().toLowerCase(),
                                    bestof,
                                    Arrays.stream(challengers).mapToObj(String::valueOf).collect(Collectors.joining("-"))),
                                    "Accept"),
                            Button.success(String.format("%s-reject-%s",
                                    mode.name().toLowerCase(),
                                    Arrays.stream(challengers).mapToObj(String::valueOf).collect(Collectors.joining("-"))),
                                    "Reject")
                    )))
                    .build()).queue();
        }
    }

    public static void handleButtonClick(ButtonClickEvent event) {
        if (event.getComponentId().startsWith("goth-accept") || event.getComponentId().startsWith("aoth-accept")) {
            final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(event.getGuild().getIdLong());
            String[] idArgs = event.getComponentId().split("-");

            Constants.Hill mode = Constants.Hill.fromString(idArgs[0]);
            if (!event.getMember().getRoles().contains(event.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode)))) {
                return;
            }

            long[] challengers = Arrays.stream(idArgs)
                    .skip(3)
                    .limit(mode.teamSize)
                    .mapToLong(Long::parseLong)
                    .sorted()
                    .toArray();
            int bestof = Integer.parseInt(idArgs[2]);

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

                // delete buttons
                event.getMessage().editMessage(new MessageBuilder(event.getMessage()).setActionRows().build()).queue();

                // insert challenge data
                Hill.ChallengeData challenge = new ChallengeData(challengers, bestof,
                        Instant.now().getEpochSecond() * 1000, null);
                curChallenges.add(challenge);

                Challenge[] xoths = Database.Hill.firstTerms(mode, Integer.MAX_VALUE);
                long[] incumbent = xoths[xoths.length - 1].opp;
                int challengeIdx = 0;
                for(int a = 0; a < xoths.length; a++) {
                    if(Arrays.equals(xoths[a].opp, incumbent)) {
                        challengeIdx += Database.Hill.getWithOpponent(mode, xoths[a].timestamp + 1,
                                (a == xoths.length - 1 ? Long.MAX_VALUE : xoths[a + 1].timestamp),
                                challengers).length;
                    }
                }
                event.reply(new MessageBuilder()
                        .append(Arrays.stream(challengers).mapToObj(c -> "<@" + c + ">").collect(Collectors.joining(" "))) // ping
                        .setEmbeds(new EmbedBuilder()
                                .setColor(Constants.Colors.SUCCESS)
                                .setTitle(mode.name() + " Challenge Accepted", 
                                        "https://generals.io/games/" + mode.toString().toLowerCase())
                                .setDescription("**"
                                        + getOpponentName(incumbent, true) + " vs " + getOpponentName(challengers, true)
                                        + (challengeIdx == 0 ? "" : (" #" + (challengeIdx + 1)))
                                        + "**")
                                .appendDescription("\nBest of " + bestof)
                                .build())
                        .setActionRows(ActionRow.of(List.of(
                                Button.link("https://generals.io/games/" 
                                        + mode.name().toLowerCase(), "Play"),
                                Button.link("https://generals.io/games/" 
                                        + mode.name().toLowerCase() + "?spectate=true",
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

            String[] idArgs = event.getComponentId().split("-");
            Constants.Hill mode = Constants.Hill.fromString(idArgs[0]);
            long[] challengers = Arrays.stream(idArgs)
                    .skip(2)
                    .limit(mode.teamSize)
                    .mapToLong(Long::parseLong)
                    .sorted()
                    .toArray();

            if (!event.getMember().getRoles().contains(event.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode)))) {
                return;
            }

            // remove buttons
            event.getMessage().editMessage(new MessageBuilder(event.getMessage()).setActionRows().build()).queue();

            event.reply(new MessageBuilder()
                    .append(Arrays.stream(challengers).mapToObj(c -> "<@" + c + ">").collect(Collectors.joining(" ")))
                    .setEmbeds(new EmbedBuilder()
                            .setColor(Constants.Colors.ERROR)
                            .setTitle(mode.toString() + " Challenge Rejected")
                            .setDescription("The " + mode.toString() + " didn't want to play against "
                                            + getOpponentName(challengers, true) + ".")
                            .build())
                    .build()).queue();
        }
    }

    private static MessageEmbed errorInvalidMode(@NotNull Message msg, String s) {
        return Utils.error(msg, "Not a valid hill mode", "`" + s + "` is not a valid hill mode");
    }
    
    @Command(name = {"hof"}, args = {"goth | aoth", "top | seq"}, desc = "Show GoTH or AoTH hall of fame.", perms
            = Constants.Perms.USER)
    public static void handleHallOfFame(@NotNull Commands self, @NotNull Message msg, String[] args) {
        Constants.Hill mode;
        if (args.length < 2) {
            mode = Constants.Hill.GoTH;
        } else {
            mode = Constants.Hill.fromString(args[1]);
        }
        if (mode == null) {
            msg.getChannel().sendMessageEmbeds(errorInvalidMode(msg, args[1])).queue();
            return;
        }

        Challenge[] xoths = Database.Hill.firstTerms(mode, Integer.MAX_VALUE);
        int[] xothOrder = IntStream.range(0, xoths.length).toArray();
        int[] termLengths = IntStream.range(0, xoths.length)
            .map(a -> Database.Hill.get(mode, xoths[a].timestamp+1, a + 1 < xoths.length ? xoths[a + 1].timestamp : Long.MAX_VALUE).length)
            .toArray();

        if (xoths.length == 0) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "No " + mode.name() + "s yet!")).queue();
            return;
        }

        if (args.length > 2 && args[2].equals("top")) {
            xothOrder = Arrays.stream(xothOrder).boxed().sorted((a, b) -> termLengths[b] - termLengths[a]).mapToInt(Integer::valueOf).toArray();
        } else if (args.length > 2 && !args[2].equals("seq")) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Third argument must be either `seq` or `top`.")).queue();
            return;
        }

        EmbedBuilder hofEmbed = new EmbedBuilder()
            .setColor(mode.color)
            .setTitle(mode.name() + " Hall of Fame")
            .setDescription(Arrays.stream(xothOrder).<String>mapToObj(a -> {
                return "#" + (a+1) + ": " + getOpponentName(xoths[a].opp, true) + 
                    " - " + termLengths[a] +
                    " challenges (" + DateTimeFormatter.ofPattern("uuuu-MM-dd").format(Instant.ofEpochMilli(xoths[a].timestamp).atOffset(ZoneOffset.UTC)) + ")";
            }).collect(Collectors.joining("\n")));

        msg.getChannel().sendMessageEmbeds(hofEmbed.build()).queue();
    }

    @Command(name = {"hadd"}, args = {"goth | aoth", "score", "opponents"}, 
            desc = "Add entry to GoTH or AoTH.", 
            perms = Constants.Perms.MOD)
    public static void handleAdd(@NotNull Commands self, @NotNull Message msg, String[] args) {
        if (args.length < 4) {
            msg.getChannel().sendMessageEmbeds(Utils.errorWrongArgs(msg, 4, args.length)).queue();
            return;
        }

        String score = args[2];
        List<Member> mentions = msg.getMentionedMembers();

        String[] scores = score.split("-");
        Database.Hill.Challenge c = new Database.Hill.Challenge();
        c.type = Constants.Hill.fromString(args[1]);
        if (mentions.size() != c.type.teamSize) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Wrong # of mentions: " + c.type.teamSize + " required")).queue();
            return;
        }

        try {
            c.scoreInc = Integer.parseInt(scores[0]);
            c.scoreOpp = Integer.parseInt(scores[1]);
        } catch(NumberFormatException e) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Score format is [goth score]-[opponent score]")).queue();
            return;
        }
        c.timestamp = Instant.now().toEpochMilli();

        for (Member member : mentions) {
            if (Database.getGeneralsName(member.getIdLong()) == null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, member.getAsMention() + " has not registered their " +
                    "generals.io user")).queue();
                return;
            }
        }
        c.opp = mentions.stream().mapToLong(x -> x.getIdLong()).toArray();
        c.replays = new String[]{".."};

        msg.getChannel().sendMessageEmbeds(logScore(msg.getGuild(), c, Constants.Hill.GoTH)).queue();
    }

    @Command(name = {"hrec", "hrecord"}, args = {"goth | aoth", "index? | mention?"}, 
            desc = "Show the challenge history of the nth GoTH/AoTH, "
                    + "or the latest GoTH/AoTH if no index is provided.", 
            perms = Constants.Perms.USER)
    public static void handleRecord(@NotNull Commands self, @NotNull Message msg, String[] args) {
        if (args.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.errorWrongArgs(msg, 2, args.length)).queue();
            return;
        }
        Constants.Hill mode = Constants.Hill.fromString(args[1]);
        if(mode == null) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify GoTH or AoTH.")).queue();
            return;
        }

        long[] mentions = msg.getMentionedMembers().stream().mapToLong(Member::getIdLong).toArray();

        int number = -1;
        if(args.length == 3 && mentions.length == 0) {
            try {
                number = Integer.parseInt(args[2]);
            } catch(NumberFormatException e) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must provide a number for " + mode.name() + " index.")).queue();
                return;
            }

            if(number <= 0) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must provide a positive number for "+  mode.name() + " index.")).queue();
                return;
            }
        }
        if (mentions.length != 0) {
            if (mentions.length != mode.teamSize) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must provide " + mode.teamSize + " mentions for "+  mode.name())).queue();
                return;
            }

            Challenge[] terms = Database.Hill.xothTerms(mode, mentions);
            if (terms.length == 0) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, Arrays.stream(mentions).<String>mapToObj(x -> "<@" + x + ">").collect(Collectors.joining(" ")) + " " + (mentions.length == 1 ? "has" : "have") + " not been " + mode.name())).queue();
                return;
            }

            number = Database.Hill.nthTerm(mode, terms[0].timestamp);
        }

        Challenge xoth = null;
        long nextTime = Long.MAX_VALUE; 
        if (number > 0) {
            Challenge[] xoths = Database.Hill.firstTerms(mode, number+1);
            if (xoths.length > number - 1) {
                xoth = xoths[number-1];
            }
            if (xoths.length > number) {
                nextTime = xoths[number].timestamp;
            }
        } else {
            Challenge[] xoths = Database.Hill.lastTerms(mode, 1);
            if (xoths.length != 0) {
                xoth=  xoths[0];
                number  = Database.Hill.nthTerm(mode, xoth.timestamp);
            }
        }

        if (xoth == null) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, mode.name() + " #" + number + " not found")).queue();
            return;
        }

        Challenge[] challenges = Database.Hill.get(mode, xoth.timestamp+1, nextTime);

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(mode.color)
            .setTitle(mode.name() + " #" + number)
            .setDescription(getOpponentName(xoth.opp, true))
            .addField("Challenges (" + challenges.length + ")", 
                challenges.length == 0 ? "No challenges" : Arrays.stream(challenges)
                    .map(c -> "vs " + getOpponentName(c.opp, true) + " - " + (c.scoreInc > c.scoreOpp? "**" + c.scoreInc + "**-" + c.scoreOpp : c.scoreInc + "-**" + c.scoreOpp + "**") + " (" + DateTimeFormatter.ofPattern("uuuu-MM-dd").format(Instant.ofEpochMilli(c.timestamp).atOffset(ZoneOffset.UTC)) + ")")
                    .collect(Collectors.joining("\n")),
                false
            );

        msg.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    public static String getOpponentName(long[] opp, boolean mention) {
        if (mention)
            return Arrays.stream(opp).mapToObj(id -> Database.getGeneralsName(id) + " (<@" + id + ">)").collect(Collectors.joining(" and "));
        else
            return Arrays.stream(opp).mapToObj(id -> Database.getGeneralsName(id)).collect(Collectors.joining(" and "));
    }
}