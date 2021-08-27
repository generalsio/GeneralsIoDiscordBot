package com.lazerpent.discord.generalsio.commands;

import com.lazerpent.discord.generalsio.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Commands.Category(cat = "game", name = "Game")
public class Game {
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

    @Commands.Command(name = {"na", "custom", "private"}, args = {"code?", "map?"}, desc = "Generate match on NA " +
                                                                                           "servers",
            perms = Constants.Perms.USER)
    public static void handleNA(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        customLink(msg, cmd, Constants.Server.NA);
    }

    @Commands.Command(name = {"eu", "eucustom", "euprivate"}, args = {"code?", "map?"}, desc = "Generate match on EU " +
                                                                                               "servers", perms =
            Constants.Perms.USER)
    public static void handleEU(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        customLink(msg, cmd, Constants.Server.EU);
    }

    @Commands.Command(name = {"bot", "botcustom", "botprivate"}, args = {"code?", "map?"}, desc = "Generate match on " +
                                                                                                  "Bot " +
                                                                                                  "servers", perms =
            Constants.Perms.USER)
    public static void handleBOT(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        customLink(msg, cmd, Constants.Server.BOT);
    }

    private static void customLink(@NotNull Message msg, @NotNull String[] cmd, @NotNull Constants.Server server) {
        String link = cmd.length > 1 && cmd[1].matches("^[\\d\\w]+$") ? cmd[1] :
                Long.toString((long) (Math.random() * Long.MAX_VALUE), 36).substring(0, 4);
        String map = cmd.length > 2 ? String.join(" ", Arrays.asList(cmd).subList(2, cmd.length)) : null;
        if (map != null && (map.equals("-") || map.equals("."))) map = null;
        msg.getChannel().sendMessage(createLinkEmbed(msg, server, link, map, 1)).queue();
    }

    @Commands.Command(name = {"plots"}, args = {"code?"}, desc = "Create a 4x Plots Lazerpent game", perms =
            Constants.Perms.USER)
    public static void handlePlots(@NotNull Commands self, @NotNull Message msg, String[] cmd) {
        String link = cmd.length > 1 ? cmd[1] : "Plots";
        msg.getChannel().sendMessage(createLinkEmbed(msg, Constants.Server.NA, link, "Plots Lazerpent", 4)).queue();
    }

    @Commands.Command(name = {"ping"}, desc = "Ping role", perms = Constants.Perms.USER)
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

    @Commands.Command(name = {"setuproles"}, desc = "Setup roles menu", perms = Constants.Perms.MOD)
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