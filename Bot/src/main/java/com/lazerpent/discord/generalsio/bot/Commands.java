package com.lazerpent.discord.generalsio.bot;

import com.lazerpent.discord.generalsio.bot.commands.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

public class Commands extends ListenerAdapter {

    // Map holding all slash commands. Identifiers must be unique. Aliases will not be considered.
    private static final Map<String, Method> slashCommands = new HashMap<>();

    static {
        List<Method> methods = new ArrayList<>(Arrays.asList(Commands.class.getDeclaredMethods()));
        Class<?>[] classes = {Game.class, Hill.class, Stats.class, Users.class, Punishments.class};

        for (Class<?> classCamelCase : classes) {
            if (classCamelCase.getAnnotation(Category.class) != null) {
                methods.addAll(Arrays.asList(classCamelCase.getDeclaredMethods()));
            }
        }
        for(Method method : methods) {
            if(method.getAnnotation(Command.class) == null) continue;
            String name = method.getAnnotation(Command.class).name();
            if(!slashCommands.containsKey(name)) {
                slashCommands.put(method.getAnnotation(Command.class).name(), method);
            } else {
                throw new IllegalStateException("Colliding command names: " + name);
            }
        }
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

    @Command(name = "help", desc = "List commands")
    public static MessageEmbed handleHelp(@NotNull SlashCommandEvent cmd,
                                          @CommandParameter(name = "perms", desc = "Permission level") @Optional Integer perms) {
        if (perms == null) {
            perms = Constants.Perms.get(Objects.requireNonNull(cmd.getMember()));
        }
        EmbedBuilder embed = new EmbedBuilder().setColor(Constants.Colors.PRIMARY)
                .setTitle("Bot Help")
                .setFooter("Permissions: " + perms);

        Map<String, List<String>> categoryCommands = new HashMap<>();
        for (Method method : slashCommands.values()) {
            Command curCommand = method.getAnnotation(Command.class);
            String category = "";
            Category cat = method.getDeclaringClass().getAnnotation(Category.class);
            if (cat != null) {
                category = cat.name();
            }
            if (curCommand.perms() <= perms) {
                categoryCommands.putIfAbsent(category, new ArrayList<>());
                categoryCommands.get(category).add(curCommand.name());
            }
        }

        for (Map.Entry<String, List<String>> entry : categoryCommands.entrySet()) {
            String commandList = entry.getValue().stream().map(x -> "!" + x).collect(Collectors.joining(", "));

            if (entry.getKey().equals("")) {
                embed.setDescription(commandList);
            } else {
                embed = embed.addField(entry.getKey(), commandList, false);
            }
        }

        return embed.build();
    }

    @Command(name = "info", desc = "Credit where credit is due")
    public static MessageEmbed handleInfo(@NotNull SlashCommandEvent cmd) {
        final EmbedBuilder bot_information = new EmbedBuilder()
                .setTitle("Bot Information")
                .setColor(Constants.Colors.PRIMARY)
                .setDescription("Authors: **Lazerpent**, **person2597**, **pasghetti**")
                .appendDescription("\n\nhttps://github.com/Lazerpent/GeneralsIoDiscordBot")
                .setFooter("Written using JDA (Java Discord API)")
                .setThumbnail(cmd.getJDA().getSelfUser().getEffectiveAvatarUrl());
        if (Constants.GUILD_INFO.get(cmd.getGuild().getIdLong()).development) {
            bot_information.addField("Development Mode", "Currently running in development mode by " + System.getenv(
                    "DEVELOPMENT_MODE_DEVELOPER"), false);
        }

        return bot_information.build();
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println(event.getJDA().getGuilds());
        List<CommandData> slashCommandBuffer = new ArrayList<>();
        for(Map.Entry<String, Method> entry : slashCommands.entrySet()) {
            String name = entry.getKey();
            Method method = entry.getValue();
            CommandData cd = new CommandData(name, method.getAnnotation(Command.class).desc());
            for(Parameter param : method.getParameters()) {
                Class<?> type = param.getType();
                boolean required = param.getAnnotation(Optional.class) == null;
                CommandParameter optionProperties = param.getAnnotation(CommandParameter.class);
                if(optionProperties == null) {
                    continue;
                }
                OptionType optionType;
                String[] choices = new String[0];
                if(type == int.class || type == Integer.class) {
                    optionType = OptionType.INTEGER;
                } else if(type == double.class || type == Double.class) {
                    optionType = OptionType.NUMBER;
                } else if(type == boolean.class || type == Boolean.class) {
                    optionType = OptionType.BOOLEAN;
                } else if(type.getAnnotation(Selection.class) != null) {
                    optionType = OptionType.STRING;
                    choices = type.getAnnotation(Selection.class).value();
                } else if(type.getAnnotation(SubcommandName.class) != null) {
                    optionType = OptionType.SUB_COMMAND;
                    choices = type.getAnnotation(SubcommandName.class).value();
                } else if(type.getAnnotation(SubcommandGroupName.class) != null) {
                    optionType = OptionType.SUB_COMMAND_GROUP;
                    choices = type.getAnnotation(SubcommandGroupName.class).value();
                } else if(type == String.class) {
                    optionType = OptionType.STRING;
                } else if(type == Member.class) {
                    // note: assumes that bot will never handle commands in DMs (all mentioned users are members)
                    optionType = OptionType.USER;
                } else if(type == MessageChannel.class) {
                    optionType = OptionType.CHANNEL;
                } else if(type == Role.class) {
                    optionType = OptionType.ROLE;
                } else if(type == IMentionable.class) {
                    optionType = OptionType.MENTIONABLE;
                } else if(type.isEnum()) {
                    optionType = OptionType.STRING;
                    choices = Arrays.stream(type.getEnumConstants()).map(Object::toString).toArray(String[]::new);
                } else {
                    throw new IllegalStateException(String.format(
                            "Parameter of type %s in %s is not valid option for slash command.",
                            type.getName(),
                            method.getName())
                    );
                }
                OptionData od = new OptionData(optionType, optionProperties.name(), optionProperties.desc(), required);
                for(String choice : choices) {
                    od.addChoice(choice, choice);
                }
                cd.addOptions(od);
            }
            slashCommandBuffer.add(cd);
        }
        event.getJDA().updateCommands().addCommands(slashCommandBuffer).queue();
        Hill.init();
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        // TODO: get slash command and take options from event and plug into parameters. also fix all the annotations
        Method command = slashCommands.get(event.getName());
        try {
            if(command != null) {
                if (command.getAnnotation(Command.class).perms() != Constants.Perms.NONE) {
                    if (Database.getGeneralsName(event.getMember().getIdLong()) == null) {
                        event.reply(new MessageBuilder().setEmbeds(
                                new EmbedBuilder()
                                        .setTitle("Unknown generals.io Username")
                                        .setDescription("""
                                                You must register your generals.io username.\s
                                                Use ``!addname username`` to register.
                                                Example: ```!addname MyName321```""")
                                        .setColor(Constants.Colors.ERROR).build()).build());
                        return; // If one is perms.none they all are so return is fine
                    }
                }

                // check for valid perms
                if (command.getAnnotation(Command.class).perms() > Constants.Perms.get(Objects.requireNonNull(event.getMember()))) {
                    event.reply(new MessageBuilder().setEmbeds(
                            new EmbedBuilder()
                                    .setTitle("Invalid Command")
                                    .setDescription("You don't have permission to use **/" + event.getName() + "**\n")
                                    .setColor(Constants.Colors.ERROR).build()).build());
                    return;
                }
                List<Object> args = new ArrayList<>();
                args.add(event);
                for (OptionMapping opt : event.getOptions()) {
                    args.add(switch (opt.getType()) {
                        case UNKNOWN -> throw new Exception("Unknown parameter type. Parameter name: " + opt.getName());
                        case SUB_COMMAND, SUB_COMMAND_GROUP, STRING -> opt.getAsString();
                        case INTEGER -> Long.valueOf(opt.getAsLong()).intValue();
                        case BOOLEAN -> opt.getAsBoolean();
                        case USER -> opt.getAsMember();
                        case CHANNEL -> opt.getAsMessageChannel();
                        case ROLE -> opt.getAsRole();
                        case MENTIONABLE -> opt.getAsMentionable();
                        case NUMBER -> opt.getAsDouble();
                    });
                }
                while(args.size() < command.getParameters().length) {
                    args.add(null); // optional parameters
                }
                Object ret = command.invoke(null, args.toArray());
                if (ret instanceof Message) {
                    event.reply((Message) ret).queue();
                } else if (ret instanceof MessageEmbed) {
                    event.reply(new MessageBuilder().setEmbeds((MessageEmbed) ret).build()).queue();
                } else if (ret != null) {
                    throw new IllegalStateException("command " + event.getName() + " does not have a valid return " +
                            "value:  " + ret.getClass().toString());
                }
            }
        } catch(Exception e) {
            logIfPresent(e, Objects.requireNonNull(event.getGuild()), event.getMember().getAsMention() + ", "
                    + event.getCommandString());
        }
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


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Command {
        String name();

        String desc();

        int perms() default Constants.Perms.NONE;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Category {
        String name();
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Selection {
        String[] value();
    }
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface SubcommandGroupName {
        String[] value();
    }
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface SubcommandName {
        String[] value();
    }
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CommandParameter {
        String name();
        String desc();
    }
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Optional {
    }
}
