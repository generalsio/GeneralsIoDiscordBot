package com.lazerpent.discord.generalsio;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.tuple.Pair;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.StandardChartTheme;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.DataFormatException;

public class ReplayStatistics {
    public static final Map<String, List<ReplayResult>> storedReplays = new HashMap<>();
    public static final StandardChartTheme theme = (StandardChartTheme) StandardChartTheme.createJFreeTheme();
    public static final Semaphore replayRequestSem = new Semaphore(250);
    public static final Semaphore replayIndividualSem = new Semaphore(250);

    static {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(Font.createFont(Font.TRUETYPE_FONT,
                    new File(Objects.requireNonNull(
                            Main.class.getClassLoader().getResource("Quicksand-Regular.ttf")).getFile())));
        } catch (Exception e) {
            System.out.println("Couldn't load font for graphs: " + e.getMessage());
        }
        String fontName = "Quicksand";
        theme.setExtraLargeFont(new Font(fontName, Font.BOLD, 28));
        theme.setLargeFont(new Font(fontName, Font.BOLD, 24));
        theme.setRegularFont(new Font(fontName, Font.BOLD, 20));
        theme.setSmallFont(new Font(fontName, Font.BOLD, 20));
        theme.setTitlePaint(Color.WHITE);
        theme.setSubtitlePaint(Color.WHITE);
        theme.setLegendItemPaint(Color.WHITE);
        theme.setAxisLabelPaint(Color.WHITE);
        theme.setBaselinePaint(Color.WHITE);
        theme.setItemLabelPaint(Color.WHITE);
        theme.setTickLabelPaint(Color.WHITE);
        theme.setRangeGridlinePaint(new Color(0xE0E0E0));
        theme.setChartBackgroundPaint(new Color(0x36393f));
        theme.setPlotBackgroundPaint(new Color(0x36393f));
        theme.setLegendBackgroundPaint(new Color(0.0f, 0.0f, 0.0f));
    }

    public static JsonArray makeReplayRequest(String username, int offset, int count) throws IOException,
            InterruptedException {
        replayRequestSem.acquire();
        int maxRetry = 5;
        IOException lastException = null;
        for (int retryCount = 0; retryCount < maxRetry; retryCount++) {
            if (retryCount != 0) {
                System.out.println("Failed to retrieve replays. Retrying... (Try #" + (retryCount + 1) + ")");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                    replayRequestSem.release();
                    throw e1;
                }
            }
            try {
                URL replayCheck = new URL("https://generals.io/api/replaysForUsername?u=" +
                                          URLEncoder.encode(username, StandardCharsets.UTF_8) +
                                          "&offset=" + offset + "&count=" + count);
                try (InputStream replays = replayCheck.openStream()) {
                    if (retryCount > 0) {
                        System.out.println("Retry success on Try #" + (retryCount + 1));
                    }
                    replayRequestSem.release();
                    return JsonParser.parseReader(
                            new InputStreamReader(replays, StandardCharsets.UTF_8)).getAsJsonArray();
                }
            } catch (IOException e) {
                lastException = e;
            }
        }
        System.out.println("Couldn't retrieve replays.");
        replayRequestSem.release();
        throw lastException;
    }

    public static List<ReplayResult> getReplays(String username, int count, int offset) {
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Callable<JsonArray>> replayCollectTasks = new ArrayList<>();
        for (int a = 0; a < (count - 1) / 200 + 1; a++) {
            int id = a;
            replayCollectTasks.add(() -> makeReplayRequest(username, offset + id * 200, Math.min(200,
                    count - 200 * id)));
        }
        List<Future<JsonArray>> replayCollectResults;
        try {
            replayCollectResults = executor.invokeAll(replayCollectTasks);
        } catch (InterruptedException e2) {
            System.out.println("Interrupted while retrieving replays: " + e2.getMessage());
            return new ArrayList<>();
        }
        List<ReplayResult> res = new ArrayList<>();
        for (Future<JsonArray> future : replayCollectResults) {
            try {
                Gson gson = new Gson();
                res.addAll(StreamSupport.stream(future.get().spliterator(), false)
                        .map((e) -> gson.fromJson(e, ReplayResult.class)).toList());
            } catch (InterruptedException e1) {
                System.out.println("Interrupted while retrieving replays: " + e1.getMessage());
                return new ArrayList<>();
            } catch (ExecutionException e1) {
                System.out.println("Could not retrieve replays: " + e1.getMessage());
                return new ArrayList<>();
            }
        }
        return res;
    }

    public static List<ReplayResult> getReplays(String username, int count) {
        return getReplays(username, count, 0);
    }

    public static List<ReplayResult> getReplays(String username) {
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Callable<JsonArray>> replayCountTasks = new ArrayList<>();
        for (int a = 0; a < 18; a++) {
            int id = a;
            replayCountTasks.add(() -> makeReplayRequest(username, (1 << id) - 1, 50));
        }
        List<Future<JsonArray>> replayCountResults;
        try {
            replayCountResults = executor.invokeAll(replayCountTasks);
        } catch (InterruptedException e2) {
            System.out.println("Interrupted while retrieving replays: " + e2.getMessage());
            return new ArrayList<>();
        }
        int szEstimate = 1;
        for (Future<JsonArray> future : replayCountResults) {
            try {
                if (future.get().size() == 0) {
                    break;
                }
            } catch (InterruptedException e1) {
                System.out.println("Interrupted while retrieving replays: " + e1.getMessage());
                return new ArrayList<>();
            } catch (ExecutionException e1) {
                System.out.println("Could not retrieve replays: " + e1.getMessage());
                return new ArrayList<>();
            }
            szEstimate *= 2;
        }
        return getReplays(username, szEstimate);
    }

    public static List<ReplayResult> addPlayerToGraph(String username) {
        synchronized (storedReplays) {
            if (storedReplays.containsKey(username)) {
                return null;
            }
            List<ReplayResult> replays = getReplays(username);
            storedReplays.put(username, replays);
            return replays;
        }
    }

    public static boolean removePlayerFromGraph(String username) {
        synchronized (storedReplays) {
            if (storedReplays.containsKey(username)) {
                storedReplays.remove(username);
                return true;
            }
            return false;
        }
    }

    public static int clearGraph() {
        synchronized (storedReplays) {
            int res = storedReplays.size();
            storedReplays.clear();
            return res;
        }
    }

    public static JFreeChart graphStatTrend(XAxisOption xAxis, GameMode gameMode, int bucketSize, int starMin) {
        String typeName;
        String xLabel;
        String yLabel;
        String title;
        if (gameMode == GameMode.FFA) {
            typeName = "classic";
            yLabel = "Average Percentile of Past " + bucketSize + " Games";
            title = "Moving Average of FFA Percentile";
        } else {
            typeName = "1v1";
            yLabel = "Average Win Rate of Past " + bucketSize + " Games";
            title = "Moving Average of 1v1 Win Rate";
        }
        if (xAxis == XAxisOption.GAMES_PLAYED) {
            xLabel = "Games played";
        } else {
            xLabel = "Time";
        }
        JFreeChart chart;
        Map<String, List<ReplayResult>> replayLookup = new HashMap<>();
        storedReplays.forEach((key, value) -> {
            List<ReplayResult> replays = value.stream()
                    .filter((r) -> r.type.equals(typeName) && r.turns >= 50 && r.hasPlayer(key) && (!r.type.equals(
                            "1v1") || r.getOpponentStars(key) >= starMin))
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.reverse(replays);
            replayLookup.put(key, replays);
        });

        if (xAxis == XAxisOption.GAMES_PLAYED) {
            XYSeriesCollection xyData = new XYSeriesCollection();
            for (String u : replayLookup.keySet()) {
                XYSeries individualData = new XYSeries(u);
                List<ReplayResult> replays = replayLookup.get(u);
                double sum = 0;
                for (int a = 0; a < replays.size(); a++) {
                    if (gameMode == GameMode.ONE_V_ONE) {
                        sum += (replays.get(a).isWin(u) ? 1 : 0);
                    } else {
                        sum += replays.get(a).getPercentile(u);
                    }
                    if (bucketSize <= a) {
                        if (gameMode == GameMode.ONE_V_ONE) {
                            sum -= (replays.get(a - bucketSize).isWin(u) ? 1 : 0);
                        } else {
                            sum -= replays.get(a - bucketSize).getPercentile(u);
                        }
                    }
                    if (bucketSize <= a + 1) {
                        individualData.add(a, sum / bucketSize);
                    }
                }
                xyData.addSeries(individualData);
            }
            chart = ChartFactory.createXYLineChart(
                    title,
                    xLabel,
                    yLabel,
                    xyData);
        } else {
            TimeSeriesCollection timeData = new TimeSeriesCollection();
            for (String u : replayLookup.keySet()) {
                TimeSeries individualData = new TimeSeries(u);
                List<ReplayResult> replays = replayLookup.get(u);
                double sum = 0;
                for (int a = 0; a < replays.size(); a++) {
                    if (gameMode != GameMode.ONE_V_ONE) {
                        sum += replays.get(a).getPercentile(u);
                    } else {
                        sum += (replays.get(a).isWin(u) ? 1 : 0);
                    }
                    if (bucketSize <= a) {
                        if (gameMode == GameMode.ONE_V_ONE) {
                            sum -= (replays.get(a - bucketSize).isWin(u) ? 1 : 0);
                        } else {
                            sum -= replays.get(a - bucketSize).getPercentile(u);
                        }
                    }
                    if (bucketSize <= a + 1) {
                        individualData.add(new Second(new Date(replays.get(a).started)), sum / bucketSize);
                    }
                }
                timeData.addSeries(individualData);
            }
            chart = ChartFactory.createTimeSeriesChart(
                    title,
                    xLabel,
                    yLabel,
                    timeData);
        }
        theme.apply(chart);
        XYPlot plot = (XYPlot) chart.getPlot();
        NumberAxis verticalAxis = (NumberAxis) plot.getRangeAxis();
        verticalAxis.setAutoRangeIncludesZero(false);
        verticalAxis.setNumberFormatOverride(new DecimalFormat("#%"));
        plot.getDomainAxis().setAutoRange(true);
        plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
        XYLineAndShapeRenderer render = (XYLineAndShapeRenderer) plot.getRenderer();
        render.setDefaultStroke(new BasicStroke(3.0f));
        render.setAutoPopulateSeriesStroke(false);
        return chart;
    }

    public static JFreeChart graphStarHistogram(String username) {
        List<ReplayResult> replays = getReplays(username);
        replays = replays.stream()
                .filter((r) -> r.type.equals("1v1") && r.turns >= 50 && r.hasPlayer(username))
                .collect(Collectors.toCollection(ArrayList::new));
        replays.sort(Comparator.comparingInt(r -> r.getOpponentStars(username)));
        DefaultCategoryDataset data = new DefaultCategoryDataset();
        for (int starMin = 0, idx = 0; idx < replays.size(); starMin += StarRange.STAR_INTERVAL) {
            int tot = 0;
            int wins = 0;
            for (; idx < replays.size() && replays.get(idx).getOpponentStars(username) < starMin + 10; idx++) {
                if (replays.get(idx).isWin(username)) wins++;
                tot++;
            }
            data.addValue(1.0 * wins / tot, username, new StarRange(starMin));
        }
        JFreeChart chart = ChartFactory.createBarChart(
                "1v1 Win Rate by Star Rating Range",
                "Star Range",
                "Win Rate",
                data);
        theme.apply(chart);
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        BarRenderer render = (BarRenderer) plot.getRenderer();
        plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        render.setBarPainter(new StandardBarPainter());
        render.setSeriesPaint(0, new Color(0x00B0B0));
        NumberAxis verticalAxis = (NumberAxis) plot.getRangeAxis();
        verticalAxis.setNumberFormatOverride(new DecimalFormat("#%"));
        return chart;
    }

    public static Replay getReplayFile(String id) {
        try {
            replayIndividualSem.acquire();
        } catch (InterruptedException e) {
            System.out.println("Interrupted while waiting for individual replay semaphore (" + id + "): " + e.getMessage());
            return null;
        }
        URL replayURL;
        try {
            replayURL = new URL("https://generalsio-replays-na.s3.amazonaws.com/" + id +
                                ".gior");
        } catch (MalformedURLException e) {
            replayIndividualSem.release();
            System.out.println("Could not acquire replay " + id + ": " + e.getMessage());
            return null;
        }
        replayIndividualSem.release();
        try (InputStream compressedReplay = replayURL.openStream()) {
            return new Replay(JsonParser.parseString(
                    LZStringImpl.decodeCompressedReplay(compressedReplay)).getAsJsonArray());
        } catch (IOException | JsonSyntaxException | DataFormatException e) {
            System.out.println("Could not decode replay " + id + ": " + e.getMessage());
            return null;
        }
    }

    public static Map<ReplayResult, Replay> convertReplayFiles(List<ReplayResult> reps) {
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            List<Future<Map.Entry<ReplayResult, Replay>>> repFiles = executor.invokeAll(reps.stream()
                    .map((r) -> (Callable<Map.Entry<ReplayResult, Replay>>) () -> Map.entry(r,
                            Objects.requireNonNull(getReplayFile(r.id)))).toList());
            return repFiles.stream().map((r) -> {
                try {
                    return r.get();
                } catch (InterruptedException e1) {
                    System.out.println("Interrupted while requesting replay files.");
                    return null;
                } catch (ExecutionException e2) {
                    System.out.println("Could not execute replay file requests.");
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (InterruptedException e) {
            System.out.println("Interrupted while requesting replay files.");
            return new HashMap<>();
        }
    }

    public static Map<String, Pair<Integer, Integer>> processRecent2v2Replays(String username) {
        Map<String, Pair<Integer, Integer>> res = new ConcurrentHashMap<>();
        List<ReplayResult> replayResults = getReplays(username, 200, 0);
        if (replayResults.size() == 0) {
            return new HashMap<>();
        }
        replayResults = replayResults.parallelStream()
                .filter((r) -> r.type.equals("2v2") || r.type.equals("custom") && r.ranking.length == 4)
                .toList();
        convertReplayFiles(replayResults).forEach((key, curReplay) -> {
            String winner = key.ranking[0].name;
            if (winner == null) return;
            if (curReplay.teams != null) {
                int winIdx = 0;
                int playerIdx = 0;
                for (int a = 0; a < curReplay.usernames.length; a++) {
                    if (curReplay.usernames[a].equals(winner)) {
                        winIdx = a;
                    }
                    if (curReplay.usernames[a].equals(username)) {
                        playerIdx = a;
                    }
                }
                String partner = "";
                Set<Integer> s = new HashSet<>();
                boolean not2v2 = false;
                for (int a = 0; a < curReplay.teams.length; a++) {
                    if (curReplay.teams[a] == curReplay.teams[playerIdx] && a != playerIdx) {
                        if (!partner.equals("")) {
                            not2v2 = true;
                            break;
                        }
                        partner = curReplay.usernames[a];
                    }
                    s.add(curReplay.teams[a]);
                }
                if (not2v2 || s.size() != 2 || partner.equals("")) return;
                res.merge(partner,
                        Pair.of((curReplay.teams[winIdx] == curReplay.teams[playerIdx] ? 1 :
                                0), 1), (a, b) -> Pair.of(a.getLeft() + b.getLeft(),
                                a.getRight() + b.getRight()));
                res.merge(username,
                        Pair.of((curReplay.teams[winIdx] == curReplay.teams[playerIdx] ? 1 :
                                0), 1), (a, b) -> Pair.of(a.getLeft() + b.getLeft(),
                                a.getRight() + b.getRight()));
            }
        });
        return res;
    }

    public enum GameMode {
        ONE_V_ONE,
        FFA,
    }

    public enum XAxisOption {
        TIME,
        GAMES_PLAYED,
    }

    @SuppressWarnings("unused")
    public static class ReplayResult {
        public String type;
        public String id;
        public long started;
        public int turns;
        public Player[] ranking;

        public double getPercentile(String username) {
            for (int a = 0; a < ranking.length; a++) {
                if (ranking[a].name != null && ranking[a].name.equals(username)) {
                    return 1.0 * (ranking.length - a) / ranking.length;
                }
            }
            return 0;
        }

        public boolean hasPlayer(String username) {
            for (Player p : ranking) {
                if (p.name != null && p.name.equals(username)) {
                    return true;
                }
            }
            return false;
        }

        public boolean isWin(String username) {
            if (ranking[0].name == null) {
                return false;
            }
            return ranking[0].name.equals(username);
        }

        public int getOpponentStars(String username) {
            if (ranking.length > 2) {
                return -1; // meant for 1v1
            }
            return ranking[ranking[0].name != null && ranking[0].name.equals(username) ? 1 : 0].stars;
        }

        public static class Player {
            public String name;
            public int stars;
        }
    }

    public record StarRange(int min) implements Comparable<StarRange> {
        public static final int STAR_INTERVAL = 10;

        @Override
        public int compareTo(StarRange other) {
            return this.min - other.min;
        }

        @Override
        public String toString() {
            return min + "-" + (min + 10);
        }
    }
}
