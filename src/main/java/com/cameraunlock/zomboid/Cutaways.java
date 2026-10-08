package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * The walls the game cuts away in its own view, for the squares round a viewer, as the world
 * shader is handed them.
 *
 * The game keeps two marks on a square for each player: its north wall is cut away, its west
 * wall is. It works them out afresh as the player moves, for the walls that stand between its
 * camera and a room the player sees into. The picture drawn as the game's own view cuts the
 * same walls, so it reads the marks and works nothing out.
 *
 * The shader is GLSL 1.20, with no whole numbers to pick bits out of and no texture unit to
 * spare, so the marks go to it as floats: a block of SIDE by SIDE squares, a row after a row,
 * and DIGITS squares to a float, each a digit of the float in base four. Twelve such digits
 * are 24 bits, which a float holds exactly.
 */
final class Cutaways {
    /** The mark of a north wall cut away and of a west wall, as the game numbers them. */
    static final int NORTH = 1, WEST = 2;
    /** How many squares a side the block is, and how many squares a float holds. */
    static final int SIDE = 60, DIGITS = 12;
    static final int FLOATS_A_ROW = SIDE / DIGITS, FLOATS = SIDE * FLOATS_A_ROW;

    private Cutaways() {}

    static void clear(float[] rows) {
        Arrays.fill(rows, 0f);
    }

    /** Sets the marks of a square of a block that was cleared and has had none set for the square since. */
    static void put(float[] rows, int column, int row, int marks) {
        rows[row * FLOATS_A_ROW + column / DIGITS] += (marks & (NORTH | WEST)) * (float) (1 << 2 * (column % DIGITS));
    }

    /** The marks of a square, read as the shader reads them. */
    static int at(float[] rows, int column, int row) {
        float digits = (float) Math.floor(rows[row * FLOATS_A_ROW + column / DIGITS] / (1 << 2 * (column % DIGITS)));
        return (int) (digits - 4f * (float) Math.floor(digits / 4f));
    }
}
