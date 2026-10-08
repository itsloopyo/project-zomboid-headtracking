package com.cameraunlock.zomboid;

import java.util.IdentityHashMap;

/**
 * Where the people and vehicles of the picture stand this frame and where each stood the frame
 * before, for the steadying pass: what moves by itself is looked for in the last frame where
 * it then was, and not where the ground under it was.
 *
 * A mover is where it stands and the angle it is turned by. The nearest MOST to the eye are
 * kept. One seen for the first time stood, as far as this knows, where it stands.
 *
 * Render thread only.
 */
final class Movers {
    static final int MOST = 16;
    /** For each mover x, y, height and angle: this frame's, and the same mover's a frame ago. */
    final float[] now = new float[MOST * 4], before = new float[MOST * 4];
    int found;
    /** Whether this frame's picture is steadied at all: nobody is noted for one that is not. */
    boolean wanted;
    private final float[] away = new float[MOST];
    private IdentityHashMap<Object, float[]> last = new IdentityHashMap<>(), seen = new IdentityHashMap<>();

    /** As a frame's people and vehicles begin to be drawn. */
    void begin(boolean wanted) {
        this.wanted = wanted;
        found = 0;
    }

    /**
     * @param who  the game's own object for the mover, the same one every frame
     * @param away how far from the eye it stands, squared
     */
    void note(Object who, float x, float y, float up, float angle, float away) {
        if (!wanted) {
            return;
        }
        float[] stood = last.get(who);
        float[] stands = stood != null ? stood : new float[4];
        int at = found;
        if (found == MOST) {
            at = 0;
            for (int i = 1; i < MOST; i++) {
                if (this.away[i] > this.away[at]) {
                    at = i;
                }
            }
            if (this.away[at] <= away) {
                at = -1;
            }
        } else {
            found++;
        }
        if (at >= 0) {
            this.away[at] = away;
            now[at * 4] = x;
            now[at * 4 + 1] = y;
            now[at * 4 + 2] = up;
            now[at * 4 + 3] = angle;
            before[at * 4] = stood != null ? stood[0] : x;
            before[at * 4 + 1] = stood != null ? stood[1] : y;
            before[at * 4 + 2] = stood != null ? stood[2] : up;
            before[at * 4 + 3] = stood != null ? stood[3] : angle;
        }
        stands[0] = x;
        stands[1] = y;
        stands[2] = up;
        stands[3] = angle;
        seen.put(who, stands);
    }

    /** With the view off: nobody of the last frame drawn is held on to, nor the map they stood on. */
    void forget() {
        last.clear();
        seen.clear();
        found = 0;
    }

    /** As the frame's people and vehicles have all been drawn: whoever was not in it is forgotten. */
    void end() {
        IdentityHashMap<Object, float[]> gone = last;
        last = seen;
        seen = gone;
        seen.clear();
    }
}
