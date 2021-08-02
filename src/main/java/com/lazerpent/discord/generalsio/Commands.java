package com.lazerpent.discord.generalsio;

import java.awt.Color;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Arrays;
import java.net.URLEncoder;
import java.util.List;

import javax.annotation.Nonnull;

import com.lazerpent.discord.generalsio.Constants.Server;
import com.lazerpent.discord.generalsio.Utils.Perms;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;

public class Commands extends ListenerAdapter {
    private final static String STRING_NULL = null;

    @Retention(RetentionPolicy.RUNTIME)
    static @interface Command {
        String[] name();
        String cat() default ""; // category
        String[] args() default {};
        String desc();
        int perms() default Utils.Perms.NONE;
    }

    @Retention(RetentionPolicy.RUNTIME)
    static @interface Category {
        String cat();
        String name();
    }

    static final String PREFIX = "!";

    private static Feedback feedBack;

    private static boolean nickWupey = false;

    public Commands() {
        feedBack = new Feedback();
    }

    // Maps holding all command methods.
    private static Map<String, Method> commands = new HashMap<>();

    // Maps holding all categories that have their own class.
    private static Map<String, Category> categories = new HashMap<>();
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

                if (! (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 3 && method.getParameterTypes()[0].equals(Commands.class) && method.getParameterTypes()[1].equals(Message.class) && method.getParameterTypes()[2].equals(String[].class)) ) {
                    throw new IllegalStateException("invalid command handler method method: bad signature: " + method.getName());
                }

                for (String name : cmd.name()) {
                    commands.put(name, method);
                }
            }
        }
    }

    @Command(name={"help"}, args={"perms"}, desc="How to use this bot")
    public static void handle_help(@NotNull Commands self, @NotNull Message msg, String[] args) {
        int perms = Utils.Perms.get(msg.getMember());
        if (args.length > 1) {
            try {
                perms = Integer.parseInt(args[1]);
                if (perms < 0 || perms > 2) {
                    throw new Exception();
                }
            } catch(Exception err) {
                msg.getChannel().sendMessageEmbeds(Utils.error(msg, "\\`" + args[1] + "' is not number between 0 and 2")).queue();
                return;
            }
        }

        EmbedBuilder embed = new EmbedBuilder().setColor(Constants.Colors.PRIMARY)
            .setTitle("Bot Help")
            .setFooter("Permissions: " + perms);
        
        Map<String, Map<String, Command>> categoryCommands = new HashMap<>();
        for (Method method: commands.values()) {
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
            for (Map.Entry<String, Command> cmdEntry : entry.getValue().entrySet()) {
                sb.append(PREFIX + cmdEntry.getKey());
                for (String arg : cmdEntry.getValue().args()) {
                    sb.append(" [" + arg + "]");
                }
                sb.append(" - " + cmdEntry.getValue().desc() + "\n");
            }

            if (entry.getKey().equals("")) {
                embed = embed.setDescription(sb.toString());
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

    @Command(name={"info"}, desc="Credit where credit is due")
    public static void handle_info(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        msg.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("Bot Information")
                .setColor(Constants.Colors.PRIMARY)
                .setDescription("Authors: **Lazerpent**, **person2597**, **pasghetti**")
                .setFooter("Written using JDA (Java Discord API)")
                .setThumbnail(msg.getJDA().getSelfUser().getEffectiveAvatarUrl()).build()).queue(); 
    }

    @Category(cat="game", name="Game")
    public static class Game {
        private static Message createLinkEmbed(@NotNull Message msg, @NotNull Constants.Server server, String[] cmd) {
            String link = cmd.length > 1 ? cmd[1] :  Long.toString((long)(Math.random() * Long.MAX_VALUE), 36).substring(0, 4);
            String map = cmd.length > 2 ? String.join(" ", Arrays.asList(cmd).subList(2, cmd.length)) : null;
            if (map != null && (map.equals("-") || map.equals("."))) map = null;

            String url = "https://" + server.host() + "/games/" + link;
            try {
                if (map != null) url += "?map=" + URLEncoder.encode(map, "UTF-8").replaceAll("\\+", "%20");
            } catch(Exception err) {
                err.printStackTrace();
            }

            return new MessageBuilder()
                .setActionRows(ActionRow.of(
                    Button.link(url, "Play"),
                    Button.link(url + "?spectate=true", "Spectate")
                ))
                .setEmbeds(
                    new EmbedBuilder()
                        .setTitle("Custom Match", url).setColor(new Color(50, 50, 150))
                        .setDescription("Link: " + url + (map == null ? "" : "\nMap: " + map))
                        .setFooter(msg.getAuthor().getAsTag() + " • " + Database.getGeneralsName(msg.getAuthor().getIdLong()),
                        msg.getAuthor().getAvatarUrl()).build()
                )
                .build();
        }

        @Command(name={"custom", "private"}, args={"map?", "code?"}, desc="Generate custom match", perms=Utils.Perms.USER)
        public static void handle_custom(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            String map = Arrays.stream(cmd).skip(1).findFirst().orElse(null);
            String link = Arrays.stream(cmd).skip(2).findFirst().orElse(null);
            msg.getChannel().sendMessage(createLinkEmbed(msg, Constants.Server.NA, cmd)).queue();
        }
    
        @Command(name={"bot", "botcustom", "botprivate"}, args={"code?"}, desc="Generate custom match on bot server", perms=Utils.Perms.USER)
        public static void handle_botcustom(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            String link = Arrays.stream(cmd).skip(1).findFirst().orElse(null);
            msg.getChannel().sendMessage(createLinkEmbed(msg, Constants.Server.BOT, cmd)).queue();
        }
            
        @Command(name={"ping"}, desc="Ping role", perms=Utils.Perms.USER)    
        public static void handle_ping(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            RoleHandler.ping(msg);
        }

        @Command(name={"setuproles"}, desc="Setup roles menu", perms=Utils.Perms.MOD)    
        public static void handle_setuprole(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
            RoleHandler.setup(msg);
        }

    }

    @Command(name={"addname"}, args={"username"}, cat="user", desc="Register generals.io username")
    public static void handle_addname(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        Registration.addName(msg, cmd);
    }

    @Command(name={"profile", "user"}, args={"username? | @mention?"}, cat="user", desc="Show username of user, or vice versa")
    public static void handle_user(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        // TODO: add link to user
        Registration.lookup(msg, cmd);
    }

    @Command(name={"setname"}, cat="user", args={"@mention", "username"}, desc="Set name of user", perms=Utils.Perms.MOD)
    public static void handle_setname(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        Registration.set(msg, cmd);
    }

    @Command(name={"nickwupey"}, cat="sadism", desc="Bully Wuped", perms=Utils.Perms.MOD)    
    public static void handle_nickwupey(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
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
        //Cancel if user is a bot
        if (event.getAuthor().isBot()) {
            return;
        }

        if (event.getChannel().getIdLong() == 774660554362716181L) {
            return; // #music
        }

        Message msg = event.getMessage();

        //If its not from a guild, send it to private message handler
        if (!msg.isFromGuild()) {
//            feedBack.privateMessage(msg);
            return;
        }

        String content = msg.getContentDisplay();
        if (!content.startsWith(PREFIX)) {
            return;
        }
        //Split the content into words, and remove the prefix
        content = content.replaceFirst(PREFIX, "");
        String[] command = content.split(" ");
        Method cmdMethod = commands.get(command[0]);
        if (cmdMethod == null) {
            return;
        }

        Command cmdInfo = cmdMethod.getAnnotation(Command.class);

        // By handling the names here, it forces members to add their generals name to use any function of the bot
        if (cmdInfo.perms() != Utils.Perms.NONE) {
            if (Database.getGeneralsName(msg.getAuthor().getIdLong()) == null) {
                msg.getChannel().sendMessageEmbeds(
                        new EmbedBuilder()
                                .setTitle("Unknown generals.io Username")
                                .setDescription("You must register your generals.io username." +
                                                " \nUse ``!addname username`` to register.\nExample: ```!addname MyName321```")
                                .setColor(new Color(123, 11, 11)).build()).queue();
                return;
            }
        }

        if (cmdInfo.perms() > Utils.Perms.get(msg.getMember())) {
            msg.getChannel().sendMessageEmbeds(Utils.error(msg, "You don't have permission to use **!" + command[0] + "**")).queue();
            return;
        }

        try {
            cmdMethod.invoke(null, this, msg, command);
        } catch(IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

        // Only change the case on the command, leave the parameters the same
/*        switch (command[0].toLowerCase()) {
            case "help":
                information.help(msg);
                break;
            case "info":
                information.info(msg);
                break;
            case "private":
            case "custom":
                information.privateMatch(msg, command);
                break;

            case "privatebot":
            case "custombot":
            case "botcustom":
                information.privateMatchBot(msg, command);
                break;
//            case "report":
//                feedBack.report(msg, command);
//                break;
            case "addname":
            case "registername":
                Registration.addName(msg, command);
                break;
            case "lookup":
            case "profile":
            case "user":
                Registration.lookup(msg, command);
                break;
            case "setname":
            case "update":
                Registration.set(msg, command);
                break;

            case "ping":
                RoleHandler.ping(msg);
                break;

            case "setuprolemessage":
                RoleHandler.setup(msg);
                break;

            case "nickwupey":
                if (msg.getAuthor().getIdLong() == 356517795791503393L) {
                    nickWupey = !nickWupey;
                    msg.getChannel().sendMessage("Switched auto-nick to " + nickWupey).queue();
                    if (nickWupey) {
                        msg.getGuild().modifyNickname(Objects.requireNonNull(msg.getGuild().retrieveMemberById(175430325755838464L).complete()), "Wupey").queue();
                    }
                }
                break;

//            case "rules":
//                msg.delete().queue();
//                List<Webhook> webhooks = msg.getTextChannel().retrieveWebhooks().complete();
//
//                Optional<Webhook> webhookOptional = webhooks.stream().filter(w -> w.getName().equals("Discord Rules")).findAny();
//                Webhook hook = webhookOptional.orElseGet(() -> msg.getTextChannel().createWebhook("Discord Rules").complete());
//                WebhookClient client = new WebhookClientBuilder(hook.getIdLong(), Objects.requireNonNull(hook.getToken())).build();
//
//
//                WebhookEmbed rules = new WebhookEmbedBuilder()
////                        .setTitle(new WebhookEmbed.EmbedTitle("Generals.io Server Rules", null))
//                        .setColor(new Color(0, 128, 128).getRGB())
//                        .setFooter(new WebhookEmbed.EmbedFooter("Rules may change at any time | Rules last updated", msg.getGuild().getIconUrl()))
//                        .setTimestamp(Instant.now())
//                        .setDescription("1. Do your best to be nice.\n" +
//                                        "\n" +
//                                        "2. Don't harass anyone.\n" +
//                                        "\n" +
//                                        "3. Don't post NSFW, inappropriate, or otherwise offensive content in **any channel**.\n" +
//                                        "\n" +
//                                        "4. Swearing is allowed but don't be excessive/malicious and absolutely no ethnic/LGBT-related/religious slurs.\n" +
//                                        "\n" +
//                                        "5. Keep all unrelated links / advertising to a **minimum**, and do not post outside of <#532835865274220545>.\n" +
//                                        "\n" +
//                                        "6. Do not use more than one Discord account on this server (i.e. no alts).\n" +
//                                        "\n" +
//                                        "7. No spam: just use your head and don't be ridiculous.\n" +
//                                        "\n" +
//                                        "8. For lengthy off-topic discussions, move to <#532835865274220545>.\n" +
//                                        "\n" +
//                                        "9. No encouraging or helping anyone to cheat in any way.\n")
//                        .build();
//
//                WebhookEmbed mods = new WebhookEmbedBuilder()
////                        .setTitle(new WebhookEmbed.EmbedTitle("Generals.io Server Moderators", null))
//                        .setColor(new Color(0, 128, 128).getRGB())
//                        .setDescription("Our <@&309536399005188097>s are here to support you and happy to help with any problem. If you see someone breaking the rules, or just have a question about the rules, feel free to message one of them. \n\n**For in-game related questions/support, do not message our moderators. Send an email to __support@generals.io__.**")
//                        .addField(new WebhookEmbed.EmbedField(false, "Current Moderators", "<@430608929853014026> \n" + "<@175430325755838464>"))
//
//                        .setFooter(new WebhookEmbed.EmbedFooter("All rules and punishments are at our moderators' discretion and may be different depending on circumstances.", msg.getGuild().getIconUrl()))
//                        .build();
//                WebhookMessageBuilder b = new WebhookMessageBuilder();
//
//                b.addFile(new File("C:\\Users\\chris\\Pictures\\Screenshot 2021-03-13 164808.png"));
//                b.setAvatarUrl(msg.getGuild().getIconUrl());
//                client.send(b.build());
//
//                b = new WebhookMessageBuilder();
//
//                b.addEmbeds(rules);
//                b.setAvatarUrl(msg.getGuild().getIconUrl());
//                client.send(b.build());
//
//                b = new WebhookMessageBuilder();
//                b.addFile(new File("C:\\Users\\chris\\Pictures\\Screenshot 2021-03-13 164313.png"));
//                b.setAvatarUrl(msg.getGuild().getIconUrl());
//                client.send(b.build());
//
//
//                b = new WebhookMessageBuilder();
//                b.addEmbeds(mods);
//                b.setAvatarUrl(msg.getGuild().getIconUrl());
//                client.send(b.build());
//
//                b = new WebhookMessageBuilder();
//                b.append("https://discord.gg/QP63V5Y");
//                b.setAvatarUrl(msg.getGuild().getIconUrl());
//                b.setUsername("Discord Invite");
//                client.send(b.build());
//
//                client.close();


//            case "apply":
//                ApplicationHandler.apply(msg);
//                break;
//
//            case "cancelapply":
//                ApplicationHandler.cancelapply(msg);
//                break;*/
// }
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
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        //When a user leaves the guild, send to event registration to remove from registration llist
//        EventRegistration.guildLeave(event.getUser());

        // Also send a goodbye message (252599855841542145 is the new channel)
        Objects.requireNonNull(event.getGuild().getTextChannelById(252599855841542145L)).sendMessage(new EmbedBuilder()
                .setTitle("Goodbye " + event.getUser().getName() + "#" + event.getUser().getDiscriminator())
                .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                .setColor(new Color(75, 200, 100))
                .setDescription("We will miss you.")
                .build()).queue();
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        // When a user joins the guild, send a message to the channel (252599855841542145 is the new channel)
        Objects.requireNonNull(event.getGuild().getTextChannelById(252599855841542145L))
                .sendMessage(new MessageBuilder().append(event.getMember()).setEmbed(new EmbedBuilder()
                        .setTitle("Welcome " + event.getMember().getEffectiveName())
                        .setThumbnail(event.getMember().getUser().getEffectiveAvatarUrl())
                        .setColor(new Color(75, 200, 100))
                        .setDescription("Make sure you add your generals.io name to our bot, using ``!addname generals.io_username``.\nExample: ```!addname MyName321```\n" +
                                        "Head over to <#754022719879643258> to register your name.")
                        .addField("Roles", "Want a role specific to the game modes you play? After registering your name, head over to <#787821221164351568> to get some roles.", false)
//                        .addField("Tournament", "**Want to register for our current Tournament?** After you add your generals.io name, use ``!register``", true)
                        .build()).build()).queue();
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