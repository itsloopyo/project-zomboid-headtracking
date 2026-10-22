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
 * What hangs on a cut wall the game takes away only where the square is in another room than
 * the viewer's, outdoors counting as one, and has the flag that lets that wall's things be cut
 * (FBORenderCell.calculateObjectTargetAlpha_NotDoorOrWall): a noteboard on a kiosk out in a
 * park stands for a viewer out there while the wall behind it is cut to its low course. A
 * third mark says so of a square.
 *
 * The shader is GLSL 1.20, with no whole numbers to pick bits out of and no texture unit to
 * spare, so the marks go to it as floats: the block's SIDE by SIDE squares a row after a row,
 * DIGITS squares to a float, each a digit of the float in base eight. Eight such digits are
 * 24 bits, which a float holds exactly. The floats go four to a vec4: as an array of floats
 * each took a register of its own, and the shader ran past the 1024 a program may have.
 */
final class Cutaways {
    /** The mark of a north wall cut away and of a west wall, as the game numbers them, and of a square whose hung things stay. */
    static final int NORTH = 1, WEST = 2, KEPT = 4;
    /** How many squares a side the block is, and how many squares a float holds. */
    static final int SIDE = 60, DIGITS = 8;
    static final int FLOATS = (SIDE * SIDE / DIGITS + 3) / 4 * 4;

    /**
     * The world shader's reading of the block: the marks of a square, and whether a point of a
     * face is cut away. Joined on before the fragment shader's main, which alone calls it.
     */
    static final String GLSL = "#define CUT_VEC4S " + FLOATS / 4 + "\n#define CUT_SIDE " + SIDE + "\n" + """
            // The walls the game cuts away, for the picture drawn as the game's own view: see Cutaways.
            // uCutMarked is the block's first square, and the height its walls are cut up to, or 0 with no block.
            uniform vec4 uCutRows[CUT_VEC4S];
            uniform vec3 uCutMarked;
            const float CUT_DIGIT[8] = float[8](1.0, 8.0, 64.0, 512.0, 4096.0, 32768.0, 262144.0, 2097152.0);

            // The game's marks on a square: 1 with its north wall cut away, 2 with its west wall, 3 with
            // both, and 4 more where what hangs on its cut walls stays. See Cutaways.
            float cutMarks(vec2 square) {
                vec2 at = square - uCutMarked.xy;
                if (at.x < 0.0 || at.y < 0.0 || at.x >= float(CUT_SIDE) || at.y >= float(CUT_SIDE)) {
                    return 0.0;
                }
                int index = int(at.y) * CUT_SIDE + int(at.x);
                int part = index / 8;
                int four = part / 4;
                float held = dot(uCutRows[four], vec4(equal(vec4(float(part - four * 4)), vec4(0.0, 1.0, 2.0, 3.0))));
                float digits = floor(held / CUT_DIGIT[index - part * 8]);
                return digits - 8.0 * floor(digits / 8.0);
            }

            // Whether a square's marks cut its wall, 1 its north and 2 its west, for a point of that
            // wall or of what hangs on it.
            bool wallCut(float marks, float wall, float hung) {
                return mod(floor(marks / wall), 2.0) > 0.5 && (hung < 0.5 || marks < 3.5);
            }

            // Whether a point is in a wall the game has cut away, or hangs on one. A square's north wall
            // runs along its north edge and its west wall along its west edge. A wall is taken by the way
            // it faces, or the end of one that stands would go with the cut wall it meets. Something
            // hung has faces every way, and goes by the wall it hangs on, hung: 1 for a north wall
            // and 2 for a west one. Gone by the wall each point is nearest, the brackets at the ends
            // of a shelf on a cut wall went by the uncut wall across the end of their square, and
            // stood on their own in the air over the cut wall. A wall's own ends run a
            // little past its square, into the corner. A wall's top faces up whichever wall it is,
            // and leans along its wall where the wall's two ends stand at different heights: it is
            // cut with any cut wall whose thickness it lies in, so the top of a straight run is cut
            // across the joints of its squares, and a counter's top against the wall keeps all but
            // the wall's own thickness.
            bool cutAway(vec3 at, vec3 facing, float hung) {
                vec2 edge = floor(at.xy + 0.5), off = abs(at.xy - edge);
                bool northCut = wallCut(cutMarks(vec2(floor(at.x - 0.06), edge.y)), 1.0, hung) || wallCut(cutMarks(vec2(floor(at.x + 0.06), edge.y)), 1.0, hung);
                bool westCut = wallCut(cutMarks(vec2(edge.x, floor(at.y - 0.06))), 2.0, hung) || wallCut(cutMarks(vec2(edge.x, floor(at.y + 0.06))), 2.0, hung);
                if (hung < 0.5 && abs(facing.z) > max(abs(facing.x), abs(facing.y))) {
                    return off.y < 0.06 && northCut || off.x < 0.06 && westCut;
                }
                float reach = hung > 0.5 ? 0.45 : 0.3;
                bool north = off.y < reach && (hung > 0.5 ? hung < 1.5 : abs(facing.y) > abs(facing.x));
                bool west = off.x < reach && (hung > 0.5 ? hung > 1.5 : abs(facing.x) >= abs(facing.y));
                return north ? northCut : west && westCut;
            }
            """;

    private Cutaways() {}

    static void clear(float[] rows) {
        Arrays.fill(rows, 0f);
    }

    /**
     * Sets the marks of a square of a block that was cleared and has had none set for the
     * square since: the game's wall marks, and whether what hangs on its cut walls stays.
     */
    static void put(float[] rows, int column, int row, int marks, boolean kept) {
        int square = row * SIDE + column;
        rows[square / DIGITS] += ((marks & (NORTH | WEST)) | (kept ? KEPT : 0)) * (float) (1 << 3 * (square % DIGITS));
    }

    /** The marks of a square, read as the shader reads them. */
    static int at(float[] rows, int column, int row) {
        int square = row * SIDE + column;
        float digits = (float) Math.floor(rows[square / DIGITS] / (1 << 3 * (square % DIGITS)));
        return (int) (digits - 8f * (float) Math.floor(digits / 8f));
    }

    /**
     * Whether what hangs on the cut walls of a square with these marks stays, as the game has
     * it: the square is in the viewer's room, or has no flag that lets the things on its marked
     * walls be cut.
     */
    static boolean kept(int marks, boolean sameRoom, boolean cutsNorth, boolean cutsWest) {
        return sameRoom || !((marks & NORTH) != 0 && cutsNorth || (marks & WEST) != 0 && cutsWest);
    }
}
