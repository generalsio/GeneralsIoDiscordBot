package com.lazerpent.discord.generalsio;

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

import org.jetbrains.annotations.NotNull;

import com.lazerpent.discord.generalsio.commands.*;

import javax.annotation.Nonnull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class Commands extends ListenerAdapter {

    static final String PREFIX = "!";
    // Maps holding all command methods.
    private static final Map<String, Method> commands = new HashMap<>();
    // Maps holding all categories that have their own class.
    private static final Map<String, Category> categories = new HashMap<>();
    //    private static Feedback feedBack;

    static {
        List<Method> methods = new ArrayList<>(Arrays.asList(Commands.class.getDeclaredMethods()));
        Class<?>[] classes = { Game.class, Hill.class, Stats.class, Users.class };

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
        if (event.getUser().isBot() || !Users.getNickWupey())
            return;
        if (event.getEntity().getIdLong() == 175430325755838464L) {
            if (!"Wupey".equals(event.getNewNickname())) {
                event.getGuild().modifyNickname(event.getEntity(), "Wupey").queue();
            }
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Command {
        String[] name();

        String cat() default ""; // category

        String[] args() default {};

        String desc();

        int perms() default Constants.Perms.NONE;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Category {
        String cat();

        String name();
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
}
