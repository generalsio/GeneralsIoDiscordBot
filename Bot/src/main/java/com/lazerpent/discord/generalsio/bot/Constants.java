package com.lazerpent.discord.generalsio.bot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.privileges.CommandPrivilege;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class storing constants
 */
public class Constants {
    public final static Map<Long, GuildInfo> GUILD_INFO = Map.of(
            // GITS
            871581354800861195L, new GuildInfo()
                    .setRoles(Map.of(
                            Mode.FFA, 871581355002175559L,
                            Mode.M1v1, 871581354800861203L,
                            Mode.M2v2, 871581354800861202L,
                            Mode.Custom, 871581354800861201L
                    ))
                    .setChannels(Map.of(
                            Mode.FFA, 871581355262226459L,
                            Mode.M1v1, 871581355262226460L,
                            Mode.M2v2, 871581355262226461L,
                            Mode.Custom, 871581355526459503L
                    ))
                    .setHillRoles(Map.of(
                            Hill.GoTH, 871581355031560285L,
                            Hill.AoTH, 871581355031560284L
                    ))
                    .setIgnoreChannels(871581355711012875L)
                    .setRolesMessage(871842464917491803L)
                    .setModeratorRole(871581355031560289L)
                    .setDeveloperRole(871581355031560288L)
                    .setEventNotificationRole(871581354800861200L)
                    .setReportCheatersChannel(871581355526459508L)
                    .setReportRecordChannel(900771668971102258L)
                    .setApplicationCategory(979185782252273684L)
                    .setDevelopment(true)
                    .build(),

            // real server
            252596486628573185L, new GuildInfo()
                    .setRoles(Map.of(
                            Mode.FFA, 761593333100838952L,
                            Mode.M1v1, 787825376969097226L,
                            Mode.M2v2, 761593217304363038L,
                            Mode.Custom, 761593016020369459L
                    ))
                    .setChannels(Map.of(
                            Mode.FFA, 442420790873423873L,
                            Mode.M1v1, 793855287110402078L,
                            Mode.M2v2, 456322523458437122L,
                            Mode.Custom, 787818938628571216L
                    ))
                    .setHillRoles(Map.of(
                            Hill.GoTH, 833446837230764062L,
                            Hill.AoTH, 851232536482021377L
                    ))
                    .setIgnoreChannels(774660554362716181L)
                    .setRolesMessage(896844526713454593L)
                    .setModeratorRole(309536399005188097L)
                    .setDeveloperRole(252879375089926145L)
                    .setErrorChannel(880559867818037248L)
                    .setReportCheatersChannel(714986176859340831L)
                    .setReportRecordChannel(900772522331619358L)
                    .setEventNotificationRole(896836627895320606L)
                    .setApplicationCategory(979185658692243487L)
                    .build()
    );

    public enum Server {
        NA, EU, BOT;

        public String host() {
            return switch (this) {
                case NA -> "generals.io";
                case EU -> "eu.generals.io";
                case BOT -> "bot.generals.io";
            };
        }
    }

    public enum Mode {
        FFA, M1v1, M2v2, Custom;

        public static Mode fromString(String s) {
            return switch (s) {
                case "ffa" -> Mode.FFA;
                case "1v1" -> Mode.M1v1;
                case "2v2" -> Mode.M2v2;
                case "custom" -> Mode.Custom;
                default -> null;
            };
        }

        public String toString() {
            return switch (this) {
                case FFA -> "ffa";
                case M1v1 -> "1v1";
                case M2v2 -> "2v2";
                case Custom -> "custom";
            };
        }
    }

    public enum Hill {
        GoTH(0, 1, new Color(26, 188, 156)),
        AoTH(1, 2, new Color(155, 89, 182));

        public final int id;
        public final int teamSize;
        public final Color color;

        Hill(int id, int teamSize, Color color) {
            this.id = id;
            this.teamSize = teamSize;
            this.color = color;
        }

        public static Hill fromId(int id) {
            for (Hill mode : Hill.values()) {
                if (mode.id == id) {
                    return mode;
                }
            }
            return null;
        }

        public static Hill fromString(String s) {
            for (Hill mode : Hill.values()) {
                if (mode.name().equalsIgnoreCase(s)) {
                    return mode;
                }
            }
            return null;
        }
    }

    public static class Colors {
        public static final Color PRIMARY = new Color(52, 152, 219);
        public static final Color ERROR = new Color(240, 71, 71);
        public static final Color SUCCESS = new Color(67, 181, 129);
    }

    public static class GuildInfo {
        public Map<Mode, Long> roles;
        public Map<Mode, Long> channels;
        public Set<Long> ignoreChannels;
        public long rolesMessage;
        public long moderatorRole;
        public long developerRole;
        public Map<Hill, Long> hillRoles;
        public boolean development;
        public long errorChannel = -1;
        public long reportCheatersChannel = -1;
        public long reportRecordChannel = -1;
        public long applicationCategory = -1;
        public long eventRole;
        private boolean built;

        public GuildInfo() {
            this.built = false;
            this.development = false;
        }

        public Mode channelToMode(long channelID) {
            for (Map.Entry<Mode, Long> e : this.channels.entrySet()) {
                if (e.getValue() == channelID) {
                    return e.getKey();
                }
            }

            return null;
        }

        public GuildInfo setRoles(Map<Mode, Long> Modes) {
            if (this.built)
                throw new IllegalStateException("object already built, why are you using it after .build()");
            this.roles = Modes;
            return this;
        }

        public GuildInfo setChannels(Map<Mode, Long> channels) {
            if (this.built) throw new IllegalStateException("object already built, how are you messing this up");
            this.channels = channels;
            return this;
        }

        public GuildInfo setIgnoreChannels(Long... ids) {
            if (this.built) throw new IllegalStateException("object already built, how are you messing this up");
            this.ignoreChannels = Set.copyOf(Arrays.asList(ids));
            return this;
        }

        public GuildInfo setRolesMessage(long id) {
            if (this.built) throw new IllegalStateException("object already built, how are you messing this up");
            this.rolesMessage = id;
            return this;
        }

        public GuildInfo setModeratorRole(long id) {
            if (this.built) throw new IllegalStateException("object already built, how are you messing this up");
            this.moderatorRole = id;
            return this;
        }

        public GuildInfo setDeveloperRole(long id) {
            if (this.built) throw new IllegalStateException("object already built, how are you messing this up");
            this.developerRole = id;
            return this;
        }

        public GuildInfo setHillRoles(Map<Hill, Long> roles) {
            if (this.built) throw new IllegalStateException("object already built, how are you messing this up");
            this.hillRoles = roles;
            return this;
        }

        public GuildInfo setDevelopment(boolean dev) {
            if (this.built) throw new IllegalStateException("object already built, how are you messing this up");
            this.development = dev;
            return this;
        }

        public GuildInfo setErrorChannel(long id) {
            if (this.built) throw new IllegalStateException("object already built, how are you messing this up");
            this.errorChannel = id;
            return this;
        }

        public GuildInfo setReportCheatersChannel(long id) {
            if (this.built) throw new IllegalStateException("object already built, how are you messing this up");
            this.reportCheatersChannel = id;
            return this;
        }

        public GuildInfo setReportRecordChannel(long id) {
            if (this.built) throw new IllegalStateException("object already built, how are you messing this up");
            this.reportRecordChannel = id;
            return this;
        }

        public GuildInfo setApplicationCategory(long id) {
            if (this.built) throw new IllegalStateException("object already built, how are you messing this up");
            this.applicationCategory = id;
            return this;
        }

        public GuildInfo setEventNotificationRole(long id) {
            if (this.built) throw new IllegalStateException("object already built, how are you messing this up");
            this.eventRole = id;
            return this;
        }

        public GuildInfo build() {
            if (this.built) throw new IllegalStateException("double-build after use");
            this.built = true;

            if (this.channels == null || this.ignoreChannels == null || this.roles == null || this.hillRoles == null)
                throw new IllegalStateException("initialization of GuildInfo wrong");

            return this;
        }
    }

    public static class Perms {
        public final static int NONE = 0;
        public final static int USER = 1;
        public final static int MOD = 2;

        /**
         * @return whether said user has a given permission.
         */
        public static int get(@NotNull Member mbr) {
            if (mbr.hasPermission(Permission.MESSAGE_MANAGE) || mbr.getIdLong() == 426133274692419615L) { // t.h.i.n.g
                return Perms.MOD;
            } else if (Database.getGeneralsName(mbr.getIdLong()) != null) {
                return Perms.USER;
            } else {
                return Perms.NONE;
            }
        }

        public static List<CommandPrivilege> getMod(long guildId) {
            return List.of(CommandPrivilege.enableRole(GUILD_INFO.get(guildId).moderatorRole),
                    CommandPrivilege.enableRole(GUILD_INFO.get(guildId).developerRole),
                    CommandPrivilege.enableUser(426133274692419615L)
            );
        }
    }
}
