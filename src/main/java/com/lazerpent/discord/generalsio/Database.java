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
            public Constants.Hill type;
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
                stm.setInt(i++, c.type.id);
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

        private static Challenge challengeFromSQL(@NotNull ResultSet result) throws SQLException {
            Challenge c = new Challenge();
            c.timestamp = result.getLong("timestamp");
            c.type = Constants.Hill.fromId(result.getInt("type"));
            c.scoreInc = result.getInt("scoreInc");
            c.scoreOpp = result.getInt("scoreOpp");
            c.opp = result.getString("opp").split(DEL);
            c.replays = result.getString("replays").split(DEL);
            if (c.replays.length == 1 && c.replays[0].length() == 0) {
                c.replays = new String[0];
            }
            return c;
        }

        /** Returns the last `n` challenges where the incumbent lost, ordered from latest to earliest. */ 
        public static Challenge[] lastTerms(Constants.Hill type, int n) {
            String sql = "SELECT * FROM "  + TABLE + " WHERE scoreInc < scoreOpp AND type = ? ORDER BY timestamp DESC LIMIT ?";
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                stm.setInt(1, type.id);
                stm.setInt(2, n);
                ResultSet result = stm.executeQuery();
                List<Challenge> challenges = new ArrayList<>();
                while (result.next()) {
                    challenges.add(challengeFromSQL(result));
                }
                return challenges.toArray(new Challenge[0]);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new Challenge[0];
        }

        /** Returns the first `n` challenges where the incumbent lost, ordered from latest to earliest. */ 
        public static Challenge[] firstTerms(Constants.Hill type, int n) {
            String sql = "SELECT * FROM "  + TABLE + " WHERE scoreInc < scoreOpp AND type = ? ORDER BY timestamp ASC LIMIT ?";
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                stm.setInt(1, type.id);
                stm.setInt(2, n);
                ResultSet result = stm.executeQuery();
                List<Challenge> challenges = new ArrayList<>();
                while (result.next()) {
                    challenges.add(challengeFromSQL(result));
                }
                return challenges.toArray(new Challenge[0]);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new Challenge[0];
        }


        public static int nthTerm(Constants.Hill type, long timestamp) {
            String sql = "SELECT COUNT(timestamp) FROM "  + TABLE + " WHERE scoreInc < scoreOpp AND type = ? AND timestamp <= ?";
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                stm.setInt(1, type.id);
                stm.setLong(2, timestamp);
                ResultSet result = stm.executeQuery();
                result.next();
                return result.getInt(1);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }

        /** Returns all challenges from `from` to `to` inclusive, from earliest to latest */
        public static Challenge[] get(Constants.Hill type, long from, long to) {
            String sql = "SELECT * FROM " + TABLE + " WHERE timestamp >= ? AND timestamp <= ? AND type = ? ORDER BY timestamp ASC";
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                stm.setLong(1, from);
                stm.setLong(2, to);
                stm.setInt(3, type.id);
                ResultSet result = stm.executeQuery();
                List<Challenge> challenges = new ArrayList<>();
                while (result.next()) {
                    challenges.add(challengeFromSQL(result));
                }
                return challenges.toArray(new Challenge[0]);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new Challenge[0];
        }
    }
}
