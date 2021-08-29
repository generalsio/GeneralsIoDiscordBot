package com.lazerpent.discord.generalsio.bot;

import com.lazerpent.discord.generalsio.bot.commands.Game;
import com.lazerpent.discord.generalsio.bot.commands.Hill;
import com.lazerpent.discord.generalsio.bot.commands.Stats;
import com.lazerpent.discord.generalsio.bot.commands.Users;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class Commands extends ListenerAdapter {

    static final String PREFIX = "!";

    // Maps holding all command methods.
    private static final Map<String, List<CommandMethod>> commands = new HashMap<>();

    // Maps holding all categories that have their own class.
    private static final Map<String, Category> categories = new HashMap<>();

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

        BiFunction<String, String, IllegalStateException> exception =
                (error, name) -> new IllegalStateException("Invalid Command Handler Method: " + error + ", " + name);


        for (Method method : methods) {
            Command cmd = method.getAnnotation(Command.class);
            if (cmd == null) {
                continue;
            }
            if (!method.getName().startsWith("handle")) {
                throw exception.apply("Illegal Name", method.getName());
            }

            if (!Modifier.isStatic(method.getModifiers())) {
                throw exception.apply("Not Static", method.getName());
            }

            // In a command the first parameter is always the message object. After that, all parameters are params
            final Parameter[] parameters = method.getParameters();
            int i = 0;
            if (!parameters[i++].getType().equals(Message.class)) {
                throw exception.apply("Invalid Parameters", method.getName());
            }
            // Minus 1 since we already know the first parameter is the message object
            List<CommandMethod.Argument> args = new ArrayList<>(parameters.length - 1);
            boolean optional = false; // All the required parameters must be before any optional ones
            for (int size = parameters.length; i < size; i++) {
                // Check the types
                Parameter param = parameters[i];

                boolean required = param.getAnnotation(Required.class) != null;
                if (required && optional) {
                    throw exception.apply("Required after Optional", method.getName());
                }
                optional = !required;

                Class<?> type = param.getType();
                int t = -1;
                if (type == String.class) {
                    t = CommandMethod.Argument.Type.STRING;
                } else if (type == Integer.class) {
                    t = CommandMethod.Argument.Type.INTEGER;
                } else if (type == Member.class) {
                    t = CommandMethod.Argument.Type.MENTION;
                }
                if (type == String[].class) {
                    continue;
                }

                Function<String, Boolean> validate = s -> true;

                // Now check option validation
                final Selection selection = param.getAnnotation(Selection.class);
                if (selection != null) {
                    if (type != String.class) {
                        throw exception.apply("Parameter selection applied on non-string", method.getName());
                    }

                    final List<String> strings = Arrays.stream(selection.opt()).toList();
                    validate = s -> strings.contains(s.toLowerCase(Locale.ROOT));
                }


                if (t == -1) {
                    throw exception.apply("Invalid Parameter Type", method.getName());
                }

                args.add(new CommandMethod.Argument(param.getName(), required, t, validate));
                // TODO generate help
            }

            for (String name : cmd.name()) {
                final CommandMethod command = new CommandMethod(method, args, cmd.perms());
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
    public static void handleHelp(@NotNull Message msg, @Required String cmd) {
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

    /**
     * Checks if the current guild has an error channel, and if it does send the exception provided to it.
     * In both cases, the error is also printed to the standard out
     *
     * @param e       Throwable (Exception) which has a stack trace to be printed to the error channel (if it exists)
     * @param g       Guild which may or may not have a error channel (dependent on Constants.GUILD_INFO)
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

    /**
     * Checks if the current guild has an error channel, and if it does send the exception provided to it.
     * In both cases, the error is also printed to the standard out
     *
     * @param e Throwable (Exception) which has a stack trace to be printed to the error channel (if it exists)
     * @param g Guild which may or may not have a error channel (dependent on Constants.GUILD_INFO)
     */
    private static void logIfPresent(Exception e, Guild g) {
        logIfPresent(e, g, "");
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println(event.getJDA().getGuilds());
        Hill.init();
    }

    private static String formatUsage(String cmd, List<CommandMethod.Argument> args) {
        StringBuilder s = new StringBuilder(PREFIX + cmd);
        for (CommandMethod.Argument arg : args) {
            s.append(" [").append(arg.name()).append(arg.required() ? "" : "?").append("]");
        }
        return s.toString();
    }

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
                System.out.println(Commands.commands);
                return;
            }

            StringBuilder errors = new StringBuilder();

            // Check each command - if the match is found then run it and ignore the rest
            commandLoop:
            for (CommandMethod cmd : commands) {
                // By handling the names here, it forces members to add their generals name to use any function of
                // the bot
                if (cmd.permission() != Constants.Perms.NONE) {
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

                if (cmd.permission() > Constants.Perms.get(Objects.requireNonNull(msg.getMember()))) {
                    errors.append("You don't have permission to use **!").append(command[0]).append("**\n");
                    continue;
                }

                // Now check parameters

                // Start by breaking up the content and using quotes (not required) to get parameters with spaces
                Matcher match = Pattern.compile("(?<=<)[^<>]+(?=>)|[^<>\\s]+").matcher(
                        String.join(" ", Arrays.copyOfRange(command, 1, command.length)));
                List<String> args = match.results()
                        .map(MatchResult::group)
                        .toList();

                List<CommandMethod.Argument> parameters = cmd.parameters();

                // Check the size first thing
                if (args.size() > parameters.size()) {
                    errors.append("Too many parameters\nUsage: ")
                            .append(formatUsage(command[0].toLowerCase(Locale.ROOT), parameters)).append("\n\n");
                    continue;
                }


                final List<Object> params = new ArrayList<>(args);
                IntStream.range(params.size(), parameters.size()).forEach(i -> params.add(null));

                for (int i = 0, parametersSize = parameters.size(); i < parametersSize; i++) {
                    CommandMethod.Argument parameter = parameters.get(i);
                    if (args.size() <= i) {

                        if (parameter.required()) {
                            errors.append("Missing parameter ").append(parameter.name()).append("\nUsage: ")
                                    .append(formatUsage(command[0].toLowerCase(Locale.ROOT), parameters)).append("\n\n");
                            continue commandLoop;
                        }
                        break;
                    }

                    // Start by checking the type
                    final String s = args.get(i);
                    if (parameter.type() == CommandMethod.Argument.Type.STRING) {
                        boolean valid = switch (parameter.name()) {
                            case "username", "username1", "username2" -> s.length() <= 18;
                            // TODO add validation for mentions

                            default -> true;
                        } || parameter.validate().apply(s);
                        if (!valid) {
                            errors.append("Invalid parameter ").append(parameter.name()).append("\n");
                            continue commandLoop;
                        }
                    } else if (parameter.type() == CommandMethod.Argument.Type.INTEGER) {
                        if (!s.matches("^-?\\d+$")) {
                            errors.append("Invalid parameter (expected number) ").append(parameter.name()).append("\n");
                            continue commandLoop;
                        }

                        params.set(i, Integer.valueOf(s));
                    } else if (parameter.type() == CommandMethod.Argument.Type.MENTION) {
                        // TODO mentions
                    }
                }


                params.add(0, msg);
                try {
                    cmd.method().invoke(null, params.toArray());
                    return;
                } catch (InvocationTargetException e) {
                    throw e.getTargetException();
                }
            }

            if (!errors.isEmpty()) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, errors.toString())).queue();
            }
        } catch (Throwable e) {
            logIfPresent(e, event.getGuild(), event.getMessage().getAuthor().getAsMention() + ", " +
                                              event.getMessage().getContentDisplay());
        }
    }

    /**
     * Set on a @Command parameter if it is required - by default parameters are considered optional
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Required {
    }


    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Selection {
        String[] opt();
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

    private static record CommandMethod(Method method, List<Argument> parameters, int permission) {
        private static record Argument(String name, boolean required, int type, Function<String, Boolean> validate) {
            private static class Type {
                public static final int STRING = 0, INTEGER = 1, MENTION = 2;
            }
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
