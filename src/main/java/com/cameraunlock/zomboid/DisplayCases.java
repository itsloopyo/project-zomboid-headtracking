package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;

/**
 * A cake shop's glass case, two squares long: the counter with a curved glass front a bakery, a pie shop, a coffee
 * shop or a pizza place keeps its cakes in. Wrapped on the box its outline makes, or stood as a card, its sprite
 * was a skewed sheet leaning on the counter beside it. It is the mod's model, the glass the mod lays round it, and,
 * while the game paints cakes over a square of it, cakes of the mod's own on that square's deck and shelf.
 */
final class DisplayCases {
    private DisplayCases() {}

    /**
     * The case's model in metres from the middle of its footprint, its front to -y, as tools/models/furniture.py builds
     * bakery_case: half its length, its front and back, the top of its top board and the board's underside, where its
     * front's curve starts and how far back it ends, the height of the rail at its foot, of its deck and of its shelf,
     * where the shelf's front edge is, and how thick its ends are. The glass is laid by these numbers.
     */
    static final float HALF = 0.98f, FRONT = -0.37f, BACK = 0.37f, TOP = 0.97f, UNDER = 0.94f, ARC_FOOT = 0.45f, CURVE_Y = 0f,
            KICK = 0.08f, DECK = 0.30f, SHELF = 0.62f, SHELF_FRONT = -0.22f, END = 0.03f;
    /** How many flat strips the curve of the glass is made of, as many as the curve of its ends. */
    static final int ARC_STEPS = 12;
    /** How far inside the front edge of the ends the glass is. */
    private static final float GLASS_IN = 0.02f;
    /**
     * The front's glass in the model, from the rail at its foot up its curve to the top board: at each step its y and
     * its height, and how far up its pane it is.
     */
    private static final float[] ARC_Y = new float[ARC_STEPS + 2], ARC_Z = new float[ARC_STEPS + 2], ARC_V = new float[ARC_STEPS + 2];

    static {
        float deep = CURVE_Y - FRONT - GLASS_IN, rise = UNDER - ARC_FOOT;
        float[] lengths = new float[ARC_STEPS + 2];
        ARC_Y[0] = FRONT + GLASS_IN;
        ARC_Z[0] = KICK;
        for (int step = 0; step <= ARC_STEPS; step++) {
            double angle = Math.PI / 2 * step / ARC_STEPS;
            ARC_Y[step + 1] = CURVE_Y - deep * (float) Math.cos(angle);
            ARC_Z[step + 1] = ARC_FOOT + rise * (float) Math.sin(angle);
            lengths[step + 1] = lengths[step] + (float) Math.hypot(ARC_Y[step + 1] - ARC_Y[step], ARC_Z[step + 1] - ARC_Z[step]);
        }
        for (int step = 0; step < lengths.length; step++) {
            ARC_V[step] = lengths[step] / lengths[ARC_STEPS + 1];
        }
    }

    /**
     * Where the far end of the case gives its colour, in metres of the model: low on its end, a little in from the back,
     * where every case's sprite paints its end panel; and its top, from over the middle of the top board on the far square.
     */
    private static final float END_DYE_UP = 0.2f, END_DYE_IN = 0.15f, TOP_DYE_IN = 0.45f;

    private static final String[] STOCK = {"pastries_few_a", "pastries_few_b", "pastries_many_a", "pastries_many_b"};

    /**
     * The case a sprite is a square of, or null. Every game sheet that has one calls it something else (a counter, a
     * stand, a display) in a group named for its glass, and what it holds is a bakery's or a restaurant's display.
     */
    static Furniture of(IsoSprite sprite, String group, String holds, PropertyContainer properties, int turns, int along, int across) {
        if (along != 1 || across != 2 || !group.contains("Glass") || !properties.has(IsoFlagType.solidtrans)
                || !holds.equals("displaycasebakery") && !holds.equals("restaurantdisplay")) {
            return null;
        }
        Furniture piece = new Furniture(sprite, "bakery_case", turns).stand(0f, 0f, true);
        piece.displayCase = true;
        // The end the game's view sees is the one on the far square, which is where the map's x or y grows.
        boolean alongX = piece.width == 2;
        piece.dyeX = alongX ? 1 : 0;
        piece.dyeY = alongX ? 0 : 1;
        float[] way = new float[2];
        Models.turn(turns, 1f, 0f, way);
        float far = Math.signum(alongX ? way[0] : way[1]);
        float[] at = new float[2];
        modelOnSquare(piece, far * (HALF - 0.005f), BACK - END_DYE_IN, at);
        piece.dye(1 << Models.WOOD, at[0], at[1], END_DYE_UP * piece.scaleUp / ViewCamera.LEVEL_HEIGHT);
        modelOnSquare(piece, far * (HALF - TOP_DYE_IN), (CURVE_Y + BACK) / 2f, at);
        return piece.alsoAt(1 << Models.WORKTOP, at[0], at[1], TOP * piece.scaleUp / ViewCamera.LEVEL_HEIGHT);
    }

    /** Where on the square that gives the piece its colour a place on the model stands, into at. */
    private static void modelOnSquare(Furniture piece, float x, float y, float[] at) {
        Models.turn(piece.turns, x, y, at);
        at[0] = piece.centreX + at[0] * piece.scale - piece.dyeX;
        at[1] = piece.centreY + at[1] * piece.scale - piece.dyeY;
    }

    /**
     * Lays the case's glass: its curved front, the shelf, and the two sliding doors at its back. The front and the back
     * face out of the case, so each is the side of a window that lets the most through to whoever looks into the case.
     * x and y are the case's first square, (centreX, centreY) where on it the case stands, foot the floor under it.
     */
    static void addGlass(WorldScene.Frame build, Furniture piece, int x, int y, float centreX, float centreY, float foot) {
        Furniture.Glass glass = new Furniture.Glass(build, piece, x, y, centreX, centreY, foot);
        float side = HALF - END;
        for (int strip = 0; strip <= ARC_STEPS; strip++) {
            float dy = ARC_Y[strip + 1] - ARC_Y[strip], dz = ARC_Z[strip + 1] - ARC_Z[strip];
            glass.face(0f, -dz, dy);
            pane(glass, -side, side, ARC_Y[strip], ARC_Z[strip], ARC_Y[strip + 1], ARC_Z[strip + 1], ARC_V[strip], ARC_V[strip + 1]);
        }
        glass.face(0f, 0f, 1f);
        flat(glass, -side, side, SHELF_FRONT, BACK - 0.06f, SHELF);
        // Two doors, one sliding behind the other.
        glass.face(0f, 1f, 0f);
        pane(glass, -side, 0.02f, BACK - 0.025f, DECK + 0.04f, BACK - 0.025f, UNDER - 0.05f, 0f, 1f);
        pane(glass, -0.02f, side, BACK - 0.012f, DECK + 0.04f, BACK - 0.012f, UNDER - 0.05f, 0f, 1f);
    }

    /** A sheet of glass across the case from x0 to x1 and from (y0, z0) to (y1, z1) in the model, v0 to v1 of its pane up it. */
    private static void pane(Furniture.Glass glass, float x0, float x1, float y0, float z0, float y1, float z1, float v0, float v1) {
        glass.corner(x0, y0, z0, 0f, v0);
        glass.corner(x1, y0, z0, 1f, v0);
        glass.corner(x1, y1, z1, 1f, v1);
        glass.corner(x0, y0, z0, 0f, v0);
        glass.corner(x1, y1, z1, 1f, v1);
        glass.corner(x0, y1, z1, 0f, v1);
    }

    /** A level sheet of glass from x0 to x1 and y0 to y1 at height z in the model. */
    private static void flat(Furniture.Glass glass, float x0, float x1, float y0, float y1, float z) {
        glass.corner(x0, y0, z, 0f, 0f);
        glass.corner(x1, y0, z, 1f, 0f);
        glass.corner(x1, y1, z, 1f, 1f);
        glass.corner(x0, y0, z, 0f, 0f);
        glass.corner(x1, y1, z, 1f, 1f);
        glass.corner(x0, y1, z, 0f, 1f);
    }

    /**
     * Stands cakes on the deck and the shelf of one square of a case while the game paints its cakes over the square,
     * many or few as the game paints them, and none while it paints none. The two squares of a case hold different
     * cakes. x and y are the square, foot the floor under it, (awayX, awayY) how far the case stands from where it is painted.
     */
    static void addStock(WorldScene.Frame build, IsoObject object, Furniture piece, int x, int y, float foot, float awayX, float awayY) {
        if (object.getOverlaySprite() == null) {
            return;
        }
        Models.Model cakes = Models.get(STOCK[(ShopFittings.many(object) ? 2 : 0) + (piece.gridX + piece.gridY & 1)]);
        float[] middle = new float[2];
        middleOf(piece, x, y, middle);
        build.placed.add(cakes, build.frame, x, y, middle[0] + awayX, middle[1] + awayY, foot + DECK * piece.scaleUp, piece.turns, piece.scale, piece.scaleUp,
                WorldScene.PLAIN, 0, 0f, 0f, 0, 0f, 0f);
    }

    /**
     * Into out, where a square's cakes stand: the middle of the square (x, y) along the case, and of the case across it.
     * The case is stood back from the middle of its squares only across its length.
     */
    static void middleOf(Furniture piece, int x, int y, float[] out) {
        out[0] = x + 0.5f + piece.centreX - piece.width / 2f;
        out[1] = y + 0.5f + piece.centreY - piece.height / 2f;
    }
}
