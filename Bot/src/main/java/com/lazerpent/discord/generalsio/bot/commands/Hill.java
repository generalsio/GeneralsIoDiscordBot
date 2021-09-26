package com.lazerpent.discord.generalsio.bot.commands;

import com.lazerpent.discord.generalsio.bot.Commands.Category;
import com.lazerpent.discord.generalsio.bot.Commands.Command;
import com.lazerpent.discord.generalsio.bot.Commands.Optional;
import com.lazerpent.discord.generalsio.bot.Commands.Selection;
import com.lazerpent.discord.generalsio.bot.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

@Category(cat = "hill", name = "GoTH/AoTH")
public class Hill {
    public static final HashMap<Constants.Hill, List<Hill.ChallengeData>> currentChallenges = new HashMap<>();

    public static final int CONCURRENT_CHALLENGE_LIMIT = 3;
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    public static void init() {
        currentChallenges.put(Constants.Hill.GoTH, new ArrayList<>());
        currentChallenges.put(Constants.Hill.AoTH, new ArrayList<>());
        EXECUTOR.scheduleAtFixedRate(() -> checkScores(Constants.Hill.GoTH), 90, 90, TimeUnit.SECONDS);
        EXECUTOR.scheduleAtFixedRate(() -> checkScores(Constants.Hill.AoTH), 2, 2, TimeUnit.MINUTES);
    }

    private static void checkScores(Constants.Hill mode) {
        Database.Hill.Challenge[] terms = Database.Hill.lastTerms(Constants.Hill.GoTH, 1);
        if (terms.length == 0 || currentChallenges.get(mode).size() == 0) {
            return;
        }
        long[] incumbent = terms[0].opp;
        List<String> replayIDs = new ArrayList<>();
        List<ReplayStatistics.ReplayResult> replays =
                ReplayStatistics.getReplays(Database.getGeneralsName(incumbent[0]), 1000);
        Map<ReplayStatistics.ReplayResult, Replay> replayMap = null;
        if (mode == Constants.Hill.AoTH) {
            replayMap = ReplayStatistics.convertReplayFiles(replays);
        }
        Collections.reverse(replays);
        synchronized (currentChallenges.get(mode)) {
            final List<ChallengeData> curChallenges = currentChallenges.get(mode);
            boolean shouldClear = false;
            for (int challengeIdx = curChallenges.size() - 1; challengeIdx >= 0; challengeIdx--) {
                Hill.ChallengeData cdata = curChallenges.get(challengeIdx);
                Database.Hill.Challenge c = new Database.Hill.Challenge();
                c.timestamp = cdata.timestamp;
                c.type = mode;
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
                    if (replay.ranking.length == 2 * mode.teamSize) {
                        boolean shouldSkip = false;
                        for (long challenger : cdata.opp) {
                            if (!replay.hasPlayer(Database.getGeneralsName(challenger))) {
                                shouldSkip = true;
                                break;
                            }
                        }
                        if (shouldSkip) continue;
                        for (long xoth : incumbent) {
                            if (!replay.hasPlayer(Database.getGeneralsName(xoth))) {
                                shouldSkip = true;
                                break;
                            }
                        }
                        if (shouldSkip) continue;
                        if (replayMap != null) {
                            Replay replayFile = replayMap.get(replay);
                            if (replayFile.teams == null) {
                                continue;
                            }
                            if (!replayFile.onSameTeam(Arrays.stream(cdata.opp)
                                    .mapToObj(Database::getGeneralsName)
                                    .toArray(String[]::new))
                                || !replayFile.onSameTeam(Arrays.stream(incumbent)
                                    .mapToObj(Database::getGeneralsName)
                                    .toArray(String[]::new))) {
                                continue;
                            }
                            if (replayFile.onSameTeam(Database.getGeneralsName(cdata.opp[0]),
                                    Database.getGeneralsName(incumbent[0]))) {
                                continue;
                            }
                        }
                        boolean incWon = false;
                        for (long xoth : incumbent) {
                            if (replay.ranking[0].name.equals(Database.getGeneralsName(xoth))) {
                                incWon = true;
                                break;
                            }
                        }
                        if (incWon) {
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
                    } catch (Exception e) {
                        cdata.challengeMsg.replyEmbeds(Utils.error(cdata.challengeMsg, e.getMessage())).queue();
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
                                + " vs " + getOpponentName(oppMember, true))
                .setTimestamp(Instant.ofEpochMilli(c.timestamp));

        if (c.replays.length != 0) {
            if (c.replays[0].equals("..")) {
                embed.addField("Note", mode.name() + " challenge manually added with !hadd", false);
            } else {
                StringBuilder sb = new StringBuilder();
                for (String replay : c.replays) {
                    sb.append("https://generals.io/replays/").append(replay).append("\n");
                }

                embed.addField("Replays", sb.toString(), false);
            }
        } else {
            embed.addField("Note", mode.name() + " manually set by !hset", false);
        }

        return embed.build();
    }

    // Logs the results of a new GoTH challenge. Updates the database, updates the guild roles, and returns a
    // MessageEmbed.
    private static MessageEmbed logScore(Guild guild, Database.Hill.Challenge c, Constants.Hill mode) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(guild.getIdLong());

        if (c.scoreInc < c.scoreOpp) {
            Database.Hill.Challenge[] terms = Database.Hill.lastTerms(mode, 1);
            Role role = Objects.requireNonNull(guild.getRoleById(GUILD_INFO.hillRoles.get(mode)));
            if (terms.length != 0) {
                long[] incumbent = terms[0].opp;
                for (long inc : incumbent) {
                    guild.removeRoleFromMember(inc, role).queue();
                }
            }
            for (long challenger : c.opp) {
                guild.addRoleToMember(challenger, role).queue();
            }
        }

        Database.Hill.add(c);

        return scoreEmbed(c, mode);
    }

    @Command(name = {"hdel", "hdelete"}, desc = "Delete completed AoTH/GoTH challenge", perms = Constants.Perms.MOD)
    public static Object handleGothDel(@NotNull Message msg, Constants.Hill mode,
                                       @Optional Integer challengeId, @Optional Integer hillId) {
        challengeId = challengeId == null ? -1 : challengeId;
        hillId = hillId == null ? -1 : hillId;

        long timestamp1;
        long timestamp2 = Long.MAX_VALUE;
        if (hillId == -1) {
            Database.Hill.Challenge[] cs = Database.Hill.lastTerms(mode, 1);
            if (cs.length == 0) {
                return Utils.error(msg, "No " + mode.name() + "s exist");
            }
            timestamp1 = cs[0].timestamp;
        } else {
            Database.Hill.Challenge[] cs = Database.Hill.firstTerms(mode, hillId + 1);
            if (cs.length == hillId) {
                timestamp1 = cs[hillId - 1].timestamp;
            } else if (cs.length > hillId) {
                timestamp1 = cs[hillId - 1].timestamp;
                timestamp2 = cs[hillId].timestamp;
            } else {
                return Utils.error(msg,
                        mode.name() + " #" + hillId + " does not exist");
            }
        }
        Database.Hill.Challenge[] challenges = Database.Hill.get(mode, timestamp1, timestamp2);
        if (challenges.length < challengeId || challenges.length == 0) {
            return Utils.error(msg,
                    mode.name() + " #" + hillId + "'s challenge #" + challengeId + " does not exist");
        }

        Database.Hill.Challenge challenge;
        if (challengeId == -1) challenge = challenges[challenges.length - 1];
        else challenge = challenges[challengeId];

        Database.Hill.delete(challenge.timestamp);

        return Utils.success(msg, "Challenge Deleted");
    }

    @Command(name = {"hset", "hreplace"}, desc = "Replace GoTH or AoTH", perms = Constants.Perms.MOD)
    public static Object handleReplace(@NotNull Message msg, Constants.Hill mode, Member player,
                                       @Optional Member partner) {
        if (msg.getMentionedMembers().size() != mode.teamSize) {
            return Utils.error(msg, "Must provide " + mode.teamSize + " mentions");
        }

        long[] tempXothIDs = msg.getMentionedMembers().stream().mapToLong(Member::getIdLong).sorted().toArray();
        Database.User[] tempXoth =
                Arrays.stream(tempXothIDs).mapToObj(Database.User::fromId).toArray(Database.User[]::new);
        for (int i = 0; i < tempXothIDs.length; i++) {
            if (tempXoth[i] == null) {
                return Utils.error(msg, "<@" + tempXothIDs[i] + "> is not registered.");
            }
        }

        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

        // Update XoTH roles
        Role role = Objects.requireNonNull(msg.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode)));
        for (Member formerXoth :
                msg.getGuild().getMembersWithRoles(msg.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode)))) {
            msg.getGuild().removeRoleFromMember(formerXoth, role).queue();
        }
        for (Database.User challenger : tempXoth) {
            msg.getGuild().addRoleToMember(challenger.discordId(), role).queue();
        }

        Database.Hill.Challenge c = new Database.Hill.Challenge();
        c.timestamp = Instant.now().toEpochMilli();
        c.type = mode;
        c.scoreInc = 0;
        c.scoreOpp = 1;
        c.opp = Arrays.stream(tempXoth).mapToLong(Database.User::discordId).toArray();
        c.replays = new String[]{};
        Database.Hill.add(c);

        return new EmbedBuilder()
                .setColor(mode.color)
                .setTitle("Temporary " + mode.name() + " set")
                .setDescription(String.format("%s %s the temporary %s. They will not be recorded in the " +
                                              "hall of fame until they win a challenge.",
                        formatUsers(tempXoth, false),
                        (tempXoth.length == 1 ? "is" : "are"), mode))
                .build();
    }

    @Command(name = {"challenge", "hchallenge"}, desc = "Challenge the current GoTH or AoTH.", perms =
            Constants.Perms.USER)
    public static Object handleChallenge(@NotNull Message msg, Constants.Hill mode, String bestof,
                                         @Optional Member partner) {
        if (bestof.length() < 2 || !bestof.substring(0, 2).equalsIgnoreCase("bo")) {
            return Utils.error(msg,
                    "\"bestof\" argument should be in format bo_ (ex. bo3).");
        }

        int bestofN;
        try {
            bestofN = Integer.parseInt(bestof.substring(2));
        } catch (NumberFormatException e) {
            return Utils.error(msg,
                    "Must specify integer for number of games in set.");
        }

        if (bestofN <= 0) {
            return Utils.error(msg,
                    "Must specify positive number for number of games in set.");
        }
        if (bestofN % 2 == 0) {
            return Utils.error(msg,
                    "Must specify odd number for number of games in set.");
        }

        long[] partners = msg.getMentionedMembers().stream().mapToLong(ISnowflake::getIdLong).toArray();
        String[] partnerNames = Arrays.stream(partners).mapToObj(Database::getGeneralsName).toArray(String[]::new);
        if (partners.length + 1 != mode.teamSize) {
            return Utils.error(msg,
                    "Must specify " + (mode.teamSize - 1) + " partner to challenge " + mode.name());
        }

        for (int i = 0; i < partners.length; i++) {
            if (partners[i] == msg.getAuthor().getIdLong()) {
                return Utils.error(msg,
                        "You can't partner with yourself, you silly, lonely fool.");
            }
            if (partnerNames[i] == null) {
                return Utils.error(msg,
                        "<@" + partners[i] + "> must register generals.io username to challenge AoTH.");
            }
        }

        long[] challengers =
                LongStream.concat(LongStream.of(msg.getAuthor().getIdLong()), Arrays.stream(partners)).toArray();

        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

        for (long challenger : challengers) {
            if (Objects.requireNonNull(msg.getGuild().getMemberById(challenger)).getRoles()
                    .contains(msg.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode)))) {
                return Utils.error(msg,
                        "<@" + challenger + "> is already a " + mode.name());
            }
        }

        if (Database.Hill.lastTerms(mode, 1).length == 0) {
            return Utils.error(msg,
                    "There is no sitting " + mode.name() + ". Ask a mod to !hreplace you to become the " +
                    "temporary " + mode.name() + ".");
        } else {
            return new MessageBuilder().append("<@&")
                    .append(String.valueOf(GUILD_INFO.hillRoles.get(mode))).append(">")
                    .setEmbeds(new EmbedBuilder()
                            .setColor(mode.color)
                            .setTitle("New " + mode.name() + " Challenge")
                            .setDescription(String.format("%s challenge%s you, best of %d. Do you accept?",
                                    getOpponentName(challengers, true),
                                    (challengers.length == 1 ? "s" : ""),
                                    bestofN))
                            .build())
                    .setActionRows(ActionRow.of(List.of(
                            Button.success(String.format("%s-accept-%d-%s",
                                            mode.name().toLowerCase(),
                                            bestofN,
                                            Arrays.stream(challengers).mapToObj(String::valueOf).collect(Collectors.joining(
                                                    "-"))),
                                    "Accept"),
                            Button.danger(String.format("%s-reject-%s",
                                            mode.name().toLowerCase(),
                                            Arrays.stream(challengers).mapToObj(String::valueOf).collect(Collectors.joining(
                                                    "-"))),
                                    "Reject")
                    )))
                    .build();
        }
    }


    @Command(name = {"hof"}, desc = "Show GoTH or AoTH hall of fame", perms = Constants.Perms.USER)
    public static void handleHallOfFame(@NotNull Message msg, @Optional Constants.Hill mode, @Optional @Selection({
            "top", "seq"}) String order, @Optional Integer limit) {
        mode = mode == null ? Constants.Hill.GoTH : mode;
        order = order == null ? "top" : order;

        final Constants.Hill mode_ = mode;
        final Database.Hill.Challenge[] xoths = Database.Hill.firstTerms(mode, Integer.MAX_VALUE);
        int[] xothOrder = IntStream.range(0, xoths.length).toArray();
        int[] termLengths = IntStream.range(0, xoths.length)
                .map(a -> Database.Hill.get(mode_, xoths[a].timestamp + 1, a + 1 < xoths.length ?
                        xoths[a + 1].timestamp : Long.MAX_VALUE).length)
                .toArray();

        limit = limit == null ? 5 : limit;

        if (xoths.length == 0) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "No " + mode.name() + "s yet!")).queue();
            return;
        }

        if (order.equals("top")) {
            xothOrder = Arrays.stream(xothOrder).boxed().sorted((a, b) -> termLengths[b] - termLengths[a])
                    .mapToInt(Integer::valueOf).toArray();
        }

        EmbedBuilder hofEmbed = new EmbedBuilder()
                .setColor(mode.color)
                .setTitle(mode.name() + " Hall of Fame")
                .setDescription(Arrays.stream(xothOrder).mapToObj(
                                a -> "#" + (a + 1) + ": " + getOpponentName(xoths[a].opp, true) + " - " + termLengths[a] +
                                     " challenges (<t:" + (xoths[a].timestamp / 1000) + ":D>)")
                        .limit(limit).collect(Collectors.joining("\n")));

        msg.getChannel().sendMessageEmbeds(hofEmbed.build()).queue();
    }

    @Command(name = {"hadd"}, desc = "Add entry to GoTH or AoTH.", perms = Constants.Perms.MOD)
    public static Object handleAdd(@NotNull Message msg, Constants.Hill mode, String score, Member opponent1,
                                   @Optional Member opponent2) {
        List<Member> mentions = msg.getMentionedMembers();

        String[] scores = score.split("-");
        Database.Hill.Challenge c = new Database.Hill.Challenge();
        c.type = mode;
        if (mentions.size() != Objects.requireNonNull(c.type).teamSize) {
            return Utils.error(msg,
                    "Wrong # of mentions: " + c.type.teamSize + " required");
        }

        try {
            c.scoreInc = Integer.parseInt(scores[0]);
            c.scoreOpp = Integer.parseInt(scores[1]);
        } catch (NumberFormatException e) {
            return Utils.error(msg,
                    "Score format is [goth score]-[opponent score]");
        }
        c.timestamp = Instant.now().toEpochMilli();

        for (Member member : mentions) {
            if (Database.getGeneralsName(member.getIdLong()) == null) {
                return Utils.error(msg,
                        member.getAsMention() + " has not registered their generals.io user");
            }
        }
        c.opp = mentions.stream().mapToLong(ISnowflake::getIdLong).toArray();
        c.replays = new String[]{".."};

        return logScore(msg.getGuild(), c, Constants.Hill.GoTH);
    }

    @Command(name = {"hrec", "hrecord"}, desc = "Show the challenge history of the given GoTH/AoTH", perms =
            Constants.Perms.USER)
    public static Object handleRecordMention(@NotNull Message msg, Constants.Hill mode, Member mention1,
                                             @Optional Member mention2) {
        List<Member> mentions = msg.getMentionedMembers();

        if (mentions.size() != mode.teamSize) {
            return Utils.error(msg, "Must provide " + mode.teamSize + " mentions for "
                                    + mode.name());
        }

        Database.Hill.Challenge[] terms = Database.Hill.xothTerms(mode,
                mentions.stream().mapToLong(Member::getIdLong).toArray());
        if (terms.length == 0) {
            return Utils.error(msg,
                    mentions.stream()
                            .map(x -> "<@" + x.getIdLong() + ">")
                            .collect(Collectors.joining(" "))
                    + " " + (mentions.size() == 1 ? "has" : "have") + " not been " + mode.name());
        }

        int number = Database.Hill.nthTerm(mode, terms[0].timestamp);
        return handleRecord(msg, mode, number);
    }

    @Command(name = {"hreplay"}, desc = "Show the replays of the given goth / challenge number", perms =
            Constants.Perms.USER)
    public static Object handleReplay(@NotNull Message msg, Constants.Hill mode, int xothIndex, int challengeNumber) {
        if (xothIndex < 1) {
            return Utils.error(msg, mode.name() + " index must be a number greater than 0");
        }

        Database.Hill.Challenge[] terms =
                Database.Hill.query().type(mode).change().limit(xothIndex + 1).sort("asc").get();
        if (terms.length < xothIndex) {
            return Utils.error(msg, "No such " + mode.name());
        }

        long start = terms[xothIndex - 1].timestamp;
        long end = Long.MAX_VALUE;
        if (terms.length > xothIndex) {
            end = terms[xothIndex].timestamp;
        }

        if (challengeNumber < 0) {
            return Utils.error(msg, "Challenge number must be 0 or greater");
        }

        Database.Hill.Challenge[] challenges =
                Database.Hill.query().type(mode).from(start).to(end).sort("asc").limit(challengeNumber + 1).get();
        if (challenges.length < challengeNumber + 1) {
            return Utils.error(msg, "No such " + mode.name() + " challenge");
        }

        Database.Hill.Challenge challenge = challenges[challengeNumber];
        return scoreEmbed(challenge, mode);
    }

    public static void handleButtonClick(ButtonClickEvent event) {
        if (event.getComponentId().startsWith("goth-accept") || event.getComponentId().startsWith("aoth-accept")) {
            final Constants.GuildInfo GUILD_INFO =
                    Constants.GUILD_INFO.get(Objects.requireNonNull(event.getGuild()).getIdLong());
            String[] idArgs = event.getComponentId().split("-");

            Constants.Hill mode = Constants.Hill.fromString(idArgs[0]);
            if (!Objects.requireNonNull(event.getMember()).getRoles()
                    .contains(event.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode)))) {
                return;
            }

            long[] challengers = Arrays.stream(idArgs)
                    .skip(3)
                    .limit(Objects.requireNonNull(mode).teamSize)
                    .mapToLong(Long::parseLong)
                    .sorted()
                    .toArray();
            int bestOf = Integer.parseInt(idArgs[2]);

            synchronized (currentChallenges.get(mode)) {
                List<ChallengeData> curChallenges = currentChallenges.get(mode);
                if (curChallenges.size() >= CONCURRENT_CHALLENGE_LIMIT) {
                    event.getChannel().sendMessageEmbeds(Utils.error(Objects.requireNonNull(event.getMessage()),
                            "You can only accept " + CONCURRENT_CHALLENGE_LIMIT + " challenges at once.")).queue();
                    return;
                }

                // delete buttons
                Objects.requireNonNull(event.getMessage())
                        .editMessage(new MessageBuilder(event.getMessage()).setActionRows().build()).queue();

                // insert challenge data
                Hill.ChallengeData challenge = new ChallengeData(challengers, bestOf,
                        Instant.now().getEpochSecond() * 1000, null);
                curChallenges.add(challenge);

                Database.Hill.Challenge[] xoths = Database.Hill.firstTerms(mode, Integer.MAX_VALUE);
                long[] incumbent = xoths[xoths.length - 1].opp;
                int challengeIdx = 0;
                for (int a = 0; a < xoths.length; a++) {
                    if (Arrays.equals(xoths[a].opp, incumbent)) {
                        challengeIdx += Database.Hill.getWithOpponent(mode, xoths[a].timestamp + 1,
                                (a == xoths.length - 1 ? Long.MAX_VALUE : xoths[a + 1].timestamp),
                                challengers).length;
                    }
                }
                event.reply(new MessageBuilder()
                        .append(Arrays.stream(challengers).mapToObj(c -> "<@" + c + ">").collect(Collectors.joining(
                                " "))) // ping
                        .setEmbeds(new EmbedBuilder()
                                .setColor(Constants.Colors.SUCCESS)
                                .setTitle(Objects.requireNonNull(mode).name() + " Challenge Accepted",
                                        "https://generals.io/games/" + mode.toString().toLowerCase())
                                .setDescription("**" + getOpponentName(incumbent, true) + " vs " +
                                                getOpponentName(challengers, true) + (challengeIdx == 0 ? "" :
                                        (" #" + (challengeIdx + 1))) + "**")
                                .appendDescription("\nBest of " + bestOf)
                                .build())
                        .setActionRows(ActionRow.of(List.of(
                                Button.link("https://generals.io/games/"
                                            + mode.name().toLowerCase(), "Play"),
                                Button.link("https://generals.io/games/"
                                            + mode.name().toLowerCase() + "?spectate=true",
                                        "Spectate")
                        )))
                        .build()).queue((inx) -> inx.retrieveOriginal().queue(m -> challenge.challengeMsg = m));
            }
        } else if (event.getComponentId().startsWith("goth-reject") || event.getComponentId().startsWith("aoth-reject"
        )) {
            final Constants.GuildInfo GUILD_INFO =
                    Constants.GUILD_INFO.get(Objects.requireNonNull(event.getGuild()).getIdLong());

            String[] idArgs = event.getComponentId().split("-");
            Constants.Hill mode = Objects.requireNonNull(Constants.Hill.fromString(idArgs[0]));
            long[] challengers = Arrays.stream(idArgs)
                    .skip(2)
                    .limit(mode.teamSize)
                    .mapToLong(Long::parseLong)
                    .sorted()
                    .toArray();

            if (!Objects.requireNonNull(event.getMember()).getRoles()
                    .contains(event.getGuild().getRoleById(GUILD_INFO.hillRoles.get(mode)))) {
                return;
            }

            // remove buttons
            Objects.requireNonNull(event.getMessage())
                    .editMessage(new MessageBuilder(event.getMessage()).setActionRows().build()).queue();

            event.reply(new MessageBuilder()
                    .append(Arrays.stream(challengers).mapToObj(c -> "<@" + c + ">").collect(Collectors.joining(" ")))
                    .setEmbeds(new EmbedBuilder()
                            .setColor(Constants.Colors.ERROR)
                            .setTitle(mode + " Challenge Rejected")
                            .setDescription("The " + mode + " didn't want to play against "
                                            + getOpponentName(challengers, true) + ".")
                            .build())
                    .build()).queue();
        }
    }

    @Command(name = {"hrec", "hrecord"}, desc = "Show the challenge history of the nth GoTH/AoTH, or the latest " +
                                                "GoTH/AoTH if no index is provided", perms = Constants.Perms.USER)
    public static Object handleRecord(@NotNull Message msg, Constants.Hill mode, @Optional Integer number) {
        if (number != null && number <= 0) {
            return Utils.error(msg,
                    "Must provide a positive number for " + mode.name() + " index.");
        }

        number = number == null ? -1 : number;

        Database.Hill.Challenge xoth = null;
        long nextTime = Long.MAX_VALUE;
        if (number > 0) {
            Database.Hill.Challenge[] xoths = Database.Hill.firstTerms(mode, number + 1);
            if (xoths.length > number - 1) {
                xoth = xoths[number - 1];
            }
            if (xoths.length > number) {
                nextTime = xoths[number].timestamp;
            }
        } else {
            Database.Hill.Challenge[] xoths = Database.Hill.lastTerms(mode, 1);
            if (xoths.length != 0) {
                xoth = xoths[0];
                number = Database.Hill.nthTerm(mode, xoth.timestamp);
            }
        }

        if (xoth == null) {
            return Utils.error(msg,
                    mode.name() + " #" + number + " not found");
        }

        Database.Hill.Challenge[] challenges = Database.Hill.get(mode, xoth.timestamp + 1, nextTime);

        String challengeString;
        if (challenges.length == 0) {
            challengeString = "No challenges";
        } else {
            StringJoiner joiner = new StringJoiner("\n");
            for (int i = 0; i < challenges.length; i++) {
                final Database.Hill.Challenge c = challenges[i];

                String date = "<t:" + (c.timestamp / 1000) + ":D>";
                String s = "#" + (i + 1) + ": vs " + getOpponentName(c.opp, true) + " - ";

                if (c.scoreInc > c.scoreOpp) {
                    s += "**" + c.scoreInc + "**-" + c.scoreOpp;
                } else {
                    s += c.scoreInc + "-**" + c.scoreOpp + "**";
                }
                joiner.add(s + " (" + date + ")");
            }
            challengeString = joiner.toString();
        }

        return new EmbedBuilder()
                .setColor(mode.color)
                .setTitle(mode.name() + " #" + number)
                .setDescription(getOpponentName(xoth.opp, true))
                .addField("Challenges (" + challenges.length + ")", challengeString, false)
                .build();
    }


    public static String formatUsers(Database.User[] users, boolean mention) {
        if (mention)
            return Arrays.stream(users).map(user -> user.username() + " (<@" + user.discordId() + ">)").collect(Collectors.joining(" and "));
        return Arrays.stream(users).map(Database.User::username).collect(Collectors.joining(" and "));
    }

    public static String getOpponentName(long[] opp, boolean mention) {
        if (mention)
            return Arrays.stream(opp).mapToObj(id -> Database.getGeneralsName(id) + " (<@" + id + ">)")
                    .collect(Collectors.joining(" and "));
        else
            return Arrays.stream(opp).mapToObj(Database::getGeneralsName).collect(Collectors.joining(" and "));
    }

    private static class ChallengeData {
        public final long[] opp;
        public final int length;
        public final long timestamp;
        public Message challengeMsg;

        public ChallengeData(long[] opp, int length, long timestamp, Message challengeMsg) {
            this.opp = opp;
            this.length = length;
            this.timestamp = timestamp;
            this.challengeMsg = challengeMsg;
        }
    }
}