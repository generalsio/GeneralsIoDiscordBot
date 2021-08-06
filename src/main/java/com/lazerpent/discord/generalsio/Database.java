package com.lazerpent.discord.generalsio;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

import java.sql.*;

public class Database {
    private static final Connection connection;
    private static final String DATABASE_TABLE = "discord_to_generals";

    static {
        Connection con;
        try {
            con = DriverManager.getConnection("jdbc:sqlite:database.db");
        } catch (SQLException e) {
            e.printStackTrace();
            con = null;
            System.exit(1);
        }
        connection = con;
    }

    public static void addDiscordGenerals(long discordId, String generalsName) {
        String sql = "insert into " + DATABASE_TABLE + " (discordId, generalsName) values(?,?)";
        try {
            PreparedStatement stm = connection.prepareStatement(sql);
            stm.setLong(1, discordId);
            stm.setString(2, generalsName);
            stm.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void updateDiscordGenerals(long oldDiscordId, long discordId, String generalsName) {
        String sql = "update " + DATABASE_TABLE + " set discordId = ?, generalsName = ? where discordId = ?";
        try {
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setLong(1, discordId);
            statement.setString(2, generalsName);
            statement.setLong(3, oldDiscordId);
            statement.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static String getGeneralsName(long discordId) {
        String sql = "select generalsName from " + DATABASE_TABLE + " where discordId=?";
        try {
            PreparedStatement stm = connection.prepareStatement(sql);
            stm.setLong(1, discordId);
            ResultSet resultSet = stm.executeQuery();
            resultSet.next();
            if (resultSet.isClosed()) {
                return null;
            }
            return resultSet.getString("generalsName");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static long getDiscordId(String generalsName) {
        String sql = "select discordId from " + DATABASE_TABLE + " where generalsName=?";
        try {
            PreparedStatement stm = connection.prepareStatement(sql);
            stm.setString(1, generalsName);
            ResultSet resultSet = stm.executeQuery();
            resultSet.next();
            if (resultSet.isClosed()) {
                return -1;
            }
            return resultSet.getLong("discordId");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static boolean noMatch(long discordId, String generalsName) {
        String sql = "select 1 from " + DATABASE_TABLE + " where generalsName=? or discordId=?";
        try {
            PreparedStatement stm = connection.prepareStatement(sql);
            stm.setString(1, generalsName);
            stm.setLong(2, discordId);
            ResultSet resultSet = stm.executeQuery();
            resultSet.next();
            return resultSet.isClosed();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }

    public static class Hill {
        private final static String TABLE = "challenges";
        private final static String DEL = "\u001F";
        // CREATE TABLE challenges (timestamp INT NOT NULL PRIMARY KEY, type INT NOT NULL, scoreInc INT NOT NULL, scoreOpp INT NOT NULL, opp TEXT NOT NULL, replays TEXT NOT NULL);

        public static class Challenge {
            public long timestamp;
            public int type;
            public int scoreInc;
            public int scoreOpp;
            public String[] opp;
            public String[] replays;
        }

        public static long add(Challenge c) {
            String sql = "INSERT INTO " + TABLE + " (timestamp, type, scoreInc, scoreOpp, opp, replays) VALUES (?, ?, ?, ?, ?, ?)";
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                int i = 1;
                stm.setLong(i++, c.timestamp);
                stm.setInt(i++, c.type);
                stm.setInt(i++, c.scoreInc);
                stm.setInt(i++, c.scoreOpp);
                stm.setString(i++, String.join(DEL, c.opp));
                stm.setString(i++, String.join(DEL, c.replays));
                stm.execute();
                return c.timestamp;
            } catch(SQLException e) {
                e.printStackTrace();
                return -1;
            }
        }

        public static void delete(long timestamp) {
            String sql = "DELETE FROM " + TABLE + " WHERE timestamp=?";
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                stm.setLong(1, timestamp);
                stm.execute();
            } catch(SQLException e) {
                e.printStackTrace();
            }
        }

        public static Challenge[] lastSwitches(n int) {
            String sql = "SELECT * FROM "  + TABLE + " WHERE scoreInc < scoreOpp ORDER BY timestamp ASC LIMIT ?";
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                stm.setInt(1, n);
                ResultSet result = stm.executeQuery();
                while (result.next()) {
                    
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
