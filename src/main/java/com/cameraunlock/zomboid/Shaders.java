package com.cameraunlock.zomboid;

import java.util.Locale;

/**
 * How long the mod's shader programs take to compile and link, and whether the up-front step
 * that compiles every one of them is over. Every compile of the mod's own is told to linked,
 * wherever it is done. Render thread.
 */
final class Shaders {
    private Shaders() {}

    private static int count;
    private static long nanos, longestNanos;
    private static String longest;
    /** Set once the up-front step has compiled every program: a compile after it is one the step missed. */
    private static boolean done;

    /** Notes a program compiled and linked, or refused, between start (System.nanoTime) and now. */
    static void linked(String name, long start) {
        long took = System.nanoTime() - start;
        if (done) {
            Log.info(String.format(Locale.ROOT, "the %s shader was compiled after the up-front step: %.1f ms", name, took / 1e6));
            return;
        }
        count++;
        nanos += took;
        if (took > longestNanos) {
            longestNanos = took;
            longest = name;
        }
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "the %s shader compiled and linked: %.1f ms", name, took / 1e6));
        }
    }

    /** Ends the up-front step, which began at began (System.nanoTime). */
    static void finished(long began) {
        done = true;
        Log.info(String.format(Locale.ROOT, "every shader is compiled: %d programs, %.0f ms of the render thread over %.1f s, the longest the %s at %.0f ms",
                count, nanos / 1e6, (System.nanoTime() - began) / 1e9, longest, longestNanos / 1e6));
    }
}
