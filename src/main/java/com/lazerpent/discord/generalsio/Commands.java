package com.lazerpent.discord.generalsio;

import com.lazerpent.discord.generalsio.Database.Hill.Challenge;
import com.lazerpent.discord.generalsio.ReplayStatistics.ReplayResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.JFreeChart;

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
import java.time.Instant;
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
                .appendDescription("\n\nhttps://github.com/Lazerpent/GeneralsIoDiscordBot")
                .setFooter("Written using JDA (Java Discord API)")
                .setThumbnail(msg.getJDA().getSelfUser().getEffectiveAvatarUrl()).build()).queue();
    }

    @Command(name = {"nickwupey"}, cat = "user", desc = "Bully Wuped", perms = Constants.Perms.MOD)
    public static void handleNickWupey(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        nickWupey = !nickWupey;
        msg.getChannel().sendMessage("Switched auto-nick to " + nickWupey).queue();
        if (nickWupey) {
            msg.getGuild().modifyNickname(Objects.requireNonNull(msg.getGuild().retrieveMemberById(175430325755838464L).complete()), "Wupey").queue();
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
        Hill.init();
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

    /**
     * Command handler for punish command. See Punishments.punish
     *
     * @param self Command Annotation
     * @param msg  Message Object
     * @param cmd  Parameters
     */
    @Command(name = {"addpunish", "punish"}, cat = "In-Game Moderation", desc = "Add user to the punishment list",
            perms = Constants.Perms.MOD)
    public static void handleAddPunish(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        Punishments.punish(msg, cmd);
    }

    /**
     * Command handler for disable command. See Punishments.disable
     *
     * @param self Command Annotation
     * @param msg  Message Object
     * @param cmd  Parameters
     */
    @Command(name = {"adddisable", "disable"}, cat = "In-Game Moderation", desc = "Add user to the disable list",
            perms = Constants.Perms.MOD)
    public static void handleAddDisable(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        Punishments.disable(msg, cmd);
    }

    /**
     * Command handler for getCommands. See Punishments.getCommands
     *
     * @param self Command Annotation
     * @param msg  Message Object
     * @param cmd  Parameters
     */
    @Command(name = {"getpunishcommand", "getpunishcommands", "cmd"}, perms = Constants.Perms.MOD,
            cat = "In-Game Moderation", desc = "Gets a list of commands to run to punish players")
    public static void handleGetCommand(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        Punishments.getCommands(msg);
    }

    @Override
    public void onButtonClick(ButtonClickEvent event) {
        if (event.getComponentId().startsWith("goth-") || event.getComponentId().startsWith("aoth-")) {
            Hill.handleButtonClick(event);
        }
        if (event.getComponentId().startsWith("punish-")) {
            Punishments.onButtonClick(event);
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

    @Category(cat = "hill", name = "GoTH/AoTH")
    public static class Hill {
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
        public static List<ChallengeData> curGothChallenges = new ArrayList<>();
        public static List<ChallengeData> curAothChallenges = new ArrayList<>();
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
                goth = Long.parseLong(terms[0].opp[0]);
            if (goth != 0) {
                String gothName = Database.getGeneralsName(goth);
                List<String> replayIDs = new ArrayList<>();
                List<ReplayStatistics.ReplayResult> replays = ReplayStatistics.getReplays(gothName, 1000);
                Collections.reverse(replays);
                synchronized (curGothChallenges) {
                    boolean shouldClear = false;
                    for (ChallengeData cdata : curGothChallenges) {
                        String opponent = Database.getGeneralsName(cdata.opp[0]);

                        Database.Hill.Challenge c = new Database.Hill.Challenge();
                        c.timestamp = cdata.timestamp;
                        c.type = Constants.Hill.GoTH;
                        c.opp = new String[]{"" + cdata.opp[0]};
                        c.scoreInc = 0;
                        c.scoreOpp = 0;
                        for (ReplayStatistics.ReplayResult replay : replays) {
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
                aoth1 = Long.parseLong(terms[0].opp[0]);
                aoth2 = Long.parseLong(terms[0].opp[1]);
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
                    for (ChallengeData cdata : curAothChallenges) {
                        String opponent1 = Database.getGeneralsName(cdata.opp[0]);
                        String opponent2 = Database.getGeneralsName(cdata.opp[1]);

                        Database.Hill.Challenge c = new Database.Hill.Challenge();
                        c.timestamp = cdata.timestamp;
                        c.type = Constants.Hill.GoTH;
                        c.opp = new String[]{"" + cdata.opp[0], "" + cdata.opp[1]};
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
            long oppMember = Long.parseLong(c.opp[0]);
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
                    inc1 = Long.parseLong(terms[0].opp[0]);
                    if (mode == Constants.Hill.AoTH) {
                        inc2 = Long.parseLong(terms[0].opp[1]);
                    }
                }
                if (inc1 != 0) {
                    guild.removeRoleFromMember(inc1, guild.getRoleById(GUILD_INFO.hillRoles.get(mode))).queue();
                    if (mode == Constants.Hill.AoTH) {
                        guild.removeRoleFromMember(inc2, guild.getRoleById(GUILD_INFO.hillRoles.get(mode))).queue();
                    }
                }

                // add GoTH role to new message
                guild.addRoleToMember(Long.parseLong(c.opp[0]), guild.getRoleById(GUILD_INFO.hillRoles.get(mode))).queue();
                if (mode == Constants.Hill.AoTH) {
                    guild.addRoleToMember(Long.parseLong(c.opp[1]),
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

        @Command(name = {"gothscore"}, args = {"score-score", "user"}, desc = "Add GoTH score for challenge", perms =
                Constants.Perms.MOD)
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
            } catch (NumberFormatException e) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Score format is [goth score]-[opponent score]")).queue();
                return;
            }
            c.timestamp = msg.getTimeCreated().toEpochSecond();
            c.type = Constants.Hill.GoTH;

            String name = Database.getGeneralsName(mention.getIdLong());
            if (name == null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, mention.getAsMention() + " has not registered " +
                                                                    "their " +
                                                                    "generals.io user")).queue();
                return;
            }
            c.opp = new String[]{name};
            c.replays = new String[0]; // TODO

            msg.getChannel().sendMessageEmbeds(logScore(msg.getGuild(), c, Constants.Hill.GoTH)).queue();
        }

        @Command(name = {"goth"}, args = {"id?"}, desc = "Show information about the given term or challenge", perms
                = Constants.Perms.USER)
        public static void handleGoth(@NotNull Commands self, @NotNull Message msg, String[] args) {
            if (args.length < 2) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Must specify a subcommand.")).queue();
                return;
            }
            if (args[1].equalsIgnoreCase("top")) {
                showGothHallOfFame(self, msg, args);
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
            if (event.getComponentId().startsWith("goth-accept")) {
                final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(event.getGuild().getIdLong());
                if (!event.getMember().getRoles().contains(event.getGuild().getRoleById(GUILD_INFO.hillRoles.get(Constants.Hill.GoTH)))) {
                    return;
                }
                synchronized (curGothChallenges) {
                    if (curGothChallenges.size() >= CONCURRENT_CHALLENGE_LIMIT) {
                        event.getChannel().sendMessageEmbeds(Utils.error(event.getMessage(),
                                "You can only accept " + CONCURRENT_CHALLENGE_LIMIT + " challenges at once.")).queue();
                        return;
                    }
                    String[] idArgs = event.getComponentId().split("-");
                    long challenger = Long.parseLong(idArgs[2]);
                    int bestof = Integer.parseInt(idArgs[3]);
                    String challengerName = Database.getGeneralsName(challenger);
                    event.getMessage().editMessage(new MessageBuilder(event.getMessage()).setActionRows().build()).queue();
                    if (challengerName == null) {
                        event.getChannel().sendMessageEmbeds(Utils.error(event.getMessage(), "Challenge could not be " +
                                                                                             "accepted: challenger " +
                                                                                             "doesn't exist.")).queue();
                        return;
                    }
                    ChallengeData challenge = new ChallengeData(new long[]{challenger}, bestof,
                            Instant.now().getEpochSecond() * 1000, null);
                    curGothChallenges.add(challenge);
                    event.reply(new MessageBuilder().append("<@" + challenger + ">")
                            .setEmbeds(new EmbedBuilder()
                                    .setColor(Constants.Colors.PRIMARY)
                                    .setTitle("GoTH Challenge Accepted", "https://generals.io/games/goth")
                                    .setDescription("**Challenger:** " + challengerName)
                                    .appendDescription("\n**Best of " + bestof + "**")
                                    .build())
                            .setActionRows(ActionRow.of(List.of(
                                    Button.link("https://generals.io/games/goth", "Play"),
                                    Button.link("https://generals.io/games/goth?spectate=true", "Spectate")
                            )))
                            .build()).queue((inx) -> {
                        inx.retrieveOriginal().queue(m -> {
                            challenge.challengeMsg = m;
                        });
                    });
                }
            } else if (event.getComponentId().startsWith("goth-reject")) {
                final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(event.getGuild().getIdLong());
                if (!event.getMember().getRoles().contains(event.getGuild().getRoleById(GUILD_INFO.hillRoles.get(Constants.Hill.GoTH)))) {
                    return;
                }
                String[] idArgs = event.getComponentId().split("-");
                long challenger = Long.parseLong(idArgs[2]);
                String challengerName = Database.getGeneralsName(challenger);
                event.getMessage().editMessage(new MessageBuilder(event.getMessage()).setActionRows().build()).queue();
                if (challengerName == null) {
                    event.getChannel().sendMessageEmbeds(Utils.error(event.getMessage(), "Challenge already invalid: " +
                                                                                         "challenger doesn't exist.")).queue();
                    return;
                }
                event.reply(new MessageBuilder().append("<@" + challenger + ">")
                        .setEmbeds(new EmbedBuilder()
                                .setColor(Constants.Colors.ERROR)
                                .setTitle("GoTH Challenge Rejected")
                                .setDescription("The GoTH didn't want to play against "
                                                + challengerName + ".")
                                .build())
                        .build()).queue();
            } else if (event.getComponentId().startsWith("aoth-accept")) {
                final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(event.getGuild().getIdLong());
                if (!event.getMember().getRoles().contains(event.getGuild().getRoleById(GUILD_INFO.hillRoles.get(Constants.Hill.AoTH)))) {
                    return;
                }
                synchronized (curAothChallenges) {
                    if (curAothChallenges.size() >= CONCURRENT_CHALLENGE_LIMIT) {
                        event.getChannel().sendMessageEmbeds(Utils.error(event.getMessage(),
                                "You can only accept " + CONCURRENT_CHALLENGE_LIMIT + " challenges at once.")).queue();
                        return;
                    }
                    String[] idArgs = event.getComponentId().split("-");
                    long challenger1 = Long.parseLong(idArgs[2]);
                    long challenger2 = Long.parseLong(idArgs[3]);
                    int bestof = Integer.parseInt(idArgs[4]);
                    String challengerName1 = Database.getGeneralsName(challenger1);
                    String challengerName2 = Database.getGeneralsName(challenger2);
                    event.getMessage().editMessage(new MessageBuilder(event.getMessage()).setActionRows().build()).queue();
                    if (challengerName1 == null || challengerName2 == null) {
                        event.getChannel().sendMessageEmbeds(Utils.error(event.getMessage(), "Challenge could not be " +
                                                                                             "accepted: challenger " +
                                                                                             "doesn't exist.")).queue();
                        return;
                    }
                    ChallengeData challenge = new ChallengeData(new long[]{challenger1, challenger2}, bestof,
                            Instant.now().getEpochSecond() * 1000, null);
                    curAothChallenges.add(challenge);
                    event.reply(new MessageBuilder().append("<@" + challenger1 + "> <@" + challenger2 + ">")
                            .setEmbeds(new EmbedBuilder()
                                    .setColor(Constants.Colors.PRIMARY)
                                    .setTitle("AoTH Challenge Accepted", "https://generals.io/games/aoth")
                                    .setDescription("**Challengers:** " + challengerName1 + " " + challengerName2)
                                    .appendDescription("\n**Best of " + bestof + "**")
                                    .build())
                            .setActionRows(ActionRow.of(List.of(
                                    Button.link("Play", "https://generals.io/games/aoth"),
                                    Button.link("Spectate", "https://generals.io/games/aoth?spectate=true")
                            )))
                            .build()).queue((inx) -> {
                        inx.retrieveOriginal().queue(m -> {
                            challenge.challengeMsg = m;
                        });
                    });
                }
            } else if (event.getComponentId().startsWith("aoth-reject")) {
                final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(event.getGuild().getIdLong());
                if (!event.getMember().getRoles().contains(event.getGuild().getRoleById(GUILD_INFO.hillRoles.get(Constants.Hill.AoTH)))) {
                    return;
                }
                String[] idArgs = event.getComponentId().split("-");
                long challenger1 = Long.parseLong(idArgs[2]);
                long challenger2 = Long.parseLong(idArgs[3]);
                String challengerName1 = Database.getGeneralsName(challenger1);
                String challengerName2 = Database.getGeneralsName(challenger2);
                event.getMessage().editMessage(new MessageBuilder(event.getMessage()).setActionRows().build()).queue();
                if (challengerName1 == null || challengerName2 == null) {
                    event.getChannel().sendMessageEmbeds(Utils.error(event.getMessage(), "Challenge already invalid: " +
                                                                                         "challenger doesn't exist.")).queue();
                    return;
                }
                event.reply(new MessageBuilder().append("<@" + challenger1 + "> <@" + challenger2 + ">")
                        .setEmbeds(new EmbedBuilder()
                                .setColor(Constants.Colors.ERROR)
                                .setTitle("AoTH Challenge Rejected")
                                .setDescription("The AoTH didn't want to play against "
                                                + challengerName1 + " and " + challengerName2 + ".")
                                .build())
                        .build()).queue();
            } else if (event.getComponentId().equals("goth-score")) {

            } else if (event.getComponentId().equals("goth-manual")) {
                final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(event.getGuild().getIdLong());

                event.reply("Ask a <@&" + GUILD_INFO.moderatorRole + "> to set your GoTH score").queue();
                event.getMessage().editMessage(new MessageBuilder(event.getMessage()).setActionRows().build()).queue();
            }
        }

        public static void showGothHallOfFame(@NotNull Commands self, @NotNull Message msg, String[] args) {
            // TODO: test hall of fame
            if (args.length > 2) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Too many arguments provided.")).queue();
                return;
            }
            Challenge[] goths = Database.Hill.firstTerms(Constants.Hill.GoTH, Integer.MAX_VALUE);
            EmbedBuilder hofEmbed = new EmbedBuilder()
                    .setTitle("GoTH Hall of Fame");
            if (goths.length == 0) {
                hofEmbed.setDescription("No GoTHs yet! Be the first by accepting and winning a challenge (!challenge)" +
                                        ".");
            } else {
                for (int a = 0; a < goths.length; a++) {
                    String gothName = Database.getGeneralsName(Long.parseLong(goths[a].opp[0]));
                    if (gothName == null) {
                        gothName = "Unknown";
                    }
                    hofEmbed.appendDescription("\n#" + (a + 1) + ": "
                                               + gothName
                                               + "(<@" + goths[a].opp[0] + ">)");
                    int terms = Database.Hill.get(Constants.Hill.GoTH, goths[a].timestamp,
                            a + 1 < goths.length ? goths[a + 1].timestamp : Long.MAX_VALUE).length;
                    String startDate = Instant.ofEpochMilli(goths[a].timestamp).toString();
                    if (startDate.indexOf('T') != -1) {
                        startDate = startDate.substring(0, startDate.indexOf('T'));
                    }
                    if (a == goths.length - 1) {
                        String suffix = "";
                        switch (terms) {
                            case 1:
                                suffix = "st";
                                break;
                            case 2:
                                suffix = "nd";
                                break;
                            case 3:
                                suffix = "rd";
                                break;
                            default:
                                suffix = "th";
                                break;
                        }
                        hofEmbed.appendDescription("\n\t" + terms + suffix
                                                   + " term, started "
                                                   + startDate + " (current)");
                    } else {
                        terms--;
                        String endDate = Instant.ofEpochMilli(goths[a + 1].timestamp).toString();
                        if (endDate.indexOf('T') != -1) {
                            endDate = endDate.substring(0, endDate.indexOf('T'));
                        }
                        hofEmbed.appendDescription("\n\t" + terms + " term"
                                                   + (terms > 1 ? "s" : "")
                                                   + ", "
                                                   + startDate + " to " + endDate);
                    }
                }
            }
            msg.getChannel().sendMessageEmbeds(hofEmbed.build()).queue();
        }

        public static void showGothRecord(@NotNull Commands self, @NotNull Message msg, String[] args) {
            // TODO: implement !goth results

        }
    }
}
