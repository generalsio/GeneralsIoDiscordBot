package com.lazerpent.discord.generalsio.bot.commands;

import com.lazerpent.discord.generalsio.bot.Commands.Category;
import com.lazerpent.discord.generalsio.bot.Commands.Command;
import com.lazerpent.discord.generalsio.bot.Commands.CommandParameter;
import com.lazerpent.discord.generalsio.bot.Constants;
import com.lazerpent.discord.generalsio.bot.Database;
import com.lazerpent.discord.generalsio.bot.RoleHandler;
import com.lazerpent.discord.generalsio.bot.Utils;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Category(name = "Game")
public class Game {
    @Command(name = "custom", desc = "Generate match on NA servers", perms = Constants.Perms.USER)
    public static void handleNA(@NotNull SlashCommandEvent cmd,
                                @CommandParameter(name = "code",
                                        desc = "ID of the custom room",
                                        optional = true) String code,
                                @CommandParameter(name = "map",
                                        desc = "Map name",
                                        optional = true) String map,
                                @CommandParameter(name = "speed",
                                        desc = "Speed at which turns pass",
                                        optional = true) Integer speed) {
        cmd.reply(customLink(cmd, Constants.Server.NA, code, map, speed)).queue();
    }

    @Command(name = "eucustom", desc = "Generate match on EU servers", perms =
            Constants.Perms.USER)
    public static void handleEU(@NotNull SlashCommandEvent cmd,
                                @CommandParameter(name = "code",
                                        desc = "ID of the custom room",
                                        optional = true) String code,
                                @CommandParameter(name = "map",
                                        desc = "Map name",
                                        optional = true) String map,
                                @CommandParameter(name = "speed",
                                        desc = "Speed at which turns pass",
                                        optional = true) Integer speed) {
        cmd.reply(customLink(cmd, Constants.Server.EU, code, map, speed)).queue();
    }

    @Command(name = "botcustom", desc = "Generate match on Bot servers", perms =
            Constants.Perms.USER)
    public static void handleBOT(@NotNull SlashCommandEvent cmd,
                                 @CommandParameter(name = "code",
                                         desc = "ID of the custom room",
                                         optional = true) String code,
                                 @CommandParameter(name = "map",
                                         desc = "Map name",
                                         optional = true) String map,
                                 @CommandParameter(name = "speed",
                                         desc = "Speed at which turns pass",
                                         optional = true) Integer speed) {
        cmd.reply(customLink(cmd, Constants.Server.BOT, code, map, speed)).queue();
    }

    @Command(name = "plots", desc = "Create a 4x Plots Lazerpent game", perms = Constants.Perms.USER)
    public static void handlePlots(@NotNull SlashCommandEvent cmd,
                                   @CommandParameter(name = "code",
                                           desc = "ID of the custom room",
                                           optional = true) String code) {
        cmd.reply(createLinkEmbed(cmd, Constants.Server.NA, code == null ? "Plots" : code, "Plots Lazerpent", 4)).queue();
    }

    @Command(name = "bogless", desc = "Create a bogless monsters game", perms = Constants.Perms.USER)
    public static void handleBogless(@NotNull SlashCommandEvent cmd,
                                     @CommandParameter(name = "code",
                                             desc = "ID of the custom room",
                                             optional = true) String code) {
        cmd.reply(createLinkEmbed(cmd, Constants.Server.NA, code == null ? "Bogless" : code, "bogless monsters", 1)).queue();
    }

    @Command(name = "ping", desc = "Ping role", perms = Constants.Perms.USER)
    public static void handlePing(@NotNull SlashCommandEvent cmd) {
        final Constants.GuildInfo GUILD_INFO =
                Constants.GUILD_INFO.get(Objects.requireNonNull(cmd.getGuild()).getIdLong());

        Constants.Mode mode = GUILD_INFO.channelToMode(cmd.getChannel().getIdLong());
        if (mode == null) {
            Utils.replyError(cmd, "**/ping** can only be used in a game mode channel.");
            return;
        }

        if (!RoleHandler.tryPing(mode)) {
            Utils.replyError(cmd, "Each role can only be pinged once every 10 minutes.");
            return;
        }

        Role role = cmd.getGuild().getRoleById(GUILD_INFO.roles.get(mode));
        cmd.reply("Pinged by " + Objects.requireNonNull(cmd.getMember()).getAsMention()).queue();
        cmd.getChannel().sendMessage(new MessageBuilder().setContent(Objects.requireNonNull(role).getAsMention()).build()).queue();
    }

    private static Message customLink(@NotNull SlashCommandEvent cmd, @NotNull Constants.Server server, String code,
                                      String map,
                                      Integer speed) {
        String link = code != null && code.matches("^[\\d\\w]+$") ? code :
                Long.toString((long) (Math.random() * Long.MAX_VALUE), 36).substring(0, 4);
        if (map != null && (map.equals("-") || map.equals("."))) map = null;
        return createLinkEmbed(cmd, server, link, map,
                speed != null && (speed == 2 || speed == 3 || speed == 4) ? speed : 1);
    }

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
                        .setFooter(Objects.requireNonNull(cmd.getMember()).getUser().getAsTag() + " • "
                                        + Database.getGeneralsName(cmd.getMember().getUser().getIdLong()),
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
}