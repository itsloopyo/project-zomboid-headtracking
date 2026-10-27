package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * What the game's own view from overhead shows of each square round the viewer, as the
 * develop shader darkens the picture drawn in its place.
 *
 * The game lights a square by how well its character sees it: in full where they can see it
 * now, dimmer where they have seen it and cannot now, and not at all where they never have.
 * It eases each square's figure towards where it is going (the square's dark multiplier,
 * 1 in sight, 0 never seen), and that eased figure is what is read here, so the darkness
 * comes and goes as the game's does. Measured in the game: a square in sight reads 1.00, one
 * behind the character never seen reads 0, and the edge between them 0.50 to 0.75.
 *
 * A room of a building the game has not opened holds no figure worth reading: nobody has
 * looked in, and the picture leaves out what is in it (Sightline). What shows of it from
 * outside is its walls and its roof, and the game lights those as the ground round them is
 * lit: a house beside the path the character sees is lit, the wing of it that stands among
 * squares they cannot see is dark. So such a room takes the figures of the open squares
 * round its building, each square of it the mean of its neighbours', worked inwards from
 * the walls.
 *
 * The roof over such a room the game lights in full whether its squares are seen or not: the
 * dark multiplier of a roof's squares on level 1 reads 0 where the ground round it is in the
 * dark (12848..12859, 3186..3194, measured), and the game's picture shows the roof lit. Each
 * closed square keeps the height its room's level ends at, and nothing drawn above it is
 * darkened: a storey over the room as well, which has not been set beside the game's.
 *
 * A block of SIDE by SIDE squares from (x, y), a byte a square, made on the logic thread for
 * one frame and read whole by the render thread when that frame is drawn. A square with
 * nothing on it keeps none of its light, as the game draws none there.
 */
final class Sight {
    static final int SIDE = 192;
    /** A room's square, whose figure is taken from round it, and a square not read, which lends none. */
    static final int CLOSED = -1, UNREAD = -2;

    /** The block's first square. */
    int x, y;
    final ByteBuffer shown = ByteBuffer.allocateDirect(SIDE * SIDE).order(ByteOrder.nativeOrder());
    /** For each square, how high the viewer's level ends over a closed room, in world height; NEVER_LIT elsewhere. */
    final FloatBuffer lit = ByteBuffer.allocateDirect(SIDE * SIDE * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    /** Higher than anything drawn, as a half float holds it. */
    static final float NEVER_LIT = 60000f;
    private final int[] held = new int[SIDE * SIDE];
    private final int[] pending = new int[SIDE * SIDE], taken = new int[SIDE * SIDE];

    /** How much of a square's light the picture keeps, 0 to 255, from the game's dark multiplier for it. */
    static int kept(float darkMulti) {
        return Math.round(Math.clamp(darkMulti, 0f, 1f) * 255f);
    }

    void begin(int middleX, int middleY) {
        x = middleX - SIDE / 2;
        y = middleY - SIDE / 2;
        Arrays.fill(held, UNREAD);
        for (int i = 0; i < SIDE * SIDE; i++) {
            lit.put(i, NEVER_LIT);
        }
    }

    /** A square's figure, 0 to 255, or CLOSED. */
    void put(int squareX, int squareY, int kept) {
        held[(squareY - y) * SIDE + squareX - x] = kept;
    }

    /** A closed square's room, whose level ends at top: what is drawn higher keeps its light. */
    void closed(int squareX, int squareY, float top) {
        put(squareX, squareY, CLOSED);
        lit.put((squareY - y) * SIDE + squareX - x, top);
    }

    /** Gives every closed square its figure and makes the block the shader reads. */
    void settle() {
        fill(held, pending, taken, SIDE, SIDE);
        for (int i = 0; i < SIDE * SIDE; i++) {
            if (held[i] == UNREAD) {
                held[i] = 0;
            }
            shown.put(i, (byte) held[i]);
        }
    }

    int at(int squareX, int squareY) {
        int column = squareX - x, row = squareY - y;
        return column < 0 || column >= SIDE || row < 0 || row >= SIDE ? 0 : shown.get(row * SIDE + column) & 255;
    }

    /**
     * Each CLOSED square of a block of rows by columns takes the mean of the neighbours along its
     * sides that have a figure, a ring at a time from the edge of the closed ones in, so no
     * side of a building is reached before another. An UNREAD square lends nothing. A closed
     * run with nothing round it at all keeps none.
     */
    static void fill(int[] figures, int[] scratch, int[] taken, int columns, int rows) {
        // scratch holds the closed squares still to be reached, taken what each takes this ring.
        int pending = 0;
        for (int at = 0; at < columns * rows; at++) {
            if (figures[at] == CLOSED) {
                scratch[pending++] = at;
            }
        }
        while (pending > 0) {
            int reached = 0;
            for (int i = 0; i < pending; i++) {
                int at = scratch[i], column = at % columns, row = at / columns;
                int sum = 0, count = 0;
                if (column > 0 && figures[at - 1] >= 0) {
                    sum += figures[at - 1];
                    count++;
                }
                if (column < columns - 1 && figures[at + 1] >= 0) {
                    sum += figures[at + 1];
                    count++;
                }
                if (row > 0 && figures[at - columns] >= 0) {
                    sum += figures[at - columns];
                    count++;
                }
                if (row < rows - 1 && figures[at + columns] >= 0) {
                    sum += figures[at + columns];
                    count++;
                }
                taken[i] = count > 0 ? Math.round(sum / (float) count) : CLOSED;
                if (count > 0) {
                    reached++;
                }
            }
            if (reached == 0) {
                break;
            }
            int left = 0;
            for (int i = 0; i < pending; i++) {
                if (taken[i] == CLOSED) {
                    scratch[left++] = scratch[i];
                } else {
                    figures[scratch[i]] = taken[i];
                }
            }
            pending = left;
        }
        for (int i = 0; i < pending; i++) {
            figures[scratch[i]] = 0;
        }
    }
}
