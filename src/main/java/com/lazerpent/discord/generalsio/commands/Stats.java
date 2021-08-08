package com.lazerpent.discord.generalsio.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jfree.chart.JFreeChart;

import com.lazerpent.discord.generalsio.*;
import com.lazerpent.discord.generalsio.Commands.Category;
import com.lazerpent.discord.generalsio.Commands.Command;
import com.lazerpent.discord.generalsio.ReplayStatistics.ReplayResult;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;

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

    private static String checkIfGraphRunnable(Message msg, String[] args) {
        if (args.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must provide a username.")).queue();
            return null;
        }
        String username = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        if (username.length() > 18) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Provided username is too long.")).queue();
            return null;
        }
        return username;
    }

    @Command(name = {"graph"}, cat = "stat", args = {"add | remove | clear"
            , "username"}, desc = "Add or remove users from graph.")
    public static void handleGraph(@NotNull Commands self, @NotNull Message msg, String[] args) {
        if (args.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Invalid graph command.")).queue();
            return;
        }
        switch (args[1]) {
            case "add":
                addToGraph(self, msg, Arrays.copyOfRange(args, 1, args.length));
                break;
            case "remove":
                removeFromGraph(self, msg, Arrays.copyOfRange(args, 1, args.length));
                break;
            case "clear":
                clearGraph(self, msg, Arrays.copyOfRange(args, 1, args.length));
                break;
            default:
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Invalid graph command.")).queue();
        }
    }

    public static void addToGraph(@NotNull Commands self, @NotNull Message msg, String[] args) {
        String username = checkIfGraphRunnable(msg, args);
        if (username == null) return;

        if (!REQUEST_LIMITER.tryAcquire()) {
            msg.getChannel()
                    .sendMessageEmbeds(
                            Utils.error(msg, "Too many commands are being processed at once! Try again later."))
                    .queue();
            return;
        }

        synchronized (REQUESTED_PLAYERS) {
            if (STORED_PLAYER_LIMIT <= REQUESTED_PLAYERS.get()) {
                msg.getChannel()
                        .sendMessageEmbeds(Utils.error(msg, "You can't add more than 6 players to the graph."))
                        .queue();
                return;
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
    }

    public static void removeFromGraph(@NotNull Commands self, @NotNull Message msg, String[] args) {
        String username = checkIfGraphRunnable(msg, args);
        if (username == null) return;
        if (!ReplayStatistics.removePlayerFromGraph(username)) {
            msg.getChannel()
                    .sendMessageEmbeds(
                            Utils.error(msg, "Player's stats could not be removed since they are not stored."))
                    .queue();
            return;
        }
        msg.getChannel().sendMessageEmbeds(new EmbedBuilder().setTitle("Successful Removal")
                .setDescription("Removed " + username + " from graph.").setColor(Constants.Colors.SUCCESS).build())
                .queue();
        REQUESTED_PLAYERS.decrementAndGet();
    }

    public static void clearGraph(@NotNull Commands self, @NotNull Message msg, String[] args) {
        if (args.length > 1) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "This command has no arguments.")).queue();
            return;
        }
        int decrease = ReplayStatistics.clearGraph();
        msg.getChannel().sendMessageEmbeds(new EmbedBuilder().setTitle("Successful Removal")
                .setDescription("Cleared all users from graph.").setColor(Constants.Colors.SUCCESS).build())
                .queue();
        REQUESTED_PLAYERS.addAndGet(-decrease);
    }

    @Command(name = {"showgraph"}, cat = "stat", args = {"gameMode",
            "games | time", "bucketSize?", "starMin?"}, desc = "Graph statistics of all stored players, either " +
                                                               "over time or games played.", perms =
            Constants.Perms.USER)
    public static void handleShowGraph(@NotNull Commands self, @NotNull Message msg, String[] args) {
        if (args.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must specify a game mode.")).queue();
            return;
        }
        String gameModeArg = args[1].toLowerCase();
        if (args.length < 3) {
            msg.getChannel()
                    .sendMessageEmbeds(
                            Utils.error(msg, "You must specify whether you will graph over time or games played."))
                    .queue();
            return;
        }
        if (args.length > 5) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Too many arguments.")).queue();
            return;
        }
        String xAxisArg = args[2].toLowerCase();
        int bucketSize = 200;
        if (args.length > 3) {
            try {
                bucketSize = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Bucket size must be an integer.")).queue();
                return;
            }
            if (bucketSize <= 0) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Bucket size must be positive.")).queue();
                return;
            }
        }
        int starMin = 0;
        if (args.length > 4) {
            if (gameModeArg.equals("ffa")) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Cannot set star minimum for FFA games.")).queue();
                return;
            }
            try {
                starMin = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Star minimum must be an integer.")).queue();
                return;
            }
            if (starMin <= 0) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Star minimum must be positive.")).queue();
                return;
            }
        }
        ReplayStatistics.GameMode gameMode;
        ReplayStatistics.XAxisOption xAxis;
        switch (gameModeArg) {
            case "1v1" -> gameMode = ReplayStatistics.GameMode.ONE_V_ONE;
            case "ffa" -> gameMode = ReplayStatistics.GameMode.FFA;
            default -> {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must specify a valid game mode.")).queue();
                return;
            }
        }
        switch (xAxisArg) {
            case "time" -> xAxis = ReplayStatistics.XAxisOption.TIME;
            case "games" -> xAxis = ReplayStatistics.XAxisOption.GAMES_PLAYED;
            default -> {
                msg.getChannel()
                        .sendMessageEmbeds(
                                Utils.error(msg, "You must specify whether you will graph over time or games " +
                                                 "played."))
                        .queue();
                return;
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
    }

    @Command(name = {"starhist"}, cat = "stat", args = {
            "username"}, desc = "Graph 1v1 win rate of a player against star rating of opponents.", perms =
            Constants.Perms.USER)
    public static void handleStarHist(@NotNull Commands self, @NotNull Message msg, String[] args) {
        String username = checkIfGraphRunnable(msg, args);
        if (username == null) return;
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

    @Command(name = {"winrecord"}, cat = "stat", args = {"username1",
            "username2"}, desc = "Show wins and losses of one player against another player in 1v1 matches.",
            perms = Constants.Perms.USER)
    public static void handleWinRecord(@NotNull Commands self, @NotNull Message msg, String[] args) {
        String argString = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Matcher match = Pattern.compile("(?<=<)[^<>]+(?=>)|[^<>\\s]+").matcher(argString);
        List<String> uArgs = match.results()
                .map(MatchResult::group)
                .collect(Collectors.toCollection(ArrayList::new));
        if (uArgs.size() > 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must provide exactly 2 usernames.")).queue();
            return;
        }
        String username1 = uArgs.get(0);
        String username2 = uArgs.get(1);
        if (username1.length() > 18 || username2.length() > 18) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Provided username is too long.")).queue();
            return;
        }
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

    @Command(name = {"getstats"}, cat = "stat", args = {
            "username"}, desc = "Calculate a player's stats.", perms = Constants.Perms.USER)
    public static void handleGetStats(@NotNull Commands self, @NotNull Message msg, String[] args) {
        String username = checkIfGraphRunnable(msg, args);
        if (username == null) return;
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

    @Command(name = {"stats2v2"}, cat = "stat", args = {
            "username"}, desc = "Calculate 2v2 win rate stats from recent games.", perms = Constants.Perms.USER)
    public static void handleStats2v2(@NotNull Commands self, @NotNull Message msg, String[] args) {
        if (args.length < 2) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must provide a username.")).queue();
            return;
        }
        String username = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        if (username.length() > 18) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Provided username is too long.")).queue();
            return;
        }
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