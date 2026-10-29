package com.cameraunlock.zomboid;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import zombie.iso.IsoGridSquare;

/**
 * Dev only, with -Dcameraunlock.zomboid.checkSpotting=true: says where the game spots a room
 * (IsoGridSquare.checkRoomSeen) or marks a square seen (Meta.dealWithSquareSeen) while the
 * first person picture covers the view, from anything but Spotting's pass. Under the picture
 * that is Spotting's alone, so each such place is a way rooms still fill with zombies by
 * something other than what the view sees. Each place is said the first time it is found.
 */
public final class SpotCheck {
    private SpotCheck() {}

    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final Set<String> said = ConcurrentHashMap.newKeySet();

    /** At the start of the game's spotting of a square's room for a player. */
    public static void roomChecked(IsoGridSquare square, int playerIndex) {
        View view = View.of(playerIndex);
        if (view != null && view.firstPerson.coversTheGamesView() && Spotting.passer != Thread.currentThread()) {
            outside("a room spotted", square);
        }
    }

    /** At the start of the game's marking of a square seen, which names no player. */
    public static void squareSeen(IsoGridSquare square) {
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            if (view != null && view.firstPerson.coversTheGamesView() && Spotting.passer != Thread.currentThread()) {
                outside("a square marked seen", square);
                return;
            }
        }
    }

    private static void outside(String what, IsoGridSquare square) {
        String from = WALKER.walk(frames -> frames.skip(2).limit(8).map(frame -> frame.getClassName() + "." + frame.getMethodName()).collect(Collectors.joining(" < ")));
        if (said.add(what + " " + from)) {
            Log.info("spot check: " + what + " under the first person picture outside Spotting's pass, at " + square.getX() + ", " + square.getY() + ", "
                    + square.getZ() + " on " + Thread.currentThread().getName() + ", from " + from);
        }
    }
}
