package com.lazerpent.discord.generalsio.bot.commands;

import com.lazerpent.discord.generalsio.bot.Bot;
import com.lazerpent.discord.generalsio.bot.Commands.Category;
import com.lazerpent.discord.generalsio.bot.Commands.Command;
import com.lazerpent.discord.generalsio.bot.Constants;
import com.lazerpent.discord.generalsio.bot.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Category(name = "Announcements")
public class Announcements {
    private final static TournamentData[] WINNER_DATA = {
            new TournamentData("First Generals.io 1v1 Tournament",
                    "March 18, 2017",
                    "I really like the simplicity of the game.",
                    null,
                    "President Trump",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("Second Generals.io 1v1 Tournament",
                    "June 17, 2017",
                    "I don't have any quotes",
                    null,
                    "Spraget",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("Third Generals.io 1v1 Tournament",
                    "July 29, 2017",
                    "I don't have any quotes",
                    null,
                    "Spraget",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("1v1 Championship (Season 1)",
                    "April 15, 2018",
                    "I don't have any quotes",
                    null,
                    "Spraget",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("2v2 Tournament #1",
                    "June 23, 2018",
                    null,
                    null,
                    "Spraget",
                    "President Trump",
                    null,
                    null,
                    null,
                    null,
                    EventType.Twos),
            new TournamentData("1v1 Championship (Season 2)",
                    "June 30, 2018",
                    "I don't have any quotes",
                    null,
                    "Spraget",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("FFA Tournament #1",
                    "August 4, 2018",
                    null,
                    null,
                    "Tiks",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.FFA),
            new TournamentData("1v1 Revival Tournament #1",
                    "May 9, 2020",
                    null,
                    null,
                    "MeltedToast",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("1v1 Revival Tournament #2",
                    "May 23, 2020",
                    "I don't have any quotes",
                    null,
                    "Spraget",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("1v1 Revival Tournament #3",
                    "June 6, 2020",
                    null,
                    null,
                    "MeltedToast",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("1v1 Revival Tournament #4",
                    "August 15, 2020",
                    "I don't have any quotes",
                    null,
                    "Spraget",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("1v1 Revival Tournament #5",
                    "August 29, 2020",
                    null,
                    null,
                    "peacemaker 2",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("2v2 Tournament #2",
                    "September 12, 2020",
                    null,
                    null,
                    "Spraget",
                    "Serge 2",
                    null,
                    null,
                    null,
                    null,
                    EventType.Twos),
            new TournamentData("2v2 Tournament #3",
                    "October 24, 2020",
                    "Utter waste of my time. Spraget and Fakuku didn't even stand a chance in the finals. " +
                            "Did we even lose a single game in the bracket? OMEGALUL",
                    null,
                    "syLph",
                    "MeltedToast",
                    "Replay",
                    "https://generals.io/replays/rcUlu1fuP",
                    "syLphReplay1.png",
                    null,
                    EventType.Twos),
            new TournamentData("1v1 Revival Tournament #6",
                    "November 21, 2020",
                    null,
                    null,
                    "MeltedToast",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("FFA Tournament #2",
                    "January 23, 2021",
                    null,
                    null,
                    "fakuku",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.FFA),
            new TournamentData("1v1 Equinox Championship",
                    "March 20, 2021",
                    null,
                    null,
                    "MeltedToast",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("2v2 Tournament #4",
                    "April 17, 2021",
                    "syLph is bad.",
                    "Ayy-lemao. Peacemaker deserved this one.",
                    "bucknuggetzzzz",
                    "syLph",
                    "Replay",
                    "https://generals.io/replays/rdEVSTOL_",
                    "syLphReplay2.png",
                    null,
                    EventType.Twos),
            new TournamentData("1v1 Championship (Season 17)",
                    "May 8, 2021",
                    "Imagine 'gerrymandering' the finals despite losing 5-4.",
                    null,
                    "Ethryn",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("World Turtle Day FFA Tournament",
                    "May 23, 2021",
                    null,
                    null,
                    "fakuku",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.FFA),
            new TournamentData("1v1 Ultimate Tournament",
                    "June 26, 2021",
                    null,
                    null,
                    "peacemaker 2",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Custom),
            new TournamentData("1v1v1v1 Random Arena",
                    "June 26, 2021",
                    null,
                    null,
                    "fakuku",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Custom),
            new TournamentData("Plots Lazerpent Tournament",
                    "June 26, 2021",
                    null,
                    null,
                    "peacemaker 2",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Custom),
            new TournamentData("2v2 Tournament #5",
                    "July 18, 2021",
                    null,
                    "syLph is bad.",
                    "Spraget",
                    "bucknuggetzzzz",
                    null,
                    null,
                    null,
                    "Spraget",
                    EventType.Twos),
            new TournamentData("1v1 Championship (Season 18)",
                    "July 31, 2021",
                    null,
                    null,
                    "MeltedToast",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("Cool FFA Tournament",
                    "August 21, 2021",
                    null,
                    null,
                    "small hen",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.FFA),
            new TournamentData("2v2 Tournament #6",
                    "September 4, 2021",
                    null,
                    "syLph is bad.",
                    "Serge 2",
                    "bucknuggetzzzz",
                    null,
                    null,
                    null,
                    null,
                    EventType.Twos),
            new TournamentData("Capy Cup KoTH",
                    "September 19, 2021",
                    null,
                    null,
                    "Wuped",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Custom),
            new TournamentData("Capy Cup Vision 2v2",
                    "September 19, 2021",
                    null,
                    null,
                    "Wuped",
                    "Girlpower",
                    null,
                    null,
                    null,
                    "Wupuey",
                    EventType.Custom),
            new TournamentData("Capy Cup Bogless",
                    "September 19, 2021",
                    "I don't have any quotes",
                    null,
                    "Spraget",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Custom),
            new TournamentData("1v1 Championship (Season 19)",
                    "October 2, 2021",
                    null,
                    null,
                    "MeltedToast",
                    null,
                    null,
                    null,
                    null,
                    null,
                    EventType.Ones),
            new TournamentData("2v2 Tournament #7",
                    "October 30, 2021",
                    "I feel like all the good players messaged each other and decided to let me win one for once, thanks y’all.",
                    null,
                    "matt",
                    "zzd233",
                    null,
                    null,
                    null,
                    "Baidu",
                    EventType.Twos),
    };
    private static BufferedImage tempCrown;

    @Command(name = "setuproles", desc = "Set up roles menu", perms = Constants.Perms.MOD)
    public static void handleSetupRoles(@NotNull SlashCommandEvent cmd) {
        final Constants.GuildInfo GUILD_INFO =
                Constants.GUILD_INFO.get(Objects.requireNonNull(cmd.getGuild()).getIdLong());

        StringBuilder sb = new StringBuilder();
        for (Constants.Mode value : Constants.Mode.values()) {
            sb.append(cmd.getGuild().getEmotesByName(value.toString(), false).get(0).getAsMention()).append(" - <@&").append(GUILD_INFO.roles.get(value)).append(">\n");
        }

        Message m = new MessageBuilder().setEmbeds(new EmbedBuilder().setTitle("Generals.io Role Selector")
                .setDescription(("""
                        To select one or more roles, simply react with the role you would like to add or remove.\s

                        Each role has a specific channel dedicated to that game mode. You can also ping all players with the role using **!ping** in that game mode's channel.

                        Want the <@&788259902254088192> role? DM or ping <@356517795791503393>. The tester role is pinged when <@356517795791503393> is testing a beta version on the BOT server.

                        %s""").formatted(sb)
                ).addField("Notification Roles", """
                        :trophy: - Notify me about upcoming events and tournaments
                        """, false)
                .setFooter("You must wait 3 seconds between adding a role and removing it.")
                .setThumbnail(cmd.getGuild().getIconUrl()).build()).build();
        m = cmd.getChannel().sendMessage(m).complete();

        for (Constants.Mode value : Constants.Mode.values()) {
            m.addReaction(cmd.getGuild().getEmotesByName(value.toString(), false).get(0)).queue();
        }
        m.addReaction("\uD83C\uDFC6").queue();
        Utils.replySuccess(cmd, "Added role selector");
    }

    private static byte[] getImageByteArray(BufferedImage bi) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(bi, "png", bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bytes.toByteArray();
    }

    private static int drawName(String name, String teamName, int curLine, int fontSize, double crownScale,
                                int spacing, Graphics2D g2) {
        if (tempCrown == null) {
            try {
                tempCrown = ImageIO.read(Bot.class.getClassLoader().getResource("crown.png"));
            } catch (IOException e) {
                e.printStackTrace();
                return curLine;
            }
        }
        BufferedImage crown = new BufferedImage(tempCrown.getWidth(), tempCrown.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics g = crown.getGraphics();
        g.drawImage(tempCrown, 0, 0, null);
        g.dispose();
        int[] buffer = ((DataBufferInt) crown.getRaster().getDataBuffer()).getData();
        for (int a = 0; a < buffer.length; a++) {
            if ((buffer[a] & 0xFF_00_00_00) != 0) {
                int upper = buffer[a] >>> 24;
                buffer[a] |= Color.WHITE.getRGB();
                buffer[a] &= 0x00_FF_FF_FF;
                buffer[a] |= (2 * upper / 3) << 24;
            }
        }

        Font font = new Font("Quicksand", Font.BOLD, fontSize);
        FontMetrics metrics = g2.getFontMetrics(font);
        curLine += spacing + metrics.getAscent();
        g2.setFont(font);
        g2.drawString(name, (int) (5 + crown.getWidth() * crownScale + 10), curLine);
        double centerY;
        if (teamName != null) {
            centerY = curLine + metrics.getDescent() + spacing / 4;
            curLine += spacing / 2 + metrics.getAscent();
            g2.drawString("(" + teamName + ")", (int) (5 + crown.getWidth() * crownScale + 10), curLine);
        } else {
            centerY =
                    curLine - metrics.getAscent() + metrics.getDescent() - metrics.getStringBounds(name, g2).getCenterY();
        }
        AffineTransform crownTransform = AffineTransform.getTranslateInstance(
                5,
                centerY - crownScale * 0.5 * crown.getHeight());
        crownTransform.concatenate(AffineTransform.getScaleInstance(crownScale, crownScale));
        AffineTransformOp op = new AffineTransformOp(crownTransform, AffineTransformOp.TYPE_BICUBIC);
        g2.drawImage(crown, op, 0, 0);
        return curLine;
    }

    private static void purgeChannel(TextChannel channel) {
        for (int a = 0; a < 10; a++) {
            List<Message> messages = channel.getHistory().retrievePast(100).complete();
            if (messages.isEmpty()) {
                break;
            }
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            for (Message m : messages) {
                tasks.add(channel.deleteMessageById(m.getId()).submit());
            }
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture<?>[0])).join();
        }
    }

    @Command(name = "tournamentfame", desc = "Display hall of fame for tournament champions", perms =
            Constants.Perms.MOD)
    public static void handleTournamentHallOfFame(@NotNull SlashCommandEvent cmd) {
        MessageChannel channel = cmd.getChannel();
        cmd.deferReply(true).queue();
        cmd.getHook().editOriginal("Generating...").complete();
        purgeChannel(cmd.getTextChannel());
        boolean first = true;
        for (TournamentData data : WINNER_DATA) {
            if (!first) {
                cmd.getChannel().sendMessage("_ _").queue();
            } else {
                first = false;
            }
            String tournamentName = data.tournamentName();
            String date = data.date();
            String quote1 = data.quote1();
            String quote2 = data.quote2();
            String winner1 = data.winner1();
            String winner2 = data.winner2();
            String replayName = data.replayName();
            String replay = data.replay();
            String replayImage = data.replayImage();
            String teamName = data.teamName();
            EventType eventType = data.eventType();
            String winner = winner1;
            if (winner2 != null) {
                winner += " and " + winner2;
            }
            Color bannerColor = eventType.eventColor;

            int nameSize = (winner2 != null ? 20 : 40);
            double crownScale = (winner2 != null ? 0.4 : 0.5);
            BufferedImage headerImage = new BufferedImage(400, (teamName != null ? 130 : (winner2 != null ? 100 : 120)),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = headerImage.createGraphics();
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            final Font font;
            try {
                font = Font.createFont(Font.TRUETYPE_FONT,
                        Objects.requireNonNull(Bot.class.getClassLoader().getResourceAsStream("Quicksand-Regular.ttf")));
            } catch (FontFormatException | IOException e) {
                e.printStackTrace();
                return;
            }
            ge.registerFont(font);
            String fontName = "Quicksand";
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (eventType == EventType.Ones) {
                g2.setBackground(new Color(0, 128, 128));
            } else {
                g2.setBackground(new Color(0x36393f));
            }
            g2.clearRect(0, 0, headerImage.getWidth(), headerImage.getHeight());
            Font curFont = new Font(fontName, Font.PLAIN, 20);
            int spacing = 20;
            FontMetrics metrics = g2.getFontMetrics(curFont);
            int innerGap = 5;
            int curLine = spacing + metrics.getAscent() + innerGap;
            g2.setFont(curFont);
            Rectangle2D bounds = metrics.getStringBounds(tournamentName, g2);
            Rectangle2D highlight = new Rectangle2D.Double(
                    0,
                    spacing,
                    bounds.getWidth() + 2 * innerGap,
                    bounds.getHeight() + 2 * innerGap);
            g2.setPaint(bannerColor);
            g2.fill(highlight);
            g2.setPaint(Color.WHITE);
            g2.drawString(tournamentName, innerGap, curLine);
            drawName(winner, teamName, curLine, nameSize, crownScale, spacing, g2);
            g2.dispose();

            channel.sendMessage(" ").addFile(getImageByteArray(headerImage), "header.png").queue();
            try {
                EmbedBuilder winnerEmbed = new EmbedBuilder()
                        .setTitle("Won on " + date)
                        .setColor((eventType == EventType.Ones) ? new Color(0, 128, 128) : eventType.eventColor);
                if (quote1 != null) {
                    winnerEmbed.appendDescription("\"" + quote1 + "\" - " + winner1 + "\n\n");
                }
                if (quote2 != null) {
                    winnerEmbed.appendDescription("\"" + quote2 + "\" - " + winner2 + "\n\n");
                }
                if (replayName != null && replay != null) {
                    winnerEmbed.appendDescription(String.format("**Highlighted replay: [%s](%s)**",
                            replayName,
                            replay));
                }
                if (replayImage != null) {
                    winnerEmbed.setImage("attachment://" + replayImage);
                }
                MessageAction winnerMessage = channel.sendMessage(new MessageBuilder().setEmbeds(winnerEmbed.build())
                        .build());
                if (replayImage != null) {
                    winnerMessage.addFile(Bot.class.getClassLoader().getResourceAsStream(replayImage).readAllBytes(),
                            replayImage);
                }
                winnerMessage.queue();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private enum EventType {
        Ones(new Color(175, 152, 0)),
        Twos(new Color(67, 99, 216)),
        FFA(new Color(0, 128, 0)),
        Custom(new Color(128, 0, 128));
        Color eventColor;

        EventType(Color c) {
            eventColor = c;
        }
    }

    private static record TournamentData(
            String tournamentName,
            String date,
            String quote1,
            String quote2,
            String winner1,
            String winner2,
            String replayName,
            String replay,
            String replayImage,
            String teamName,
            EventType eventType) {
    }
}
