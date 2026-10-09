package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Where rain and snow cannot fall although the picture shows no roof there: the rooms of the
 * buildings the game's own view has opened. Their roofs and the floors over the viewer's are
 * not built, so neither the picture's depth nor the map of roofs keeps anything out of them.
 *
 * A block of SIDE by SIDE squares, and for each how many storeys the rooms over it reach,
 * counted from the ground: what is in the air over the square and under that many storeys is
 * indoors. A room under the ground covers nothing: what is over it is the open air.
 * Made on the logic thread when the opened buildings change and never written again, so the
 * render thread reads one whole.
 */
final class Shelter {
    static final int SIDE = 256;
    /** How far the viewer goes from the block's middle before another is made round them, in squares. */
    static final int STRAY = SIDE / 4;
    /** Under the ground nothing over the viewer is built and everything is indoors. */
    static final Shelter EVERYWHERE = new Shelter(0, 0);

    /** The block's first square. */
    final int x, y;
    final ByteBuffer storeys = ByteBuffer.allocateDirect(SIDE * SIDE).order(ByteOrder.nativeOrder());

    Shelter(int middleX, int middleY) {
        x = middleX - SIDE / 2;
        y = middleY - SIDE / 2;
    }

    /** A room's rectangle of squares, with the number of storeys from the ground up to the room's ceiling. The highest room over a square counts. */
    void cover(int fromX, int fromY, int wide, int deep, int count) {
        int held = Math.min(count, 255);
        for (int row = Math.max(fromY - y, 0); row < Math.min(fromY + deep - y, SIDE); row++) {
            for (int column = Math.max(fromX - x, 0); column < Math.min(fromX + wide - x, SIDE); column++) {
                if ((storeys.get(row * SIDE + column) & 255) < held) {
                    storeys.put(row * SIDE + column, (byte) held);
                }
            }
        }
    }

    int at(int squareX, int squareY) {
        int column = squareX - x, row = squareY - y;
        return column < 0 || column >= SIDE || row < 0 || row >= SIDE ? 0 : storeys.get(row * SIDE + column) & 255;
    }

    boolean strayedFrom(int squareX, int squareY) {
        return Math.abs(squareX - x - SIDE / 2) > STRAY || Math.abs(squareY - y - SIDE / 2) > STRAY;
    }
}
