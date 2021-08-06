package com.lazerpent.discord.generalsio;

import net.dv8tion.jda.api.entities.Guild;
import com.lazerpent.discord.generalsio.ReplayStatistics.ReplayResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.JFreeChart;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Commands extends ListenerAdapter {

    static final String PREFIX = "!";
    // Maps holding all command methods.
    private static final Map<String, Method> commands = new HashMap<>();
    // Maps holding all categories that have their own class.
    private static final Map<String, Category> categories = new HashMap<>();
    //    private static Feedback feedBack;
    private static boolean nickWupey = false;

    static {
        List<Method> methods = new ArrayList<>(Arrays.asList(Commands.class.getDeclaredMethods()));
        Class<?>[] classes = Commands.class.getDeclaredClasses();

        for (Class<?> classCamelCase : classes) {
            Category category = classCamelCase.getAnnotation(Category.class);
            if (category != null) {
                methods.addAll(Arrays.asList(classCamelCase.getDeclaredMethods()));
                categories.put(category.cat(), category);
            }
        }

        for (Method method : methods) {
            Command cmd = method.getAnnotation(Command.class);
            if (cmd != null) {
                if (!method.getName().startsWith("handle")) {
                    throw new IllegalStateException("invalid command handler method: " + method.getName());
                }

                if (!(Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 3
                      && method.getParameterTypes()[0].equals(Commands.class)
                      && method.getParameterTypes()[1].equals(Message.class)
                      && method.getParameterTypes()[2].equals(String[].class))) {
                    throw new IllegalStateException(
                            "invalid command handler method method: bad signature: " + method.getName());
                }

                for (String name : cmd.name()) {
                    commands.put(name, method);
                }
            }
        }
    }

    public Commands() {
//        feedBack = new Feedback();
    }

    @Command(name = {"help"}, args = {"perms"}, desc = "How to use this bot")
    public static void handleHelp(@NotNull Commands self, @NotNull Message msg, String[] args) {
        int perms = Constants.Perms.get(Objects.requireNonNull(msg.getMember()));
        if (args.length > 1) {
            try {
                perms = Integer.parseInt(args[1]);
                if (perms < 0 || perms > 2) {
                    throw new Exception();
                }
            } catch (Exception err) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "\\`" + args[1] + "' is not number between 0 and " +
                                                                    "2")).queue();
                return;
            }
        }

        EmbedBuilder embed = new EmbedBuilder().setColor(Constants.Colors.PRIMARY)
                .setTitle("Bot Help")
                .setFooter("Permissions: " + perms);

        Map<String, Map<String, Command>> categoryCommands = new HashMap<>();
        for (Method method : commands.values()) {
            Command cmd = method.getAnnotation(Command.class);
            String category = cmd.cat();
            if (category.equals("")) {
                Category cat = method.getDeclaringClass().getAnnotation(Category.class);
                if (cat != null) {
                    category = cat.cat();
                }
            }
            if (cmd.perms() <= perms) {
                categoryCommands.putIfAbsent(category, new HashMap<>());
                categoryCommands.get(category).put(cmd.name()[0], cmd);
            }
        }

        for (Map.Entry<String, Map<String, Command>> entry : categoryCommands.entrySet()) {
            StringBuilder sb = new StringBuilder();
            entry.getValue().forEach((key, value) -> {
                sb.append(PREFIX).append(key);
                Arrays.stream(value.args()).forEach(arg -> sb.append(" [").append(arg).append("]"));
                sb.append(" - ").append(value.desc()).append("\n");
            });

            if (entry.getKey().equals("")) {
                embed.setDescription(sb.toString());
                continue;
            }

            String catName = entry.getKey();
            if (categories.containsKey(catName)) {
                catName = categories.get(catName).name();
            }

            embed = embed.addField(catName, sb.toString(), false);
        }

        msg.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    @Command(name = {"info"}, desc = "Credit where credit is due")
    public static void handleInfo(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        msg.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("Bot Information")
                .setColor(Constants.Colors.PRIMARY)
                .setDescription("Authors: **Lazerpent**, **person2597**, **pasghetti**")
                .appendDescription("\n\nSource code: github.com/Lazerpent/GeneralsIoDiscordBot/")
                .setFooter("Written using JDA (Java Discord API)")
                .setThumbnail(msg.getJDA().getSelfUser().getEffectiveAvatarUrl()).build()).queue();
    }

    @Command(name = {"nickwupey"}, cat = "user", desc = "Bully Wuped", perms = Constants.Perms.MOD)
    public static void handleNickWupey(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        if (msg.getAuthor().getIdLong() == 356517795791503393L) {
            nickWupey = !nickWupey;
            msg.getChannel().sendMessage("Switched auto-nick to " + nickWupey).queue();
            if (nickWupey) {
                msg.getGuild().modifyNickname(Objects.requireNonNull(msg.getGuild().retrieveMemberById(175430325755838464L).complete()), "Wupey").queue();
            }
        }
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(event.getGuild().getIdLong());

        // Cancel if user is a bot or in an ignored channel
        if (event.getAuthor().isBot() || GUILD_INFO.ignoreChannels.contains(event.getChannel().getIdLong())) {
            return;
        }

        Message msg = event.getMessage();
        // ignore DMs
        if (!msg.isFromGuild()) {
            return;
        }

        String content = msg.getContentDisplay();
        if (!content.startsWith(PREFIX)) {
            return;
        }

        // split content into words; remove prefix
        content = content.replaceFirst(PREFIX, "");
        String[] command = content.split(" ");
        Method cmdMethod = commands.get(command[0].toLowerCase());
        if (cmdMethod == null) {
            return;
        }

        Command cmdInfo = cmdMethod.getAnnotation(Command.class);

        // By handling the names here, it forces members to add their generals name to use any function of the bot
        if (cmdInfo.perms() != Constants.Perms.NONE) {
            if (Database.getGeneralsName(msg.getAuthor().getIdLong()) == null) {
                msg.getChannel().sendMessageEmbeds(
                        new EmbedBuilder()
                                .setTitle("Unknown generals.io Username")
                                .setDescription("""
                                        You must register your generals.io username.\s
                                        Use ``!addname username`` to register.
                                        Example: ```!addname MyName321```""")
                                .setColor(Constants.Colors.ERROR).build()).queue();
                return;
            }
        }

        if (cmdInfo.perms() > Constants.Perms.get(Objects.requireNonNull(msg.getMember()))) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg,
                    "You don't have permission to use **!" + command[0] + "**")).queue();
            return;
        }

        try {
            cmdMethod.invoke(null, this, msg, command);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        // Send a goodbye message (252599855841542145 is the new channel)
        Objects.requireNonNull(event.getGuild().getTextChannelById(252599855841542145L)).sendMessageEmbeds(new EmbedBuilder()
                .setTitle("Goodbye " + event.getUser().getName() + "#" + event.getUser().getDiscriminator())
                .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                .setColor(Constants.Colors.ERROR)
                .setDescription("We will miss you.")
                .build()).queue();
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        // When a user joins the guild, send a message to the channel (252599855841542145 is the new channel)
        Objects.requireNonNull(event.getGuild().getTextChannelById(252599855841542145L))
                .sendMessage(new MessageBuilder().append(event.getMember()).setEmbeds(new EmbedBuilder()
                        .setTitle("Welcome " + event.getMember().getEffectiveName())
                        .setThumbnail(event.getMember().getUser().getEffectiveAvatarUrl())
                        .setColor(Constants.Colors.SUCCESS)
                        .setDescription("""
                                Make sure you add your generals.io name to our bot, using ``!addname generals.io_username``.
                                Example: ```!addname MyName321```
                                Head over to <#754022719879643258> to register your name.""")
                        .addField("Roles", "Want a role specific to the game modes you play? After registering your " +
                                           "name, head over to <#787821221164351568> to get some roles.", false)
                        .build()).build()).queue();
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println(event.getJDA().getGuilds());
    }

    @Override
    public void onGuildMessageReactionAdd(@NotNull GuildMessageReactionAddEvent event) {
        RoleHandler.reactionAdd(event);
    }

    @Override
    public void onGuildMemberUpdateNickname(@NotNull GuildMemberUpdateNicknameEvent event) {
        if (event.getUser().isBot() || !nickWupey)
            return;
        if (event.getEntity().getIdLong() == 175430325755838464L) {
            if (!"Wupey".equals(event.getNewNickname())) {
                event.getGuild().modifyNickname(event.getEntity(), "Wupey").queue();
            }
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Command {
        String[] name();

        String cat() default ""; // category

        String[] args() default {};

        String desc();

        int perms() default Constants.Perms.NONE;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Category {
        String cat();

        String name();
    }

    @Category(cat = "game", name = "Game")
    public static class Game {
        private static Message createLinkEmbed(@NotNull Message msg, @NotNull Constants.Server server,
                                               @NotNull String link, @Nullable String map, int speed) {
            String baseURL = "https://" + server.host() + "/games/" + Utils.encodeURI(link);

            List<String> query = new ArrayList<>();
            if (link.equals("main") || link.equals("1v1")) {
                map = null;
                speed = 1;
                baseURL = "https://" + server.host() + "/?queue=" + Utils.encodeURI(link);
            } else if (link.equals("2v2")) {
                map = null;
                speed = 1;
                baseURL = "https://" + server.host() + "/teams/matchmaking";
            } else {
                if (speed != 1) {
                    query.add("speed=" + speed);
                }
                if (map != null) {
                    query.add("map=" + Utils.encodeURI(map));
                }
            }

            String playURL = query.size() == 0 ? baseURL : baseURL + "?" + String.join("&", query);
            query.add("spectate=true");
            String spectateURL = baseURL + "?" + String.join("&", query);

            EmbedBuilder embed =
                    new EmbedBuilder()
                            .setTitle("Custom Match", playURL).setColor(Constants.Colors.PRIMARY)
                            .setDescription(playURL + "\n")
                            .appendDescription(speed != 1 ? "\n**Speed:** " + speed : "")
                            .appendDescription(map != null ? "\n**Map:** " + map : "")
                            .setFooter(msg.getAuthor().getAsTag() + " • " + Database.getGeneralsName(msg.getAuthor().getIdLong()),
                                    msg.getAuthor().getAvatarUrl());

            List<Button> buttons = new ArrayList<>(List.of(
                    Button.link(playURL, "Play"),
                    Button.link(spectateURL, "Spectate")
            ));

            if (map != null) {
                buttons.add(Button.link("https://" + server.host() + "/maps/" + Utils.encodeURI(map), "Map"));
            }

            return new MessageBuilder()
                    .setActionRows(ActionRow.of(buttons))
                    .setEmbeds(
                            embed.build()
                    )
                    .build();
        }

        @Command(name = {"na", "custom", "private"}, args = {"code?", "map?"}, desc = "Generate match on NA servers",
                perms = Constants.Perms.USER)
        public static void handleNA(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            customLink(msg, cmd, Constants.Server.NA);
        }

        @Command(name = {"eu", "eucustom", "euprivate"}, args = {"code?", "map?"}, desc = "Generate match on EU " +
                                                                                          "servers", perms =
                Constants.Perms.USER)
        public static void handleEU(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            customLink(msg, cmd, Constants.Server.EU);
        }

        @Command(name = {"bot", "botcustom", "botprivate"}, args = {"code?", "map?"}, desc = "Generate match on Bot " +
                                                                                             "servers", perms =
                Constants.Perms.USER)
        public static void handleBOT(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            customLink(msg, cmd, Constants.Server.BOT);
        }

        private static void customLink(@NotNull Message msg, @NotNull String[] cmd, @NotNull Constants.Server server) {
            String link = cmd.length > 1 ? cmd[1] :
                    Long.toString((long) (Math.random() * Long.MAX_VALUE), 36).substring(0, 4);
            String map = cmd.length > 2 ? String.join(" ", Arrays.asList(cmd).subList(2, cmd.length)) : null;
            if (map != null && (map.equals("-") || map.equals("."))) map = null;
            msg.getChannel().sendMessage(createLinkEmbed(msg, server, link, map, 1)).queue();
        }

        @Command(name = {"plots"}, args = {"code?"}, desc = "Create a 4x Plots Lazerpent game", perms =
                Constants.Perms.USER)
        public static void handlePlots(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            String link = cmd.length > 1 ? cmd[1] : "Plots";
            msg.getChannel().sendMessage(createLinkEmbed(msg, Constants.Server.NA, link, "Plots Lazerpent", 4)).queue();
        }

        @Command(name = {"ping"}, desc = "Ping role", perms = Constants.Perms.USER)
        public static void handlePing(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

            Constants.Mode mode = GUILD_INFO.channelToMode(msg.getChannel().getIdLong());
            if (mode == null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "**!ping** can only be used in a game mode " +
                                                                    "channel")).queue();
                return;
            }

            if (!RoleHandler.tryPing(mode)) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Each role can only be pinged once every 10 " +
                                                                    "minutes")).queue();
                return;
            }

            Role role = msg.getGuild().getRoleById(GUILD_INFO.roles.get(mode));
            msg.getChannel().sendMessage(Objects.requireNonNull(role).getAsMention() + " (pinged by " + Objects.requireNonNull(msg.getMember()).getAsMention() + ")").queue();
        }

        @Command(name = {"setuproles"}, desc = "Setup roles menu", perms = Constants.Perms.MOD)
        public static void handleSetupRoles(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

            StringBuilder sb = new StringBuilder();
            for (Constants.Mode value : Constants.Mode.values()) {
                sb.append(msg.getGuild().getEmotesByName(value.toString(), false).get(0).getAsMention()).append(" - <@&").append(GUILD_INFO.roles.get(value)).append(">\n");
            }

            Message m = msg.getChannel().sendMessageEmbeds(new EmbedBuilder().setTitle("Generals.io Role Selector")
                    .setDescription("To select one or more roles, simply react with the role you would like to add or" +
                                    " remove. \n\nEach role has a specific channel dedicated to that game mode. " +
                                    "You can also ping all players with the role using **!ping** in that game mode's " +
                                    "channel.\n\nWant the <@&788259902254088192> role? DM or ping " +
                                    "<@356517795791503393>. The tester role is pinged when <@356517795791503393> is " +
                                    "testing a beta version on the BOT server.\n\n" +
                                    sb
                    ).setFooter("You must wait 3 seconds between adding a role and removing it.")
                    .setThumbnail(msg.getGuild().getIconUrl()).build()).complete();

            for (Constants.Mode value : Constants.Mode.values()) {
                m.addReaction(msg.getGuild().getEmotesByName(value.toString(), false).get(0)).queue();
            }
        }

    }

    @Category(cat = "user", name = "Users")
    public static class Users {
        @Command(name = {"profile", "user"}, args = {"username? | @mention?"}, cat = "user", perms =
                Constants.Perms.USER, desc = "Show username of user, or vice versa")
        public static void handleUser(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            long discordId;
            String name;

            if (cmd.length == 1) {
                discordId = msg.getAuthor().getIdLong();
                name = Database.getGeneralsName(discordId);
                if (name == null) {
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg,
                            Objects.requireNonNull(msg.getMember()).getAsMention() + " has not registered their " +
                            "generals.io user")).queue();
                    return;
                }
            } else {
                List<Member> members = msg.getMentionedMembers();
                if (members.size() == 0) {
                    name = Arrays.stream(cmd).skip(1).collect(Collectors.joining(" "));
                    discordId = Database.getDiscordId(name);
                    if (discordId == -1) {
                        msg.getChannel().sendMessageEmbeds(Utils.error(msg, name + " is not registered to any discord" +
                                                                            " user")).queue();
                        return;
                    }
                } else if (members.size() != 1) {
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You can only lookup one user at a time")).queue();
                    return;
                } else {
                    discordId = members.get(0).getIdLong();
                    name = Database.getGeneralsName(discordId);
                    if (name == null) {
                        msg.getChannel().sendMessageEmbeds(Utils.error(msg, members.get(0).getAsMention() + " has not" +
                                                                            " registered their generals.io user")).queue();
                        return;
                    }
                }
            }

            msg.getChannel().sendMessage(
                    new MessageBuilder()
                            .setEmbeds(new EmbedBuilder().setTitle("Profile", "https://generals" +
                                                                                       ".io/profiles/" + Utils.encodeURI(name)).setColor(Constants.Colors.PRIMARY)
                                .appendDescription("**Username:** " + name)
                                .appendDescription("\n**Discord:** " + Objects.requireNonNull(msg.getGuild().getMemberById(discordId)).getAsMention())
                                .build())
                            .setActionRows(ActionRow.of(Button.link("https://generals.io/profiles/" + Utils.encodeURI(name), "Visit")))
                            .build()).queue();
        }

        @Command(name = {"addname"}, args = {"username"}, cat = "user", desc = "Register generals.io username")
        public static void handleAddName(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

            if (cmd.length < 2) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Missing username")).queue();
                return;
            }
            String username;
            if ((username = Database.getGeneralsName(Objects.requireNonNull(msg.getMember()).getIdLong())) != null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You're already registered as **" + username +
                                                                    "**. Ask a <@&" + GUILD_INFO.moderatorRole + "> " +
                                                                    "to change your username.")).queue();
                return;
            }

            //Get the generals.io username, skipping the messaged name
            username = Arrays.stream(cmd).skip(1).collect(Collectors.joining(" "));

            long l;
            if ((l = Database.getDiscordId(username)) != -1) {
                if (msg.getGuild().retrieveBanById(l).complete() != null) {
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg, "**" + username + "** is banned")).queue();
                    return;
                }
                Member m = msg.getGuild().getMemberById(l);
                if (m != null) {
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg, "**" + username + "** is already registered " +
                                                                        "to " + m.getAsMention())).queue();
                    return;
                }
            }

            Database.addDiscordGenerals(Objects.requireNonNull(msg.getMember()).getIdLong(), username);
            msg.getChannel().sendMessageEmbeds(new EmbedBuilder().setTitle("Username Added").setColor(Constants.Colors.SUCCESS)
                    .setDescription(msg.getMember().getAsMention() + " is now generals.io user **" + username + "**").build()).queue();
        }

        @Command(name = {"setname"}, cat = "user", args = {"@mention", "username"}, desc = "Change generals.io " +
                                                                                           "username of user", perms
                = Constants.Perms.MOD)
        public static void handleSetName(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            if (msg.getMentionedMembers().size() == 0) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must mention the member to update")).queue();
                return;
            }

            if (cmd.length < 2) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must provide the generals.io username to " +
                                                                    "update")).queue();
                return;
            }

            Member m = msg.getMentionedMembers().get(0);
            String tmp = m.getEffectiveName();
            int a = tmp.length();
            tmp = tmp.replaceAll(" ", "");
            a -= tmp.length();
            String name = Arrays.stream(cmd).skip(2 + a).collect(Collectors.joining(" "));

            if (Database.noMatch(m.getIdLong(), name)) {
                Database.addDiscordGenerals(m.getIdLong(), name);
            } else {
                Database.updateDiscordGenerals(m.getIdLong(), m.getIdLong(), name);
            }
            msg.getChannel().sendMessage(new MessageBuilder()
                    .setContent(m.getAsMention())
                    .setEmbeds(
                            new EmbedBuilder().setTitle("Username Updated").setColor(Constants.Colors.SUCCESS)
                                    .setDescription(m.getAsMention() + " is now generals.io user **" + name + "**").build()).build()).queue();
        }

    }

    @Category(cat = "stat", name = "Stats")
    static class Stats {
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

        @Command(name = {"addtograph"}, cat = "stat", args = {
                "username"}, desc = "Find a player's stats and store them for graphing.")
        public static void handleAddToGraph(@NotNull Commands self, @NotNull Message msg, String[] args) {
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

        @Command(name = {"removefromgraph"}, cat = "stat", args = {
                "username"}, desc = "Remove a player's stats from the graph.")
        public static void handleRemoveFromGraph(@NotNull Commands self, @NotNull Message msg, String[] args) {
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

        @Command(name = {"cleargraph"}, cat = "stat", desc = "Remove all players' stats from the graph.")
        public static void handleClearGraph(@NotNull Commands self, @NotNull Message msg, String[] args) {
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

    @Category(cat = "hill", name = "GoTH/AoTH")
    public static class Hill {
        private static class ChallengeData {
            public long opp;
            public int length;
        }
        private static Map<Long, ChallengeData> challengeData = new HashMap<>();

        private static MessageEmbed scoreEmbed(Database.Hill.Challenge c) {
            long oppMember = Database.getDiscordId(c.opp[0]);
            EmbedBuilder embed =  new EmbedBuilder()
                .setTitle("GoTH Results")
                .setColor(Constants.Colors.PRIMARY)
                .setDescription((c.scoreInc > c.scoreOpp ? "**" + c.scoreInc + "**-" + c.scoreOpp : c.scoreInc + "-**" + c.scoreOpp + "**") + " vs " + c.opp[0] + " <@" + oppMember + ">")
                .setFooter("ID: C" + c.timestamp);

            if (c.replays.length != 0) {
                StringBuilder sb = new StringBuilder();
                for (String replay : c.replays) {
                    sb.append("https://generals.io/replays/" + replay + "\n");
                }

                embed.addField("Replays", sb.toString(), false);
            }

            return embed.build();
        }

        // Logs the results of a new GoTH challenge. Updates the database, updates the guild roles, and returns a MessageEmbed.
        private static MessageEmbed logScore(Guild guild, Database.Hill.Challenge c) {
            final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(guild.getIdLong());

            if (c.scoreInc < c.scoreOpp) {
                Database.Hill.Challenge[] terms = Database.Hill.lastTerms(Constants.Hill.GoTH, 1);
                // remove GoTH role from incumbent
                if (terms.length != 0) {
                    guild.removeRoleFromMember(Database.getDiscordId(terms[0].opp[0]), guild.getRoleById(GUILD_INFO.hillRoles.get(Constants.Hill.GoTH))).queue();
                }

                // add GoTH role to new message
                guild.addRoleToMember(Database.getDiscordId(c.opp[0]), guild.getRoleById(GUILD_INFO.hillRoles.get(Constants.Hill.GoTH))).queue();
            }

            Database.Hill.add(c);

            return scoreEmbed(c);
        }

        @Command(name = {"gothdel", "gothdelete"}, args={"id"}, desc = "Delete completed GoTH challenge", perms = Constants.Perms.MOD)
        public static void handleGothDel(@NotNull Commands self, @NotNull Message msg, String[] args) {
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
                msg.getChannel().sendMessageEmbeds(Utils.success(msg, "Challenge Deleted", "If `C" + timestamp + "` existed, it was deleted")).queue();
            } catch(NumberFormatException e) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Invalid challenge ID")).queue();
                return;
            }
        }

        @Command(name = {"gothscore"}, args={"score-score", "user"}, desc = "Add GoTH score for challenge", perms = Constants.Perms.MOD)
        public static void handleGothScore(@NotNull Commands self, @NotNull Message msg, String[] args) {
            if (args.length < 3 || msg.getMentionedMembers().size() == 0) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must mention a user and provide a score")).queue();
                return;                    
            }

            String score = args[1];
            Member mention = msg.getMentionedMembers().get(0);

            String[] scores = score.split("-");
            Database.Hill.Challenge c = new Database.Hill.Challenge();
            try {
                c.scoreInc = Integer.parseInt(scores[0]);
                c.scoreOpp = Integer.parseInt(scores[1]);
            } catch(NumberFormatException e) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Score format is [goth score]-[opponent score]")).queue();
                return;
            }
            c.timestamp = msg.getTimeCreated().toEpochSecond();
            c.type = Constants.Hill.GoTH;
            
            String name = Database.getGeneralsName(mention.getIdLong());
            if (name == null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, mention.getAsMention() + " has not registered their " +
                    "generals.io user")).queue();
                return;
            }
            c.opp = new String[]{name};
            c.replays = new String[0]; // TODO

            msg.getChannel().sendMessageEmbeds(logScore(msg.getGuild(), c)).queue();
        }

        private static void handleGothChallenge(@NotNull Commands self, @NotNull Message msg, String[] args) {
            final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

            String challengerName = Database.getGeneralsName(msg.getAuthor().getIdLong());

            List<Role> roles = msg.getMember().getRoles();
            for (Role role : roles) {
                if (role.getIdLong() == GUILD_INFO.hillRoles.get(Constants.Hill.GoTH)) {
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Nice try", "Can't challenge yourself :D")).queue();
                    return;
                }
            }

            if (args.length != 3) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Wrong number of arguments")).queue();
                return;
            }

            String boStr = args[2];
            int bo_ = 0;
            try {
                bo_ = Integer.parseInt(boStr.substring(2));
            } catch(NumberFormatException e) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, boStr + " is not in the format bo[NUMBER]")).queue();
                return;
            }
            final int bo = bo_;

            if (bo % 2 == 0) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must be best of an odd number")).queue();
                return;
            }

            msg.getChannel().sendMessage(new MessageBuilder().append("<@&" + GUILD_INFO.hillRoles.get(Constants.Hill.GoTH) + ">")
                .setEmbeds(new EmbedBuilder()
                    .setColor(Constants.Colors.PRIMARY)
                    .setTitle("GoTH Challenge", "https://generals.io/games/goth")
                    .setDescription("**Challenger:** " + challengerName + " " + msg.getAuthor().getAsMention() + "\n**Best of:** " + bo)
                    .build())
                .setActionRows(ActionRow.of(List.of(
                    Button.link("https://generals.io/games/goth", "Play"),
                    Button.link("https://generals.io/games/goth?spectate=true", "Spectate"),
                    Button.success("goth-score", "Score"),
                    Button.danger("goth-manual", "Manual Score")
                )))
                .build()).queue(m -> {
                    ChallengeData cdata = new ChallengeData();
                    cdata.opp = msg.getAuthor().getIdLong();
                    cdata.length = bo;
                    challengeData.put(m.getIdLong(), cdata);
                });
        }

        @Command(name = {"goth"}, desc = "List out GoTH challenges in current term", perms = Constants.Perms.USER)
        public static void handleGoth(@NotNull Commands self, @NotNull Message msg, String[] args) {
            final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

            if (args.length > 1  && args[1].equals("challenge")) {
                handleGothChallenge(self, msg, args);
                return;
            }

            char c = 'T';
            long n = 0;
            if (args.length > 1 && args[1].length() != 0) {
                c = args[1].charAt(0);
                try {
                    n = Long.parseLong(args[1].substring(1));
                } catch (NumberFormatException e) {
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg, args[1] + " is not a number")).queue();
                    return;
                }
            }

            if (c == 'T') {
                Database.Hill.Challenge term;
                Database.Hill.Challenge nextTerm = null;
                int nth = -1;
                if (n == 0) {
                    Database.Hill.Challenge[] terms = Database.Hill.lastTerms(Constants.Hill.GoTH, 1);
                    if (terms.length == 0) {
                        msg.getChannel().sendMessageEmbeds(Utils.error(msg, "GoTH term not found")).queue();
                        return;
                    }
                    term = terms[0];
                    nth = Database.Hill.nthTerm(Constants.Hill.GoTH, term.timestamp);
                } else {
                    Database.Hill.Challenge[] terms = Database.Hill.firstTerms(Constants.Hill.GoTH, (int)n+1);
                    if (terms.length < (int)n) {
                        msg.getChannel().sendMessageEmbeds(Utils.error(msg, "GoTH term not found")).queue();
                        return;
                    }
                    term = terms[(int)n-1];

                    if (terms.length > n) {
                        nextTerm = terms[(int)n];
                    }
                    nth = (int)n;
                }
                Database.Hill.Challenge[] challenges = Database.Hill.get(Constants.Hill.GoTH, term.timestamp+1, nextTerm == null ? System.currentTimeMillis() : nextTerm.timestamp);
                StringBuilder sb = new StringBuilder();

                for (Database.Hill.Challenge ch : challenges) {
                    long oppMember = Database.getDiscordId(ch.opp[0]);
                    sb.append("`C" + ch.timestamp + "`: " + ch.scoreInc + "-" + ch.scoreOpp + " vs " + ch.opp[0] + " <@" + oppMember + ">\n");
                }
                if (challenges.length == 0) {
                    sb.append("No challenges");
                }

                String inc = term.opp[0];
                Member incMember = msg.getGuild().getMemberById(Database.getDiscordId(inc));

                msg.getChannel().sendMessageEmbeds(new EmbedBuilder()
                    .setColor(Constants.Colors.PRIMARY)
                    .setTitle("GoTH Term")
                    .setDescription("<@&" + GUILD_INFO.hillRoles.get(Constants.Hill.GoTH) + ">: " + inc + " " + incMember.getUser().getAsMention())
                    .addField("Challenges (" + challenges.length + ")", sb.toString(), false)
                    .setFooter("ID: T" + nth)
                    .build()).queue();
            } else if (c == 'C') {
                Database.Hill.Challenge[] challenges = Database.Hill.get(Constants.Hill.GoTH, n, n);
                if (challenges.length == 0) {
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg, "GoTH challenge not found")).queue();
                    return;
                }

                msg.getChannel().sendMessageEmbeds(scoreEmbed(challenges[0])).queue();
            }
        }

        public static void handleButtonClick(ButtonClickEvent event) {
            if (event.getComponentId().equals("goth-score")) {
                ChallengeData cdata = challengeData.get(event.getMessage().getIdLong());
                String opponent = Database.getGeneralsName(cdata.opp);
                Database.Hill.Challenge[] terms = Database.Hill.lastTerms(Constants.Hill.GoTH, 1);
                if (terms.length == 0) {
                    event.replyEmbeds(Utils.error(event.getMessage(), "No incumbent GoTH"));
                    return;
                }
                String goth = terms[0].opp[0];

                Database.Hill.Challenge c = new Database.Hill.Challenge();
                c.timestamp = event.getMessage().getTimeCreated().toEpochSecond();
                c.type = Constants.Hill.GoTH;
                c.opp = new String[]{opponent};
                c.scoreInc = 0;
                c.scoreOpp = 0;
                challengeData.remove(event.getMessage().getIdLong());

                List<String> replayIDs = new ArrayList<>();
                List<ReplayStatistics.ReplayResult> replays = ReplayStatistics.getLastReplays(goth, 200);
                for (ReplayStatistics.ReplayResult replay : replays) {
                    if (replay.ranking.length == 2 && replay.hasPlayer(opponent) && replay.hasPlayer(goth)) {
                        if (replay.started < event.getMessage().getTimeCreated().toEpochSecond() * 1000) {
                            break;
                        }

                        if (replay.ranking[0].name.equals(goth)) {
                            c.scoreInc += 1;
                        } else {
                            c.scoreOpp += 1;
                        }
                        replayIDs.add(replay.id);
                    }
                }
                c.replays = replayIDs.toArray(new String[0]);

                event.replyEmbeds(logScore(event.getGuild(), c)).queue();
                event.getMessage().editMessage(new MessageBuilder(event.getMessage()).setActionRows().build()).queue();
            } else if (event.getComponentId().equals("goth-manual")) {
                final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(event.getGuild().getIdLong());

                event.reply("Ask a <@&" + GUILD_INFO.moderatorRole + "> to set your GoTH score").queue();
                event.getMessage().editMessage(new MessageBuilder(event.getMessage()).setActionRows().build()).queue();
            }
        }
    }

    public void onButtonClick(ButtonClickEvent event) {
        if (event.getComponentId().startsWith("goth-")) {
            Hill.handleButtonClick(event);
        }
    }
}
