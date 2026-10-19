package com.cameraunlock.zomboid;

import java.util.WeakHashMap;
import zombie.iso.objects.IsoTelevision;

/** Apart from Screens, which the tests load without the game's televisions. */
public final class ScreenMarks {
    private ScreenMarks() {}

    /** What each set showed when it was last looked at: see marked. Logic thread. */
    private static final WeakHashMap<IsoTelevision, Integer> screens = new WeakHashMap<>();

    /**
     * In place of a television's marking its chunk as it lays another sprite over its glass
     * (IsoTelevision.updateTvScreen), which a set that is on does every few ticks, between
     * the frames of its bars or its picture. A build shows only whether the set is dark, on
     * the bars or on a programme (Screens.showing), so only a change of that is marked, and
     * as a thing on the chunk and not part of a building: the sets left on in a tower had the
     * finish of its walls, its roofs and its rooms read again many times a second.
     */
    public static void marked(IsoTelevision set, long flags) {
        int showing = Screens.showing(set);
        Integer was = screens.put(set, showing);
        ChunkMarks.hush(set, flags);
        if (was == null || was != showing) {
            ChunkMarks.thing(set.getSquare());
        }
    }
}
