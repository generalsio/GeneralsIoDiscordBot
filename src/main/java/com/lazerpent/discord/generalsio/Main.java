package com.lazerpent.discord.generalsio;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.security.auth.login.LoginException;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.ERROR);

        // The token comes from the environment variable GENERALS_DISCORD_TOKEN
        String token = System.getenv("GENERALS_DISCORD_TOKEN");

        if (token == null) {
            System.out.println("No token provided");
            return;
        }

        JDABuilder builder = JDABuilder.create(token, Arrays.asList(GatewayIntent.values()));
        builder.addEventListeners(new Commands());
        builder.setAutoReconnect(true);
        builder.setActivity(Activity.playing("https://generals.io/"));
        try {
            builder.build();
        } catch (LoginException e) {
            e.printStackTrace();
        }
    }
}
