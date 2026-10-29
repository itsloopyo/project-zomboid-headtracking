package com.cameraunlock.zomboid;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import zombie.iso.IsoGridSquare;
import zombie.iso.RoomDef;
import zombie.iso.areas.IsoRoom;

/**
 * Dev only, with -Dcameraunlock.zomboid.checkSpotting=true: says where the game spots a room
 * (IsoCell.roomSpotted, which every way of spotting one comes to) or marks a square seen
 * (Meta.dealWithSquareSeen) while a first person picture covers the view, from anything but
 * Spotting's rounds and the light's own calls for a player whose view the picture does not cover
 * (Hooks.roomSeenByLight and squareSeenByLight, which know the player). Each such place is a way
 * rooms fill with zombies, or squares are marked, by something other than what the view sees.
 * Not all of them are wrong: the game spots the rooms of the building a player is in as it draws
 * the player's view (FBORenderCell.checkSeenRooms), which the picture leaves running. Each place
 * is said the first time it is found.
 */
public final class SpotCheck {
    private SpotCheck() {}

    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final Set<String> said = ConcurrentHashMap.newKeySet();
    private static final String HOOKS = "com.cameraunlock.zomboid.Hooks";

    /** At the start of the game's spotting of a room, which names no player. */
    public static void roomSpotted(IsoRoom room) {
        if (Spotting.passer == Thread.currentThread()) {
            Spotting.roomsSpotted.increment();
        } else if (covered() && !byTheLightFor("roomSeenByLight")) {
            RoomDef def = room.getRoomDef();
            RoomDef.RoomRect rect = def.rects.get(0);
            outside("a room spotted", "the room " + def.getName() + " at " + rect.x + ", " + rect.y + ", " + def.level);
        }
    }

    /** At the start of the game's marking of a square seen, which names no player. */
    public static void squareSeen(IsoGridSquare square) {
        if (covered() && !byTheLightFor("squareSeenByLight")) {
            outside("a square marked seen", "the square at " + square.getX() + ", " + square.getY() + ", " + square.getZ());
        }
    }

    /** At the start of the game's marking of a chunk level to be built again for its light, which it does for each change it finds: counted where a round's asking found the light changed. */
    public static void levelMarked() {
        if (Spotting.passer == Thread.currentThread()) {
            Spotting.levelsMarked.increment();
        }
    }

    /** Whether a first person picture covers a view, and this is not the thread a round is making the game's calls on. */
    private static boolean covered() {
        if (Spotting.passer == Thread.currentThread()) {
            return false;
        }
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            if (view != null && view.firstPerson.coversTheGamesView()) {
                return true;
            }
        }
        return false;
    }

    /** Whether the call comes from the light's hook of that name, which makes the game's own call only for a player whose view the picture does not cover. */
    private static boolean byTheLightFor(String hook) {
        return WALKER.walk(frames -> frames.anyMatch(frame -> frame.getClassName().equals(HOOKS) && frame.getMethodName().equals(hook)));
    }

    private static void outside(String what, String where) {
        String from = WALKER.walk(frames -> frames.skip(2).limit(8).map(frame -> frame.getClassName() + "." + frame.getMethodName()).collect(Collectors.joining(" < ")));
        if (said.add(what + " " + from)) {
            Log.info("spot check: " + what + " under a first person picture outside Spotting's rounds, " + where + " on " + Thread.currentThread().getName() + ", from " + from);
        }
    }
}
