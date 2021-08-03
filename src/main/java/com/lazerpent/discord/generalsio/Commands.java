package com.lazerpent.discord.generalsio;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public class Commands extends ListenerAdapter {

    // Maps holding all command methods.
    private static final Map<String, Method> commands = new HashMap<>();
    // Maps holding all categories that have their own class.
    private static final Map<String, Category> categories = new HashMap<>();

    static final String PREFIX = "!";
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

                if (!(Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 3 && method.getParameterTypes()[0].equals(Commands.class) && method.getParameterTypes()[1].equals(Message.class) && method.getParameterTypes()[2].equals(String[].class))) {
                    throw new IllegalStateException("invalid command handler method method: bad signature: " + method.getName());
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
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "\\`" + args[1] + "' is not number between 0 and 2")).queue();
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
    public static void handle_info(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        msg.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("Bot Information")
                .setColor(Constants.Colors.PRIMARY)
                .setDescription("Authors: **Lazerpent**, **person2597**, **pasghetti**")
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
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You don't have permission to use **!" + command[0] + "**")).queue();
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
                        .addField("Roles", "Want a role specific to the game modes you play? After registering your name, head over to <#787821221164351568> to get some roles.", false)
                        .build()).build()).queue();
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


    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println(event.getJDA().getGuilds());
    }

    @Override
    public void onGuildMessageReactionAdd(@NotNull GuildMessageReactionAddEvent event) {
        RoleHandler.reactionAdd(event);
    }

    @Category(cat = "game", name = "Game")
    public static class Game {
        private static Message createLinkEmbed(@NotNull Message msg, @NotNull Constants.Server server, @NotNull String link, @Nullable String map, int speed) {
            String url = "https://" + server.host() + "/games/" + Utils.encodeURI(link) + (speed != 1 ? "?speed=" + speed : "");
            if (link.equals("main") || link.equals("1v1")) {
                map = null;
                url = "https://" + server.host() + "/?queue=" + Utils.encodeURI(link);
            } else if (link.equals("2v2")) {
                map = null;
                url = "https://" + server.host() + "/teams/matchmaking";
            } else {
                if (map != null) {
                    try {
                        url += "&map=" + Utils.encodeURI(map);
                    } catch (Exception ignored) {
                    } // silently drop map
                }
            }

            EmbedBuilder embed =
                    new EmbedBuilder()
                            .setTitle("Custom Match", url).setColor(Constants.Colors.PRIMARY)
                            .setFooter(msg.getAuthor().getAsTag() + " • " + Database.getGeneralsName(msg.getAuthor().getIdLong()),
                                    msg.getAuthor().getAvatarUrl());
            embed.setDescription(url + "\n");
            if (speed != 1)
                embed = embed.appendDescription("\n**Speed:** " + speed);
            if (map != null)
                embed = embed.appendDescription("\n**Map:** " + map);

            List<Button> buttons = new ArrayList<>(List.of(
                    Button.link(url, "Play"),
                    Button.link(url + "&spectate=true", "Spectate")
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

        @Command(name = {"na", "custom", "private"}, args = {"code?", "map?"}, desc = "Generate match on NA servers", perms = Constants.Perms.USER)
        public static void handleNA(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            customLink(msg, cmd, Constants.Server.NA);
        }

        @Command(name = {"eu", "eucustom", "euprivate"}, args = {"code?", "map?"}, desc = "Generate match on EU servers", perms = Constants.Perms.USER)
        public static void handleEU(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            customLink(msg, cmd, Constants.Server.EU);
        }

        @Command(name = {"bot", "botcustom", "botprivate"}, args = {"code?", "map?"}, desc = "Generate match on Bot servers", perms = Constants.Perms.USER)
        public static void handleBOT(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            customLink(msg, cmd, Constants.Server.BOT);
        }

        private static void customLink(@NotNull Message msg, @NotNull String[] cmd, @NotNull Constants.Server server) {
            String link = cmd.length > 1 ? cmd[1] : Long.toString((long) (Math.random() * Long.MAX_VALUE), 36).substring(0, 4);
            String map = cmd.length > 2 ? String.join(" ", Arrays.asList(cmd).subList(2, cmd.length)) : null;
            if (map != null && (map.equals("-") || map.equals("."))) map = null;
            msg.getChannel().sendMessage(createLinkEmbed(msg, server, link, map, 1)).queue();
        }

        @Command(name = {"plots"}, args = {"code?"}, desc = "Create a 4x Plots Lazerpent game", perms = Constants.Perms.USER)
        public static void handlePlots(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            String link = cmd.length > 1 ? cmd[1] : "Plots";
            msg.getChannel().sendMessage(createLinkEmbed(msg, Constants.Server.NA, link, "Plots Lazerpent", 4)).queue();
        }

        @Command(name = {"ping"}, desc = "Ping role", perms = Constants.Perms.USER)
        public static void handlePing(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

            Constants.Mode mode = GUILD_INFO.channelToMode(msg.getChannel().getIdLong());
            if (mode == null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "**!ping** can only be used in a game mode channel")).queue();
                return;
            }

            if (!RoleHandler.tryPing(mode)) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Each role can only be pinged once every 10 minutes")).queue();
                return;
            }

            Role role = msg.getGuild().getRoleById(GUILD_INFO.roles.get(mode));
            msg.getChannel().sendMessage(Objects.requireNonNull(role).getAsMention() + " (pinged by " + Objects.requireNonNull(msg.getMember()).getAsMention() + ")").queue();
        }

        @Command(name = {"setuproles"}, desc = "Setup roles menu", perms = Constants.Perms.MOD)
        public static void handle_setuprole(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

            StringBuilder sb = new StringBuilder();
            for (Constants.Mode value : Constants.Mode.values()) {
                sb.append(msg.getGuild().getEmotesByName(value.toString(), false).get(0).getAsMention()).append(" - <@&").append(GUILD_INFO.roles.get(value)).append(">\n");
            }

            Message m = msg.getChannel().sendMessageEmbeds(new EmbedBuilder().setTitle("Generals.io Role Selector")
                    .setDescription("To select one or more roles, simply react with the role you would like to add or remove. \n\nEach role has a specific channel dedicated to that game mode. " +
                                    "You can also ping all players with the role using **!ping** in that game mode's channel.\n\nWant the <@&788259902254088192> role? DM or ping <@356517795791503393>. The tester role is pinged when <@356517795791503393> is testing a beta version on the BOT server.\n\n" +
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
        @Command(name = {"profile", "user"}, args = {"username? | @mention?"}, cat = "user", perms = Constants.Perms.USER, desc = "Show username of user, or vice versa")
        public static void handle_user(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            long discordId;
            String name;

            if (cmd.length == 1) {
                discordId = msg.getAuthor().getIdLong();
                name = Database.getGeneralsName(discordId);
                if (name == null) {
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg, Objects.requireNonNull(msg.getMember()).getAsMention() + " has not registered their generals.io user")).queue();
                    return;
                }
            } else {
                List<Member> members = msg.getMentionedMembers();
                if (members.size() == 0) {
                    name = Arrays.stream(cmd).skip(1).collect(Collectors.joining(" "));
                    discordId = Database.getDiscordId(name);
                    if (discordId == -1) {
                        msg.getChannel().sendMessageEmbeds(Utils.error(msg, name + " is not registered to any discord user")).queue();
                        return;
                    }
                } else if (members.size() != 1) {
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You can only lookup one user at a time")).queue();
                    return;
                } else {
                    discordId = members.get(0).getIdLong();
                    name = Database.getGeneralsName(discordId);
                    if (name == null) {
                        msg.getChannel().sendMessageEmbeds(Utils.error(msg, members.get(0).getAsMention() + " has not registered their generals.io user")).queue();
                        return;
                    }
                }
            }

            msg.getChannel().sendMessage(
                    new MessageBuilder()
                            .setEmbeds(new EmbedBuilder().setTitle("Profile: " + name, "https://generals.io/profiles/" + Utils.encodeURI(name)).setColor(Constants.Colors.PRIMARY)
                                    .appendDescription("**Discord:** " + Objects.requireNonNull(msg.getGuild().getMemberById(discordId)).getAsMention())
                                    .appendDescription("\n**Link:** " + "https://generals.io/profiles/" + Utils.encodeURI(name)).build())
                            .setActionRows(ActionRow.of(Button.link("https://generals.io/profiles/" + Utils.encodeURI(name), "Visit")))
                            .build()).queue();
        }

        @Command(name = {"addname"}, args = {"username"}, cat = "user", desc = "Register generals.io username")
        public static void handle_addname(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(msg.getGuild().getIdLong());

            if (cmd.length < 2) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "Missing username")).queue();
                return;
            }
            String username;
            if ((username = Database.getGeneralsName(Objects.requireNonNull(msg.getMember()).getIdLong())) != null) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You're already registered as **" + username + "**. Ask a <@&" + GUILD_INFO.moderatorRole + "> to change your username.")).queue();
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
                    msg.getChannel().sendMessageEmbeds(Utils.error(msg, "**" + username + "** is already registered to " + m.getAsMention())).queue();
                    return;
                }
            }

            Database.addDiscordGenerals(Objects.requireNonNull(msg.getMember()).getIdLong(), username);
            msg.getChannel().sendMessageEmbeds(new EmbedBuilder().setTitle("Username Added").setColor(Constants.Colors.SUCCESS)
                    .setDescription(msg.getMember().getAsMention() + " is now generals.io user **" + username + "**").build()).queue();
        }

        @Command(name = {"setname"}, cat = "user", args = {"@mention", "username"}, desc = "Change generals.io username of user", perms = Constants.Perms.MOD)
        public static void handle_setname(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            if (msg.getMentionedMembers().size() == 0) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must mention the member to update")).queue();
                return;
            }

            if (cmd.length < 2) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You must provide the generals.io username to update")).queue();
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
                    .setContent(Objects.requireNonNull(msg.getMember()).getAsMention())
                    .setEmbeds(
                            new EmbedBuilder().setTitle("Username Updated").setColor(Constants.Colors.SUCCESS)
                                    .setDescription(msg.getMember().getAsMention() + " is now generals.io user **" + name + "**").build()).build()).queue();
        }

    }

    @Override
    public void onGuildMemberUpdateNickname(@NotNull GuildMemberUpdateNicknameEvent event) {
        if (event.getUser().isBot() || !nickWupey) return;
        if (event.getEntity().getIdLong() == 175430325755838464L) {
            if (!"Wupey".equals(event.getNewNickname())) {
                event.getGuild().modifyNickname(event.getEntity(), "Wupey").queue();
            }
        }
    }
}