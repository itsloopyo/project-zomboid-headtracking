package com.cameraunlock.zomboid;

import java.util.Map;

/**
 * What a fault in the mod takes down. Where the stack says the code that threw is one of the
 * parts of the mod a row of CameraUnlock.ini switches off, that part goes off until the game
 * restarts and the view goes on without it: a bug in the hit glow is no reason to lose first
 * person. The file is not changed, so the part is back at the next start. A part is known by
 * the classes it alone is made of, each of which is called only while the part is on; a class
 * that parts and the view share is no part's, and a fault in it is the view's.
 *
 * A fault no part owns drops the frame it was thrown in. Only when such faults keep coming
 * (Tally) does the view itself go off, as a frame that never builds would leave the screen
 * stuck on the last one it had.
 */
final class Faults {
    private Faults() {}

    private static final String PACKAGE = Faults.class.getPackageName() + ".";

    /**
     * The classes each part alone is made of, and which nothing calls while the part is off but to
     * let go of what it holds: see Faults. A part whose code is reached whatever it says (a patch
     * of the game's that runs always, a class other parts lean on) is left out, as switching it
     * off would stop nothing.
     */
    static final Map<String, Part> PARTS = Map.ofEntries(
            Map.entry("ThirdPerson", Part.THIRD_PERSON),
            Map.entry("HitDirection", Part.HIT_DIRECTION), Map.entry("HitArcs", Part.HIT_DIRECTION),
            Map.entry("ImpactDraw", Part.SHOT_IMPACTS),
            Map.entry("DebrisDraw", Part.FALLING_DEBRIS),
            Map.entry("AimBlur", Part.AIM_BLUR),
            Map.entry("GranTurismoTone", Part.GRAN_TURISMO_TONE_MAPPING),
            Map.entry("FishingHold", Part.FISHING_ROD),
            Map.entry("Surroundings", Part.SURROUNDINGS_IN_REFLECTIONS), Map.entry("SurroundingsSchedule", Part.SURROUNDINGS_IN_REFLECTIONS),
            Map.entry("ChunkCentre", Part.HOLD_CHUNK_MAP_CENTRE),
            Map.entry("SetCaptions", Part.CAPTIONS_OVER_SETS),
            Map.entry("Props", Part.FURNITURE_DECOR));

    /** How many parts have been switched off by a fault, so each view can say so once, and their names on the options screen. */
    private static volatile int switchedOff;
    private static final java.util.List<String> broken = new java.util.ArrayList<>();
    private static volatile String names;

    /**
     * Switches off the part whose code threw: the part the class nearest the throw of the mod's
     * own is made of, in the throw or, where that has none of the mod's classes, in what caused
     * it, as a build thread's fault comes back wrapped. Null where that class is no part's, or a
     * part's that is off already and threw all the same: the fault is then the view's.
     *
     * @return the part's name on the options screen
     */
    static synchronized String switchOff(Throwable thrown) {
        for (Throwable at = thrown; at != null; at = at.getCause() == at ? null : at.getCause()) {
            for (StackTraceElement frame : at.getStackTrace()) {
                String name = frame.getClassName();
                if (!name.startsWith(PACKAGE)) {
                    continue;
                }
                int inner = name.indexOf('$', PACKAGE.length());
                Part part = PARTS.get(name.substring(PACKAGE.length(), inner < 0 ? name.length() : inner));
                if (part == null || !part.on()) {
                    return null;
                }
                part.broke();
                String label = OptionsLayout.label(part.key);
                broken.add(label);
                int last = broken.size() - 1;
                names = last == 0 ? label : String.join(", ", broken.subList(0, last)) + " and " + label;
                switchedOff++;
                return label;
            }
        }
        return null;
    }

    /** How many parts faults have switched off: a view that has said fewer has more to say. */
    static int switchedOff() {
        return switchedOff;
    }

    /** The names on the options screen of the parts faults have switched off, in the order they went. */
    static String names() {
        return names;
    }

    /**
     * The faults no part owns, by when they came. Dropping a frame now and then is better than
     * losing the view, but one that throws on most frames is a view that does not work.
     */
    static final class Tally {
        /** As many faults as this inside WITHIN put the view off. */
        static final int MOST = 10;
        static final long WITHIN = 5_000_000_000L;
        private final long[] at = new long[MOST];
        private int next, count;

        /** Counts a fault at now, in nanoseconds: whether there have been too many. */
        boolean tooMany(long now) {
            at[next] = now;
            next = (next + 1) % MOST;
            count = Math.min(count + 1, MOST);
            return count == MOST && now - at[next] <= WITHIN;
        }
    }
}
