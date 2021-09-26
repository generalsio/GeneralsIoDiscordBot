package com.lazerpent.discord.generalsio.dependencies;

import gnu.trove.map.TLongObjectMap;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.entities.MemberImpl;
import net.dv8tion.jda.internal.interactions.CommandInteractionImpl;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

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

    public static User getUser(SlashCommandEvent event) {
        try {
            Field field = CommandInteractionImpl.class.getDeclaredField("resolved");
            field.setAccessible(true);

            @SuppressWarnings("unchecked") final TLongObjectMap<Object> resolved =
                    (TLongObjectMap<Object>) field.get(event.getInteraction());

            final long[] keys = resolved.keys();
            // This will only have one item, because of the event type
            long id = keys[0];
            return ((MemberImpl) resolved.get(id)).getUser();
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return null;
        }
    }
}