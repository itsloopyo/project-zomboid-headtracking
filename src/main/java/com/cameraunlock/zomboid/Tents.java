package com.cameraunlock.zomboid;

import java.util.IdentityHashMap;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The cloth of a tent and of a market stall's canopy, from the game's sheet of them
 * (location_military_tent_01: green from 0, tan from 64). The game marks the canopy's
 * squares as roof, so that they are cut away with a roof, and read as a building's roof they
 * were fitted into slabs tilted every which way with eaves and gutters under them. They are no
 * building's roof: each square of canopy is laid on the plane its sprite paints, which the
 * squares of one tent share at their edges, and the pieces round it where they are painted.
 *
 * Read off the sprites (2x frame, the square's north corner at 64, 192, 192 pixels a level):
 * every square of canopy is a plane that climbs or falls a sixth of a level across the square,
 * from 0 at the eaves (the floor of the level the canopy is on) to a ridge at a third of a
 * level on a stall four squares wide, or half a level on a tent six wide. The gable at each end
 * is a triangle on the edge of its square under the canopy's line. Beyond the eaves on the side
 * the game's view sees, a strip of cloth carries the canopy's slope on for a little, painted on
 * the edge of the next square on the level below at the top of that level, and a rope runs
 * from under it at the corners to a peg in the ground.
 */
final class Tents {
    static final int CANOPY = 0, GABLE = 1, FLAP = 2, ROPE = 3;
    /** The sheet's pieces repeat in another colour this far on. */
    private static final int COLOURS = 64;
    private static final String SHEET = "location_military_tent_01_";
    /** How far a canopy climbs or falls across a square, in levels. */
    static final float STEP = 1f / 6f;
    /** How high a gable reaches over the eaves, in levels: a tent six squares wide. */
    static final float GABLE_HIGH = 0.5f;
    /** How far past its square's edge the strip beyond the eaves goes, in squares: the painted strip is narrower, and the rest of the face is clear. */
    static final float FLAP_OUT = 0.25f;

    /**
     * One piece of a tent on its square. alongX is the way the canopy's slope runs (west to
     * east, else north to south), or for a piece on an edge whether the edge is the north one
     * (it runs along x) or the west one. from and to are the canopy's heights at the square's
     * west or north edge and at its east or south edge, in levels over the level the piece is
     * on. A rope runs from (topA, topB, topH) on its square to (footA, footB) on the ground.
     */
    record Piece(int kind, boolean alongX, float from, float to, float topA, float topB, float topH, float footA, float footB) {
        static Piece canopy(boolean alongX, int fromSixths, int toSixths) {
            return new Piece(CANOPY, alongX, fromSixths * STEP, toSixths * STEP, 0f, 0f, 0f, 0f, 0f);
        }

        static Piece edge(int kind, boolean north) {
            return new Piece(kind, north, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        }

        static Piece rope(boolean north, float topA, float topB, float topH, float footA, float footB) {
            return new Piece(ROPE, north, 0f, 0f, topA, topB, topH, footA, footB);
        }
    }

    private static final Piece[] PIECES = new Piece[COLOURS];

    static {
        // The slope across x: a stall four squares wide is 16 22 23 21, a tent six wide 16 17 18 19 20 21.
        PIECES[16] = Piece.canopy(true, 0, 1);
        PIECES[17] = Piece.canopy(true, 1, 2);
        PIECES[18] = Piece.canopy(true, 2, 3);
        PIECES[19] = Piece.canopy(true, 3, 2);
        PIECES[20] = Piece.canopy(true, 2, 1);
        PIECES[21] = Piece.canopy(true, 1, 0);
        PIECES[22] = Piece.canopy(true, 1, 2);
        PIECES[23] = Piece.canopy(true, 2, 1);
        // Across y: 29 28 25 24, and 29 28 27 26 25 24; 30 and 31 are 25 and 28 with a pole under them.
        PIECES[24] = Piece.canopy(false, 1, 0);
        PIECES[25] = Piece.canopy(false, 2, 1);
        PIECES[26] = Piece.canopy(false, 3, 2);
        PIECES[27] = Piece.canopy(false, 2, 3);
        PIECES[28] = Piece.canopy(false, 1, 2);
        PIECES[29] = Piece.canopy(false, 0, 1);
        PIECES[30] = Piece.canopy(false, 2, 1);
        PIECES[31] = Piece.canopy(false, 1, 2);
        for (int place = 40; place <= 45; place++) {
            PIECES[place] = Piece.edge(GABLE, false);
        }
        for (int place = 48; place <= 53; place++) {
            PIECES[place] = Piece.edge(GABLE, true);
        }
        PIECES[32] = Piece.edge(FLAP, false);
        PIECES[33] = Piece.edge(FLAP, false);
        PIECES[34] = Piece.edge(FLAP, false);
        PIECES[37] = Piece.edge(FLAP, true);
        PIECES[38] = Piece.edge(FLAP, true);
        PIECES[39] = Piece.edge(FLAP, true);
    }

    /**
     * The ropes, where each sprite paints its line: the top under the eaves on the edge the
     * strip is on, the foot on the ground, as shares of the square and levels.
     */
    private static final Piece[] ROPES = new Piece[COLOURS];

    static {
        ROPES[33] = Piece.rope(false, 0f, 0.84f, 0.906f, 0.586f, 0.695f);
        ROPES[35] = Piece.rope(false, 0f, 0.875f, 0.906f, 0.617f, 0.758f);
        ROPES[36] = Piece.rope(true, 0.78f, 0f, 0.89f, 0.70f, 0.68f);
        ROPES[38] = Piece.rope(true, 0.72f, 0f, 0.885f, 0.63f, 0.65f);
    }

    private static final IdentityHashMap<IsoSprite, Piece> known = new IdentityHashMap<>(), roped = new IdentityHashMap<>();

    private Tents() {}

    /** For a map loaded anew: its sprites are not the ones before's. */
    static void forget() {
        known.clear();
        roped.clear();
    }

    static Piece of(IsoSprite sprite) {
        Piece piece = known.get(sprite);
        if (piece == null && !known.containsKey(sprite)) {
            piece = of(sprite.getName());
            known.put(sprite, piece);
        }
        return piece;
    }

    static Piece ropeOf(IsoSprite sprite) {
        Piece rope = roped.get(sprite);
        if (rope == null && !roped.containsKey(sprite)) {
            rope = ropeOf(sprite.getName());
            roped.put(sprite, rope);
        }
        return rope;
    }

    /** Whether a sprite is a square of a tent's canopy: under it the canvas is the ceiling, and no flat one is laid. */
    static boolean isCanvas(IsoSprite sprite) {
        Piece piece = of(sprite);
        return piece != null && piece.kind() == CANOPY;
    }

    /** Whether a square holds a square of a tent's canopy. */
    static boolean canvasOn(IsoGridSquare square) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && isCanvas(sprite)) {
                return true;
            }
        }
        return false;
    }

    /** The piece of a tent a sprite paints, other than its rope, or null for any other sprite. */
    static Piece of(String name) {
        int place = place(name);
        return place < 0 ? null : PIECES[place % COLOURS];
    }

    /** The rope a sprite of a tent paints, or null. */
    static Piece ropeOf(String name) {
        int place = place(name);
        return place < 0 ? null : ROPES[place % COLOURS];
    }

    private static int place(String name) {
        if (name == null || !name.startsWith(SHEET)) {
            return -1;
        }
        String digits = name.substring(SHEET.length());
        if (digits.isEmpty() || digits.length() > 3 || !digits.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        int place = Integer.parseInt(digits);
        return place >= 2 * COLOURS ? -1 : place;
    }
}
