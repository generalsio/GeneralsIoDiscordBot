package com.lazerpent.discord.generalsio.bot.commands;

import com.lazerpent.discord.generalsio.bot.*;
import com.lazerpent.discord.generalsio.bot.Commands.Category;
import com.lazerpent.discord.generalsio.bot.Commands.Command;
import com.lazerpent.discord.generalsio.bot.Commands.CommandParameter;
import com.lazerpent.discord.generalsio.bot.Commands.Optional;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Category(name = "Game")
public class Game {
    private static Message createLinkEmbed(@NotNull SlashCommandEvent cmd, @NotNull Constants.Server server,
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
                        .setFooter(cmd.getMember().getUser().getAsTag() + " • " + Database.getGeneralsName(cmd.getMember().getUser().getIdLong()),
                                cmd.getMember().getUser().getAvatarUrl());

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

    @Command(name = "custom", desc = "Generate match on NA servers",
            perms = Constants.Perms.USER)
    public static Message handleNA(@NotNull SlashCommandEvent cmd,
                                   @CommandParameter(name = "code", desc = "ID of the custom room") @Optional String code,
                                   @CommandParameter(name = "map", desc = "Map name") @Optional String map,
                                   @CommandParameter(name = "speed", desc = "Speed at which turns pass") @Optional Integer speed) {
        return customLink(cmd, Constants.Server.NA, code, map, speed);
    }

    @Command(name = "eucustom", desc = "Generate match on EU servers", perms =
            Constants.Perms.USER)
    public static Message handleEU(@NotNull SlashCommandEvent cmd,
                                   @CommandParameter(name = "code", desc = "ID of the custom room") @Optional String code,
                                   @CommandParameter(name = "map", desc = "Map name") @Optional String map,
                                   @CommandParameter(name = "speed", desc = "Speed at which turns pass") @Optional Integer speed) {
        return customLink(cmd, Constants.Server.EU, code, map, speed);
    }

    @Command(name = "botcustom", desc = "Generate match on Bot servers", perms =
            Constants.Perms.USER)
    public static Message handleBOT(@NotNull SlashCommandEvent cmd,
                                    @CommandParameter(name = "code", desc = "ID of the custom room") @Optional String code,
                                    @CommandParameter(name = "map", desc = "Map name") @Optional String map,
                                    @CommandParameter(name = "speed", desc = "Speed at which turns pass") @Optional Integer speed) {
        return customLink(cmd, Constants.Server.BOT, code, map, speed);
    }

    private static Message customLink(@NotNull SlashCommandEvent cmd, @NotNull Constants.Server server, String code, String map,
                                      Integer speed) {
        String link = code != null && code.matches("^[\\d\\w]+$") ? code :
                Long.toString((long) (Math.random() * Long.MAX_VALUE), 36).substring(0, 4);
        if (map != null && (map.equals("-") || map.equals("."))) map = null;
        return createLinkEmbed(cmd, server, link, map,
                speed != null && (speed == 2 || speed == 3 || speed == 4) ? speed : 1);
    }

    @Command(name = "plots", desc = "Create a 4x Plots Lazerpent game", perms = Constants.Perms.USER)
    public static Message handlePlots(@NotNull SlashCommandEvent cmd,
                                      @CommandParameter(name = "code", desc = "ID of the custom room") @Optional String code) {
        return createLinkEmbed(cmd, Constants.Server.NA, code == null ? "Plots" : code
                , "Plots Lazerpent", 4);
    }

    @Command(name = "bogless", desc = "Create a bogless monsters game", perms = Constants.Perms.USER)
    public static Message handleBogless(@NotNull SlashCommandEvent cmd,
                                        @CommandParameter(name = "code", desc = "ID of the custom room") @Optional String code) {
        return createLinkEmbed(cmd, Constants.Server.NA, code == null ? "Bogless" : code
                , "bogless monsters", 1);
    }

    @Command(name = "ping", desc = "Ping role", perms = Constants.Perms.USER)
    public static Object handlePing(@NotNull SlashCommandEvent cmd) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(cmd.getGuild().getIdLong());

        Constants.Mode mode = GUILD_INFO.channelToMode(cmd.getChannel().getIdLong());
        if (mode == null) {
            return Utils.error(cmd, "**!ping** can only be used in a game mode channel");
        }

        if (!RoleHandler.tryPing(mode)) {
            return Utils.error(cmd, "Each role can only be pinged once every 10 minutes");
        }

        Role role = cmd.getGuild().getRoleById(GUILD_INFO.roles.get(mode));
        cmd.reply(Objects.requireNonNull(role).getAsMention() + " (pinged by " + Objects.requireNonNull(cmd.getMember()).getAsMention() + ")").queue();
        return null;
    }

    @Command(name = "setuproles", desc = "Setup roles menu", perms = Constants.Perms.MOD)
    public static void handleSetupRoles(@NotNull SlashCommandEvent cmd) {
        final Constants.GuildInfo GUILD_INFO = Constants.GUILD_INFO.get(cmd.getGuild().getIdLong());

        StringBuilder sb = new StringBuilder();
        for (Constants.Mode value : Constants.Mode.values()) {
            sb.append(cmd.getGuild().getEmotesByName(value.toString(), false).get(0).getAsMention()).append(" - <@&").append(GUILD_INFO.roles.get(value)).append(">\n");
        }

        Message m = new MessageBuilder().setEmbeds(new EmbedBuilder().setTitle("Generals.io Role Selector")
                .setDescription("To select one or more roles, simply react with the role you would like to add or" +
                                " remove. \n\nEach role has a specific channel dedicated to that game mode. " +
                                "You can also ping all players with the role using **!ping** in that game mode's " +
                                "channel.\n\nWant the <@&788259902254088192> role? DM or ping " +
                                "<@356517795791503393>. The tester role is pinged when <@356517795791503393> is " +
                                "testing a beta version on the BOT server.\n\n" +
                                sb
                ).setFooter("You must wait 3 seconds between adding a role and removing it.")
                .setThumbnail(cmd.getGuild().getIconUrl()).build()).build();

        for (Constants.Mode value : Constants.Mode.values()) {
            m.addReaction(cmd.getGuild().getEmotesByName(value.toString(), false).get(0)).queue();
        }
        cmd.reply(new MessageBuilder().setEmbeds(Utils.success(cmd, "Added role selector")).build()).queue();
    }

}