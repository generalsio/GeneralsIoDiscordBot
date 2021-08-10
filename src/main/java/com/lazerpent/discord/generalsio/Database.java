package com.lazerpent.discord.generalsio;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Database {
    private static final Connection connection;
    private static final String DATABASE_TABLE = "discord_to_generals";

    static {

        // Validate that this provided DB is valid (if its not then start creating the needed tables)
        File f = new File("database.db");
        if (!f.exists()) {
            System.out.println("Unable to find file database.db, creating empty database.");
            try {
                //noinspection ResultOfMethodCallIgnored
                f.createNewFile();
            } catch (IOException e) {
                System.out.println("Error creating database.db");
                e.printStackTrace();
            }
        }

        Connection con;
        try {
            con = DriverManager.getConnection("jdbc:sqlite:database.db");
        } catch (SQLException e) {
            e.printStackTrace();
            con = null;
            System.exit(1);
        }
        connection = con;

        // Start validating the tables
        try {
            connection.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS discord_to_generals (discordId int, generalsName text)");
        } catch (SQLException e) {
            System.out.println("Error creating table discord_to_generals.");
            System.out.println(e.getErrorCode());
            e.printStackTrace();
        }

        try {
            connection.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS challenges " +
                    "(timestamp INT NOT NULL PRIMARY KEY, " +
                    "type INT NOT NULL, " +
                    "scoreInc INT NOT NULL, " +
                    "scoreOpp INT NOT NULL, " +
                    "opp TEXT NOT NULL, " +
                    "replays TEXT NOT NULL)");
        } catch (SQLException e) {
            System.out.println("Error creating table challenges.");
            e.printStackTrace();
        }
        
        try {
            connection.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS team_names " +
                    "(team TEXT NOT NULL PRIMARY KEY, " +
                    "teamName TEXT NOT NULL)");
        } catch (SQLException e) {
            System.out.println("Error creating table team_names.");
            e.printStackTrace();
        }

        try {
            connection.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS punishments (username text not null," +
                    " disable integer default 0 not null)");

            // Also check the unique key row
            connection.createStatement().execute("CREATE UNIQUE INDEX IF NOT EXISTS punishments_username_uindex " +
                                                 "on punishments (username)");
        } catch (SQLException e) {
            System.out.println("Error creating table punishments.");
            e.printStackTrace();
        }

        // It is assumed that the correct rows exist, if not then refer to above statements to create correct tables
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

    /**
     * Adds a provided username to the punishment list. If the user is already in the list
     * (either as punishment or disable), nothing happens.
     *
     * @param username Username to add to the punishment list
     */
    public static void addPunishment(String username) {
        try {
            PreparedStatement stm = connection.prepareStatement("INSERT OR IGNORE INTO " +
                                                                "punishments(username) VALUES(?)");
            stm.setString(1, username);
            stm.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a provided username to the disable list. If a user is already in the list as a punish only, updates to
     * disable. If a user is already marked as disable, does nothing.
     *
     * @param username Username to add to the disable list
     */
    public static void addDisable(String username) {
        try {
            PreparedStatement stm = connection.prepareStatement("INSERT OR REPLACE INTO " +
                                                                "punishments(username, disable) VALUES(?, 1)");
            stm.setString(1, username);
            stm.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Updates the two parameter lists with the respective users to punish and disable. This uses pointers, so the lists
     * passed in are the lists which will get the usernames.
     *
     * @param punish  Players to punish ONLY (disable players not included)
     * @param disable Players to disable
     */
    public static void getPunishments(List<String> punish, List<String> disable) {
        try {
            final ResultSet resultSet = connection.createStatement().executeQuery("SELECT * from punishments");
            while (resultSet.next()) {
                (resultSet.getBoolean("disable") ? disable : punish)
                        .add(resultSet.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes all users from the punishment list
     */
    public static void clearPunishments() {
        try {
            connection.createStatement().execute("DELETE FROM punishments");
        } catch (SQLException ignored) {
        }
    }

    public static class Hill {
        private final static String DEL = "\u001F";

        public static long add(Challenge c) {
            String sql = "INSERT INTO challenges (timestamp, type, scoreInc, scoreOpp, opp, replays) VALUES (?, ?," +
                         " ?, ?, ?, ?)";
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                int i = 1;
                stm.setLong(i++, c.timestamp);
                stm.setInt(i++, c.type.id);
                stm.setInt(i++, c.scoreInc);
                stm.setInt(i++, c.scoreOpp);
                stm.setString(i++, Arrays.stream(c.opp)
                        .mapToObj(String::valueOf)
                        .collect(Collectors.joining(DEL)));
                stm.setString(i, String.join(DEL, c.replays));
                stm.execute();
                return c.timestamp;
            } catch (SQLException e) {
                e.printStackTrace();
                return -1;
            }
        }

        public static void delete(long timestamp) {
            String sql = "DELETE FROM challenges WHERE timestamp=?";
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                stm.setLong(1, timestamp);
                stm.execute();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        /**
         * Returns the last `n` challenges where the incumbent lost, ordered from latest to earliest.
         */
        public static Challenge[] lastTerms(Constants.Hill type, int n) {
            String sql = "SELECT * FROM challenges WHERE scoreInc < scoreOpp AND type = ? ORDER BY timestamp DESC " +
                         "LIMIT ?";
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                stm.setInt(2, n);
                stm.setInt(1, type.id);
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

        private static Challenge challengeFromSQL(@NotNull ResultSet result) throws SQLException {
            Challenge c = new Challenge();
            c.timestamp = result.getLong("timestamp");
            c.type = Constants.Hill.fromId(result.getInt("type"));
            c.scoreInc = result.getInt("scoreInc");
            c.scoreOpp = result.getInt("scoreOpp");
            c.opp = Arrays.stream(result.getString("opp").split(DEL))
                    .mapToLong(Long::parseLong)
                    .toArray();
            c.replays = result.getString("replays").split(DEL);
            if (c.replays.length == 1 && c.replays[0].length() == 0) {
                c.replays = new String[0];
            }
            return c;
        }

        /**
         * Returns the first `n` challenges where the incumbent lost, ordered from earliest to latest.
         */
        public static Challenge[] firstTerms(Constants.Hill type, int n) {
            String sql = "SELECT * FROM challenges WHERE scoreInc < scoreOpp AND type = ? ORDER BY timestamp " +
                         "LIMIT ?";
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

        /**
         * Returns the challenges for a given user, ordered from latest to earliest.
         */
        public static Challenge[] xothTerms(Constants.Hill type, long[] xoth) {
            String sql = "SELECT * FROM challenges WHERE scoreInc < scoreOpp AND type = ? AND opp = ? ORDER BY timestamp DESC";
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                stm.setInt(1, type.id);
                stm.setString(2, Arrays.stream(xoth).<String>mapToObj(x -> x+"").collect(Collectors.joining(DEL)));
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
            String sql = "SELECT COUNT(timestamp) FROM challenges WHERE scoreInc < scoreOpp AND type = ? AND " +
                         "timestamp <= ?";
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

        /**
         * Returns all challenges from `from` to `to` inclusive, from earliest to latest
         */
        public static Challenge[] get(Constants.Hill type, long from, long to) {
            String sql = "SELECT * FROM challenges WHERE timestamp >= ? AND timestamp <= ? AND type = ? ORDER BY " +
                         "timestamp";
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
        
        public static Challenge[] getWithOpponent(Constants.Hill type, long from, long to, long[] opp) {
            String sql = "SELECT * FROM challenges WHERE timestamp >= ? AND timestamp <= ? AND type = ? AND opp = ? ORDER BY "
                    + "timestamp";
            
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                stm.setLong(1, from);
                stm.setLong(2, to);
                stm.setInt(3, type.id);
                stm.setString(4, Arrays.stream(opp)
                        .mapToObj(String::valueOf)
                        .collect(Collectors.joining(DEL)));
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
        
        public static String getTeamName(long[] opp) {
            String sql = "SELECT teamName FROM team_names WHERE team = ?";
            String team = Arrays.stream(opp).mapToObj(String::valueOf)
                    .collect(Collectors.joining(DEL));
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                stm.setString(1, team);
                ResultSet result = stm.executeQuery();
                if(result.next()) {
                    return result.getString("teamName");
                } else {
                    return null;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
        
        public static void setTeamName(long[] opp, String name) {
            String sql = "INSERT INTO team_names(team, teamName) "
                    + "VALUES(?, ?) ON CONFLICT(team)"
                    + "DO UPDATE SET teamName = excluded.teamName";
            String team = Arrays.stream(opp).mapToObj(String::valueOf)
                    .collect(Collectors.joining(DEL));
            try {
                PreparedStatement stm = connection.prepareStatement(sql);
                stm.setString(1, team);
                stm.setString(2, name);
                stm.execute();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        // CREATE TABLE challenges (timestamp INT NOT NULL PRIMARY KEY, type INT NOT NULL, scoreInc INT NOT NULL,
        // scoreOpp INT NOT NULL, opp TEXT NOT NULL, replays TEXT NOT NULL);
        // CREATE TABLE team_names (team TEXT NOT NULL, teamName TEXT NOT NULL);
        public static class Challenge {
            public long timestamp;
            public Constants.Hill type;
            public int scoreInc;
            public int scoreOpp;
            public long[] opp;
            public String[] replays;
        }
    }
}
