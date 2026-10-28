package com.cameraunlock.zomboid;

/**
 * Dev only: the game brings a square's light and sight up to date from its lighting engine each
 * time either is read (LightingJNI.JNILighting.update), through one scratch array it keeps for
 * every square, so the mod reads them on the logic thread alone (FigureLights). A read made from
 * any other thread with the mod's code under it throws here, where it is made, and not frames
 * later where the two threads meet in the game's own code.
 */
public final class LightingThread {
    private LightingThread() {}

    private static final String MOD = "com.cameraunlock.zomboid.", SELF = LightingThread.class.getName();
    private static final StackWalker WALKER = StackWalker.getInstance();
    /** The game's logic thread, taken the first time it reads a square's light. */
    private static volatile Thread logic;
    private static volatile boolean gameSaid;

    /** At the start of every update of a square's light. */
    public static void update() {
        Thread now = Thread.currentThread();
        if (now == logic) {
            return;
        }
        if (logic == null && "MainThread".equals(now.getName())) {
            logic = now;
            Log.info("lighting thread check: the logic thread is " + now.getName());
            return;
        }
        if (WALKER.walk(frames -> frames.anyMatch(frame -> frame.getClassName().startsWith(MOD) && !frame.getClassName().equals(SELF)))) {
            throw new IllegalStateException("the game's light of a square is read on " + now.getName() + ", off the logic thread, by the mod");
        }
        if (!gameSaid) {
            gameSaid = true;
            Log.info("lighting thread check: the game itself reads a square's light on " + now.getName());
        }
    }
}
