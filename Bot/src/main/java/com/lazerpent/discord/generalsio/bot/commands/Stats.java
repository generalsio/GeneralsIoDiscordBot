package com.lazerpent.discord.generalsio.bot.commands;

import com.lazerpent.discord.generalsio.bot.Commands.Command;
import com.lazerpent.discord.generalsio.bot.Commands.Optional;
import com.lazerpent.discord.generalsio.bot.Commands.Selection;
import com.lazerpent.discord.generalsio.bot.Constants;
import com.lazerpent.discord.generalsio.bot.ReplayStatistics;
import com.lazerpent.discord.generalsio.bot.ReplayStatistics.ReplayResult;
import com.lazerpent.discord.generalsio.bot.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jfree.chart.JFreeChart;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.lazerpent.discord.generalsio.bot.Commands.Category;

@Category(cat = "stat", name = "Stats")
public class Stats {
    private static final Semaphore REQUEST_LIMITER = new Semaphore(20);
    private static final AtomicInteger REQUESTED_PLAYERS = new AtomicInteger();
    private static final int STORED_PLAYER_LIMIT = 6;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

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
            statEmbed
                    .appendDescription(String.format("\n**1v1 Win Rate:** %.2f%%", winRate1v1.getAsDouble() * 100));
        }
        OptionalDouble winRateFFA = replays.stream()
                .filter((r) -> r.type.equals("classic") && r.hasPlayer(username) && r.turns >= 50)
                .mapToInt((r) -> (r.isWin(username) ? 1 : 0)).average();
        if (winRateFFA.isPresent()) {
            statEmbed
                    .appendDescription(String.format("\n**FFA Win Rate:** %.2f%%", winRateFFA.getAsDouble() * 100));
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

    @Command(name = {"graph"}, cat = "stat", desc = "Add or remove users from graph.", perms = Constants.Perms.USER)
    public static Object handleGraph(@NotNull Message msg,
                                     @Selection({"add", "remove"}) String option,
                                     String username) {
        return switch (option) {
            case "add" -> addToGraph(msg, username);
            case "remove" -> removeFromGraph(msg, username);
            default -> Utils.error(msg, "Invalid graph command");
        };
    }

    @Command(name = {"graph"}, cat = "stat", desc = "Add or remove users from graph.", perms = Constants.Perms.USER)
    public static MessageEmbed handleGraph(@NotNull Message msg, @Selection({"clear"}) String clear) {
        int decrease = ReplayStatistics.clearGraph();
        REQUESTED_PLAYERS.addAndGet(-decrease);
        return new EmbedBuilder().setTitle("Successful Removal")
                .setDescription("Cleared all users from graph.").setColor(Constants.Colors.SUCCESS).build();
    }

    public static MessageEmbed addToGraph(@NotNull Message msg, String username) {
        if (!REQUEST_LIMITER.tryAcquire()) {
            return Utils.error(msg, "Too many commands are being processed at once! Try again later.");
        }

        synchronized (REQUESTED_PLAYERS) {
            if (STORED_PLAYER_LIMIT <= REQUESTED_PLAYERS.get()) {
                return Utils.error(msg, "You can't add more than 6 players to the graph.");
            }
            REQUESTED_PLAYERS.incrementAndGet();
        }
        msg.getChannel().sendMessageEmbeds(
                        new EmbedBuilder().setTitle("Loading Stats").setDescription("It might take a few moments.").build())
                .queue((loadMsg) -> EXECUTOR.execute(() -> {
                    List<ReplayResult> replays = ReplayStatistics.addPlayerToGraph(username);
                    if (replays == null) {
                        REQUESTED_PLAYERS.decrementAndGet();
                        msg.getChannel()
                                .sendMessageEmbeds(Utils.error(msg, username + " was already added to the graph."))
                                .queue();
                    } else {
                        msg.getChannel().sendMessageEmbeds(getStatEmbed(replays, username, true)).queue();
                    }
                    loadMsg.delete().complete();
                    REQUEST_LIMITER.release();
                }));
        return null;
    }

    public static MessageEmbed removeFromGraph(@NotNull Message msg, String username) {
        if (!ReplayStatistics.removePlayerFromGraph(username)) {
            return Utils.error(msg, "Player's stats could not be removed since they are not stored.");
        }
        REQUESTED_PLAYERS.decrementAndGet();
        return new EmbedBuilder().setTitle("Successful Removal")
                .setDescription("Removed " + username + " from graph.").setColor(Constants.Colors.SUCCESS).build();
    }

    @Command(name = {"showgraph"}, cat = "stat", perms = Constants.Perms.USER,
            desc = "Graph statistics of all stored players, either over time or games played.")
    public static MessageEmbed handleShowGraph(@NotNull Message msg,
                                               @Selection({"1v1", "ffa"}) String mode,
                                               @Selection({"game", "time"}) String graph,
                                               @Optional Integer bucketSize, @Optional Integer starMin) {
        if (bucketSize == null) {
            bucketSize = 200;
        }
        if (bucketSize < 0) {
            return Utils.error(msg, "Bucket size must be positive.");
        }

        if (starMin != null) {
            if (mode.equals("ffa")) {
                return Utils.error(msg, "Cannot set star minimum for FFA games.");
            }
            if (starMin <= 0) {
                return Utils.error(msg, "Star minimum must be positive.");
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
                return Utils.error(msg, "You must specify a valid game mode.");
            }
        }
        switch (graph) {
            case "time" -> xAxis = ReplayStatistics.XAxisOption.TIME;
            case "games" -> xAxis = ReplayStatistics.XAxisOption.GAMES_PLAYED;
            default -> {
                return Utils.error(msg, "You must specify whether you will graph over time or games " +
                                        "played.");
            }
        }

        final int bucketSizeCapture = bucketSize;
        final int starMinCapture = starMin;
        EXECUTOR.execute(() -> {
            if (!REQUEST_LIMITER.tryAcquire()) {
                msg.getChannel()
                        .sendMessageEmbeds(
                                Utils.error(msg, "Too many commands are being processed at once! Try again later."))
                        .queue();
                return;
            }
            JFreeChart chart = ReplayStatistics.graphStatTrend(xAxis, gameMode, bucketSizeCapture, starMinCapture);
            ByteArrayOutputStream bytes = renderGraph(msg, chart);
            if (bytes == null) return;
            msg.getChannel()
                    .sendMessageEmbeds(
                            new EmbedBuilder().setTitle("Stat Graph").setImage("attachment://graph.png").build())
                    .addFile(bytes.toByteArray(), "graph.png").queue();
            REQUEST_LIMITER.release();
        });
        return null;
    }

    @Command(name = {"starhist"}, cat = "stat", perms = Constants.Perms.USER,
            desc = "Graph 1v1 win rate of a player against star rating of opponents.")
    public static void handleStarHist(@NotNull Message msg, String username) {
        if (!REQUEST_LIMITER.tryAcquire()) {
            msg.getChannel()
                    .sendMessageEmbeds(
                            Utils.error(msg, "Too many commands are being processed at once! Try again later."))
                    .queue();
            return;
        }
        msg.getChannel().sendMessageEmbeds(
                        new EmbedBuilder().setTitle("Loading graph").setDescription("It might take a few moments.").build())
                .queue((loadMsg) -> EXECUTOR.execute(() -> {
                    JFreeChart chart = ReplayStatistics.graphStarHistogram(username);
                    ByteArrayOutputStream bytes = renderGraph(msg, chart);
                    if (bytes == null) return;
                    msg.getChannel()
                            .sendMessageEmbeds(new EmbedBuilder().setTitle("Star Graph")
                                    .setImage("attachment://graph.png").build())
                            .addFile(bytes.toByteArray(), "graph.png").queue();
                    loadMsg.delete().complete();
                    REQUEST_LIMITER.release();
                }));
    }

    private static ByteArrayOutputStream renderGraph(Message msg, JFreeChart chart) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(chart.createBufferedImage(800, 600), "png", bytes);
        } catch (IOException e) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Could not create graph image."))
                    .queue();
            REQUEST_LIMITER.release();
            return null;
        }
        return bytes;
    }

    @Command(name = {"winrecord"}, cat = "stat", perms = Constants.Perms.USER,
            desc = "Show wins and losses of one player against another player in 1v1 matches.")
    public static void handleWinRecord(@NotNull Message msg, String username1, String username2) {
        if (!REQUEST_LIMITER.tryAcquire()) {
            msg.getChannel()
                    .sendMessageEmbeds(
                            Utils.error(msg, "Too many commands are being processed at once! Try again later."))
                    .queue();
            return;
        }
        msg.getChannel().sendMessageEmbeds(new EmbedBuilder().setTitle("Loading Win Record")
                .setDescription("It might take a few moments.").build()).queue((loadMsg) -> EXECUTOR.execute(() -> {
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
            loadMsg.delete().complete();
            msg.getChannel().sendMessageEmbeds(vsEmbed.build()).queue();
            REQUEST_LIMITER.release();
        }));
    }

    @Command(name = {"getstats"}, cat = "stat", desc = "Calculate a player's stats.", perms = Constants.Perms.USER)
    public static void handleGetStats(@NotNull Message msg, String username) {
        if (!REQUEST_LIMITER.tryAcquire()) {
            msg.getChannel()
                    .sendMessageEmbeds(
                            Utils.error(msg, "Too many commands are being processed at once! Try again later."))
                    .queue();
            return;
        }
        msg.getChannel().sendMessageEmbeds(
                        new EmbedBuilder().setTitle("Loading Stats").setDescription("It might take a few moments.").build())
                .queue((loadMsg) -> EXECUTOR.execute(() -> {
                    List<ReplayResult> replays = ReplayStatistics.getReplays(username);
                    loadMsg.delete().complete();
                    msg.getChannel().sendMessageEmbeds(getStatEmbed(replays, username, false)).queue();
                    REQUEST_LIMITER.release();
                }));
    }

    @Command(name = {"stats2v2"}, cat = "stat", desc = "Calculate 2v2 win rate stats from recent games.", perms =
            Constants.Perms.USER)
    public static void handleStats2v2(@NotNull Message msg, String username) {
        if (!REQUEST_LIMITER.tryAcquire()) {
            msg.getChannel()
                    .sendMessageEmbeds(
                            Utils.error(msg, "Too many commands are being processed at once! Try again later."))
                    .queue();
            return;
        }
        EXECUTOR.execute(() -> {
            Map<String, Pair<Integer, Integer>> res2v2 = ReplayStatistics.processRecent2v2Replays(username);
            Pair<Integer, Integer> overall = res2v2.get(username);
            if (overall == null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg,
                        "Could not find recent 2v2 games for " + username + ".")).queue();
            } else {
                EmbedBuilder embed2v2 = new EmbedBuilder().setTitle("2v2 Stats").setColor(Constants.Colors.SUCCESS)
                        .setDescription(String.format("\n**Overall Record:** %dW-%dL (%.2f%%)", overall.getLeft(),
                                overall.getRight() - overall.getLeft(),
                                100.0 * overall.getLeft() / overall.getRight()));
                for (String u : res2v2.keySet()) {
                    if (u.equals(username))
                        continue;
                    Pair<Integer, Integer> cur = res2v2.get(u);
                    embed2v2.appendDescription(String.format("\n**Record with %s:** %dW-%dL (%.2f%%)", u,
                            cur.getLeft(),
                            cur.getRight() - cur.getLeft(), 100.0 * cur.getLeft() / cur.getRight()));
                }
                msg.getChannel().sendMessageEmbeds(embed2v2.build()).queue();
            }
            REQUEST_LIMITER.release();
        });
    }
}