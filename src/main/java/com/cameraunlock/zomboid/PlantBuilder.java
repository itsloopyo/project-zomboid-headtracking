package com.cameraunlock.zomboid;

import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;

/**
 * What grows in a chunk being built: the grass, the flowers and the litter strewn on its ground, and the boles and
 * crowns of its trees. One to a frame of the world's picture, and it writes into that frame's meshes.
 */
final class PlantBuilder {
    private static final int LONG_TUFTS = 13;
    /** How far either side of where it stands a tuft of long grass far off reaches, in tiles. */
    private static final float LONG_TUFT_HALF = 0.34f;
    /** How far from a bush's middle its outer cards stand, in tiles. */
    private static final float BUSH_SPREAD = 0.16f;
    /** How far from the line of a wall or a fence on the edge of its square a plant beside it ends, in tiles. */
    private static final float ROOM_CLEAR = WorldScene.WALL_HALF + 0.02f;
    /** A bole is no thinner or thicker than this, in tiles, whatever its sprite says. */
    private static final float BOLE_THINNEST = 0.05f, BOLE_THICKEST = 0.4f;
    /**
     * Where up the part of a tree's sprite that is its bole the bole's width is read, as shares
     * of the way up it: a tree in leaf's below its boughs, and an evergreen's just over its foot,
     * where it is seen under the needles; and how thick an evergreen's is at most.
     */
    private static final float[] LEAFY_ROWS = {0.2f, 0.45f}, EVERGREEN_ROWS = {0.04f, 0.08f, 0.12f};
    private static final float EVERGREEN_THICKEST = 0.14f, EVERGREEN_HIDDEN = 0.08f;
    /** How high over its foot an evergreen's needles start, in tiles: below it its sprite paints its shadow on the ground. */
    private static final float EVERGREEN_FOOT = 0.3f;
    /**
     * Ivy on the ground round a tree: how far from the bole's middle it reaches at least and how
     * much further at most, how far past its square it may spread onto open ground beside it, and
     * how far in from an edge of the square it keeps where it may not.
     */
    private static final float GROUND_IVY_LEAST = 0.55f, GROUND_IVY_MORE = 0.5f, GROUND_IVY_SPILL = 0.6f, GROUND_IVY_IN = 0.03f;
    private static final float GRASS_SPILL = 0.35f;
    /** How far in from a side where the lawn ends its plants keep, in tiles. */
    private static final float LAWN_KEEPS_IN = 0.05f;
    /**
     * Grass spread over bare ground stands up where it covers more of it than the first of these, and
     * grows as thick and as well as on its own square where it covers the second. The picture
     * crumbles its edge round half, with noise the plants cannot follow, so it starts thin enough
     * that hardly a blade stands on the ground the crumbs bare.
     */
    private static final float SPREAD_GROWS = 0.45f, SPREAD_THRIVES = 0.9f;
    private static final int CROWN_LEAST_PLACES = 12;
    /**
     * A fern the game paints: its size for half the width its sprite paints, at least and at most,
     * and how many fronds it has. Round, it reaches out its radius both ways along the screen's
     * width, which is 1.41 of the box half boxOf reads off that width.
     */
    private static final float FERN_OF_HALF = 1.55f, FERN_SMALLEST = 0.3f, FERN_LARGEST = 1.2f;
    private static final int PAINTED_FRONDS = 6;
    /** The least share of its leaves a deciduous tree too far off for a crown has on it and keeps the game's sprite of its leaves. */
    private static final float FAN_KEPT = 0.5f;

    private final WorldScene world;
    private final WorldScene.Frame build;

    private final float[] crownLeft = new float[Flora.CROWN_BANDS], crownRight = new float[Flora.CROWN_BANDS], crownSizes = new float[Flora.SIZES];
    private final int[] crownCounts = new int[Flora.CROWN_BANDS];
    private final Flora flora = new Flora();
    private final float[] littered = new float[4];
    /** The square being strewn: how deep in leaf and in needles each corner is as a build lays them, see Litter. */
    private final float[] leafAt = new float[4], needleAt = new float[4];
    /** A piece of litter being laid: each corner's place on the square, kerb field, place on the map, height, depths and light, and the piece cut to one side of a kerb. */
    private final float[] pieceA = new float[4], pieceB = new float[4], pieceKerb = new float[4];
    private final float[] cutA = new float[8], cutB = new float[8], cutKerb = new float[8];
    private final float[] pieceX = new float[8], pieceY = new float[8], pieceLift = new float[8], pieceLeaf = new float[8], pieceNeedle = new float[8];
    private final float[] pieceRed = new float[8], pieceGreen = new float[8], pieceBlue = new float[8], pieceUnlit = new float[8], pieceDay = new float[8];
    /** The rectangle and texels of the frame as long grass found it, put back once the grass is grown. */
    private final SpriteGeometry.Frame heldTexels = new SpriteGeometry.Frame();
    /** The tree whose bole was stood last: its square, how thick and tall the bole is, and its bark. Its crown, which comes after it, grows limbs from it. */
    private int boleX = Integer.MIN_VALUE, boleY, boleBark;
    private float boleThick, boleTall;
    /** The species of the tree whose bole was stood last (TreeForm), and the outline its trunk sprite paints (Flora.woodShape), or null. */
    private int boleKind = TreeForm.NONE;
    private float[] boleShape;

    /**
     * What a plant's sprite's name says of it, read once a sprite and not at every build of every
     * square it stands on: whether it is a dead tree, a fern, a bush or a leafy weed, an evergreen,
     * a crop (Crops.of), the sprig a tree of it bears, its bark (Flora.barkedAs), the colours it
     * turns (Flora.turnsAs) and its species (TreeForm.kindOf). A sprite with no name is none of
     * these, and bears what an unnamed tree would, which nothing asks of it.
     */
    private record Plant(String name, boolean dead, boolean fern, boolean bushy, boolean stems, boolean evergreen, int crop, int treeSprig, int barkedAs, int turnsAs,
            int kind) {}

    private static final Known<IsoSprite, Plant> plants = new Known<>();

    /** Logic thread, when the world being built is another (WorldScene): its sprites are read afresh. */
    static void forget() {
        plants.clear();
    }

    private Plant plantOf(IsoSprite sprite) {
        String name = sprite.getName();
        Plant plant = plants.get(sprite);
        if (plant != null && plant.name() == name) {
            return plant;
        }
        boolean named = name != null;
        plant = new Plant(name, Flora.deadTree(name), Flora.fernSprite(name),
                named && (name.startsWith("f_bushes") || name.startsWith("vegetation_foliage") || name.startsWith("vegetation_ornamental") || Flora.leafyWeed(name)),
                named && Flora.bushStems(name), Flora.evergreen(name), Crops.of(name), named ? Flora.sprigOf(name, false) : Flora.LINDEN, named ? Flora.barkedAs(name) : Flora.BARKED_AS_ANY,
                named ? Flora.turnsAs(name) : Flora.TURNS_AS_ANY, named ? TreeForm.kindOf(name) : TreeForm.NONE);
        plants.replace(sprite, plant);
        return plant;
    }

    PlantBuilder(WorldScene world, WorldScene.Frame build) {
        this.world = world;
        this.build = build;
    }

    /** How much of the square being grown the lawn grass spreading over it covers: see FloorBuilder.uppermostSpread. */
    private final float[] spreadCover = new float[SpriteGeometry.GRID_COVER];
    /** The ground of a square that grass spreads over: it grows only where the grass covers it, and fewer and thinner towards its edge. */
    private final Grass.Ground spreadGround = new Grass.Ground() {
        @Override
        public float vigour(float a, float b) {
            return build.vigour(a, b) * spreadThrives(spreadCover, a, b);
        }

        @Override
        public boolean grows(float a, float b) {
            return build.grows(a, b) && spreadGrows(spreadCover, a, b);
        }
    };

    /** Whether grass spread over a square grows a plant at (a, b), a place Grass.lawn picked: the more of it the better it thrives there. */
    static boolean spreadGrows(float[] cover, float a, float b) {
        return (Flora.mix(Float.floatToRawIntBits(a), Float.floatToRawIntBits(b), 37) & 0xff) < spreadThrives(cover, a, b) * 256f;
    }

    /** How thick and how well grass spread over a square grows at (a, b), from how much of the square it covers (a SpriteGeometry.GRID_COVER): 0 to 1. */
    static float spreadThrives(float[] cover, float a, float b) {
        float t = Math.clamp((SpriteGeometry.gridCover(cover, a, b) - SPREAD_GROWS) / (SPREAD_THRIVES - SPREAD_GROWS), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Says that what is being added is made by the band's own distance: the build is then another in every other band. */
    private void alikeOnly() {
        build.alike &= 1 << build.building.band;
    }

    /** How deep in fallen leaves the point (a, b) of a square of the chunk being built is. */
    float woodedAt(int localX, int localY, float a, float b) {
        int at = localX * Terrain.CORNERS + localY;
        return (build.wooded[at] * (1f - a) + build.wooded[at + Terrain.CORNERS] * a) * (1f - b)
                + (build.wooded[at + 1] * (1f - a) + build.wooded[at + Terrain.CORNERS + 1] * a) * b;
    }

    private boolean isLawn(int at) {
        return build.groundKind[at] == Terrain.NATURAL && build.lightOpen[at] == 1 && build.groundTexture[at] != null && (build.groundRank[at] - 1) % 64 >= 33;
    }

    /**
     * Grass the game paints flat on the ground stands up near the eye: every square of open
     * grass grows its own plants, each the colour of the ground it stands on. See Grass.
     */
    void growGrass(int x, int y, int localX, int localY) {
        int own = (localX + 1) * WorldScene.LIGHT_SPAN + localY + 1;
        if (!isLawn(own)) {
            growSpread(x, y, localX, localY);
            return;
        }
        // Over more grass a blade may hang past its square. Over a path it is untidy, and
        // through the wall of a house it is wrong, so on a side where the lawn ends the
        // plants keep in from the edge. Where its grass spreads over the next square the lawn
        // goes on, as thick to the line as the grass growing over there, and stops short of
        // a wall or a fence on that line only.
        boolean spreadWest = spreadsTo(own, own - WorldScene.LIGHT_SPAN, localX - 1, localY), spreadEast = spreadsTo(own, own + WorldScene.LIGHT_SPAN, localX + 1, localY);
        boolean spreadNorth = spreadsTo(own, own - 1, localX, localY - 1), spreadSouth = spreadsTo(own, own + 1, localX, localY + 1);
        build.lawnWest = spreadWest || isLawn(own - WorldScene.LIGHT_SPAN);
        build.lawnEast = spreadEast || isLawn(own + WorldScene.LIGHT_SPAN);
        build.lawnNorth = spreadNorth || isLawn(own - 1);
        build.lawnSouth = spreadSouth || isLawn(own + 1);
        float lowA = build.lawnWest ? 0f : LAWN_KEEPS_IN, highA = build.lawnEast ? 1f : 1f - LAWN_KEEPS_IN;
        float lowB = build.lawnNorth ? 0f : LAWN_KEEPS_IN, highB = build.lawnSouth ? 1f : 1f - LAWN_KEEPS_IN;
        if (spreadWest || spreadEast || spreadNorth || spreadSouth) {
            roomFor(x, y, 0);
            lowA = spreadWest ? Math.max(lowA, build.room[0] - x) : lowA;
            highA = spreadEast ? Math.min(highA, build.room[1] - x) : highA;
            lowB = spreadNorth ? Math.max(lowB, build.room[2] - y) : lowB;
            highB = spreadSouth ? Math.min(highB, build.room[3] - y) : highB;
        }
        build.grownLocalX = localX;
        build.grownLocalY = localY;
        build.grownUnder = 0.85f;
        build.frameOf(build.groundObject[own], build.groundTexture[own]);
        // Looking no way, as long grass is grown: a lawn left out behind the view was bare when the head turned to it.
        Grass.lawn(build.geometry, build.laid.get(build.groundTexture[own].getID()), build.frame, x, y, build.camera.eyeX, build.camera.eyeY, 0f, 0f,
                lowA, highA, lowB, highB, build, build.grassDensity(), build.gamesView);
    }

    /** Whether the ground of the square at own spreads over the square next to it at, which is not lawn of its own. */
    private boolean spreadsTo(int own, int at, int localX, int localY) {
        return !isLawn(at) && build.floorBuilder.spreadsOnto(own, at, localX, localY);
    }

    /**
     * Lawn grass the picture spreads over the bare ground of the next square stands up there too,
     * as far as it covers the ground, thinning towards its edge, where a blade or two leans out
     * over the bare ground. Laid flat, it read as paint on it. Its open ground goes on into the
     * squares round as the lawn's does, and it ends as a lawn ends: in from paving or a house,
     * and short of a wall or a fence.
     */
    private void growSpread(int x, int y, int localX, int localY) {
        int at = build.floorBuilder.uppermostSpread(x, y, localX, localY, spreadCover);
        if (at < 0 || !isLawn(at)) {
            return;
        }
        int own = (localX + 1) * WorldScene.LIGHT_SPAN + localY + 1;
        build.lawnWest = spillsTo(own - WorldScene.LIGHT_SPAN);
        build.lawnEast = spillsTo(own + WorldScene.LIGHT_SPAN);
        build.lawnNorth = spillsTo(own - 1);
        build.lawnSouth = spillsTo(own + 1);
        roomFor(x, y, 0);
        float lowA = Math.max(build.lawnWest ? 0f : LAWN_KEEPS_IN, build.room[0] - x), highA = Math.min(build.lawnEast ? 1f : 1f - LAWN_KEEPS_IN, build.room[1] - x);
        float lowB = Math.max(build.lawnNorth ? 0f : LAWN_KEEPS_IN, build.room[2] - y), highB = Math.min(build.lawnSouth ? 1f : 1f - LAWN_KEEPS_IN, build.room[3] - y);
        build.grownLocalX = localX;
        build.grownLocalY = localY;
        build.grownUnder = 0.85f;
        build.frameOf(build.groundObject[at], build.groundTexture[at]);
        Grass.lawn(build.geometry, build.laid.get(build.groundTexture[at].getID()), build.frame, x, y, build.camera.eyeX, build.camera.eyeY, 0f, 0f,
                lowA, highA, lowB, highB, spreadGround, build.grassDensity(), build.gamesView);
    }

    /**
     * What stands in and lies on open ground besides its grass. Under trees the ground is
     * litter: fallen leaf, spread as far out from them as the season says, and needles under
     * the evergreens, with a leaf, a twig or a cone standing proud. Leaf lies on paths and in
     * the gutter as on a lawn, and less of it out in the street. In a wood ferns grow out of
     * it. A lawn has flowers in drifts, and here and there a weed. The litter is laid as far
     * as the land is drawn; the rest only near the eye, where it can be told from the ground
     * it stands on.
     */
    void strew(int x, int y, int localX, int localY, float away2) {
        int own = (localX + 1) * WorldScene.LIGHT_SPAN + localY + 1;
        if (build.lightOpen[own] != 1 || Litter.keeps(build.groundKind[own], true) == 0f) {
            return;
        }
        boolean natural = build.groundKind[own] == Terrain.NATURAL;
        float deepest = 0f;
        for (int corner = 0; corner < 4; corner++) {
            int i = localX + (corner == 1 || corner == 2 ? 1 : 0), j = localY + (corner >= 2 ? 1 : 0);
            float keeps = cornerKeeps(build.groundKind, build.lightOpen, i, j);
            // The floor of a wood is open ground: a corner that touches a road, a path or a roof is no part of one.
            boolean open = true;
            for (int ci = 0; ci <= 1; ci++) {
                for (int cj = 0; cj <= 1; cj++) {
                    int touching = (i + ci) * WorldScene.LIGHT_SPAN + j + cj;
                    open &= build.groundKind[touching] == Terrain.NATURAL && build.lightOpen[touching] == 1;
                }
            }
            leafAt[corner] = build.leaves[i * Terrain.CORNERS + j] * keeps;
            needleAt[corner] = build.needles[i * Terrain.CORNERS + j] * keeps;
            littered[corner] = open ? build.wooded[i * Terrain.CORNERS + j] : 0f;
            deepest = Math.max(deepest, Math.max(leafAt[corner] * Litter.SPREAD_MOST, needleAt[corner]));
        }
        SpriteGeometry.Mesh cards = build.laid.get(WorldScene.PLAIN);
        float[] kerbs = build.kerbs;
        if (deepest > 0.02f) {
            build.layers.lie(SpriteGeometry.LAYERS_UP, build.floorLayer + 1);
            byte kind = build.leafKinds[localX * Terrain.CHUNK_TILES + localY];
            // Where a kerb crosses the square or the gutter runs over it, the litter is laid in pieces, each on its own side of the kerb.
            boolean pieces = kerbs != null && (build.geometry.kerb != null || nearKerb(kerbs, localX, localY));
            int parts = pieces ? LITTER_PIECES : 1;
            for (int p = 0; p < parts; p++) {
                for (int q = 0; q < parts; q++) {
                    layLitter(cards, x, y, localX, localY, (float) p / parts, (float) q / parts, 1f / parts, kerbs, kind);
                }
            }
            build.layers.none();
        }
        float deep = (littered[0] + littered[1] + littered[2] + littered[3]) / 4f;
        boolean tree = build.woods[(localX + Litter.RING) * WorldScene.WOOD_SPAN + localY + Litter.RING] != Litter.NONE;
        if (natural && away2 < WorldScene.FERN_REACH * WorldScene.FERN_REACH && !tree && isLawn(own)) {
            int ferns = Flora.ferns(x, y, deep);
            if (ferns > 0) {
                roomFor(x, y, 0);
            }
            for (int fern = 0; fern < ferns; fern++) {
                int seed = Flora.mix(x, y, 40 + fern);
                float size = 0.4f + 0.3f * Flora.unit(seed, 16), reach = Flora.fernReach(size);
                // Its root is held in from a wall or a fence on the square's edge by as far as its fronds reach.
                float a = Flora.within(0.15f + 0.7f * Flora.unit(seed, 0), reach, build.room[0] - x, build.room[1] - x);
                float b = Flora.within(0.15f + 0.7f * Flora.unit(seed, 8), reach, build.room[2] - y, build.room[3] - y);
                if (build.geometry.kerb != null && build.geometry.onStreet(a, b)) {
                    continue;
                }
                int fronds = away2 < WorldScene.FERN_NEAR * WorldScene.FERN_NEAR ? 5 + (seed >>> 24 & 1) : 4;
                Flora.addFern(cards, x + a, y + b, build.geometry.liftAt(a, b), size, 0f, fronds, seed, build.frame);
            }
        }
        if (away2 < WorldScene.FALLEN_REACH * WorldScene.FALLEN_REACH && deepest > 0.02f) {
            addFallen(cards, x, y, localX, localY, kerbs);
        }
        if (!natural) {
            return;
        }
        if (away2 < WorldScene.FLOWER_REACH * WorldScene.FLOWER_REACH && isLawn(own) && deep < 0.35f) {
            addFlowers(cards, x, y, 0.1f, 0.14f, 0.022f);
            if (Flora.weed(x, y)) {
                int seed = Flora.mix(x, y, 70);
                float a = 0.2f + 0.6f * Flora.unit(seed, 0), b = 0.2f + 0.6f * Flora.unit(seed, 8);
                if (build.geometry.kerb != null && build.geometry.onStreet(a, b)) {
                    return;
                }
                for (int leaf = 0; leaf < Flora.WEED_LEAVES; leaf++) {
                    int each = Flora.mix(seed, leaf, 71);
                    float length = 0.13f + 0.07f * Flora.unit(each, 0);
                    Flora.addLeaf(cards, x + a, y + b, build.geometry.liftAt(a, b) + 0.01f, length, length * 0.5f,
                            (leaf + 0.6 * Flora.unit(each, 8)) * 2.0 * Math.PI / Flora.WEED_LEAVES, Math.toRadians(15.0 + 25.0 * Flora.unit(each, 16)),
                            Flora.WEED, 0, 0.05f, build.frame);
                }
            }
        }
    }

    /**
     * Lays the piece of the square's litter size across from (a, b) of it. Where a kerb runs
     * across the piece, each side of the kerb's line is laid at its own height, the street's a
     * kerb below the land's.
     */
    private void layLitter(SpriteGeometry.Mesh cards, int x, int y, int localX, int localY, float a, float b, float size, float[] kerbs, byte kind) {
        for (int corner = 0; corner < 4; corner++) {
            pieceA[corner] = a + (corner == 1 || corner == 2 ? size : 0f);
            pieceB[corner] = b + (corner >= 2 ? size : 0f);
            pieceKerb[corner] = kerbs == null ? -1f : Kerbs.at(kerbs, localX + pieceA[corner], localY + pieceB[corner]);
        }
        if (build.geometry.kerb == null) {
            layPiece(cards, x, y, 4, pieceA, pieceB, pieceKerb, kind, build.geometry.kerbStreet, false);
            return;
        }
        for (int side = 0; side < 2; side++) {
            boolean street = side == 0;
            int count = Litter.side(4, pieceA, pieceB, pieceKerb, street, cutA, cutB, cutKerb);
            if (count >= 3) {
                layPiece(cards, x, y, count, cutA, cutB, cutKerb, kind, street, true);
            }
        }
    }

    /** Lays a polygon of the square's litter, its corners at (a, b) of the square with their kerb field: on the street's side of the kerb or the land's, where sided says one crosses the square. */
    private void layPiece(SpriteGeometry.Mesh cards, int x, int y, int count, float[] a, float[] b, float[] kerb, byte kind, boolean street, boolean sided) {
        float shows = 0f;
        for (int corner = 0; corner < count; corner++) {
            float at = a[corner], bt = b[corner];
            float gutter = Litter.gutter(kerb[corner]);
            pieceX[corner] = x + at;
            pieceY[corner] = y + bt;
            pieceLeaf[corner] = across(leafAt, at, bt) * gutter;
            pieceNeedle[corner] = across(needleAt, at, bt) * gutter;
            float up = across(build.geometry.lift, at, bt);
            pieceLift[corner] = sided && street != build.geometry.kerbStreet ? up + (street ? -Terrain.KERB : Terrain.KERB) : up;
            pieceRed[corner] = across(build.frame.cornerRed, at, bt);
            pieceGreen[corner] = across(build.frame.cornerGreen, at, bt);
            pieceBlue[corner] = across(build.frame.cornerBlue, at, bt);
            pieceUnlit[corner] = build.frame.unlitAt((1f - at) * (1f - bt), at * (1f - bt), at * bt, (1f - at) * bt);
            pieceDay[corner] = build.frame.dayAt((1f - at) * (1f - bt), at * (1f - bt), at * bt, (1f - at) * bt);
            shows = Math.max(shows, Math.max(pieceLeaf[corner] * Litter.SPREAD_MOST, pieceNeedle[corner]));
        }
        if (shows > 0.02f) {
            Flora.addLitter(cards, count, pieceX, pieceY, pieceLift, pieceLeaf, pieceNeedle, kind, pieceRed, pieceGreen, pieceBlue, pieceUnlit, pieceDay, build.frame, a, b);
        }
    }

    /**
     * How much of what falls at the corner (i, j) of a chunk's squares stays there: as little
     * as the least of the four squares it touches keeps (Litter.keeps), so that a square and its
     * neighbour agree on it. kinds and open are WorldScene.Frame's groundKind and lightOpen.
     */
    static float cornerKeeps(byte[] kinds, byte[] open, int i, int j) {
        float keeps = 1f;
        for (int ci = 0; ci <= 1; ci++) {
            for (int cj = 0; cj <= 1; cj++) {
                int touching = (i + ci) * WorldScene.LIGHT_SPAN + j + cj;
                keeps = Math.min(keeps, Litter.keeps(kinds[touching], open[touching] == 1));
            }
        }
        return keeps;
    }

    /** How many pieces a side the litter of a square a kerb crosses, or whose gutter runs over it, is laid in. */
    static final int LITTER_PIECES = 4;

    /** Whether the street of the chunk's square (localX, localY) is within a gutter's width of a kerb. */
    static boolean nearKerb(float[] kerbs, int localX, int localY) {
        boolean street = false, gutter = false;
        for (int corner = 0; corner < 4; corner++) {
            float kerb = Kerbs.at(kerbs, localX + (corner == 1 || corner == 2 ? 1 : 0), localY + (corner >= 2 ? 1 : 0));
            street |= kerb > 0f;
            gutter |= kerb < Litter.GUTTER_ENDS;
        }
        return street && gutter;
    }

    /** A value at the point (a, b) of a square, from its corners in the order of SpriteGeometry.lift. */
    private static float across(float[] corners, float a, float b) {
        return (corners[0] * (1f - a) + corners[1] * a) * (1f - b) + (corners[3] * (1f - a) + corners[2] * a) * b;
    }

    /**
     * The fallen leaves, twigs and cones that stand proud of the litter of a square near the
     * eye: as many leaves as the leaf is deep at the end of autumn, each shown from its own step
     * of the season's spread, in the colours of the tree that dropped most of it, and cones, or
     * holly's leaves, as deep as the needles are.
     */
    private void addFallen(SpriteGeometry.Mesh cards, int x, int y, int localX, int localY, float[] kerbs) {
        byte kind = build.leafKinds[localX * Terrain.CHUNK_TILES + localY];
        for (int card = 0; card < Litter.CARDS_MOST; card++) {
            int seed = Flora.mix(x, y, 50 + card);
            float a = 0.05f + 0.9f * Flora.unit(seed, 0), b = 0.05f + 0.9f * Flora.unit(seed, 8);
            float cover = across(leafAt, a, b) * (kerbs == null ? 1f : Litter.gutter(Kerbs.at(kerbs, localX + a, localY + b)));
            int step = Litter.cardStep(x, y, card, cover);
            if (step == 0) {
                continue;
            }
            double turn = Flora.unit(seed, 16) * 2.0 * Math.PI;
            float tall = Flora.unit(seed, 24);
            if (card == 0 && (seed & 3) == 0) {
                // A twig lies all year where the floor of a wood is leaf all year.
                if (step <= Litter.stepOf(Litter.SPREAD_SUMMER)) {
                    Flora.addLeaf(cards, x + a, y + b, build.geometry.liftAt(a, b) + 0.03f, 0.22f + 0.2f * tall, 0.035f, turn, Math.toRadians(3.0 + 5.0 * tall),
                            Flora.TWIG, 0, 0f, build.frame);
                }
                continue;
            }
            float length = 0.09f + 0.05f * tall;
            Flora.addLeaf(cards, x + a, y + b, build.geometry.liftAt(a, b) + 0.02f, length, length * 0.7f, turn, Math.toRadians(8.0 + 22.0 * tall),
                    Litter.leafPick(kind, Flora.unit(Flora.mix(x, y, 170 + card), 0)), step, 0f, build.frame);
        }
        boolean holly = build.needleKinds[localX * Terrain.CHUNK_TILES + localY] == Litter.HOLLY;
        int cones = Litter.cones(x, y, across(needleAt, 0.5f, 0.5f));
        for (int cone = 0; cone < cones; cone++) {
            int seed = Flora.mix(x, y, 180 + cone);
            float a = 0.1f + 0.8f * Flora.unit(seed, 0), b = 0.1f + 0.8f * Flora.unit(seed, 8);
            if (kerbs != null && Kerbs.at(kerbs, localX + a, localY + b) > 0f) {
                continue;
            }
            double turn = Flora.unit(seed, 16) * 2.0 * Math.PI;
            float size = 0.06f + 0.03f * Flora.unit(seed, 24);
            Flora.addLeaf(cards, x + a, y + b, build.geometry.liftAt(a, b) + 0.015f, size, size * (holly ? 0.75f : 0.55f), turn, Math.toRadians(holly ? 10.0 : 4.0),
                    holly ? Flora.FALLEN_BROWN : Flora.CONE, 0, 0f, build.frame);
        }
    }

    /** The flowers of a square of grass: heads at least lowest above the ground and up to taller more, the smallest of them least across. */
    private void addFlowers(SpriteGeometry.Mesh cards, int x, int y, float lowest, float taller, float least) {
        int flowers = Flora.flowers(x, y);
        for (int flower = 0; flower < flowers; flower++) {
            int seed = Flora.mix(x, y, 60 + flower);
            float a = 0.08f + 0.84f * Flora.unit(seed, 0), b = 0.08f + 0.84f * Flora.unit(seed, 8);
            if (build.geometry.kerb != null && build.geometry.onStreet(a, b)) {
                continue;
            }
            Flora.addFlower(cards, x + a, y + b, build.geometry.liftAt(a, b), lowest + taller * Flora.unit(seed, 16), least * (1f + 0.65f * Flora.unit(seed, 24)),
                    Flora.unit(seed, 4) * 2.0 * Math.PI, Flora.flowerKind(x, y, flower), (seed >>> 12) % Flora.FLOWER_THINNINGS, build.frame);
        }
    }

    private boolean spillsTo(int at) {
        return build.groundKind[at] == Terrain.NATURAL && build.lightOpen[at] == 1;
    }

    boolean growLongGrass(int x, int y) {
        int own = (build.waterLocalX + 1) * WorldScene.LIGHT_SPAN + build.waterLocalY + 1;
        if (build.groundTexture[own] == null) {
            return false;
        }
        float originU = build.frame.originU, originV = build.frame.originV;
        heldTexels.texelsOf(build.frame);
        build.frameOf(build.groundObject[own], build.groundTexture[own]);
        // Nor does it spill through a wall or a fence on the square's edge, and its tufts keep to their own side of one.
        roomFor(x, y, 0);
        float roomLowA = build.room[0] - x, roomHighA = build.room[1] - x, roomLowB = build.room[2] - y, roomHighB = build.room[3] - y;
        float lowA = spillsTo(own - WorldScene.LIGHT_SPAN) && roomLowA < 0f ? -GRASS_SPILL : 0.12f, highA = spillsTo(own + WorldScene.LIGHT_SPAN) && roomHighA > 1f ? 1f + GRASS_SPILL : 0.88f;
        float lowB = spillsTo(own - 1) && roomLowB < 0f ? -GRASS_SPILL : 0.12f, highB = spillsTo(own + 1) && roomHighB > 1f ? 1f + GRASS_SPILL : 0.88f;
        SpriteGeometry.Mesh mesh = build.laid.get(build.groundTexture[own].getID());
        float away = build.bandAway;
        // Near the eye it is grown plant by plant. Far off, where a blade is finer than the
        // picture can draw, it is cards with the blades cut out of them, and between the two
        // the cards rise out of the ground as the plants sink into it.
        // Past where the plants end and the flowers too, it is the same cards from any distance.
        if (build.within(Grass.RANK_ENDS, false) | build.within(WorldScene.FLOWER_REACH, false)) {
            alikeOnly();
        }
        if (away < Grass.RANK_ENDS) {
            build.lawnWest = build.lawnEast = build.lawnNorth = build.lawnSouth = true;
            build.grownLocalX = build.waterLocalX;
            build.grownLocalY = build.waterLocalY;
            build.grownUnder = 0.7f;
            // Grown as from an eye the band's distance off and looking no way: none of it is left out as behind the view.
            Grass.rank(build.geometry, mesh, build.frame, x, y, x + 0.5f - away, y + 0.5f, 0f, 0f, lowA, highA, lowB, highB, build, build.grassDensity());
        }
        float carded = Math.clamp((away - Grass.RANK_THINS) / (Grass.RANK_ENDS - Grass.RANK_THINS), 0f, 1f);
        // Fewer cards with fewer plants, and never none: a clump of long grass is still there to be seen.
        int tufts = Math.max(1, Math.round(LONG_TUFTS * build.grassDensity()));
        for (int tuft = 0; tuft < tufts && carded > 0f; tuft++) {
            int hash = x * 19349663 ^ y * 83492791 ^ tuft * 73856093;
            hash = (hash ^ hash >>> 13) * 0x5bd1e995;
            hash ^= hash >>> 15;
            // A clump of long grass is not the shape of the square the game put it on. Towards
            // open ground its tufts spill a third of a square past the edge, shorter the
            // further out they stand, so a patch has a ragged outline. Towards a road, a
            // path or a floor it keeps inside.
            double turn = (hash >>> 16 & 0xff) / 255.0 * Math.PI;
            float cos = (float) Math.cos(turn), sin = (float) Math.sin(turn);
            float a = Flora.within(lowA + (highA - lowA) * (hash & 0xff) / 255f, Math.abs(cos) * LONG_TUFT_HALF, roomLowA, roomHighA);
            float b = Flora.within(lowB + (highB - lowB) * (hash >>> 8 & 0xff) / 255f, Math.abs(sin) * LONG_TUFT_HALF, roomLowB, roomHighB);
            float out = Math.max(Math.max(-a, a - 1f), Math.max(-b, b - 1f));
            float tall = (0.45f + 0.5f * (hash >>> 24 & 0xff) / 255f) * (out > 0f ? 1f - 1.8f * out : 1f) * carded;
            float vigour = 1f - 0.7f * woodedAt(build.waterLocalX, build.waterLocalY, Math.clamp(a, 0f, 1f), Math.clamp(b, 0f, 1f));
            build.geometry.addTuft(mesh, build.frame, x, y, a, b, cos, sin, LONG_TUFT_HALF, tall, SpriteGeometry.LONG_GRASS, vigour);
        }
        if (away < WorldScene.FLOWER_REACH) {
            // Flowers in long grass stand as tall as it does.
            addFlowers(build.laid.get(WorldScene.PLAIN), x, y, 0.4f, 0.35f, 0.03f);
        }
        build.frame.originU = originU;
        build.frame.originV = originV;
        build.frame.texelsOf(heldTexels);
        return true;
    }

    /**
     * Stands what a garden's sprite (Flora.gardenPlant) paints higher than its square's far corner:
     * the plant grown up out of its bed, which laid on the bed with its furrows was a bed of sprouts.
     * What is painted lower is the bed, and lies on it (FloorBuilder). The game paints dug furrows and
     * a seedling no higher, and they stand nothing. The bed is laid after, in the frame's own material.
     */
    void addOutOfItsBed(SpriteGeometry.Mesh mesh, IsoSprite sprite, IsoObjectType type, Texture texture, int x, int y, int z) {
        SpriteGeometry.Frame frame = build.frame;
        float bed = (frame.top + frame.height - SpriteGeometry.frameV(frame, 0f, 0f, 0f)) / frame.height;
        if (bed >= 1f) {
            return;
        }
        float material = frame.material, red = frame.red;
        int site = frame.site;
        build.geometry.skipped = Math.max(bed, 0f);
        try {
            addGrown(mesh, sprite, type, texture, x, y, z);
        } finally {
            build.geometry.skipped = 0f;
            frame.material = material;
            frame.red = red;
            frame.site = site;
        }
    }

    /**
     * Grows the roses of a bed the game paints on the ground (Flowerbeds) out of it, near enough
     * the eye. Nothing until its sprite has been read: the build is then made again.
     */
    void addRoses(Texture texture, int x, int y, int z) {
        if (build.within(Flowerbeds.REACH, false)) {
            alikeOnly();
        }
        if (build.bandAway >= Flowerbeds.REACH) {
            return;
        }
        Flowerbeds.Bed bed = Flowerbeds.of(texture);
        if (bed == null) {
            build.building.waitFor(texture);
            return;
        }
        roomFor(x, y, z);
        // Roses go by the season: the build is made again as the season moves on.
        build.building.seasonal = true;
        long now = Seasons.now();
        int season = Seasons.seasonOf(now);
        Flowerbeds.grow(build.geometry, build.laid.get(texture.getID()), build.frame, bed, x, y, build.bandAway, build.room, season,
                Seasons.stepped(season, Seasons.progressionOf(now)));
        Flowerbeds.pebbles(flora, build.geometry, build.solid.get(WorldScene.PLAIN), build.frame, bed, x, y, z * ViewCamera.LEVEL_HEIGHT, build.bandAway);
    }

    /**
     * A tree or a bush is not a picture of one that turns to face whoever looks at it.
     * It stands in the world: the sprite is set up several times through the plant's
     * middle, fanned round it, every other one mirrored, so that from any side there
     * are leaves in front of leaves and they pass each other as the eye moves. Which
     * way the fan is turned comes from where the plant stands, so no two are alike.
     */
    void addGrown(SpriteGeometry.Mesh mesh, IsoSprite sprite, IsoObjectType type, Texture texture, int x, int y, int z) {
        if (type != IsoObjectType.tree) {
            addGrownOn(mesh, sprite, type, texture, x, y, z);
            return;
        }
        treeTile = sprite.getName();
        treeX = x;
        treeY = y;
        // Everything stood for a tree is its tree's: see SpriteGeometry.VERTEX_FLOATS.
        SpriteGeometry.Mesh trunks = build.solid.get(WorldScene.PLAIN), ivy = build.laid.get(WorldScene.PLAIN);
        mesh.rooted(x, y);
        trunks.rooted(x, y);
        ivy.rooted(x, y);
        try {
            addGrownOn(mesh, sprite, type, texture, x, y, z);
        } finally {
            mesh.unrooted();
            trunks.unrooted();
            ivy.unrooted();
        }
    }

    private void addGrownOn(SpriteGeometry.Mesh mesh, IsoSprite sprite, IsoObjectType type, Texture texture, int x, int y, int z) {
        int cards = type == IsoObjectType.tree ? 5 : 3;
        // A tree's bole is round. A tree in leaf shows nothing else of its trunk's sprite,
        // so the bole stands in for all its cards. A bare one, or a dead one, goes on to have
        // its branches made in wood as well (see bare below).
        Plant plant = plantOf(sprite);
        String grown = plant.name();
        boolean dead = plant.dead();
        if (build.overLayer == 0) {
            bushPlaces = null;
            bushOnStems = false;
        }
        if ((type == IsoObjectType.tree || dead) && build.overLayer == 0 && Own.plants(build.playerIndex) && addBole(sprite, texture, x, y, z) && build.geometry.keep < 1f) {
            return;
        }
        // A fern the game paints is a fern of the mod's own, as big as the painted one.
        if (plant.fern() && Own.plants(build.playerIndex) && addFern(x, y, z)) {
            return;
        }
        roomFor(x, y, z);
        // A tree's cards are its whole crown, which over a wall's top reaches across it: only its crown of sprigs is kept to its room.
        if (type != IsoObjectType.tree) {
            build.geometry.roomLowA = build.room[0] - x;
            build.geometry.roomHighA = build.room[1] - x;
            build.geometry.roomLowB = build.room[2] - y;
            build.geometry.roomHighB = build.room[3] - y;
        }
        // A bush is leaves all over, as a tree's crown is.
        // A bush's leaves are smaller than a tree's.
        boolean bush = type != IsoObjectType.tree && plant.bushy();
        // So is every other plant the game paints with a dark line round each leaf: the
        // line is what makes it a drawing. A tree's trunk is left as it is, and a dead tree is bark.
        if (grown != null && type != IsoObjectType.tree && Own.plants(build.playerIndex)) {
            build.frame.material = dead ? WorldScene.BARK : bush ? Flora.BUSH : WorldScene.LEAVES;
        }
        // A deciduous tree's leaves go through autumn and come back through spring, each tree
        // on days of its own (Seasons). One the season has bare never gets here: see bareBySeason.
        boolean leafy = grown != null && type == IsoObjectType.tree && build.overLayer > 0 && Own.plants(build.playerIndex);
        int treeHash = Seasons.treeHash(x, y);
        boolean seasonal = leafy && !plant.evergreen();
        float leaf = seasonal ? Seasons.leafOn(treeHash) : 1f;
        // The leaves of a tree and a bush, near enough to be seen as leaves, are a crown of
        // sprigs. The fan then stands only in the sun's own pictures, to throw the shadow.
        float fanned = build.frame.material, red = build.frame.red, green = build.frame.green, blue = build.frame.blue;
        int site = build.frame.site;
        // A crop's cards stand as tall as the crop, seen from the eye. From overhead they stand as tall as painted, the game's picture of it.
        boolean cropCards = plant.crop() != Crops.NONE && !world.terrain.level;
        build.geometry.upright = cropCards ? Crops.cardShare(build.frame, build.box) : 1f;
        // A crop near the eye is grown stalk by stalk, and its cards then only throw its shadow,
        // or stand lower among the stalks where those are thinned (Crops.amongStalks).
        if (Own.plants(build.playerIndex) && growCrop(texture, plant.crop(), x, y)) {
            float among = cropCards ? Crops.amongStalks(plant.crop(), build.bandAway) : 0f;
            if (among > 0f) {
                build.geometry.upright *= among;
            } else {
                Flora.shadeOnly(build.frame, 1f);
            }
        }
        // An evergreen has no leaves of its own to lay over its trunk: its sprite paints the needles with the trunk, and is its crown.
        boolean evergreen = type == IsoObjectType.tree && build.overLayer == 0 && build.geometry.keep == 1f && plant.evergreen();
        // A tree with no leaves, dead or bare for the winter, has a crown of wood alone, grown from its bole.
        boolean bare = build.overLayer == 0 && build.geometry.keep == 1f && (type == IsoObjectType.tree && !plant.evergreen() || dead) && boleX == x && boleY == y;
        int sprig = bare ? Flora.BARE : bush ? Flora.SHRUB : plant.treeSprig();
        boolean crowned = grown != null && (bush || type == IsoObjectType.tree && build.overLayer > 0 || evergreen || bare) && Own.plants(build.playerIndex)
                && addCrown(texture, plant, sprig, x, y, z, leaf, seasonal ? Flora.autumnHue(plant.turnsAs(), Flora.otherHue(treeHash)) : Flora.YELLOW_LEAF, seasonal ? Seasons.turned(treeHash) : 0f);
        if (crowned) {
            Flora.shadeOnly(build.frame, leaf);
        }
        // A second plant on the square turns its fan half a card from the first's: the game paints
        // two crops on one square of a dense field, and fans in the same planes fight for depth.
        // What the game draws over a plant (a tree's leaves) stays in its plant's planes.
        if (build.overLayer == 0) {
            build.plantsHere++;
        }
        double turn = ((x * 73856093 ^ y * 19349663) >>> 8 & 0xff) / 255.0 * Math.PI + Math.max(build.plantsHere - 1, 0) * Math.PI / (2 * cards);
        for (int card = 0; card < cards; card++) {
            double angle = turn + card * Math.PI / cards;
            float side = (card & 1) == 0 ? 1f : -1f;
            float alongX = (float) Math.cos(angle), alongY = (float) Math.sin(angle);
            // Leaves lie on the same card as their trunk, and each layer has to stand
            // clear of the one under it on the side the eye is on, square to this card:
            // a move straight towards the eye does nothing for a card seen at a slant.
            // Which side that is, the picture finds as it draws: see PipelineShaders.FACED.
            build.geometry.clear = build.overLayer;
            build.geometry.addUpright(mesh, build.frame, x, y, z, 0.5f, 0.5f, 0f, side * alongX, side * alongY, false, true);
            build.geometry.clear = 0f;
        }
        build.frame.material = fanned;
        build.frame.red = red;
        build.frame.green = green;
        build.frame.blue = blue;
        build.frame.site = site;
        if (bush && !crowned && Own.plants(build.playerIndex) && build.within(WorldScene.BUSH_REACH, false)) {
            // A fan of cards through one line is thin seen along that line, and from
            // above it is a star. A bush is a clump: three more cards stand round its
            // middle, each across the way out to it, so there are leaves on every side.
            for (int outer = 0; outer < 3; outer++) {
                double out = turn + (outer + 0.5) * 2.0 * Math.PI / 3.0;
                float outX = (float) Math.cos(out), outY = (float) Math.sin(out);
                float side = (outer & 1) == 0 ? 1f : -1f;
                build.geometry.nudgeX = outX * BUSH_SPREAD;
                build.geometry.nudgeY = outY * BUSH_SPREAD;
                build.geometry.addUpright(mesh, build.frame, x, y, z, 0.5f, 0.5f, 0f, -outY * side, outX * side, false, true);
            }
        }
        build.geometry.nudgeX = build.geometry.nudgeY = 0f;
        build.geometry.upright = 1f;
        build.geometry.roomLowA = build.geometry.roomLowB = -Float.MAX_VALUE;
        build.geometry.roomHighA = build.geometry.roomHighB = Float.MAX_VALUE;
    }

    /**
     * Grows the crop the frame's sprite paints, if it is one and near enough: see Crops. False,
     * and nothing grown, for no crop, one too far off, and one whose sprite is not read yet.
     */
    private boolean growCrop(Texture texture, int crop, int x, int y) {
        if (crop == Crops.NONE) {
            return false;
        }
        // A square the game paints two of one crop on is grown once: a second growing would stand
        // every stalk again in the first's place, its own height and colour, the two fighting for depth.
        if ((build.cropsGrownHere & 1 << crop) != 0) {
            return true;
        }
        if (build.within(Crops.REACH, false)) {
            alikeOnly();
        }
        if (build.bandAway >= Crops.REACH || !SpriteGeometry.boxOf(build.frame, build.box)) {
            return false;
        }
        float[] places = WorldScene.cropColours.get(texture);
        if (places == null) {
            places = Flora.inside(Flora.leafPlaces(texture::isMaskSet, build.frame), texture::isMaskSet);
            if (places.length < CROWN_LEAST_PLACES * 2) {
                return false;
            }
            places = WorldScene.cropColours.keep(texture, places);
        }
        float lowA = Math.max(0.04f, build.room[0] - x), highA = Math.min(0.96f, build.room[1] - x);
        float lowB = Math.max(0.04f, build.room[2] - y), highB = Math.min(0.96f, build.room[3] - y);
        Crops.grow(build.geometry, build.laid.get(texture.getID()), build.frame, places, crop, x, y, Crops.tall(build.box[3]), build.bandAway, lowA, highA, lowB, highB);
        build.cropsGrownHere |= 1 << crop;
        return true;
    }

    /**
     * Stands a fern where the frame's sprite paints one, its fronds reaching as far as the
     * painted fern does. False for a sprite whose outline says too little to size it by.
     */
    private boolean addFern(int x, int y, int z) {
        if (!SpriteGeometry.boxOf(build.frame, build.box)) {
            return false;
        }
        float size = Math.clamp(build.box[2] * FERN_OF_HALF, FERN_SMALLEST, FERN_LARGEST), reach = Flora.fernReach(size);
        roomFor(x, y, z);
        float a = Flora.within(Math.clamp(build.box[0], 0.2f, 0.8f), reach, build.room[0] - x, build.room[1] - x);
        float b = Flora.within(Math.clamp(build.box[1], 0.2f, 0.8f), reach, build.room[2] - y, build.room[3] - y);
        int seed = Flora.mix(x, y, 85);
        Flora.addFern(build.laid.get(WorldScene.PLAIN), x + a, y + b, z * ViewCamera.LEVEL_HEIGHT + build.geometry.liftAt(a, b), size, build.box[3] * ViewCamera.LEVEL_HEIGHT,
                PAINTED_FRONDS + (seed >>> 24 & 1), seed, build.frame);
        return true;
    }

    /**
     * Whether the tree on the square, its trunk's sprite named so, has none of its leaves
     * drawn whatever the game hangs on it: a deciduous one the season has bare, or one too far
     * off for a crown and more than half bare, whose leaves would be the game's whole sprite.
     * It is drawn as a tree the game has bare is, with a crown of wood alone (Flora.BARE).
     */
    boolean bareBySeason(String name, int x, int y) {
        if (name == null || !Own.plants(build.playerIndex) || Flora.evergreen(name)) {
            return false;
        }
        // What the build makes of this tree goes by the season: it is made again as the season moves on.
        build.building.seasonal = true;
        float leaf = Seasons.leafOn(Seasons.treeHash(x, y));
        return leaf == 0f || leaf < FAN_KEPT && !world.crownedToSight && !build.within(Crowns.REACH, true);
    }

    /**
     * Sets room to the room of a plant on the square (see Flora.ROOM_UNDER): its own side of
     * a wall or a fence on any edge of the square. The other side, and a room indoors behind
     * a house's wall, shows nothing of it.
     */
    private void roomFor(int x, int y, int z) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        build.room[0] = WorldScene.edged(square, false) ? x + ROOM_CLEAR : -Float.MAX_VALUE;
        build.room[1] = WorldScene.edged(build.cell.getGridSquare(x + 1, y, z), false) ? x + 1f - ROOM_CLEAR : Float.MAX_VALUE;
        build.room[2] = WorldScene.edged(square, true) ? y + ROOM_CLEAR : -Float.MAX_VALUE;
        build.room[3] = WorldScene.edged(build.cell.getGridSquare(x, y + 1, z), true) ? y + 1f - ROOM_CLEAR : Float.MAX_VALUE;
    }

    /**
     * Stands a crown of sprigs for the plant whose leaves the frame is set to, in the mesh of
     * the leaves' own texture, which gives the sprigs their colour. False when the plant is
     * too far off for one or its texture shows too little to make one of. leaf, hue and turned
     * are Flora.addCrown's.
     */
    private boolean addCrown(Texture texture, Plant plant, int sprig, int x, int y, int z, float leaf, int hue, float turned) {
        boolean bush = sprig == Flora.SHRUB;
        if (!world.crownedToSight && !build.within(Crowns.REACH, true)) {
            return false;
        }
        float away = build.bandAway;
        float[] places = WorldScene.leafPlaces.get(texture);
        if (places == null) {
            places = Flora.leafPlaces(texture::isMaskSet, build.frame);
            // An evergreen's sprite paints the shadow it throws on the ground round its foot, which is no needles.
            if (!bush && plant.evergreen()) {
                places = Flora.above(places, build.frame, SpriteGeometry.frameV(build.frame, 0.5f, 0.5f, 0f), EVERGREEN_FOOT);
            }
            // A texture that shows nothing yet may only not have its outline read yet.
            if (places.length >= CROWN_LEAST_PLACES * 2) {
                places = WorldScene.leafPlaces.keep(texture, places);
            }
        }
        boolean few = places.length < CROWN_LEAST_PLACES * 2;
        // The game paints a bush in layers over each other: its stems, its leaves, then its flowers or
        // berries. The layer that reaches furthest so far is its shape, and a smaller one over it, or
        // one too sparse to be a crown, is a bloom on that crown. Made of its own places, a bloom was
        // a crown inside the leaves, hidden by them.
        float spread = bush ? Flora.spread(places) : 0f;
        // A bush's bare stems are wood: made a crown of their own, in the stems' colour, they were a brown sack round the bush
        // whatever leaves the season hung on it. They stand as canes, and the leaves' crown grows out along them.
        if (bush && plant.stems() && build.overLayer == 0) {
            if (!few) {
                bushPlaces = places;
                bushSpread = spread;
                bushX = x;
                bushY = y;
                bushZ = z;
                bushOnStems = true;
            }
            return false;
        }
        boolean sameBush = bush && build.overLayer > 0 && bushPlaces != null && bushX == x && bushY == y && bushZ == z;
        boolean bloom = sameBush && (bushOnStems ? few : few || spread < bushSpread);
        boolean onStems = sameBush && bushOnStems;
        bushOnStems = false;
        if (few && !bloom) {
            return false;
        }
        if (bloom) {
            places = bushPlaces;
        } else if (onStems) {
            // The leaves over a bush's stems leave most of the stems bare, a few leaves on long canes: they grow out along every stem.
            places = Flora.with(places, bushPlaces);
        }
        if (!bloom && bush) {
            bushPlaces = places;
            bushSpread = spread;
            bushX = x;
            bushY = y;
            bushZ = z;
        }
        // The bole stood last may be another tree's, in another chunk: only its own tree's crown grows limbs from it.
        boolean boled = !bush && boleX == x && boleY == y;
        // A young tree, of the erosion's own size (FarTreeKinds.SMALL), is a few leaves on a stem, which the game paints
        // leaf by leaf as it does a bush's: overhead its crown is painted as a bush's is. Laid with sprigs it was a ball of leaves to the ground.
        String tile = treeX == x && treeY == y ? treeTile : null;
        boolean painted = bush || tile != null && FarTreeKinds.speciesOf(tile) != null && FarTreeKinds.sizeOf(tile) == FarTreeKinds.SMALL;
        // A grown tree's crown has no places on the wood its sprite paints. Until the sprite is read it
        // has them, and the chunk is built again once it has been.
        if (!painted) {
            float[] leaves = CrownWood.leaves(texture, build.frame, places);
            if (leaves == null) {
                build.building.waitFor(texture);
            } else if (leaves.length < CROWN_LEAST_PLACES * 2) {
                return false;
            } else {
                places = leaves;
            }
        }
        int seed = Flora.mix(x, y, 83);
        float base = z * ViewCamera.LEVEL_HEIGHT + build.geometry.liftAt(0.5f, 0.5f) + build.geometry.raised * ViewCamera.LEVEL_HEIGHT;
        float footU = SpriteGeometry.frameU(build.frame, 0.5f, 0.5f), footV = SpriteGeometry.frameV(build.frame, 0.5f, 0.5f, 0f);
        int bark = Flora.barkOf(plant.barkedAs(), seed);
        if (!bush && sprig != Flora.BARE) {
            seenForFar(texture, places, x, y, footU, footV, boled ? boleThick : BOLE_THINNEST);
            seenForLeafFall(places, x, y, footU, footV);
        }
        if (build.crownsInline) {
            boolean limbed = boled && Crowns.limbed(away, sprig, leaf);
            return Flora.addCrown(build.solid.get(texture.getID()), build.solid.get(WorldScene.PLAIN), build.frame, places, x + 0.5f, y + 0.5f, base, footU, footV, Crowns.detail(away), Crowns.close(away),
                    limbed ? boleThick : 0f, limbed ? boleTall : 0f, limbed ? boleBark : bark, sprig, seed, build.room, leaf, hue, turned,
                    boled ? boleKind : TreeForm.NONE, boled ? boleShape : null, world.twiggedFrom, painted, world.terrain.level, bloom);
        }
        if (!Flora.measure(build.frame, places, footU, footV, !bush, crownLeft, crownRight, crownCounts, crownSizes)) {
            return false;
        }
        (build.afresh ? WorldScene.crownsChecked : build.crownsRead).add(texture.getID(), build.frame, places, x + 0.5f, y + 0.5f, base, footU, footV, away, boled ? boleThick : 0f, boled ? boleTall : 0f,
                boled ? boleBark : bark, bark, sprig, seed, build.room, leaf, hue, turned, boled ? boleKind : TreeForm.NONE, boled ? boleShape : null,
                world.twiggedFrom, painted, world.terrain.level, bloom);
        return true;
    }

    /** The places of the widest-reaching layer of the bush whose crown was stood last, how far they reach (Flora.spread), and its square: a smaller layer over it there blooms on that crown. */
    private float[] bushPlaces;
    private float bushSpread;
    /** Whether bushPlaces are the bare stems' of the bush (Flora.bushStems), which have no crown of their own: the leaves over them make it. */
    private boolean bushOnStems;
    private int bushX, bushY, bushZ;

    /** The tile of the tree whose trunk was stood last, and its square: a crown is its tree's when it is on the same square. */
    private String treeTile;
    private int treeX = Integer.MIN_VALUE, treeY;

    /** Notes how a tree the built world stands looks, for the far trees of its kind (FarTreeLooks), when that kind has not been noted lately. */
    private void seenForFar(Texture leaves, float[] places, int x, int y, float footU, float footV, float bole) {
        String tile = treeX == x && treeY == y ? treeTile : null;
        long now = System.nanoTime();
        if (tile != null && FarTreeLooks.wants(tile, now) && Flora.measure(build.frame, places, footU, footV, true, crownLeft, crownRight, crownCounts, crownSizes)) {
            FarTreeLooks.saw(tile, crownSizes, crownLeft, crownRight, bole, leaves, now);
        }
    }

    /** Notes the crown of a broad-leaved tree's tile the first time one is built, which is where its falling leaves let go from. */
    private void seenForLeafFall(float[] places, int x, int y, float footU, float footV) {
        String tile = treeX == x && treeY == y ? treeTile : null;
        if (tile != null && LeafFall.sheds(tile) && LeafTrees.crownWanted(tile) && Flora.measure(build.frame, places, footU, footV, true, crownLeft, crownRight, crownCounts, crownSizes)) {
            LeafTrees.crownSeen(tile, crownSizes);
        }
    }

    /**
     * Stands a boulder on the square, as big as its sprite's outline says it is. False for a
     * sprite whose outline is too flat to say: a stone lying in the grass, which stays as it is.
     */
    boolean addRock(int x, int y, int z) {
        if (!SpriteGeometry.boxOf(build.frame, build.box)) {
            return false;
        }
        float a = Math.clamp(build.box[0], 0.2f, 0.8f), b = Math.clamp(build.box[1], 0.2f, 0.8f);
        flora.addRock(build.solid.get(WorldScene.PLAIN), x + a, y + b, z * ViewCamera.LEVEL_HEIGHT + build.geometry.liftAt(a, b), Math.clamp(build.box[2] * 1.15f, 0.15f, 0.6f),
                Math.clamp(build.box[3] * ViewCamera.LEVEL_HEIGHT, 0.12f, 1.4f), build.within(WorldScene.ROCK_NEAR, false), Flora.mix(x, y, 80), 0f, build.frame);
        return true;
    }

    /**
     * Stands a round bole where a tree's sprite shows its trunk, with ivy up some. False when
     * the tree is too far off for one, or its sprite has no bole that can be read.
     */
    private boolean addBole(IsoSprite sprite, Texture texture, int x, int y, int z) {
        if (!world.crownedToSight && !build.within(WorldScene.TRUNK_REACH, true)) {
            return false;
        }
        Plant plant = plantOf(sprite);
        float[] bole = WorldScene.boles.get(sprite);
        if (bole == null) {
            bole = measureBole(texture, plant.evergreen());
            bole = WorldScene.boles.keep(sprite, bole);
            if (bole.length > 0 && !plant.evergreen()) {
                Trunks.stood(sprite, bole[0]);
            }
        }
        if (bole.length == 0) {
            return false;
        }
        boolean near = build.within(WorldScene.TRUNK_NEAR, false);
        int seed = Flora.mix(x, y, 81);
        float base = z * ViewCamera.LEVEL_HEIGHT + build.geometry.liftAt(0.5f, 0.5f);
        boleBark = Flora.barkOf(plant.barkedAs(), seed);
        boleKind = TreeForm.withoutForms ? TreeForm.NONE : plant.kind();
        boleShape = boleKind == TreeForm.NONE ? null : woodShape(sprite, texture);
        // Where its crown will show its wood (see Crowns.limbed), a broad-leaved tree's limbs leave the bole lower than
        // where its leaves begin, and a birch or a hawthorn is stems from a short foot, ending in a low dome they go on up from.
        boolean wooded = boleShape != null && (world.crownedToSight || build.within(Crowns.REACH, true))
                && (build.geometry.keep == 1f || Seasons.leafOn(Seasons.treeHash(x, y)) < Flora.WOOD_SHOWS || build.within(Crowns.LIMB_REACH, false));
        boolean footed = wooded && TreeForm.footed(boleKind);
        float thick = footed ? bole[0] * TreeForm.FOOT_THICK : bole[0];
        float tall = footed ? bole[1] * TreeForm.FOOT_TALL : wooded ? bole[1] * TreeForm.UNDER_WOOD : bole[1];
        flora.addTrunk(build.solid.get(WorldScene.PLAIN), x + 0.5f, y + 0.5f, base, thick, tall, near, !footed, boleBark, seed, build.frame);
        boleX = x;
        boleY = y;
        boleThick = thick;
        boleTall = tall;
        if (near && Flora.ivied(x, y)) {
            flora.addIvy(build.laid.get(WorldScene.PLAIN), x + 0.5f, y + 0.5f, base, thick, tall, tall * (0.5f + 0.5f * Flora.unit(seed, 16)), seed, build.frame);
            addGroundIvy(x, y, z, thick, seed);
        }
        return true;
    }

    /**
     * The outline of the branches the tree sprite the frame is set to paints, as Flora.woodShape
     * measures it, kept by sprite: what a broad-leaved tree's wood is grown to from its first
     * lost leaf to bare. Null, and nothing kept, while the sprite's outline is not read yet.
     */
    private float[] woodShape(IsoSprite sprite, Texture texture) {
        float[] known = WorldScene.woodShapes.get(sprite);
        if (known != null) {
            return known;
        }
        float[] places = WorldScene.leafPlaces.get(texture);
        if (places == null) {
            places = Flora.leafPlaces(texture::isMaskSet, build.frame);
            if (places.length < CROWN_LEAST_PLACES * 2) {
                return null;
            }
            places = WorldScene.leafPlaces.keep(texture, places);
        }
        float[] shape = Flora.woodShape(build.frame, places, SpriteGeometry.frameU(build.frame, 0.5f, 0.5f), SpriteGeometry.frameV(build.frame, 0.5f, 0.5f, 0f));
        if (shape != null) {
            shape = WorldScene.woodShapes.keep(sprite, shape);
        }
        return shape;
    }

    /**
     * Ivy over the ground round the foot of a tree whose bole it climbs (Flora.addGroundIvy).
     * It keeps to the tree's square, and spreads onto a square beside it only where that is open
     * natural ground on this side of any wall or fence: none on a path, a road or a floor.
     */
    private void addGroundIvy(int x, int y, int z, float radius, int seed) {
        int own = (build.waterLocalX + 1) * WorldScene.LIGHT_SPAN + build.waterLocalY + 1;
        roomFor(x, y, z);
        float lowX = Math.max(spillsTo(own - WorldScene.LIGHT_SPAN) ? x - GROUND_IVY_SPILL : x + GROUND_IVY_IN, build.room[0]);
        float highX = Math.min(spillsTo(own + WorldScene.LIGHT_SPAN) ? x + 1f + GROUND_IVY_SPILL : x + 1f - GROUND_IVY_IN, build.room[1]);
        float lowY = Math.max(spillsTo(own - 1) ? y - GROUND_IVY_SPILL : y + GROUND_IVY_IN, build.room[2]);
        float highY = Math.min(spillsTo(own + 1) ? y + 1f + GROUND_IVY_SPILL : y + 1f - GROUND_IVY_IN, build.room[3]);
        float reach = GROUND_IVY_LEAST + GROUND_IVY_MORE * Flora.unit(seed, 24);
        Flora.addGroundIvy(build.laid.get(WorldScene.PLAIN), x + 0.5f, y + 0.5f, x, y, z * ViewCamera.LEVEL_HEIGHT, build.geometry.lift, radius, reach,
                lowX, highX, lowY, highY, seed, build.frame);
    }

    /**
     * Reads a bole off the tree sprite the frame is set to: how tall it is, which is as much of
     * the sprite as a tree in leaf shows, and how thick, which is the narrowest of what is
     * painted across the sprite at a few heights (see LEAFY_ROWS). NO_BOLE for a sprite painted
     * right across at those heights, which is no trunk, and for one painted at none; an
     * evergreen's needles hide a thin trunk instead.
     */
    private float[] measureBole(Texture texture, boolean evergreen) {
        float topRow = build.frame.top + build.frame.height * (1f - WorldScene.BOLE);
        float footRow = Math.min(SpriteGeometry.frameV(build.frame, 0.5f, 0.5f, 0f), build.frame.top + build.frame.height - 1f);
        float tall = SpriteGeometry.heightDrawnAt(build.frame, 0.5f, 0.5f, topRow) * ViewCamera.LEVEL_HEIGHT;
        if (tall < 0.3f) {
            return WorldScene.NO_BOLE;
        }
        int narrowest = Integer.MAX_VALUE;
        // An evergreen's needles come down to the ground: its trunk shows only just over its foot, under them.
        float[] rows = evergreen ? EVERGREEN_ROWS : LEAFY_ROWS;
        for (float share : rows) {
            int v = Math.round(footRow + (topRow - footRow) * share);
            int first = -1, last = -1;
            for (int u = (int) build.frame.left; u < build.frame.left + build.frame.width; u++) {
                if (texture.isMaskSet(u, v)) {
                    if (first < 0) {
                        first = u;
                    }
                    last = u;
                }
            }
            if (first >= 0) {
                narrowest = Math.min(narrowest, last - first + 1);
            }
        }
        if (narrowest == Integer.MAX_VALUE || narrowest >= build.frame.width * 0.6f) {
            // An evergreen whose needles sweep the ground still has a trunk up its middle, hidden by them.
            return evergreen ? new float[] {EVERGREEN_HIDDEN, tall} : WorldScene.NO_BOLE;
        }
        float radius = narrowest / 2f * SpriteGeometry.TILES_PER_PIXEL_ACROSS / build.frame.scale;
        return new float[] {Math.clamp(radius, BOLE_THINNEST, evergreen ? EVERGREEN_THICKEST : BOLE_THICKEST), tall};
    }
}
