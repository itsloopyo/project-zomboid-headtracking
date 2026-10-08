package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * The spans of overhead wire: what a length of wire hangs between.
 *
 * The map holds a wire as the straight lengths that cross each square of air under it (Wires),
 * and nothing in it says where a wire is held up. A length hung at the height of the land
 * under it follows the ground, and a run of them rises and falls with every swell between two
 * poles. A wire is held at the insulators of two poles and hangs between them in a curve,
 * whatever the ground under it does. So each length is followed along its line, square by
 * square and both ways, to the insulator it is made fast to at each end, and takes its height
 * from where between the two it is.
 *
 * A run does not always end on a pole. Its last length may stop in the air, where the map's
 * artists stopped drawing it, and then the wire is held where it stops. Or the map may not be
 * loaded as far as the next pole: then a pole is taken to stand as far off as poles mostly do,
 * so that the wire hangs much as it will when the map has come in.
 */
final class Spans {
    /** A wire hangs lower in the middle of its span than at its ends by this share of the span's length. */
    static final float SAG = 0.03f;
    /**
     * The most a wire hangs below the line between its ends, in tiles, however long its span.
     * The insulators stand Wires.UP of a level over the floor of the level the wires' sprites
     * hang on, which is 1.32 tiles: a wire between two poles on level ground is never nearer
     * that floor than 0.4 of a tile.
     */
    static final float MOST_SAG = 0.9f;
    /**
     * A wire is never nearer the land under it than this, in tiles: over a rise between two
     * poles that stand low it is held up off the ground, clear of the roof of a van.
     */
    static final float CLEAR = 3f;
    /** How far apart the poles of a run mostly stand, in tiles: 18 to 32 on the runs measured, most often 25 or 26. */
    static final float USUAL = 26f;
    /** How far off a wire's line the next length of it, or an insulator it passes over, may lie, in tiles. The wires of one run are no nearer each other than 0.36. */
    static final float OFF_LINE = 0.15f;
    /** The next length of a wire starts no further on than this from where the last ended, and reaches at least this much further, in tiles. */
    private static final float GAP = 0.3f, FURTHER = 0.05f;
    /** Two lengths run the same way when the sine of the angle between them is under this. */
    private static final float SAME_WAY = 0.17f;
    /** An end of a span: made fast to an insulator, loose where the wire stops, or not known because the map is not loaded that far. */
    static final int FAST = 0, LOOSE = 1, WANTING = 2;
    /** How far round a length's square the poles are looked for whose insulators it may reach, in squares: an arm's end is a square from its pole and a length's end half a square outside its own. */
    private static final int POLES_WITHIN = 2;
    /**
     * Numbers a piece of wire is given by. Where its two ends are on the map, then for each
     * end of its span: where it is, where the land's height is taken that it stands over, and
     * which kind of end it is.
     */
    static final int PIECE = 14, FROM = 4, TO = 9, END_LAND = 2, END_KIND = 4;

    /** The map as the spans are found on it, on the one level a run hangs on. */
    interface Hung {
        /**
         * The lengths of wire the sprites on a square show, into out: four numbers each, the
         * two ends on the map. How many numbers, or -1 for a square the map has not loaded.
         */
        int lines(int x, int y, float[] out);

        /**
         * The insulators of the pole whose top is on a square, into out from the place at:
         * four numbers each, where it stands and where the pole's axis is. How many numbers.
         */
        int pins(int x, int y, float[] out, int at);
    }

    /** The most numbers the lines of one square come to, and the pins of one pole. */
    static final int MOST_LINES = 64, POLE_PINS = 12;

    private final Hung map;
    private final float[] lines = new float[MOST_LINES], pins = new float[(2 * POLES_WITHIN + 1) * (2 * POLES_WITHIN + 1) * POLE_PINS];
    private final float[] own = new float[pins.length], end = new float[5];
    /** What touch found: the pin each end of a length is made fast to, or -1, and those it passes over between them, in the order it passes them. */
    private int first, last, passed;
    private final int[] cuts = new int[pins.length / 4];
    private final float[] cutsAlong = new float[pins.length / 4];

    Spans(Hung map) {
        this.map = map;
    }

    /** The sag of a span of a length, in tiles. */
    static float sag(float span) {
        return Math.min(SAG * span, MOST_SAG);
    }

    /**
     * How far below the straight line between its ends a wire hangs at a share of the way
     * along its span: the curve a cable hangs in is as near a parabola as makes no difference
     * while it sags by a few hundredths of its span.
     */
    static float hangs(float along, float sag) {
        float within = Math.clamp(along, 0f, 1f);
        return 4f * sag * within * (1f - within);
    }

    /** How far along the span of a piece a point is, as a share: 0 at the span's first end and 1 at its other. */
    static float along(float[] pieces, int piece, float x, float y) {
        float spanX = pieces[piece + TO] - pieces[piece + FROM], spanY = pieces[piece + TO + 1] - pieces[piece + FROM + 1];
        float span = spanX * spanX + spanY * spanY;
        return span < 1e-4f ? 0f : Math.clamp(((x - pieces[piece + FROM]) * spanX + (y - pieces[piece + FROM + 1]) * spanY) / span, 0f, 1f);
    }

    /** The length of the span of a piece over the map, in tiles. */
    static float span(float[] pieces, int piece) {
        float spanX = pieces[piece + TO] - pieces[piece + FROM], spanY = pieces[piece + TO + 1] - pieces[piece + FROM + 1];
        return (float) Math.sqrt(spanX * spanX + spanY * spanY);
    }

    /**
     * Which of its two ways the way across a wire is taken: what to multiply (x, y) by, 1 or
     * -1, so that every length of a span has the same one whichever of its ends it is read
     * from. The line the choice turns on lies along none of the ways a wire runs on the map.
     */
    static float acrossSign(float x, float y) {
        return x * 0.8f + y * 0.6f < 0f ? -1f : 1f;
    }

    /** How far out of step two wires swing for each tile they hang apart on their arms, in swings. */
    private static final float OUT_OF_STEP = 0.02f;

    /**
     * Where in its swing the span of a piece is, as a share of one swing, the same for every
     * piece of the span. It goes by where the two poles stand, which the wires of one run
     * share and which is as good as chance from one span to the next, and a little by where
     * on their arms the wire is made fast, so the wires of a run swing nearly together.
     */
    static float swing(float[] pieces, int piece) {
        int from = piece + FROM, to = piece + TO;
        float landX = pieces[from + END_LAND] + pieces[to + END_LAND], landY = pieces[from + END_LAND + 1] + pieces[to + END_LAND + 1];
        float offX = pieces[from] - pieces[from + END_LAND] + (pieces[to] - pieces[to + END_LAND]);
        float offY = pieces[from + 1] - pieces[from + END_LAND + 1] + (pieces[to + 1] - pieces[to + END_LAND + 1]);
        float swings = landX * 0.7548777f + landY * 0.5698403f + (offX + 0.5f * offY) * OUT_OF_STEP;
        return swings - (float) Math.floor(swings);
    }

    /** Whether any end of the pieces' spans is not known for want of map. */
    static boolean wanting(float[] pieces) {
        for (int piece = 0; piece < pieces.length; piece += PIECE) {
            if (pieces[piece + FROM + END_KIND] == WANTING || pieces[piece + TO + END_KIND] == WANTING) {
                return true;
            }
        }
        return false;
    }

    /**
     * The pieces a length of wire on a square is hung as, PIECE numbers each: one, or more
     * where it passes over an insulator between its ends, which holds it there, and none for
     * a length that is all within reach of one insulator.
     */
    float[] find(int x, int y, float x0, float y0, float x1, float y1) {
        float length = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
        float wayX = (x1 - x0) / length, wayY = (y1 - y0) / length;
        // Kept apart: following the wire on from an end reads the pins of other squares over these.
        int found = pinsRound(x, y);
        System.arraycopy(pins, 0, own, 0, found);
        touch(own, found, x0, y0, x1, y1);
        int[] held = new int[passed + 2];
        held[0] = first;
        System.arraycopy(cuts, 0, held, 1, passed);
        held[passed + 1] = last;
        float[] pieces = new float[0];
        for (int cut = 0; cut + 1 < held.length; cut++) {
            int from = held[cut], to = held[cut + 1];
            float fromX = from >= 0 ? own[from] : x0, fromY = from >= 0 ? own[from + 1] : y0;
            float toX = to >= 0 ? own[to] : x1, toY = to >= 0 ? own[to + 1] : y1;
            if ((toX - fromX) * wayX + (toY - fromY) * wayY <= FURTHER) {
                continue;
            }
            pieces = Arrays.copyOf(pieces, pieces.length + PIECE);
            int piece = pieces.length - PIECE;
            pieces[piece] = fromX;
            pieces[piece + 1] = fromY;
            pieces[piece + 2] = toX;
            pieces[piece + 3] = toY;
            end(pieces, piece + FROM, from, fromX, fromY, -wayX, -wayY);
            end(pieces, piece + TO, to, toX, toY, wayX, wayY);
            guess(pieces, piece + FROM, piece + TO);
            guess(pieces, piece + TO, piece + FROM);
        }
        return pieces;
    }

    /** Fills pins with the insulators of every pole near a square, and says how many numbers that is. */
    private int pinsRound(int x, int y) {
        int found = 0;
        for (int poleY = y - POLES_WITHIN; poleY <= y + POLES_WITHIN; poleY++) {
            for (int poleX = x - POLES_WITHIN; poleX <= x + POLES_WITHIN; poleX++) {
                found += map.pins(poleX, poleY, pins, found);
            }
        }
        return found;
    }

    /**
     * Which of some pins a length of wire is held by, into first, last, cuts and passed. An
     * end is made fast to the nearest insulator within Poles.MADE_FAST of it: where a run turns
     * at a pole its sprites stop a hand or two short of the arm. Between its ends a length is
     * held by every insulator it passes over.
     */
    private void touch(float[] from, int found, float x0, float y0, float x1, float y1) {
        float length = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
        float wayX = (x1 - x0) / length, wayY = (y1 - y0) / length;
        first = nearest(from, found, x0, y0);
        last = nearest(from, found, x1, y1);
        passed = 0;
        for (int pin = 0; pin < found; pin += 4) {
            float along = (from[pin] - x0) * wayX + (from[pin + 1] - y0) * wayY, off = Math.abs((from[pin] - x0) * wayY - (from[pin + 1] - y0) * wayX);
            if (pin == first || pin == last || along <= 0f || along >= length || off >= OFF_LINE) {
                continue;
            }
            int at = passed++;
            while (at > 0 && cutsAlong[at - 1] > along) {
                cuts[at] = cuts[at - 1];
                cutsAlong[at] = cutsAlong[at - 1];
                at--;
            }
            cuts[at] = pin;
            cutsAlong[at] = along;
        }
    }

    /** Which of some pins is the nearest to a point within Poles.MADE_FAST of it, or -1. */
    private static int nearest(float[] from, int found, float x, float y) {
        float nearest = Poles.MADE_FAST * Poles.MADE_FAST;
        int which = -1;
        for (int pin = 0; pin < found; pin += 4) {
            float awayX = from[pin] - x, awayY = from[pin + 1] - y, away = awayX * awayX + awayY * awayY;
            if (away < nearest) {
                nearest = away;
                which = pin;
            }
        }
        return which;
    }

    /** Writes one end of a piece's span: the pin it is made fast to, or what following the wire on from (x, y) comes to. */
    private void end(float[] pieces, int at, int pin, float x, float y, float wayX, float wayY) {
        if (pin < 0) {
            follow(x, y, wayX, wayY);
            System.arraycopy(end, 0, pieces, at, 5);
            return;
        }
        pieces[at] = own[pin];
        pieces[at + 1] = own[pin + 1];
        pieces[at + END_LAND] = own[pin + 2];
        pieces[at + END_LAND + 1] = own[pin + 3];
        pieces[at + END_KIND] = FAST;
    }

    /**
     * An end the map is not loaded as far as, with the other made fast: a pole is taken to
     * stand the usual span from the other, or where the map ends if that is further, on land
     * as high as the last that is known.
     */
    private static void guess(float[] pieces, int at, int other) {
        if (pieces[at + END_KIND] != WANTING || pieces[other + END_KIND] != FAST) {
            return;
        }
        float awayX = pieces[at] - pieces[other], awayY = pieces[at + 1] - pieces[other + 1], away = (float) Math.sqrt(awayX * awayX + awayY * awayY);
        float far = Math.max(away, USUAL);
        pieces[at] = pieces[other] + awayX / away * far;
        pieces[at + 1] = pieces[other + 1] + awayY / away * far;
    }

    /**
     * Follows a wire on from where a length of it ends, the way it runs, into end: where its
     * span ends that way, where the land is taken there and which kind of end it is.
     */
    private void follow(float fromX, float fromY, float wayX, float wayY) {
        float atX = fromX, atY = fromY;
        while (true) {
            int middleX = (int) Math.floor(atX + wayX * 0.5f), middleY = (int) Math.floor(atY + wayY * 0.5f);
            boolean unloaded = false, next = false;
            float nearest = OFF_LINE, nextFromX = 0f, nextFromY = 0f, nextToX = 0f, nextToY = 0f;
            int nextX = 0, nextY = 0;
            for (int beside = 0; beside < 9; beside++) {
                int squareX = middleX + beside % 3 - 1, squareY = middleY + beside / 3 - 1;
                int count = map.lines(squareX, squareY, lines);
                if (count < 0) {
                    unloaded = true;
                    continue;
                }
                for (int line = 0; line < count; line += 4) {
                    float x0 = lines[line], y0 = lines[line + 1], x1 = lines[line + 2], y1 = lines[line + 3];
                    float start = (x0 - atX) * wayX + (y0 - atY) * wayY, stop = (x1 - atX) * wayX + (y1 - atY) * wayY;
                    if (start > stop) {
                        float held = start;
                        start = stop;
                        stop = held;
                        x0 = lines[line + 2];
                        y0 = lines[line + 3];
                        x1 = lines[line];
                        y1 = lines[line + 1];
                    }
                    float length = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
                    float off = Math.max(Math.abs((x0 - atX) * wayY - (y0 - atY) * wayX), Math.abs((x1 - atX) * wayY - (y1 - atY) * wayX));
                    if (start < GAP && stop > FURTHER && off < nearest && Math.abs((x1 - x0) * wayY - (y1 - y0) * wayX) < SAME_WAY * length) {
                        nearest = off;
                        next = true;
                        nextFromX = x0;
                        nextFromY = y0;
                        nextToX = x1;
                        nextToY = y1;
                        nextX = squareX;
                        nextY = squareY;
                    }
                }
            }
            if (!next) {
                end[0] = atX;
                end[1] = atY;
                end[END_LAND] = atX;
                end[END_LAND + 1] = atY;
                end[END_KIND] = unloaded ? WANTING : LOOSE;
                return;
            }
            // The first insulator that holds the next length, as that length finds it for itself.
            touch(pins, pinsRound(nextX, nextY), nextFromX, nextFromY, nextToX, nextToY);
            int held = first >= 0 ? first : passed > 0 ? cuts[0] : last;
            if (held >= 0) {
                end[0] = pins[held];
                end[1] = pins[held + 1];
                end[END_LAND] = pins[held + 2];
                end[END_LAND + 1] = pins[held + 3];
                end[END_KIND] = FAST;
                return;
            }
            atX = nextToX;
            atY = nextToY;
        }
    }
}
