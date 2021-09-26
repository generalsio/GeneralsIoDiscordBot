package com.lazerpent.discord.generalsio.dependencies;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jetbrains.annotations.NotNull;

public class UserCommandData extends CommandData {
    public UserCommandData(@NotNull String name) {
        super("temp", "temp");
        this.name = name;
        this.description = "";
    }

    @NotNull
    @Override
    public DataObject toData() {
        return super.toData().put("type", 2);
    }
}