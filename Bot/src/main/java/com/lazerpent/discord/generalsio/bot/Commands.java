package com.lazerpent.discord.generalsio.bot;

import com.lazerpent.discord.generalsio.bot.commands.Game;
import com.lazerpent.discord.generalsio.bot.commands.Hill;
import com.lazerpent.discord.generalsio.bot.commands.Stats;
import com.lazerpent.discord.generalsio.bot.commands.Users;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Commands extends ListenerAdapter {

    static final String PREFIX = "!";

    // Maps holding all command methods.
    private static final Map<String, List<CommandMethod>> commands = new HashMap<>();

    // Maps holding all categories that have their own class.
    private static final Map<String, Category> categories = new HashMap<>();

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Command {
        String[] name();

        String cat() default ""; // category

        String desc();

        int perms() default Constants.Perms.NONE;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Category {
        String cat();

        String name();
    }

    /**
     * Checks if the current guild has an error channel, and if it does send the exception provided to it.
     * In both cases, the error is also printed to the standard out
     *
     * @param e       Throwable (Exception) which has a stack trace to be printed to the error channel (if it exists)
     * @param g       Guild which may or may not have an error channel (dependent on Constants.GUILD_INFO)
     * @param context String (which may be empty) representing context of this error, such as command being run at time
     */
    private static void logIfPresent(Throwable e, Guild g, String context) {
        e.printStackTrace();
        long channel = Constants.GUILD_INFO.get(g.getIdLong()).errorChannel;
        if (channel != -1) {
            StringWriter write = new StringWriter();
            e.printStackTrace(new PrintWriter(write));
            final TextChannel c = Objects.requireNonNull(g.getTextChannelById(channel));
            if (context.length() > 0) {
                c.sendMessage(context).queue();
            }
            c.sendMessage(write.toString().substring(0, 1999)).queue();
        }
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Selection {
        String[] value();
    }

    /**
     * Checks if the current guild has an error channel, and if it does send the exception provided to it.
     * In both cases, the error is also printed to the standard out
     *
     * @param e Throwable (Exception) which has a stack trace to be printed to the error channel (if it exists)
     * @param g Guild which may or may not have an error channel (dependent on Constants.GUILD_INFO)
     */
    private static void logIfPresent(Exception e, Guild g) {
        logIfPresent(e, g, "");
    }

    private static String formatUsage(String cmd, CommandMethod method) {
        StringBuilder s = new StringBuilder(PREFIX + cmd);
        for (Parameter param : method.params()) {
            if (param.getType() != Message.class)
                s.append(" [").append(param.getName()).append(param.getAnnotation(Optional.class) == null ? "" : "?").append("]");
        }
        return s.toString();
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        try {
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

            String content = msg.getContentRaw();
            if (!content.startsWith(PREFIX)) {
                return;
            }

            // split content into words; remove prefix
            content = content.replaceFirst(PREFIX, "");
            String[] command = content.split(" ");
            List<CommandMethod> commands = Commands.commands.get(command[0].toLowerCase());
            if (commands == null) {
                return;
            }
            Matcher match = Pattern.compile("(?<=<)[^<>]+(?=>)|[^<>\\s]+").matcher(
                    String.join(" ", Arrays.copyOfRange(command, 1, command.length)));
            String[] args = match.results()
                    .map(MatchResult::group)
                    .toArray(String[]::new);


            // check each command - if the match is found then run it and ignore the rest
            Map<CommandMethod, String> errors = new HashMap<>();
            for (CommandMethod cmd : commands) {
                // TODO: this assumes that commands with perm NONE do not have any overloads with higher perms
                if (cmd.command().perms() != Constants.Perms.NONE) {
                    if (Database.getGeneralsName(msg.getAuthor().getIdLong()) == null) {
                        msg.getChannel().sendMessageEmbeds(
                                new EmbedBuilder()
                                        .setTitle("Unknown generals.io Username")
                                        .setDescription("""
                                                You must register your generals.io username.\s
                                                Use ``!addname username`` to register.
                                                Example: ```!addname MyName321```""")
                                        .setColor(Constants.Colors.ERROR).build()).queue();
                        return; // If one is perms.none they all are so return is fine
                    }
                }

                // check for valid perms
                if (cmd.command().perms() > Constants.Perms.get(Objects.requireNonNull(msg.getMember()))) {
                    errors.put(cmd, "You don't have permission to use **!" + command[0] + "**\n");
                    continue;
                }

                // check if arguments match
                Pair<Object[], String> result = cmd.args(msg, args);
                if (result.getKey() == null) {
                    errors.put(cmd, result.getValue());
                    continue;
                }

                // run command
                try {
                    System.out.println(Arrays.stream(result.getKey()).map(x -> x == null ? "null" : x.toString()).collect(Collectors.joining(" + ")));
                    Object ret = cmd.method().invoke(null, result.getKey());
                    if (ret instanceof Message) {
                        msg.getChannel().sendMessage((Message) ret).queue();
                    } else if (ret instanceof MessageEmbed) {
                        msg.getChannel().sendMessageEmbeds((MessageEmbed) ret).queue();
                    } else if (ret != null) {
                        throw new IllegalStateException("command " + command[0] + " does not have a valid return " +
                                                        "value");
                    }
                    return;
                } catch (InvocationTargetException e) {
                    throw e.getTargetException();
                }
            }

            if (!errors.isEmpty()) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "No Matches",
                        errors.entrySet().stream().map(entry -> formatUsage(command[0], entry.getKey()) + "\n" + entry.getValue()).collect(Collectors.joining("\n\n"))
                )).queue();
            }
        } catch (Throwable e) {
            logIfPresent(e, event.getGuild(), event.getMessage().getAuthor().getAsMention() + ", " +
                                              event.getMessage().getContentDisplay());
        }
    }

    static {
        List<Method> methods = new ArrayList<>(Arrays.asList(Commands.class.getDeclaredMethods()));
        Class<?>[] classes = {Game.class, Hill.class, Stats.class, Users.class};

        for (Class<?> classCamelCase : classes) {
            Category category = classCamelCase.getAnnotation(Category.class);
            if (category != null) {
                methods.addAll(Arrays.asList(classCamelCase.getDeclaredMethods()));
                categories.put(category.cat(), category);
            }
        }

        for (Method method : methods) {
            Command cmd = method.getAnnotation(Command.class);
            if (cmd == null) {
                continue;
            }

            for (String name : cmd.name()) {
                final CommandMethod command = new CommandMethod(method);
                final List<CommandMethod> commandMethods = commands.get(name);
                if (commandMethods != null) {
                    commandMethods.add(command);
                } else {
                    commands.put(name, new ArrayList<>(Collections.singleton(command)));
                }
            }
        }
    }

    @Command(name = {"help"}, desc = "How to use this bot")
    public static void handleHelp(@NotNull Message msg, String cmd) {
        System.out.println("c " + cmd);
    }

    @Command(name = {"help"}, desc = "How to use this bot")
    public static void handleHelp(@NotNull Message msg, Integer perms) {
        System.out.println("p " + perms);
//        int perms = Constants.Perms.get(Objects.requireNonNull(msg.getMember()));
//        if (args.length > 1) {
//            try {
//                perms = Integer.parseInt(args[1]);
//                if (perms < 0 || perms > 2) {
//                    throw new Exception();
//                }
//            } catch (Exception err) {
//                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "\\`" + args[1] + "' is not number between 0
//                        and" +
//                        "2")).queue();
//                return;
//            }
//        }
//
//        EmbedBuilder embed = new EmbedBuilder().setColor(Constants.Colors.PRIMARY)
//                .setTitle("Bot Help")
//                .setFooter("Permissions: " + perms);
//
//        Map<String, Map<String, Command>> categoryCommands = new HashMap<>();
//        for (Method method : commands.values()) {
//            Command cmd = method.getAnnotation(Command.class);
//            String category = cmd.cat();
//            if (category.equals("")) {
//                Category cat = method.getDeclaringClass().getAnnotation(Category.class);
//                if (cat != null) {
//                    category = cat.cat();
//                }
//            }
//            if (cmd.perms() <= perms) {
//                categoryCommands.putIfAbsent(category, new HashMap<>());
//                categoryCommands.get(category).put(cmd.name()[0], cmd);
//            }
//        }
//
//        for (Map.Entry<String, Map<String, Command>> entry : categoryCommands.entrySet()) {
//            StringBuilder sb = new StringBuilder();
//            entry.getValue().forEach((key, value) -> {
//                sb.append(PREFIX).append(key);
//                Arrays.stream(value.args()).forEach(arg -> sb.append(" [").append(arg).append("]"));
//                sb.append(" - ").append(value.desc()).append("\n");
//            });
//
//            if (entry.getKey().equals("")) {
//                embed.setDescription(sb.toString());
//                continue;
//            }
//
//            String catName = entry.getKey();
//            if (categories.containsKey(catName)) {
//                catName = categories.get(catName).name();
//            }
//
//            embed = embed.addField(catName, sb.toString(), false);
//        }
//
//        msg.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    @Command(name = {"info"}, desc = "Credit where credit is due")
    public static void handleInfo(@NotNull Message msg) {
        final EmbedBuilder bot_information = new EmbedBuilder()
                .setTitle("Bot Information")
                .setColor(Constants.Colors.PRIMARY)
                .setDescription("Authors: **Lazerpent**, **person2597**, **pasghetti**")
                .appendDescription("\n\nhttps://github.com/Lazerpent/GeneralsIoDiscordBot")
                .setFooter("Written using JDA (Java Discord API)")
                .setThumbnail(msg.getJDA().getSelfUser().getEffectiveAvatarUrl());
        if (Constants.GUILD_INFO.get(msg.getGuild().getIdLong()).development) {
            bot_information.addField("Development Mode", "Currently running in development mode by " + System.getenv(
                    "DEVELOPMENT_MODE_DEVELOPER"), false);
        }

        msg.getChannel().sendMessageEmbeds(bot_information.build()).queue();
    }

    /**
     * Command handler for punish command. See Punishments. punish
     *
     * @param msg Message Object
     * @param cmd Parameters
     */
    @Command(name = {"addpunish", "punish"}, cat = "In-Game Moderation", desc = "Add user to the punishment list",
            perms = Constants.Perms.MOD)
    public static void handleAddPunish(@NotNull Message msg, String[] cmd) {
        Punishments.punish(msg, cmd);
    }

    /**
     * Command handler for disable command. See Punishments. disable
     *
     * @param msg Message Object
     * @param cmd Parameters
     */
    @Command(name = {"adddisable", "disable"}, cat = "In-Game Moderation", desc = "Add user to the disable list",
            perms = Constants.Perms.MOD)
    public static void handleAddDisable(@NotNull Message msg, String[] cmd) {
        Punishments.disable(msg, cmd);
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Optional {
    }

    private static record CommandMethod(Method method) {
        private static final BiFunction<String, String, IllegalStateException> exception =
                (error, name) -> new IllegalStateException("Invalid Command Handler Method `" + name + "`: " + error);

        public CommandMethod {
            java.util.Objects.requireNonNull(method.getAnnotation(Command.class));
            if (!method.getName().startsWith("handle"))
                throw exception.apply("illegal name: name must start with `handle`", method.getName());
            if (!Modifier.isStatic(method.getModifiers())) throw exception.apply("not static", method.getName());

            // TODO: checks
        }

        public Command command() {
            return this.method().getAnnotation(Command.class);
        }

        public Parameter[] params() {
            return this.method().getParameters();
        }

        public Pair<Object[], String> args(Message msg, String[] args) {
            Parameter[] params = params();
            if (args.length > params.length) {
                return new ImmutablePair<>(null,
                        "Expected maximum " + params.length + " parameters, found " + args.length + " parameters");
            }

            Object[] out = new Object[params.length];
            int j = 0;
            for (int i = 0; i < params.length; i++) {
                Parameter param = params[i];

                Class<?> type = param.getType();
                if (type == Message.class) {
                    out[i] = msg;
                    continue;
                }

                if (j >= args.length) { // argument not present
                    out[i] = null;

                    if (param.getAnnotation(Optional.class) == null) {
                        return new ImmutablePair<>(null,
                                "Expected minimum " + (j + 1) + " parameters, found " + args.length + " parameters");
                    }
                    j++;
                    continue;
                }

                if (type == byte.class || type == Byte.class) {
                    out[i] = Byte.parseByte(args[j]);
                } else if (type == short.class || type == Short.class) {
                    out[i] = Short.parseShort(args[j]);
                } else if (type == int.class || type == Integer.class) {
                    out[i] = Integer.parseInt(args[j]);
                } else if (type == long.class || type == Long.class) {
                    out[i] = Long.parseLong(args[j]);
                } else if (type == String.class) {
                    out[i] = args[j];

                    Selection sel = param.getAnnotation(Selection.class);
                    if (sel != null) {
                        if (!Arrays.asList(sel.value()).contains(args[j])) {
                            return new ImmutablePair<>(null, "Parameter " + param.getName() + " must be " +
                                                             String.join(" | ", sel.value()) + ", is " + args[j]);
                        }
                    }
                } else if (type == Member.class) {
                    if (args[j].startsWith("<@") && args[j].endsWith(">")) {
                        long userID;
                        try {
                            userID = Long.parseLong(args[j]);
                        } catch (NumberFormatException e) {
                            return new ImmutablePair<>(null, "Argument " + param.getName() + " is not a mention");
                        }

                        out[i] = msg.getGuild().getMemberById(userID);
                        if (out[i] == null) {
                            return new ImmutablePair<>(null, "Argument " + param.getName() + " mentions a member that" +
                                                             " no longer exists");
                        }
                    } else {
                        return new ImmutablePair<>(null, "Argument " + param.getName() + " is not a mention");
                    }
                } else if (type == String[].class) {
                    if (i != params.length - 1) {
                        return new ImmutablePair<>(null, "Argument " + param.getName() + " is String[] and therefore " +
                                                         "must be the last argument");
                    }

                    out[i] = Arrays.copyOfRange(args, j, args.length);
                } else if (type.isEnum()) {
                    try {
                        Object ret = type.getMethod("values").invoke(null);
                        Object[] items = new Object[Array.getLength(ret)];
                        String[] names = new String[items.length];
                        for (int k = 0; k < items.length; k++) {
                            items[k] = Array.get(ret, k);
                            String name = (String) type.getMethod("name").invoke(items[k]);
                            names[k] = name.toLowerCase();
                        }

                        for (int k = 0; k < items.length; k++) {
                            if (Objects.equals(names[k], args[j].toLowerCase())) {
                                out[i] = items[k];
                                break;
                            }
                        }

                        return new ImmutablePair<>(null, "Argument " + param.getName() + " must be " +
                                                         String.join(" | ", names) + ", is " + args[j]);
                    } catch (Throwable t) {
                        throw new IllegalStateException(t.getMessage());
                    }
                } else {
                    return new ImmutablePair<>(null, "Unknown type");
                }
                // TODO:
                // - enums
                // - Guild?
                // - Database.User

                j++;
            }

            return new ImmutablePair<>(out, "");
        }
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println(event.getJDA().getGuilds());
        Hill.init();
    }

    /**
     * Command handler for getCommands. See Punishments.getCommands
     *
     * @param msg Message Object
     * @param cmd Parameters
     */
    @Command(name = {"getpunishcommand", "getpunishcommands", "cmd"}, perms = Constants.Perms.MOD,
            cat = "In-Game Moderation", desc = "Gets a list of commands to run to punish players")
    public static void handleGetCommand(@NotNull Message msg, String[] cmd) {
        Punishments.getCommands(msg);
    }


    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        try {
            // Send a goodbye message (252599855841542145 is the new channel)
            Objects.requireNonNull(event.getGuild().getTextChannelById(252599855841542145L)).sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("Goodbye " + event.getUser().getName() + "#" + event.getUser().getDiscriminator())
                    .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                    .setColor(Constants.Colors.ERROR)
                    .setDescription("We will miss you.")
                    .build()).queue();
        } catch (Exception e) {
            logIfPresent(e, event.getGuild());
        }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        try {
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
                            .addField("Roles", "Want a role specific to the game modes you play? After registering " +
                                               "your " +
                                               "name, head over to <#787821221164351568> to get some roles.", false)
                            .build()).build()).queue();
        } catch (Exception e) {
            logIfPresent(e, event.getGuild());
        }
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

    @Override
    public void onGuildMessageReactionAdd(@NotNull GuildMessageReactionAddEvent event) {
        try {
            RoleHandler.reactionAdd(event);
        } catch (Exception e) {
            logIfPresent(e, event.getGuild());
        }
    }

    @Override
    public void onGuildMemberUpdateNickname(@NotNull GuildMemberUpdateNicknameEvent event) {
        try {
            if (event.getUser().isBot() || !Users.getNickWupey())
                return;
            if (event.getEntity().getIdLong() == 175430325755838464L) {
                if (!"Wupey".equals(event.getNewNickname())) {
                    event.getGuild().modifyNickname(event.getEntity(), "Wupey").queue();
                }
            }
        } catch (Exception e) {
            logIfPresent(e, event.getGuild());
        }
    }
}
