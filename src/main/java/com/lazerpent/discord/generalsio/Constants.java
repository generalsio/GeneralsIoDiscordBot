package com.lazerpent.discord.generalsio;

import java.awt.Color;

import java.util.Map;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;

/** Class storing constants */
public class Constants {
    public static class GuildInfo {
        private boolean built;
        public Map<Utils.Mode, Long> roles;
        public Map<Utils.Mode, Long> channels;
        public Set<Long> ignoreChannels;
        public long rolesMessage;

        public GuildInfo() {
            this.built = false;
        }

        public Utils.Mode channelToMode(long channelID) {
            for (Map.Entry<Utils.Mode, Long> e : this.channels.entrySet()) {
                if (Long.valueOf(e.getValue()) == channelID) {
                    return e.getKey();
                }    
            }

            return null;
        }

        public GuildInfo setRoles(Map<Utils.Mode, Long> Modes) {
            if (this.built) throw new IllegalStateException("object already built, why are you using it after .build()");
            this.roles = Modes;
            return this;
        }

        public GuildInfo setChannels(Map<Utils.Mode, Long> channels) {
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
            this.rolesMessage = id;
            return this;
        }

        public GuildInfo build() {
            if (this.built) throw new IllegalStateException("double-build after use");
            this.built = true;

            if (this.channels == null || this.ignoreChannels== null || this.roles == null)
                throw new IllegalStateException("initialization of GuildInfo wrong");

            return this;
        }
    }

    public final static Map<Long, GuildInfo> GUILD_INFO = Map.of(
        // GITS
        871581354800861195L, new GuildInfo()
            .setRoles(Map.of(
                Utils.Mode.FFA,    871581355002175559L,
                Utils.Mode.M1v1,   871581354800861203L,
                Utils.Mode.M2v2,   871581354800861202L,
                Utils.Mode.Custom, 871581354800861201L
            ))
            .setChannels(Map.of(
                Utils.Mode.FFA,    871581355262226459L,
                Utils.Mode.M1v1,   871581355262226460L,
                Utils.Mode.M2v2,   871581355262226461L,
                Utils.Mode.Custom, 871581355526459503L
            ))
            .setIgnoreChannels(871581355711012875L)
            .setRolesMessage(871842464917491803L)
            .build(),

        // real server
        252596486628573185L, new GuildInfo()
            .setRoles(Map.of(
                Utils.Mode.FFA,    761593333100838952L,
                Utils.Mode.M1v1,   787825376969097226L,
                Utils.Mode.M2v2,   761593217304363038L,
                Utils.Mode.Custom, 761593016020369459L
            ))
            .setChannels(Map.of(
                Utils.Mode.FFA,    442420790873423873L,
                Utils.Mode.M1v1,   793855287110402078L,
                Utils.Mode.M2v2,   456322523458437122L,
                Utils.Mode.Custom, 787818938628571216L
            ))
            .setIgnoreChannels(774660554362716181L)
            .setRolesMessage(795825358905409606L)
            .build()
    );

    public static class Colors {
        public static Color PRIMARY = new Color(0, 128, 128);
    }

    public static enum Server {
        NA, EU, BOT;

        public String host() {
            switch (this) {
            case NA: return "generals.io";
            case EU: return "eu.generals.io";
            case BOT: return "bot.generals.io";
            }
            return "";
        }
    }
}
