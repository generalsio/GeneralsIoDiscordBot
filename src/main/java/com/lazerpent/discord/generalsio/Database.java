package com.lazerpent.discord.generalsio;

import java.sql.*;

public class Database {
    private static final String DATABASE_TABLE = "discord_to_generals";

    public static final Connection connection;


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

    public static Connection getConnection() {
        return connection;
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
}
