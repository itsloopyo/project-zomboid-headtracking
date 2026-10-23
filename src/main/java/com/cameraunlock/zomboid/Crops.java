package com.cameraunlock.zomboid;

/**
 * The crops the game paints standing in its fields, grown stalk by stalk: wheat ripe for the
 * harvest, corn gone dry on the stalk and a field of dead leafy plants. Each stalk and leaf is a
 * strip of the kind Grass grows, coloured from where the crop's sprite paints it, so a field
 * keeps the game's colour for its crop and season and is no longer a few cards set across
 * each other.
 *
 * Every plant's place and shape come from its place on the map, so the same field stands there
 * whenever its chunk is built. Far from the eye there are fewer stalks, as in long grass, and
 * past REACH the game's card stands in for them.
 */
final class Crops {
    /** What a crop sprite is: none of these, ripe wheat, dry corn, or a dead leafy crop. */
    static final int NONE = -1, WHEAT = 0, CORN = 1, LEAFY = 2;
    /** Within this many tiles of the eye a field is grown, and past it the game's card is. */
    static final float REACH = 30f;
    private static final String SHEET = "vegetation_farm_01_";
    /** How many stalks a square of each crop has near the eye, along each side of the square. */
    private static final int WHEAT_SITES = 6, CORN_SITES = 2, LEAFY_SITES = 2;
    /** A crop is never shorter or taller than this, in tiles, whatever its sprite says. */
    private static final float LOWEST = 0.3f, TALLEST = 2.6f;

    private Crops() {}

    /**
     * Among a wheat field's thinned stalks its cards stand as well, from this many tiles off,
     * lower than the crop and rising to its height by WHEAT_CARDS_FULL: the stalks there are too few
     * to hide the ground between them, and past REACH the cards are the whole field.
     */
    static final float WHEAT_CARDS_FROM = 2f, WHEAT_CARDS_FULL = 22f;

    /** Which crop a sprite is, by its name. */
    static int of(String name) {
        int place = name == null ? -1 : Sheets.place(name, SHEET);
        if (place >= 0 && place <= 5) {
            return WHEAT;
        }
        if (place >= 32 && place <= 39) {
            return CORN;
        }
        return place >= 40 && place <= 47 ? LEAFY : NONE;
    }

    /** How tall a crop whose sprite paints it that many levels high stands, in tiles. */
    static float tall(float levels) {
        return Math.clamp(levels * ViewCamera.LEVEL_HEIGHT, LOWEST, TALLEST);
    }

    /**
     * How tall a crop's upright card stands as a share of what its sprite paints, for the crop to
     * stand as tall as it is (tall). Stood through the middle of its square, the card's top is
     * where the sprite paints the plants at the back of the square: higher by half the square's
     * depth on the screen, so the card stands taller than the crop. 1 for a sprite whose outline
     * says too little to size it by. box is filled as SpriteGeometry.boxOf fills it.
     */
    static float cardShare(SpriteGeometry.Frame frame, float[] box) {
        if (!SpriteGeometry.boxOf(frame, box)) {
            return 1f;
        }
        float card = (SpriteGeometry.frameV(frame, 0.5f, 0.5f, 0f) - frame.top) * SpriteGeometry.TILES_PER_PIXEL_UP / frame.scale;
        return card > 0f ? Math.min(1f, tall(box[3]) / card) : 1f;
    }

    /** How tall a field's cards stand among its stalks that many tiles off, as a share of the crop: 0 where the stalks alone are the crop. */
    static float amongStalks(int crop, float away) {
        return crop == WHEAT ? Math.clamp((away - WHEAT_CARDS_FROM) / (WHEAT_CARDS_FULL - WHEAT_CARDS_FROM), 0f, 1f) : 0f;
    }

    /**
     * Grows the crop on the square (x, y) as from away tiles off, tall tiles high, between lowA..highA
     * and lowB..highB of the square. places is Flora.leafPlaces of the frame: each stalk takes its
     * colour from one of them, so none is coloured from where the sprite paints nothing.
     */
    static void grow(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, float[] places, int crop, int x, int y, float tall,
            float away, float lowA, float highA, float lowB, float highB) {
        int sites = crop == WHEAT ? WHEAT_SITES : crop == CORN ? CORN_SITES : LEAFY_SITES;
        float kept = Grass.kept(away * away * 0.8f);
        float broadens = Math.min(1f / (float) Math.sqrt(kept), 2.8f);
        int steps = away < 6f ? 3 : away < 14f ? 2 : 1;
        int count = places.length / 2;
        for (int i = 0; i < sites; i++) {
            for (int j = 0; j < sites; j++) {
                int hash = Flora.mix(x * sites + i, y * sites + j, 230 + crop);
                // Wheat thins with distance as long grass does. A square of corn is only four stalks: it keeps them all.
                if (crop == WHEAT && Flora.unit(hash, 0) >= kept) {
                    continue;
                }
                float a = lowA + (highA - lowA) * (i + 0.2f + 0.6f * Flora.unit(hash, 8)) / sites;
                float b = lowB + (highB - lowB) * (j + 0.2f + 0.6f * Flora.unit(hash, 16)) / sites;
                int place = (int) (Flora.unit(hash, 4) * count) % count;
                geometry.plant(frame, x, y, a, b, colourU(frame, places[place * 2]), colourV(frame, places[place * 2 + 1]));
                int more = Flora.mix(hash, i, 7);
                float high = tall * (0.82f + 0.3f * Flora.unit(more, 0));
                switch (crop) {
                    case WHEAT -> wheat(geometry, mesh, frame, more, high, broadens, steps);
                    case CORN -> corn(geometry, mesh, frame, more, high, broadens, steps);
                    default -> leafy(geometry, mesh, frame, more, high, broadens, steps);
                }
            }
        }
        mesh.face(0f, 0f, 1f);
    }

    /** Where across the texture a frame pixel is. */
    static float colourU(SpriteGeometry.Frame frame, float pixel) {
        return frame.uStart + (pixel - frame.left) / frame.width * (frame.uEnd - frame.uStart);
    }

    /** Where down the texture a frame pixel is. */
    static float colourV(SpriteGeometry.Frame frame, float pixel) {
        return frame.vStart + (pixel - frame.top) / frame.height * (frame.vEnd - frame.vStart);
    }

    /** A stalk of wheat: a straight stem with an ear on it, and a dry leaf or two low down. */
    private static void wheat(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, int more, float high, float broadens, int steps) {
        float turn = Flora.unit(more, 24) * 6.2831853f, dirX = Grass.cos(turn), dirY = Grass.sin(turn);
        float seed = Flora.unit(more, 20);
        geometry.addStrip(mesh, frame, dirX, dirY, 0f, 0f, 0.03f + 0.1f * Flora.unit(more, 4), 0.05f + 0.15f * Flora.unit(more, 8), high * 0.85f,
                0.007f * broadens, 0f, Math.min(steps, 2), SpriteGeometry.EVEN, Grass.STEM, seed, 0.2f);
        // The ear goes on the way the stem leant, and a ripe one nods.
        geometry.addStrip(mesh, frame, dirX, dirY, geometry.endAlong, geometry.endUp, geometry.endLean, 0.2f + 0.4f * Flora.unit(more, 12), high * 0.15f,
                0.022f * Math.min(broadens, 1.6f), 0f, 1, SpriteGeometry.EVEN, Grass.SPIKE, seed, 0.2f);
        if (steps < 2) {
            return;
        }
        geometry.addStrip(mesh, frame, -dirY, dirX, 0f, high * 0.2f, 0.6f + 0.4f * Flora.unit(more, 16), 0.9f, high * 0.3f, 0.012f * broadens, 0.5f,
                Math.min(steps, 2), SpriteGeometry.POINTED, Grass.STRAW, Flora.unit(more, 14), 0.2f);
    }

    /**
     * A stalk of corn gone dry: a stout stem with a tassel at its top and long leaves all the way
     * up it, on alternate sides, arching out and hanging over at their tips.
     */
    private static void corn(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, int more, float high, float broadens, int steps) {
        float turn = Flora.unit(more, 24) * 6.2831853f, dirX = Grass.cos(turn), dirY = Grass.sin(turn);
        float seed = Flora.unit(more, 20);
        geometry.addStrip(mesh, frame, dirX, dirY, 0f, 0f, 0.02f + 0.08f * Flora.unit(more, 4), 0.04f + 0.1f * Flora.unit(more, 8), high,
                0.028f * broadens, 0.3f, Math.min(steps, 2), SpriteGeometry.EVEN, Grass.STEM, seed, 0.15f);
        float topAlong = geometry.endAlong, topUp = geometry.endUp, topLean = geometry.endLean;
        geometry.addStrip(mesh, frame, dirX, dirY, topAlong, topUp, topLean + 0.2f, 0.6f + 0.4f * Flora.unit(more, 12), high * 0.16f,
                0.06f * Math.min(broadens, 1.6f), 0f, 1, SpriteGeometry.EVEN, Grass.PANICLE, seed, 0.15f);
        int leaves = steps > 1 ? 7 + (more >>> 6 & 3) : 4;
        for (int leaf = 0; leaf < leaves; leaf++) {
            int own = Flora.mix(more, leaf, 31);
            float share = 0.12f + 0.72f * (leaf + 0.5f * Flora.unit(own, 0)) / leaves;
            float side = turn + ((leaf & 1) == 0 ? 0f : 3.1415927f) + 0.6f * (Flora.unit(own, 8) - 0.5f);
            // The leaf grows from the stem where the stem has leant to by then.
            float from = high * share;
            geometry.addStrip(mesh, frame, Grass.cos(side), Grass.sin(side), 0f, from, 0.55f + 0.45f * Flora.unit(own, 16), 1.1f + 0.8f * Flora.unit(own, 4),
                    high * (0.32f + 0.18f * Flora.unit(own, 12)) * (1.15f - 0.5f * share), (0.05f + 0.025f * Flora.unit(own, 20)) * Math.min(broadens, 2f),
                    0.8f * (Flora.unit(own, 24) - 0.5f), steps, SpriteGeometry.POINTED, Grass.STRAW, Flora.unit(own, 14), 0.15f);
        }
    }

    /** A dead leafy plant: a stem branching out with small dry leaves held out all round it. */
    private static void leafy(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, int more, float high, float broadens, int steps) {
        float turn = Flora.unit(more, 24) * 6.2831853f;
        float seed = Flora.unit(more, 20);
        int stems = 2 + (more >>> 5 & 1);
        for (int stem = 0; stem < stems; stem++) {
            int own = Flora.mix(more, stem, 37);
            float way = turn + stem * 6.2831853f / stems + 0.5f * Flora.unit(own, 0);
            geometry.addStrip(mesh, frame, Grass.cos(way), Grass.sin(way), 0f, 0f, 0.05f + 0.12f * Flora.unit(own, 4), 0.1f * Flora.unit(own, 8),
                    high * (0.8f + 0.2f * Flora.unit(own, 12)), 0.009f * broadens, 0f, Math.min(steps, 2), SpriteGeometry.EVEN, Grass.STEM, seed, 0.1f);
            // Leaves grow from the plant's upright: its stems stand near enough straight that they come off them.
            float stemUp = geometry.endUp;
            int leaves = steps > 1 ? 6 : 3;
            for (int leaf = 0; leaf < leaves; leaf++) {
                int each = Flora.mix(own, leaf, 41);
                float share = 0.25f + 0.75f * (leaf + Flora.unit(each, 0)) / leaves;
                float side = way + leaf * 2.39996f;
                geometry.addStrip(mesh, frame, Grass.cos(side), Grass.sin(side), 0f, stemUp * share, 0.9f + 0.5f * Flora.unit(each, 4),
                        0.5f + 0.6f * Flora.unit(each, 8), high * (0.12f + 0.08f * Flora.unit(each, 12)), 0.045f * Math.min(broadens, 2f), 0.6f * (Flora.unit(each, 16) - 0.5f),
                        Math.min(steps, 2), SpriteGeometry.POINTED, Grass.STRAW, Flora.unit(each, 14), 0.1f);
            }
        }
    }
}
