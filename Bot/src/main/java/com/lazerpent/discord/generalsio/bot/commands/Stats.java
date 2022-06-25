package com.lazerpent.discord.generalsio.bot.commands;

import com.lazerpent.discord.generalsio.bot.Commands.Command;
import com.lazerpent.discord.generalsio.bot.Commands.CommandParameter;
import com.lazerpent.discord.generalsio.bot.Constants;
import com.lazerpent.discord.generalsio.bot.ReplayStatistics;
import com.lazerpent.discord.generalsio.bot.ReplayStatistics.ReplayResult;
import com.lazerpent.discord.generalsio.bot.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;
import org.jfree.chart.JFreeChart;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.lazerpent.discord.generalsio.bot.Commands.Category;

@Category(name = "Stats")
public class Stats {
    private static final Semaphore REQUEST_LIMITER = new Semaphore(20);
    private static final AtomicInteger REQUESTED_PLAYERS = new AtomicInteger();
    private static final int STORED_PLAYER_LIMIT = 6;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private static int findWinStreak(PrimitiveIterator.OfInt wins) {
        int curStreak = 0;
        int streak = 0;
        while (wins.hasNext()) {
            int win = wins.nextInt();
            if (win == 1) {
                curStreak++;
            } else {
                curStreak = 0;
            }
            if(curStreak > streak) {
                streak = curStreak;
            }
        }
        return streak;
    }

    public static MessageEmbed getStatEmbed(List<ReplayResult> replays, String username, boolean saved) {
        EmbedBuilder statEmbed = new EmbedBuilder().setTitle((saved ? "Saved " : "") + username + "'s Stats")
                .setDescription("**Total Games Played:** " + replays.size()).setColor(Constants.Colors.SUCCESS);
        long timePlayed = (long) replays.stream().mapToDouble((r) -> r.turns
                * Math.min(0.5,
                0.5 * r.getPercentile(username) * r.ranking.length / (r.ranking.length - 1))).sum();
        statEmbed.appendDescription("\n**Total Time Played:** " + (timePlayed / 3600) + "h "
                + (timePlayed / 60 % 60) + "m " + (timePlayed % 60) + "s");
        OptionalDouble winRate1v1 = replays.stream()
                .filter((r) -> r.type.equals("1v1") && r.hasPlayer(username) && r.turns >= 50)
                .mapToInt((r) -> (r.isWin(username) ? 1 : 0)).average();
        if (winRate1v1.isPresent()) {
            statEmbed.appendDescription(String.format("\n**1v1 Win Rate:** %.2f%%", winRate1v1.getAsDouble() * 100));
            int streak1v1 = findWinStreak(replays.stream()
                    .filter((r) -> r.type.equals("1v1") && r.hasPlayer(username) && r.turns >= 50)
                    .mapToInt((r) -> (r.isWin(username) ? 1 : 0)).iterator());
            statEmbed.appendDescription((String.format("\n**1v1 Best Win Streak:** %d", streak1v1)));
        }
        OptionalDouble winRateFFA = replays.stream()
                .filter((r) -> r.type.equals("classic") && r.hasPlayer(username) && r.turns >= 50)
                .mapToInt((r) -> (r.isWin(username) ? 1 : 0)).average();
        if (winRateFFA.isPresent()) {
            statEmbed.appendDescription(String.format("\n**FFA Win Rate:** %.2f%%", winRateFFA.getAsDouble() * 100));
            int streakFFA = findWinStreak(replays.stream()
                    .filter((r) -> r.type.equals("classic") && r.hasPlayer(username) && r.turns >= 50)
                    .mapToInt((r) -> (r.isWin(username) ? 1 : 0)).iterator());
            statEmbed.appendDescription(String.format("\n**FFA Best Win Streak:** %d", streakFFA));
        }
        OptionalDouble avgPercentileFFA = replays.stream()
                .filter((r) -> r.type.equals("classic") && r.hasPlayer(username) && r.turns >= 50)
                .mapToDouble((r) -> r.getPercentile(username)).average();
        if (avgPercentileFFA.isPresent()) {
            statEmbed.appendDescription(
                    String.format("\n**FFA Avg. Percentile:** %.2f%%", avgPercentileFFA.getAsDouble() * 100));
        }
        return statEmbed.build();
    }

    @Command(name = "graph", subname = "add", desc = "Add user to graph", perms = Constants.Perms.USER)
    public static void handleGraphAdd(@NotNull SlashCommandEvent cmd,
                                      @CommandParameter(name = "username",
                                              desc = "The player to add") String username) {
        if (!REQUEST_LIMITER.tryAcquire()) {
            Utils.replyError(cmd, "Too many commands are being processed at once! Try again later.");
            return;
        }

        synchronized (REQUESTED_PLAYERS) {
            if (STORED_PLAYER_LIMIT <= REQUESTED_PLAYERS.get()) {
                Utils.replyError(cmd, "You can't add more than 6 players to the graph.");
                return;
            }
            REQUESTED_PLAYERS.incrementAndGet();
        }
        cmd.deferReply().queue();
        EXECUTOR.execute(() -> {
            List<ReplayResult> replays = ReplayStatistics.addPlayerToGraph(username);
            if (replays == null) {
                REQUESTED_PLAYERS.decrementAndGet();
                cmd.getHook().editOriginalEmbeds(
                        Utils.error(cmd, username + " was already added to the graph.")).queue();
            } else {
                cmd.getHook().editOriginalEmbeds(getStatEmbed(replays, username, true)).queue();
            }
            REQUEST_LIMITER.release();
        });
    }

    @Command(name = "graph", subname = "remove", desc = "Remove user from graph", perms = Constants.Perms.USER)
    public static void handleGraphRemove(@NotNull SlashCommandEvent cmd,
                                         @CommandParameter(name = "username",
                                                 desc = "The player to remove") String username) {
        cmd.deferReply().queue();
        if (!ReplayStatistics.removePlayerFromGraph(username)) {
            cmd.getHook().editOriginalEmbeds(Utils.error(cmd,
                    "Player's stats could not be removed since they are not stored.")).queue();
            return;
        }
        REQUESTED_PLAYERS.decrementAndGet();
        cmd.getHook().editOriginalEmbeds(new EmbedBuilder().setTitle("Successful Removal")
                .setDescription("Removed " + username + " from graph.").setColor(Constants.Colors.SUCCESS).build()).queue();
    }

    @Command(name = "graph", subname = "clear", desc = "Clear all users from graph", perms = Constants.Perms.USER)
    public static void handleGraphClear(@NotNull SlashCommandEvent cmd) {
        cmd.deferReply().queue();
        int decrease = ReplayStatistics.clearGraph();
        REQUESTED_PLAYERS.addAndGet(-decrease);
        cmd.getHook().editOriginalEmbeds(new EmbedBuilder().setTitle("Successful Removal")
                .setDescription("Cleared all users from graph.").setColor(Constants.Colors.SUCCESS).build()).queue();
    }

    @Command(name = "graph", subname = "show", perms = Constants.Perms.USER,
            desc = "Graph statistics of all stored players, either over time or games played.")
    public static void handleShowGraph(@NotNull SlashCommandEvent cmd,
                                       @CommandParameter(name = "mode",
                                               desc = "The game mode to consider",
                                               choices = {"1v1", "ffa"}) String mode,
                                       @CommandParameter(name = "x_axis",
                                               desc = "The scale to graph games against",
                                               choices = {"games", "time"}) String graph,
                                       @CommandParameter(name = "bucket_size",
                                               desc = "The number of games to average to find win rate",
                                               optional = true) Integer bucketSize,
                                       @CommandParameter(name = "star_min",
                                               desc = "The minimum number of stars an opponent can have for " +
                                                       "a game to be considered in the graph",
                                               optional = true) Integer starMin) {
        if (bucketSize == null) {
            bucketSize = 200;
        }
        if (bucketSize < 0) {
            Utils.replyError(cmd, "Bucket size must be positive.");
            return;
        }

        if (starMin != null) {
            if (mode.equals("ffa")) {
                Utils.replyError(cmd, "Cannot set star minimum for FFA games.");
                return;
            }
            if (starMin <= 0) {
                Utils.replyError(cmd, "Star minimum must be positive.");
                return;
            }
        } else {
            starMin = 0;
        }

        ReplayStatistics.GameMode gameMode;
        ReplayStatistics.XAxisOption xAxis;
        switch (mode) {
            case "1v1" -> gameMode = ReplayStatistics.GameMode.ONE_V_ONE;
            case "ffa" -> gameMode = ReplayStatistics.GameMode.FFA;
            default -> {
                Utils.replyError(cmd, "You must specify a valid game mode.");
                return;
            }
        }
        switch (graph) {
            case "time" -> xAxis = ReplayStatistics.XAxisOption.TIME;
            case "games" -> xAxis = ReplayStatistics.XAxisOption.GAMES_PLAYED;
            default -> {
                Utils.replyError(cmd, "You must specify whether you will graph over time or games played.");
                return;
            }
        }

        cmd.deferReply().queue();
        final int bucketSizeCapture = bucketSize;
        final int starMinCapture = starMin;
        EXECUTOR.execute(() -> {
            if (!REQUEST_LIMITER.tryAcquire()) {
                cmd.getHook().editOriginalEmbeds(
                                Utils.error(cmd, "Too many commands are being processed at once! Try again later."))
                        .queue();
                return;
            }
            JFreeChart chart = ReplayStatistics.graphStatTrend(xAxis, gameMode, bucketSizeCapture, starMinCapture);
            ByteArrayOutputStream bytes = renderGraph(cmd, chart);
            if (bytes == null) return;
            cmd.getHook().editOriginal(new MessageBuilder().setEmbeds(
                            new EmbedBuilder().setTitle("Stat Graph").setImage("attachment://graph.png").build()).build())
                    .addFile(bytes.toByteArray(), "graph.png").queue();
            REQUEST_LIMITER.release();
        });
    }

    @Command(name = "starhist", perms = Constants.Perms.USER,
            desc = "Graph 1v1 win rate of a player against star rating of opponents.")
    public static void handleStarHist(@NotNull SlashCommandEvent cmd,
                                      @CommandParameter(name = "username",
                                              desc = "The player whose win rate should be plotted") String username) {
        if (!REQUEST_LIMITER.tryAcquire()) {
            Utils.replyError(cmd, "Too many commands are being processed at once! Try again later.");
            return;
        }
        cmd.deferReply().queue();
        EXECUTOR.execute(() -> {
            JFreeChart chart = ReplayStatistics.graphStarHistogram(username);
            ByteArrayOutputStream bytes = renderGraph(cmd, chart);
            if (bytes == null) return;
            cmd.getHook().editOriginalEmbeds(new EmbedBuilder().setTitle("Star Graph")
                            .setImage("attachment://graph.png").build())
                    .addFile(bytes.toByteArray(), "graph.png").queue();
            REQUEST_LIMITER.release();
        });
    }

    private static ByteArrayOutputStream renderGraph(SlashCommandEvent cmd, JFreeChart chart) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(chart.createBufferedImage(800, 600), "png", bytes);
        } catch (IOException e) {
            cmd.getChannel().sendMessageEmbeds(Utils.error(cmd, "Could not create graph image."))
                    .queue();
            REQUEST_LIMITER.release();
            return null;
        }
        return bytes;
    }

    @Command(name = "winrecord", perms = Constants.Perms.USER,
            desc = "Show wins and losses of one player against another player in 1v1 matches.")
    public static void handleWinRecord(@NotNull SlashCommandEvent cmd,
                                       @CommandParameter(name = "username1",
                                               desc = "The first player") String username1,
                                       @CommandParameter(name = "username2",
                                               desc = "The second player") String username2) {
        if (!REQUEST_LIMITER.tryAcquire()) {
            Utils.replyError(cmd, "Too many commands are being processed at once! Try again later.");
            return;
        }
        cmd.deferReply().queue();
        EXECUTOR.execute(() -> {
            List<ReplayResult> replays = ReplayStatistics.getReplays(username1);
            replays = replays.stream().filter((r) -> r.hasPlayer(username2) && r.turns >= 50
                            && (r.type.equals("1v1") || r.type.equals("custom") && r.ranking.length == 2))
                    .collect(Collectors.toCollection(ArrayList::new));
            int wins1v1 = 0;
            int tot1v1 = 0;
            int winsCustom = 0;
            int totCustom = 0;
            for (ReplayResult r : replays) {
                if (r.type.equals("1v1")) {
                    if (r.isWin(username1))
                        wins1v1++;
                    tot1v1++;
                } else {
                    if (r.isWin(username1))
                        winsCustom++;
                    totCustom++;
                }
            }
            EmbedBuilder vsEmbed = new EmbedBuilder().setTitle(username1 + " vs. " + username2)
                    .setDescription("**In 1v1 Queue:** " + wins1v1 + "-" + (tot1v1 - wins1v1))
                    .appendDescription(
                            "\n**In Custom 1v1s:** " + winsCustom + "-" + (totCustom - winsCustom))
                    .appendDescription("\n**Total:** " + (wins1v1 + winsCustom) + "-"
                            + (totCustom + tot1v1 - winsCustom - wins1v1));
            if (replays.size() > 3) {
                vsEmbed.appendDescription("\n\n**Sample replays:**");
                for (int a = replays.size() - 1; a >= replays.size() - 3; a--) {
                    Collections.swap(replays, a, ThreadLocalRandom.current().nextInt(a + 1));
                    vsEmbed.appendDescription(
                            "\n<https://generals.io/replays/" + replays.get(a).id + ">");
                }
            }
            cmd.getHook().editOriginalEmbeds(vsEmbed.build()).queue();
            REQUEST_LIMITER.release();
        });
    }

    @Command(name = "getstats", desc = "Calculate a player's stats.", perms = Constants.Perms.USER)
    public static void handleGetStats(@NotNull SlashCommandEvent cmd,
                                      @CommandParameter(name = "username", desc = "The player whose stats should be " +
                                              "calculated")
                                              String username) {
        if (!REQUEST_LIMITER.tryAcquire()) {
            Utils.replyError(cmd, "Too many commands are being processed at once! Try again later.");
            return;
        }
        cmd.deferReply().queue();
        EXECUTOR.execute(() -> {
            List<ReplayResult> replays = ReplayStatistics.getReplays(username);
            cmd.getHook().editOriginalEmbeds(getStatEmbed(replays, username, false)).queue();
            REQUEST_LIMITER.release();
        });
    }

    @Command(name = "stats2v2", desc = "Calculate 2v2 win rate stats from recent games (starting from 1 day ago by default).", perms =
            Constants.Perms.USER)
    public static void handleStats2v2(@NotNull SlashCommandEvent cmd,
                                      @CommandParameter(name = "username",
                                              desc = "The player whose 2v2 stats should be calculated") String username,
                                      @CommandParameter(name = "days",
                                              desc = "The number of days to look back over.",
                                              optional = true) Integer days) {
        if (days != null && days <= 0) {
            Utils.replyError(cmd, "Number of days must be a positive number.");
            return;
        }
        if (!REQUEST_LIMITER.tryAcquire()) {
            Utils.replyError(cmd, "Too many commands are being processed at once! Try again later.");
            return;
        }
        cmd.deferReply().queue();
        EXECUTOR.execute(() -> {
            int checkDays = 1;
            if (days != null) {
                checkDays = days;
            }
            ReplayStatistics.SearchResult2v2 res2v2 = ReplayStatistics.processRecent2v2Replays(username, checkDays);
            ReplayStatistics.WinRecord2v2 overall = res2v2.partnerStats().get(username);
            if (overall == null) {
                cmd.getHook().editOriginalEmbeds(
                        Utils.error(cmd, "Could not find recent 2v2 games for " + username + ".")).queue();
            } else {
                EmbedBuilder embed2v2 = new EmbedBuilder().setTitle("2v2 Stats").setColor(Constants.Colors.SUCCESS).setDescription("Stats from after <t:" + Math.max(res2v2.lastChecked(), Instant.now().minus(checkDays, ChronoUnit.DAYS).getEpochSecond()) + ":f>");
                embed2v2.appendDescription(String.format("\n\n**Overall Record:** %dW-%dL (%.2f%%)", overall.wins(),
                        overall.totalPlayed() - overall.wins(),
                        100.0 * overall.wins() / overall.totalPlayed()));
                for (Map.Entry<String, ReplayStatistics.WinRecord2v2> entry : res2v2.partnerStats().entrySet()) {
                    String u = entry.getKey();
                    ReplayStatistics.WinRecord2v2 cur = entry.getValue();
                    if (u.equals(username))
                        continue;
                    embed2v2.appendDescription(String.format("\n**Record with %s:** %dW-%dL (%.2f%%)", u,
                            cur.wins(),
                            cur.totalPlayed() - cur.wins(), 100.0 * cur.wins() / cur.totalPlayed()));
                }
                cmd.getHook().editOriginalEmbeds(embed2v2.build()).queue();
            }
            REQUEST_LIMITER.release();
        });
    }
}