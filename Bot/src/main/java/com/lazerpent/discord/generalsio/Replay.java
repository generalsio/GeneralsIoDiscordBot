package com.lazerpent.discord.generalsio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * Represents a generals.io replay file.
 */
@SuppressWarnings("unused") // Most of the replay fields are not used by the bot, but are required by the API response
public class Replay {
    /**
     * The version number of the replay file.
     */
    public final int version;
    /**
     * The replay ID of the replay.
     */
    public final String id;
    /**
     * The width of the map.
     */
    public final int mapWidth;
    /**
     * The height of the map.
     */
    public final int mapHeight;
    /**
     * The list of usernames of players, ordered by player index.
     */
    public final String[] usernames;
    /**
     * The list of stars that each player had at the time of the game, ordered by
     * player index.
     */
    public final int[] stars;
    /**
     * The list of map indices for cities that were on the map.
     */
    public final int[] cities;
    /**
     * The list of army counts for each city.
     */
    public final int[] cityArmies;
    /**
     * The list of map indices of generals, ordered by player index.
     */
    public final int[] generals;
    /**
     * The list of map indices of mountains.
     */
    public final int[] mountains;
    /**
     * The list of moves made in the replay.
     */
    public final Replay.Move[] moves;
    /**
     * The list of surrenders and death in the replay.
     */
    public final Replay.Surrender[] afks;
    /**
     * The list of teams that each player was on, ordered by player index. Empty or
     * null if not a team game.
     */
    public int[] teams;
    /**
     * The title of the custom map where this game was played. Null if no custom map
     * was used.
     */
    public String map_title;
    /**
     * The list of map indices of neutral tiles on the map.
     */
    public int[] neutralTiles;
    /**
     * The list of armies for each neutral tile on the map.
     */
    public int[] neutralArmies;
    /**
     * The list of map indices of swamp tiles on the map.
     */
    public int[] swamps;
    public ChatMessage[] chat; // not currently used
    public int[] playerColors; // not currently used
    public int[] lightBlocks; // not currently used
    public int[] settings; // not currently used

    public Replay(JsonArray arr) {
        int idx = 0;
        version = arr.get(idx++).getAsInt();
        id = arr.get(idx++).getAsString();
        mapWidth = arr.get(idx++).getAsInt();
        mapHeight = arr.get(idx++).getAsInt();
        usernames = StreamSupport.stream(arr.get(idx++).getAsJsonArray().spliterator(), false)
                .map(JsonElement::getAsString).toArray(String[]::new);
        JsonElement tempS = arr.get(idx++);
        if (tempS.isJsonNull()) {
            stars = null;
        } else {
            stars = StreamSupport.stream(tempS.getAsJsonArray().spliterator(), false)
                    .mapToInt(JsonElement::getAsInt).toArray();
        }
        cities = StreamSupport.stream(arr.get(idx++).getAsJsonArray().spliterator(), false)
                .mapToInt(JsonElement::getAsInt).toArray();
        cityArmies = StreamSupport.stream(arr.get(idx++).getAsJsonArray().spliterator(), false)
                .mapToInt(JsonElement::getAsInt).toArray();
        generals = StreamSupport.stream(arr.get(idx++).getAsJsonArray().spliterator(), false)
                .mapToInt(JsonElement::getAsInt).toArray();
        mountains = StreamSupport.stream(arr.get(idx++).getAsJsonArray().spliterator(), false)
                .mapToInt(JsonElement::getAsInt).toArray();
        moves = StreamSupport.stream(arr.get(idx++).getAsJsonArray().spliterator(), false)
                .map((e) -> new Move(e.getAsJsonArray())).toArray(Replay.Move[]::new);
        afks = StreamSupport.stream(arr.get(idx++).getAsJsonArray().spliterator(), false)
                .map((e) -> new Surrender(e.getAsJsonArray())).toArray(Replay.Surrender[]::new);
        if (idx < arr.size()) {
            JsonElement teamsArray = arr.get(idx++);
            if (teamsArray.isJsonNull()) {
                teams = null;
            } else {
                teams = StreamSupport.stream(teamsArray.getAsJsonArray().spliterator(), false)
                        .mapToInt(JsonElement::getAsInt).toArray();
            }
        }
        if (idx < arr.size()) {
            JsonElement mapTitleString = arr.get(idx++);
            if (mapTitleString.isJsonNull()) {
                map_title = null;
            } else {
                map_title = mapTitleString.getAsString();
            }
        }
        if (idx < arr.size()) {
            neutralTiles = StreamSupport.stream(arr.get(idx++).getAsJsonArray().spliterator(), false)
                    .mapToInt(JsonElement::getAsInt).toArray();
        }
        if (idx < arr.size()) {
            neutralArmies = StreamSupport.stream(arr.get(idx++).getAsJsonArray().spliterator(), false)
                    .mapToInt(JsonElement::getAsInt).toArray();
        }
        if (idx < arr.size()) {
            swamps = StreamSupport.stream(arr.get(idx++).getAsJsonArray().spliterator(), false)
                    .mapToInt(JsonElement::getAsInt).toArray();
        }
        if (idx < arr.size()) {
            chat = StreamSupport.stream(arr.get(idx++).getAsJsonArray().spliterator(), false)
                    .map((e) -> new ChatMessage(e.getAsJsonArray())).toArray(Replay.ChatMessage[]::new);
        }
        if (idx < arr.size()) {
            playerColors = StreamSupport.stream(arr.get(idx++).getAsJsonArray().spliterator(), false)
                    .mapToInt(JsonElement::getAsInt).toArray();
        }
        if (idx < arr.size()) {
            lightBlocks = StreamSupport.stream(arr.get(idx++).getAsJsonArray().spliterator(), false)
                    .mapToInt(JsonElement::getAsInt).toArray();
        }
        if (idx < arr.size()) {
            settings = StreamSupport.stream(arr.get(idx).getAsJsonArray().spliterator(), false)
                    .mapToInt(JsonElement::getAsInt).toArray();
        }
    }

    public boolean onSameTeam(String... players) {
        Set<Integer> teamIdx = new HashSet<>();
        for (String u : players) {
            boolean found = false;
            for (int a = 0; a < usernames.length; a++) {
                if (usernames[a].equals(u)) {
                    teamIdx.add(teams[a]);
                    found = true;
                    break;
                }
            }
            if (!found) teamIdx.add(-1);
        }
        return (teamIdx.size() == 1 && !teamIdx.contains(-1));
    }

    /**
     * Represents moves made in replays.
     */
    public static class Move {
        /**
         * The index of the player that made the move.
         */
        public final int index;
        /**
         * The starting map index of the move.
         */
        public final int start;
        /**
         * The ending map index of the move.
         */
        public final int end;
        /**
         * 1 if the move was a split move.
         */
        public final int is50;
        /**
         * The turn when the move was made.
         */
        public final int turn;
        /**
         * The amount of army moved in this move. This field is not part of the replay
         * file itself, but used to record information necessary to reverse the replay.
         */
        public final int attackStrength;
        /**
         * The index of the player that was attacked in this move. -1 if the move goes
         * into empty land. This field is not part of the replay file itself, but used
         * to record information necessary to reverse the replay.
         */
        public final int defenderIdx;
        /**
         * True if the move resulted in the capture of a general. This field is not part
         * of the replay file itself, but used to record information necessary to
         * reverse the replay.
         */
        public final boolean wasCapture;

        public Move(JsonArray arr) {
            int idx = 0;
            index = arr.get(idx++).getAsInt();
            start = arr.get(idx++).getAsInt();
            end = arr.get(idx++).getAsInt();
            is50 = arr.get(idx++).getAsInt();
            turn = arr.get(idx).getAsInt();
            attackStrength = -1;
            defenderIdx = -1;
            wasCapture = false;
        }
    }

    /**
     * Represents surrenders and deaths of players. If this event has only occurred
     * once for a given player, the player has surrendered. If this event has
     * occurred twice for a given player, the player has died.
     */
    public static class Surrender {
        /**
         * The index of the player.
         */
        public final int index;
        /**
         * The turn when the player surrendered or died.
         */
        public final int turn;

        public Surrender(JsonArray arr) {
            int idx = 0;
            index = arr.get(idx++).getAsInt();
            turn = arr.get(idx).getAsInt();
        }
    }

    public static class ChatMessage {
        public final String message;
        public final String prefix;
        public final int index;
        public final int turn;

        public ChatMessage(JsonArray arr) {
            int idx = 0;
            message = arr.get(idx++).getAsString();
            prefix = arr.get(idx++).getAsString();
            index = arr.get(idx++).getAsInt();
            turn = arr.get(idx).getAsInt();
        }
    }
}