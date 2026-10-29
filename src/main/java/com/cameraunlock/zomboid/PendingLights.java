package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Locale;

/**
 * The levels of chunks the lighting engine has said are relit, from the asking to the taking
 * of their squares: see WorldScene.Frame.pullLights.
 *
 * The engine relights the map a pass at a time, about ten passes a second, and the frame
 * that first saw a pass took every square of every level it found relit: on the drive that
 * frame's light pulls took 5 to 6 ms, about once a pass, where a frame's took under 2 ms on
 * the mean. So the takes are spread over the frames a pass lasts, nearest chunk first. The
 * frame that first sees a pass takes as many squares as the last pass took a frame, and a
 * quarter more. Each later frame of the pass takes as many again, or more where what waits
 * would outlast the pass: what waits over the frames the pass is expected to have left, this
 * one among them, from the frames passes have lasted of late. What a pass found and has
 * not taken by its end is due: it is taken in the frame that first sees the next pass,
 * wherever that frame comes to it and however much it has taken already. The engine puts
 * its flag for a level down when it is asked, so a level is held here from the asking until
 * its squares are taken, across a pass and whether or not its chunk is still in view. Only
 * a chunk the map lets go of takes its levels with it.
 *
 * Kept by the places of ChunkMarks' table, which no two chunks of the loaded map share.
 * Logic thread only.
 */
final class PendingLights {
    /** What next gives when nothing of a chunk is to be taken now. */
    static final int NONE = Integer.MIN_VALUE;
    /** Dev only: at the end of every frame the levels found are held to those taken, let go and waiting, and none may be due. */
    static final boolean CHECKED = Boolean.getBoolean("cameraunlock.zomboid.checkLightPulls");
    private static final int PLACES = ChunkMarks.SIDE * ChunkMarks.SIDE, LOWEST_LEVEL = -32, LEVELS = 64;
    /**
     * However little the last pass took, a frame may take this many squares, eight levels'
     * worth: after a pass with nothing to take, the share would be nothing, and everything
     * the next pass found would wait for the pass after it.
     */
    static final int LEAST_SHARE = 8 * WorldScene.CHUNK_TILES * WorldScene.CHUNK_TILES;

    /** The levels waiting in each place that were found in the frame's pass, and those due: a bit for each from the lowest level of the map up. */
    private final long[] levels = new long[PLACES], due = new long[PLACES];
    private final int[] wx = new int[PLACES], wy = new int[PLACES];
    /**
     * The frame the levels waiting in each place were found in, and the same for those due: the
     * place's first finding of them, so a level found again later is counted from the first.
     */
    private final int[] foundFrame = new int[PLACES], dueFrame = new int[PLACES];
    /** The levels that came to wait in each place in the frame beside them: one of those taken in that frame is taken no later than it was before. */
    private final long[] foundNow = new long[PLACES];
    private final int[] foundNowFrame = new int[PLACES];
    /** The places with levels waiting, in no order, and where in it each of them is. */
    private final int[] waiting = new int[PLACES], at = new int[PLACES];
    private int count;

    private int pass = NONE, frame;
    private int framesInPass, squaresInPass;
    /** The squares a frame takes: worked out from the last pass, and every one there is until a pass has been seen. */
    private int share = Integer.MAX_VALUE;
    /** What the frame may still take before it takes only what is due. */
    private int left;
    /** The frames passes have lasted of late, moved a quarter of the way to each pass's as it ends: 0 until one has. */
    private double framesAPass;
    /** Since the set was made: every level found is taken, let go or waiting. */
    private long found, taken, letGo;
    /** Since the set was made: the squares of the levels taken, for what a level waiting holds on the mean. */
    private long squaresTaken;

    /**
     * Dev only, for the rig: how many frames since the last asking took their light pulls in
     * each 10 microseconds, the last holding every one longer. The p99 is told as the top of
     * its 10 microseconds, or the longest frame where that is less.
     */
    private final int[] frameTimes = new int[5000];
    private int tollFrames, tollSquaresNow, tollMostSquares, tollMostWaiting, tollOldestWaiting, tollOldestTaken;
    private long tollMicros, tollMostMicros, tollSquares, tollWaiting, tollFound, tollTaken, tollLate, tollDue, tollLetGo, tollAgain;

    /** The bit of a level in what a place holds. */
    static long bit(int level) {
        if (level < LOWEST_LEVEL || level >= LOWEST_LEVEL + LEVELS) {
            throw new IllegalArgumentException("level " + level + " is outside the map's levels, " + LOWEST_LEVEL + " to " + (LOWEST_LEVEL + LEVELS - 1));
        }
        return 1L << (level - LOWEST_LEVEL);
    }

    /**
     * The squares a frame takes after a pass that took so many over so many frames: their mean
     * and a quarter more. On the drive a pass lasts three frames or four, and a share worked
     * out over four and taken over three still takes all but a sixteenth before the next pass.
     */
    static int shareOf(int squares, int frames) {
        return Math.max(LEAST_SHARE, (squares + squares / 4 + frames - 1) / frames);
    }

    /**
     * A frame begins to take, in the engine's pass given. The first frame of a pass works out the
     * share from the last, and what waits falls due. A later one takes more than the share
     * where what waits, spread over the frames the pass is expected to have left, comes to more.
     */
    void begin(int pass, int frame) {
        if (pass != this.pass) {
            if (this.pass != NONE) {
                share = shareOf(squaresInPass, framesInPass);
                framesAPass = framesAPass == 0.0 ? framesInPass : framesAPass + (framesInPass - framesAPass) / 4.0;
            }
            for (int i = 0; i < count; i++) {
                int place = waiting[i];
                if (due[place] == 0L) {
                    dueFrame[place] = foundFrame[place];
                }
                due[place] |= levels[place];
                levels[place] = 0L;
            }
            this.pass = pass;
            framesInPass = 0;
            squaresInPass = 0;
        }
        this.frame = frame;
        framesInPass++;
        left = share;
        if (framesInPass > 1) {
            // Not rounded: rounded down, passes of 2.7 frames on the mean would leave the second frame all of a pass's light.
            double framesLeft = Math.max(1.0, framesAPass - framesInPass + 1);
            long squaresALevel = taken == 0L ? WorldScene.CHUNK_TILES * WorldScene.CHUNK_TILES : Math.max(1L, squaresTaken / taken);
            left = (int) Math.max(share, (long) Math.ceil(levelsWaiting() * squaresALevel / framesLeft));
        }
        tollSquaresNow = 0;
    }

    /** Levels of the chunk at (wx, wy) found relit in this frame, as bits: see bit. */
    void found(int place, int wx, int wy, long relit) {
        if (relit == 0L) {
            return;
        }
        if (waits(place) && (this.wx[place] != wx || this.wy[place] != wy)) {
            // The chunk a table's width off that had the place has left the map: the loaded map is never so wide.
            letGo(place);
        }
        if (!waits(place)) {
            waiting[count] = place;
            at[place] = count++;
            this.wx[place] = wx;
            this.wy[place] = wy;
        }
        long held = levels[place] | due[place];
        long fresh = relit & ~held;
        if (levels[place] == 0L && (relit & ~due[place]) != 0L) {
            foundFrame[place] = frame;
        }
        // One found again while it is due is taken as due: taken now, it is taken with this pass's light.
        levels[place] |= relit & ~due[place];
        if (foundNowFrame[place] != frame) {
            foundNow[place] = 0L;
            foundNowFrame[place] = frame;
        }
        foundNow[place] |= fresh;
        found += Long.bitCount(fresh);
        tollFound += Long.bitCount(fresh);
        tollAgain += Long.bitCount(relit & held);
    }

    boolean waits(int place) {
        return (levels[place] | due[place]) != 0L;
    }

    /** The lowest level of the chunk in the place to take now, or NONE: what is due first, and then what its pass found while the frame has some of its share left. */
    int next(int place) {
        if (due[place] != 0L) {
            return Long.numberOfTrailingZeros(due[place]) + LOWEST_LEVEL;
        }
        if (levels[place] == 0L || left <= 0) {
            return NONE;
        }
        return Long.numberOfTrailingZeros(levels[place]) + LOWEST_LEVEL;
    }

    /** The level next gave was taken, so many squares of it. */
    void took(int place, int level, int squares) {
        long bit = bit(level);
        boolean wasDue = (due[place] & bit) != 0L;
        tollOldestTaken = Math.max(tollOldestTaken, frame - (wasDue ? dueFrame[place] : foundFrame[place]));
        if (wasDue) {
            tollDue++;
        }
        due[place] &= ~bit;
        levels[place] &= ~bit;
        taken++;
        tollTaken++;
        if (foundNowFrame[place] != frame || (foundNow[place] & bit) == 0L) {
            tollLate++;
        }
        left -= squares;
        squaresTaken += squares;
        squaresInPass += squares;
        tollSquaresNow += squares;
        if (!waits(place)) {
            drop(place);
        }
    }

    /** The chunk in the place has left the map: what waited of it goes with it. */
    void letGo(int place) {
        int levelsLeft = Long.bitCount(levels[place]) + Long.bitCount(due[place]);
        letGo += levelsLeft;
        tollLetGo += levelsLeft;
        levels[place] = 0L;
        due[place] = 0L;
        drop(place);
    }

    private void drop(int place) {
        int moved = waiting[--count];
        waiting[at[place]] = moved;
        at[moved] = at[place];
    }

    /** How many places have levels waiting: each is waitingAt one of 0 to this, and one taken from or let go of may move another to its index. */
    int waiting() {
        return count;
    }

    int waitingAt(int index) {
        return waiting[index];
    }

    int wx(int place) {
        return wx[place];
    }

    int wy(int place) {
        return wy[place];
    }

    /** How many levels wait in all. */
    long levelsWaiting() {
        return found - taken - letGo;
    }

    /** The frame's takes are done, in so many nanoseconds all told. */
    void end(long nanos) {
        long micros = nanos / 1000L;
        frameTimes[(int) Math.min(frameTimes.length - 1, micros / 10L)]++;
        tollFrames++;
        tollMicros += micros;
        tollMostMicros = Math.max(tollMostMicros, micros);
        tollSquares += tollSquaresNow;
        tollMostSquares = Math.max(tollMostSquares, tollSquaresNow);
        long waitingNow = levelsWaiting();
        tollWaiting += waitingNow;
        tollMostWaiting = (int) Math.max(tollMostWaiting, waitingNow);
        for (int i = 0; i < count; i++) {
            int place = waiting[i];
            tollOldestWaiting = Math.max(tollOldestWaiting, frame - (due[place] != 0L ? dueFrame[place] : foundFrame[place]));
        }
        if (CHECKED) {
            check();
        }
    }

    /** Dev only, between frames: every level found is taken, let go or waiting, the places listed as waiting are those with levels, and none is due. */
    void check() {
        long held = 0L;
        for (int i = 0; i < count; i++) {
            int place = waiting[i];
            if (!waits(place) || at[place] != i) {
                throw new IllegalStateException("light pulls: place " + place + " is listed as waiting at " + i + " with levels " + Long.toBinaryString(levels[place])
                        + " and its index " + at[place]);
            }
            if (due[place] != 0L) {
                throw new IllegalStateException("light pulls: the chunk at " + wx[place] + ", " + wy[place] + " still has levels " + Long.toBinaryString(due[place])
                        + " waiting from before pass " + pass + " at the end of frame " + frame + ", found in frame " + dueFrame[place]);
            }
            held += Long.bitCount(levels[place]);
        }
        if (held != found - taken - letGo) {
            throw new IllegalStateException("light pulls: " + found + " levels found, " + taken + " taken and " + letGo + " let go, but " + held + " wait where "
                    + (found - taken - letGo) + " should");
        }
    }

    /** Dev only: the takes since the last asking and what waited, for a rig run's window. */
    String tolls() {
        int frames = Math.max(1, tollFrames), bucket = 0, below = frameTimes[0];
        while (below <= tollFrames * 0.99 && bucket < frameTimes.length - 1) {
            below += frameTimes[++bucket];
        }
        String told = String.format(Locale.ROOT,
                "light pulls a frame %.3f ms (p99 %.2f, most %.3f), passes of %.1f frames, squares taken a frame %.0f (most %d), levels waiting at a frame's end %.1f (most %d,"
                        + " found as long as %d frames before), levels found %d, taken %d (in a later frame than found %d, due %d, as long as %d frames after found), let go %d,"
                        + " found again while waiting %d, waiting now %d",
                tollMicros / 1e3 / frames, Math.min((bucket + 1) / 100.0, tollMostMicros / 1e3), tollMostMicros / 1e3, framesAPass, (double) tollSquares / frames, tollMostSquares,
                (double) tollWaiting / frames, tollMostWaiting, tollOldestWaiting, tollFound, tollTaken, tollLate, tollDue, tollOldestTaken, tollLetGo, tollAgain, levelsWaiting());
        if (CHECKED) {
            check();
            told += ", checked";
        }
        Arrays.fill(frameTimes, 0);
        tollFrames = tollMostSquares = tollMostWaiting = tollOldestWaiting = tollOldestTaken = 0;
        tollMicros = tollMostMicros = tollSquares = tollWaiting = tollFound = tollTaken = tollLate = tollDue = tollLetGo = tollAgain = 0L;
        return told;
    }
}
