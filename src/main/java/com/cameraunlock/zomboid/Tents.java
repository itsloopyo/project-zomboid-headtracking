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
    /**
     * How many frame pixels further in than a face's usual inset the texels of the canvas are
     * taken. The sprites paint up to a pixel or two short of their squares' edges and of the
     * canopy's line, and the texels there are clear: from inside a tent each joint of the
     * canvas, each gable's top and each eave against its wall was a row of slits of sky.
     */
    static final float DRAWN_IN = 3f;
    /**
     * How far inside the tent a gable is lined at its top and at its foot, in squares, and how
     * far under the canopy's line the lining stops, in levels. A gable's picture is cut to its
     * sprite, which stops a pixel or two short of the canopy's line, and along that line the
     * canvas's edge and the gable's top are two faces that share no corner: from inside, specks
     * of sky. The lining is the gable's picture again inside it, hidden from outside behind the
     * gable. It leans: at its foot it stands just off the end wall below, so no ledge shows
     * there, and at its top it is far enough in that the canvas's edge, which its cut to its
     * sprite leaves short of the gable by a few pixels, is behind it from inside, and what is
     * seen over its top is the canvas's underside. It stops just under the canvas: run up
     * through it, the two were too near to tell apart along where they met, and the lining's
     * paler texels showed there as a light line.
     */
    static final float LINING_IN = 0.15f, LINING_FOOT_IN = 0.005f, LINING_UNDER = 0.005f;
    /**
     * How many frame pixels further in than a face's usual inset the lining's texels are
     * taken. The artist outlines a gable's top with a line a few pixels wide, pale on the sunny
     * slope and dark on the other; taken where the canvas's are, the lining's top was that
     * line, a pale or dark seam along the canvas from inside.
     */
    static final float LINING_DRAWN_IN = 8f;
    /**
     * How far past its square's edge the strip beyond the eaves goes, in squares: as far as its
     * sprites paint it, 16 frame pixels down from the edge where a quarter of a square is 24.
     * Laid a quarter wide, the face was cut to the slant of the paint's edge, a stair of pixels.
     */
    static final float FLAP_OUT = 1f / 6f;

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

        /** A gable, as high as the canopy over its edge: from and to in sixths, at the edge's west or north end and at its other. */
        static Piece gable(boolean north, int fromSixths, int toSixths) {
            return new Piece(GABLE, north, fromSixths * STEP, toSixths * STEP, 0f, 0f, 0f, 0f, 0f);
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
        // The gables under them: 48..53 under 16..21 on a north edge, 45..40 under 29..24 on a west one.
        PIECES[48] = Piece.gable(true, 0, 1);
        PIECES[49] = Piece.gable(true, 1, 2);
        PIECES[50] = Piece.gable(true, 2, 3);
        PIECES[51] = Piece.gable(true, 3, 2);
        PIECES[52] = Piece.gable(true, 2, 1);
        PIECES[53] = Piece.gable(true, 1, 0);
        PIECES[45] = Piece.gable(false, 0, 1);
        PIECES[44] = Piece.gable(false, 1, 2);
        PIECES[43] = Piece.gable(false, 2, 3);
        PIECES[42] = Piece.gable(false, 3, 2);
        PIECES[41] = Piece.gable(false, 2, 1);
        PIECES[40] = Piece.gable(false, 1, 0);
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

    /**
     * Whether a tent's sprite is laid as its pieces, the canvas and the rope, given the piece of
     * canvas it is, the rope it carries and whether the mod's own furniture is drawn. The rope
     * is the mod's own line, so with the furniture off a sprite that carries one is left whole
     * to the game's sprite, the strip it paints with it: laid as a strip it lost its rope.
     */
    static boolean laid(Piece piece, Piece rope, boolean furniture) {
        return rope == null ? piece != null : furniture;
    }

    /**
     * The four corners of a gable's lining on its square, each (a, b, h): along the gable's
     * edge from its west or north end, at its foot and then up to the canvas over it. The tent
     * is on the square when canvasHere, and on the square across the edge when not.
     */
    static void lining(Piece gable, boolean canvasHere, float[] corners) {
        float way = canvasHere ? 1f : -1f, foot = way * LINING_FOOT_IN, top = way * LINING_IN;
        boolean north = gable.alongX();
        float endA = north ? 1f : 0f, endB = north ? 0f : 1f;
        WorldScene.set(corners, 0, north ? 0f : foot, north ? foot : 0f, 0f);
        WorldScene.set(corners, 1, endA + (north ? 0f : foot), endB + (north ? foot : 0f), 0f);
        WorldScene.set(corners, 2, endA + (north ? 0f : top), endB + (north ? top : 0f), Math.max(gable.to() - LINING_UNDER, 0f));
        WorldScene.set(corners, 3, north ? 0f : top, north ? top : 0f, Math.max(gable.from() - LINING_UNDER, 0f));
    }

    /**
     * Where on its gable's square each corner of a lining takes its picture, each (a, b, h):
     * the point of the gable straight out from it, no higher than the canopy's line.
     */
    static void liningPicture(Piece gable, float[] corners, float[] onGable) {
        boolean north = gable.alongX();
        for (int corner = 0; corner < 4; corner++) {
            float along = north ? corners[corner * 3] : corners[corner * 3 + 1];
            float top = gable.from() + (gable.to() - gable.from()) * along;
            WorldScene.set(onGable, corner, north ? along : 0f, north ? 0f : along, Math.min(corners[corner * 3 + 2], top));
        }
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
