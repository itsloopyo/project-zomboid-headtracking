package com.cameraunlock.zomboid;

import com.cameraunlock.zomboid.SpriteGeometry.Mesh;
import java.util.Arrays;
import java.util.HashMap;

/**
 * What grows on open ground in the first-person picture and what lies on it, where the game
 * has only a flat painted square or a flat painted card: the round bole of a tree and its
 * roots, ivy up it, boulders, ferns, weeds and flowers, fallen leaves and twigs.
 *
 * Everything here is worked out from where a square is on the map, so the same plants stand in
 * the same places every time the square is built, and nothing is kept between builds.
 */
final class Flora {
    /**
     * What a vertex is made of, as SpriteGeometry.Frame.material carries it: numbers above
     * those of the mod's furniture, which end at 51. The world shader has the same numbers.
     */
    static final float LITTER = 56f, TRUNK = 57f, IVY = 58f, ROCK = 59f, FROND = 60f, LEAF = 61f, FLOWER = 62f, BUSH = 63f;

    /**
     * Which leaf a LEAF card is. The card's texture coordinate across carries it in the twos
     * above the 0 to 1 it runs over, and its coordinate up carries in the same way from which
     * step of Litter's spread a fallen leaf shows: see Litter.cardStep. A fallen leaf in its
     * tree's autumn colour is FALLEN_HUE and that colour's place among HUES after it.
     */
    static final int WEED = 0, FALLEN_BROWN = 1, TWIG = 2, CONE = 3, FALLEN_HUE = 4;
    /** Which flower a FLOWER card is, carried the same way, or that it is a flower's stalk. */
    static final int WHITE = 0, YELLOW = 1, PURPLE = 2, STALK = 3;
    /**
     * Flowers are left out of the picture with distance, each from one of this many steps of
     * it. A FLOWER card's texture coordinate up carries which, in the twos above the 0 to 1 it
     * runs over. The world shader has the same number.
     */
    static final int FLOWER_THINNINGS = 8;
    private static final float PICK_STEP = 2f;

    /** How far from a tree the shade and the floor of a wood reach, in tiles. */
    static final float WOOD_REACH = 3.2f;
    /** How many squares either way of a corner its trees are looked for: all that are within WOOD_REACH of it. */
    static final int WOOD_RING = 3;
    private static final float WOOD_GAIN = 0.9f;

    static final int FLOWERS_MOST = 4, FERNS_MOST = 2, WEED_LEAVES = 5;
    /** Flowers come in drifts this many squares across. */
    private static final int DRIFT = 4;

    static final int TRUNK_SIDES = 12, TRUNK_SIDES_FAR = 6;
    /** How far below the ground a trunk and a boulder start, in tiles: the ground under them slopes. */
    private static final float SUNK = 0.12f;
    /** How much thinner a bole is at its top than at its foot, and how far past its top it runs on to a point, as a share of its height. */
    private static final float TAPER = 0.78f;
    static final float SPIRE = 0.45f;
    /** How much of a tile one repeat of the bark's or the ivy's picture covers, give or take what makes it fit round the bole. */
    private static final float BARK_SPAN = 0.45f, IVY_SPAN = 0.55f;
    /**
     * The barks, in the order the pictures of them are laid one behind another in the jar's
     * one texture of them, which tools/textures packs and a test holds to BARKS. The first is
     * also the grain of a tree left as the game's sprite.
     */
    static final int BARK_OAK = 0, BARK_LINDEN = 1, BARK_MAPLE = 2, BARK_BIRCH = 3, BARK_PINE = 4, BARK_HEMLOCK = 5, BARK_HOLLY = 6, BARK_DOGWOOD = 7;
    static final String[] BARKS = {"oak", "linden", "maple", "birch", "pine", "hemlock", "holly", "dogwood"};
    /**
     * A bole's texture coordinates carry more than where on the bark's picture a vertex is,
     * in whole steps above it that are the same all over one bole. Across: BARK_ACROSS for
     * each quarter of the picture past the first that goes round the bole, BARK_WRAPS_MOST
     * of them more for a picture laid upside down, and twice that for each of the
     * BARK_SHIFTS places it may start from. Up: BARK_UP for each bark after the first. The
     * world shader has the same numbers.
     */
    static final float BARK_ACROSS = 8f, BARK_UP = 32f;
    static final int BARK_WRAPS_MOST = 16, BARK_SHIFTS = 4;
    private static final float IVY_STANDS_OFF = 0.02f;
    /**
     * Ivy on the ground round a bole: how many ways round and rings out it is made of, how far
     * clear of the bark it starts, how far over the ground it lies and how much higher against
     * the bole, and how much of the way to nothing it thins at its rim. What thins it also lets
     * the wind stir it, so its rim never takes the whole wind a plant's top does.
     */
    private static final int GROUND_IVY_SIDES = 14, GROUND_IVY_RINGS = 3;
    private static final float GROUND_IVY_STANDS_OFF = 0.01f, GROUND_IVY_LIFT = 0.02f, GROUND_IVY_HEAP = 0.04f, GROUND_IVY_THINS = 0.7f;
    /** Where across the frond's picture its stem meets the bottom edge. The picture is packed so. */
    static final float FROND_ROOT = 0.75f;
    /** A frond's width over its length, which is the shape of its picture. */
    private static final float FROND_WIDE = 0.83f;
    private static final float FROND_SWAY = 0.25f;

    private static final int RINGS = 4;
    private final float[] ringX = new float[RINGS * (TRUNK_SIDES + 1)], ringY = new float[RINGS * (TRUNK_SIDES + 1)];
    private final float[] ringSlope = new float[RINGS * (TRUNK_SIDES + 1)];
    private final float[] ringUp = new float[RINGS], ringRun = new float[RINGS];
    private final float[] rock = new float[BALL.length];

    /** A number that is the same every time for one place and one purpose, and unlike its neighbours'. */
    static int mix(int x, int y, int salt) {
        int hash = x * 73856093 ^ y * 19349663 ^ salt * 83492791;
        hash = (hash ^ hash >>> 13) * 0x5bd1e995;
        return hash ^ hash >>> 15;
    }

    /** Eight bits of a mixed number as 0 to 1. */
    static float unit(int hash, int shift) {
        return (hash >>> shift & 0xff) / 255f;
    }

    /**
     * How far into a wood each corner of a chunk is, 0 on open ground and 1 in a wood, the
     * same all year: where ferns grow and flowers do not.
     *
     * trees says what tree, if any, stands on each square: the chunk's and Litter.RING more on
     * every side, x first. A corner takes something from every tree within WOOD_REACH of it, the
     * more the nearer. Two chunks that share a corner see the same trees round it and give it
     * the same depth.
     */
    static void wooded(byte[] trees, int tiles, float[] corners) {
        int span = tiles + 2 * Litter.RING, across = tiles + 1;
        for (int i = 0; i < across; i++) {
            for (int j = 0; j < across; j++) {
                float sum = 0f;
                for (int di = -WOOD_RING; di < WOOD_RING; di++) {
                    for (int dj = -WOOD_RING; dj < WOOD_RING; dj++) {
                        if (trees[(i + Litter.RING + di) * span + j + Litter.RING + dj] == Litter.NONE) {
                            continue;
                        }
                        float awayX = di + 0.5f, awayY = dj + 0.5f;
                        float near = 1f - (float) Math.sqrt(awayX * awayX + awayY * awayY) / WOOD_REACH;
                        if (near > 0f) {
                            sum += near * near;
                        }
                    }
                }
                corners[i * across + j] = Math.min(sum * WOOD_GAIN, 1f);
            }
        }
    }

    /** How many flowers stand on a square of grass: none on most, a few where a drift of them lies. */
    static int flowers(int x, int y) {
        int drift = mix(Math.floorDiv(x, DRIFT), Math.floorDiv(y, DRIFT), 11);
        int own = mix(x, y, 12);
        if (unit(drift, 0) < 0.3f) {
            return 1 + (own >>> 8 & 3);
        }
        return unit(own, 0) < 0.15f ? 1 : 0;
    }

    /** Which flower the square's flower number index is: mostly the one its drift is made of. */
    static int flowerKind(int x, int y, int index) {
        int drift = mix(Math.floorDiv(x, DRIFT), Math.floorDiv(y, DRIFT), 11);
        int own = mix(x, y, 20 + index);
        return Math.floorMod(unit(own, 16) < 0.75f ? drift >>> 8 : own >>> 8, 3);
    }

    /** Whether a broad-leaved weed grows on a square of grass. */
    static boolean weed(int x, int y) {
        return unit(mix(x, y, 13), 0) < 0.12f;
    }

    /** How many ferns stand on a square as deep in a wood as wooded says. */
    static int ferns(int x, int y, float wooded) {
        float chance = unit(mix(x, y, 14), 0);
        return chance < 0.22f * wooded ? 2 : chance < 0.6f * wooded ? 1 : 0;
    }

    /** Whether ivy grows up the tree on a square. */
    static boolean ivied(int x, int y) {
        return unit(mix(x, y, 16), 0) < 0.4f;
    }

    /**
     * A crown's sprigs: CROWN and the six numbers after it, one for each kind of sprig in the
     * picture of them, and SHADE for what is only there to throw the crown's shadow. The world
     * and shadow shaders have the same numbers.
     */
    static final float CROWN = 72f, SHADE = 80f;

    /**
     * Sets the frame to make what is stood next throw a shadow and be in no other picture
     * (SHADE), as much shadow as a mass of leaves with that share of its leaves on it throws.
     * The share rides in the vertex's red, which only the sun's pictures read: see
     * PipelineShaders.SHADOW_VERTEX. The caller puts the frame's red back after.
     */
    static void shadeOnly(SpriteGeometry.Frame frame, float leaves) {
        frame.material = SHADE;
        frame.red = leaves;
    }
    /**
     * The inside of a crown: a closed mass of leaves in shade, within the sprigs, so that a
     * look through the crown ends on leaves and not on the sky behind the tree. A real crown
     * is leaves all the way in, and only its rim is seen through. The world and shadow shaders
     * have the same number.
     */
    static final float CORE = 81f;
    /**
     * How far out the inside of a crown comes, as a share of the crown's own reach, between
     * which shares of its height it stands, how much of the crown's light gets to it, how
     * far it draws in at its foot as a share of how far out it is there, and how many rings
     * it is made of and how many faces round.
     */
    private static final float CORE_OF_REACH = 0.74f, CORE_FOOT = 0.06f, CORE_HEAD = 0.95f, CORE_SHADE = 0.6f, CORE_UNDER = 0.3f;
    private static final int CORE_RINGS = 6, CORE_ROUND = 10;
    /** How many kinds of sprig a vertex of the inside can say it is among: its texture coordinate's part over the whole number is the kind's place in so many. */
    static final float CORE_KINDS = 8f;
    /** The sprigs in their picture, four to a row. */
    static final int LINDEN = 0, MAPLE = 1, BIRCH = 2, PINE = 3, HEMLOCK = 4, HOLLY = 5, SHRUB = 6;
    /**
     * Not a sprig: the crown of a tree with no leaves, dead or bare in winter, which is its wood
     * alone, limbs out to where its sprite paints branches and twigs off them.
     */
    static final int BARE = 7;
    /** How many places are tried on a plant's sprite for where it paints leaves. */
    private static final int CROWN_TRIES = 2400;
    /** A sprig's texture coordinate holds where on the plant's sprite it takes its colour, in this many steps across the texture, under where on the sprig the vertex is. */
    static final float CROWN_STEPS = 2048f;
    /**
     * The colours a crown's leaves turn in autumn, one of the HUES, and in how many steps they
     * turn towards it, from green to brown. Its sprigs and its inside carry both in the square of
     * their tree, as a fraction of one over or under its whole number y, AUTUMN_CODES codes of
     * 1 / AUTUMN_SPACING each, centred on it: well inside the quarter of a square what reads a
     * tree's square allows (PipelineShaders' uFaded test), and whole steps of a float at any
     * square the map has. The world shader takes them apart: see PipelineShaders.AUTUMN.
     */
    static final int YELLOW_LEAF = 0, GOLD_LEAF = 1, ORANGE_LEAF = 2, SCARLET_LEAF = 3, CLARET_LEAF = 4, HUES = 5, TURNS = 8;
    static final int AUTUMN_CODES = HUES * TURNS;
    static final float AUTUMN_SPACING = 128f;
    /** A crown with less of its leaves on it than this has no inside: it is seen through. Between this and full leaf the inside draws in. */
    private static final float CORE_GOES = 0.5f;
    /** A crown with less of its leaves on it than this has a bare tree's wood in it, at any distance: see Crowns.limbed. */
    static final float WOOD_SHOWS = 0.9f;
    /** A sprig's corners are this far in from the whole numbers either side, so that neither end of it rounds into the next step. */
    private static final float CROWN_INSET = 0.25f;
    private static final float SPRIG_SMALLEST = 0.3f, SPRIG_LARGEST = 0.8f, SHRUB_SMALLEST = 0.2f, SHRUB_LARGEST = 0.42f;
    private static final float CROWN_FILL = 15f;
    private static final int CROWN_LEAST = 12;
    /**
     * A crown is not one round mass but several boughs, each a mass of its own with hollows
     * between: how far in from the crown's reach the deepest hollow lies, as a share of the
     * reach, how much darker it is there than on a bough's top (0.7 of the way up a swell
     * the boughs change nothing), and how much more the light
     * is made of the swell than its true slope gives.
     */
    private static final float BOUGH_HOLLOW = 0.34f, BOUGH_SHADE = 0.3f, BOUGH_RELIEF = 1.4f;
    /**
     * Near the eye a crown is looked into and not at, and what is between its sprigs shows:
     * how many more sprigs it has then, as a share of what it has from further off, all of
     * them inside the crown, where a place on the sprite may bear a second; and the share
     * of its sprigs that are borne on a twig, which is how long for the sprig's size, how
     * thick where it leaves the bough and at its tip, and how far it drops for its length.
     */
    private static final float CLOSE_FILL = 1.3f, TWIG_SHARE = 0.45f, TWIG_LONG = 0.45f, TWIG_THICK = 0.01f, TWIG_TIP = 0.004f,
            TWIG_DROP = 0.45f;
    /**
     * The wood inside a crown: a leader up its middle, as thin at its top as LIMB_TIP, and
     * limbs off it out to the boughs, each as thick where it leaves as LIMB_OF_LEADER of the
     * leader there, with branches off each to the leaves either side.
     */
    static final float LEADER_TOP = 0.62f, LIMB_TIP = 0.014f, LIMB_OF_LEADER = 0.7f, LIMB_OUT = 0.8f, BRANCH_OF_LIMB = 0.5f, BRANCH_TIP = 0.008f;
    /**
     * A bare tree's limbs and branches end this far out of its outline, its twigs carrying on
     * past them; how many twigs each branch has; and from what detail (see addCrown) on it has them.
     */
    private static final float BARE_OUT = 0.9f, BARE_TWIGGED = 0.5f, TWIGLETS = 0.95f;
    private static final int BARE_TWIGS = 3;
    /** How many sticks a square of fallen wood has at least: up to three more. */
    private static final int FALLEN_STICKS_LEAST = 2;
    private static final int LEADER_SIDES = 6, LIMB_SIDES = 5, BRANCH_SIDES = 4, TWIG_SIDES = 3, LIMB_PIECES = 4, LEADER_PIECES = 4;
    /**
     * Wood is not straight: how far a leader wanders aside at each joint, as a share of the
     * crown's height; how far a limb or a branch kinks aside at each joint, and how much a
     * branch bows up, as shares of its length.
     */
    private static final float WAVER = 0.025f, KINK = 0.16f, BOW = 0.06f;
    /** The longest a piece of a branch near the eye runs straight, as a share of the crown's height: twice as long far off. */
    private static final float STRAIGHT_MOST = 0.12f;
    /** From this detail (see addCrown) on wood is bent at every joint, and its twigs as many as its species has. */
    private static final float NEAR_BENDS = 0.9f;
    /** Dev only, each a saving in what bare wood costs, on unless switched off to set it beside the wood without it: see WorldScene.without. */
    static volatile boolean fineOutOfShadow = true, nearTwiglets = true, piecesByDistance = true, twigsByDistance = true;
    /** How far up the bark's picture wood in a crown starts: clear of the moss a bole has at its foot. */
    private static final float WOOD_UP = 3f;
    /**
     * A leaf sprite also paints, faintly, the shade the crown throws on the trunk: a thin
     * column under the leaves that is no part of the crown. Going down from where the sprite
     * is widest, the crown ends where it is first narrower than this share of that.
     */
    private static final float TRUNK_SHADE = 0.22f;
    /** The share of a sprite's places that are given a sprig at most: with more, the choosing of them towards the outside is lost. */
    private static final float CROWN_MOST_OF_PLACES = 0.6f;
    /** How far a sprig's tip moves in the wind beyond what its height gives it. */
    private static final float SPRIG_FLUTTER = 0.05f;
    /**
     * How much of the wind a sprig takes for each tile it is above the plant's foot, and at most.
     * Far less than the game's whole card takes: a sprig is leaves a few pixels across, and
     * the picture is steadied over several frames, so leaves that travel far in a frame are a smear.
     */
    private static final float SWAY_PER_TILE = 0.08f, SWAY_MOST = 0.3f;
    /**
     * A plant's room, lowest x, highest x, lowest y and highest y on the map: beside a wall or a
     * fence, its own side of it. What of a crown is lower over its foot than ROOM_UNDER, a
     * wall's top, stays in it however the wind sways it, and what is higher reaches over.
     */
    static final float ROOM_UNDER = ViewCamera.LEVEL_HEIGHT;
    /** The room of a plant with nothing round it. */
    static final float[] OPEN = {-Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE};

    /**
     * Where a plant's sprite paints leaves: frame pixels across and down, two numbers a place,
     * spread evenly over what is painted. Empty for a sprite painted nowhere.
     */
    static float[] leafPlaces(SpriteShape.Painted painted, SpriteGeometry.Frame frame) {
        int across = Math.max(1, Math.round((float) Math.sqrt(CROWN_TRIES * frame.width / Math.max(frame.height, 1f))));
        int down = Math.max(1, CROWN_TRIES / across);
        float[] found = new float[across * down * 2];
        int count = 0;
        for (int i = 0; i < across; i++) {
            for (int j = 0; j < down; j++) {
                int hash = mix(i, j, 90);
                float u = frame.left + (i + unit(hash, 0)) * frame.width / across, v = frame.top + (j + unit(hash, 8)) * frame.height / down;
                if (painted.at((int) u, (int) v)) {
                    found[count++] = u;
                    found[count++] = v;
                }
            }
        }
        return Arrays.copyOf(found, count);
    }

    /** The places of leafPlaces at least least tiles over the foot, which the sprite shows at the frame row footV. */
    static float[] above(float[] places, SpriteGeometry.Frame frame, float footV, float least) {
        float up = ViewCamera.LEVEL_HEIGHT / SpriteGeometry.PIXELS_PER_LEVEL / frame.scale;
        float[] kept = new float[places.length];
        int count = 0;
        for (int i = 0; i < places.length; i += 2) {
            if ((footV - places[i + 1]) * up >= least) {
                kept[count++] = places[i];
                kept[count++] = places[i + 1];
            }
        }
        return Arrays.copyOf(kept, count);
    }

    /**
     * The places of leafPlaces painted on every side as well: inside what the sprite paints, off
     * the dark line the game draws round everything and off the pale glint along its edges.
     */
    static float[] inside(float[] places, SpriteShape.Painted painted) {
        float[] kept = new float[places.length];
        int count = 0;
        for (int i = 0; i < places.length; i += 2) {
            int u = (int) places[i], v = (int) places[i + 1];
            if (painted.at(u - 1, v) && painted.at(u + 1, v) && painted.at(u, v - 1) && painted.at(u, v + 1)) {
                kept[count++] = places[i];
                kept[count++] = places[i + 1];
            }
        }
        return Arrays.copyOf(kept, count);
    }

    /**
     * The outline a crown's sprite paints, for addCrown's woodShape: its SIZES, then how far it
     * reaches left and right at each of CROWN_BANDS heights. Null when it shows too little to
     * make a crown of.
     */
    static float[] woodShape(SpriteGeometry.Frame frame, float[] places, float footU, float footV) {
        float[] shape = new float[SIZES + 2 * CROWN_BANDS], left = new float[CROWN_BANDS], right = new float[CROWN_BANDS];
        if (!measure(frame, places, footU, footV, left, right, shape)) {
            return null;
        }
        System.arraycopy(left, 0, shape, SIZES, CROWN_BANDS);
        System.arraycopy(right, 0, shape, SIZES + CROWN_BANDS, CROWN_BANDS);
        return shape;
    }

    /** What measure leaves of a crown's size: how wide it is, its lowest and highest leaves over the foot, how tall, and the height under which the sprite paints only trunk. */
    static final int WIDEST = 0, LOWEST = 1, HIGHEST = 2, TALL = 3, UNDER = 4, SIZES = 5;
    static final int CROWN_BANDS = 12;

    /**
     * Measures the crown addCrown would make of the frame: its sizes, and how far out it
     * reaches at each of CROWN_BANDS heights, half of how wide the sprite paints it there.
     * False when the sprite shows too little to make a crown of.
     */
    static boolean measure(SpriteGeometry.Frame frame, float[] places, float footU, float footV, float[] reachLeft, float[] reachRight, float[] sizes) {
        int count = places.length / 2;
        if (count < CROWN_LEAST) {
            return false;
        }
        float across = SpriteGeometry.TILES_PER_PIXEL_ACROSS / frame.scale;
        float up = ViewCamera.LEVEL_HEIGHT / SpriteGeometry.PIXELS_PER_LEVEL / frame.scale;
        float widest = 0f, lowest = 0f, highest = 0f, tall = 0f, under = -Float.MAX_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            widest = 0f;
            lowest = Float.MAX_VALUE;
            highest = -Float.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                float high = (footV - places[i * 2 + 1]) * up;
                if (high < under) {
                    continue;
                }
                widest = Math.max(widest, Math.abs(places[i * 2] - footU) * across);
                lowest = Math.min(lowest, high);
                highest = Math.max(highest, high);
            }
            if (widest < 0.05f) {
                return false;
            }
            tall = Math.max(highest - lowest, 1e-3f);
            Arrays.fill(reachLeft, 0f);
            Arrays.fill(reachRight, 0f);
            for (int i = 0; i < count; i++) {
                float high = (footV - places[i * 2 + 1]) * up;
                if (high < under) {
                    continue;
                }
                int band = Math.min((int) ((high - lowest) / tall * CROWN_BANDS), CROWN_BANDS - 1);
                float side = (places[i * 2] - footU) * across;
                reachLeft[band] = Math.max(reachLeft[band], -side);
                reachRight[band] = Math.max(reachRight[band], side);
            }
            int broadest = 0, from = 0;
            for (int band = 1; band < CROWN_BANDS; band++) {
                if (reachLeft[band] + reachRight[band] > reachLeft[broadest] + reachRight[broadest]) {
                    broadest = band;
                }
            }
            for (int band = broadest - 1; band >= 0 && from == 0; band--) {
                if (reachLeft[band] + reachRight[band] < TRUNK_SHADE * (reachLeft[broadest] + reachRight[broadest])) {
                    from = band + 1;
                }
            }
            if (pass == 1 || from == 0) {
                break;
            }
            under = lowest + tall * from / CROWN_BANDS;
        }
        sizes[WIDEST] = widest;
        sizes[LOWEST] = lowest;
        sizes[HIGHEST] = highest;
        sizes[TALL] = tall;
        sizes[UNDER] = under;
        return true;
    }

    /**
     * A crown of leaves in place of the sprite that paints one: sprigs growing outward all
     * round the plant's middle, each a card with a branch tip in leaf cut out of it, standing
     * where the sprite turned about the plant's upright shows leaves and taking its colour from
     * the sprite there. So the crown has the game's shape, size and season, and from any side
     * is leaves in front of leaves against the sky.
     *
     * places is leafPlaces of the frame; (x, y, base) the plant's foot, which the sprite shows
     * at the frame pixel (footU, footV). detail is 1 for a plant near the eye and less for one
     * further off, which is made of fewer and larger sprigs that cover as much. close is 0
     * for a plant looked at from outside and 1 for one stood under: by as much, it has more
     * sprigs inside it and twigs to bear them, made in wood in the bark that is bark's
     * number. A tree whose bole is boleRadius thick at its top, boleTall over its foot, has
     * a leader and limbs in wood as well: 0 for a plant with no bole, or too far off for them.
     * False, and nothing made, when the sprite shows too little to make a crown of. room is
     * where the crown is kept to below a wall's top: see ROOM_UNDER. leaf is the share of its
     * leaves the plant has on it (Seasons.leafOn), above 0; hue the colour its leaves turn
     * in autumn, one of the HUES, and turned how far they have, 0 green and 1 brown.
     */
    static boolean addCrown(Mesh mesh, Mesh wood, SpriteGeometry.Frame frame, float[] places, float x, float y, float base, float footU, float footV,
            float detail, float close, float boleRadius, float boleTall, int bark, int sprig, int seed, float[] room, float leaf, int hue, float turned) {
        return addCrown(mesh, wood, frame, places, x, y, base, footU, footV, detail, close, boleRadius, boleTall, bark, sprig, seed, room, leaf, hue, turned,
                TreeForm.NONE, null);
    }

    /**
     * The same, for a tree of a broad-leaved species, TreeForm's kind (NONE for any other),
     * whose wood is grown to that species' proportions, and, once it shows, to woodShape: the
     * outline of the branches the tree's trunk sprite paints, as woodShape measures it (null
     * for the crown's own).
     */
    static boolean addCrown(Mesh mesh, Mesh wood, SpriteGeometry.Frame frame, float[] places, float x, float y, float base, float footU, float footV,
            float detail, float close, float boleRadius, float boleTall, int bark, int sprig, int seed, float[] room, float leaf, int hue, float turned,
            int kind, float[] woodShape) {
        if (!(leaf > 0f && leaf <= 1f) || hue < 0 || hue >= HUES || !(turned >= 0f && turned <= 1f)) {
            throw new IllegalArgumentException("a crown with leaf " + leaf + ", hue " + hue + " and turned " + turned);
        }
        int count = places.length / 2;
        Workings workings = frame.crownWorkings == null ? frame.crownWorkings = new Workings() : frame.crownWorkings;
        float[] reachLeft = workings.reachLeft, reachRight = workings.reachRight, sizes = workings.sizes;
        if (!measure(frame, places, footU, footV, reachLeft, reachRight, sizes)) {
            return false;
        }
        // A tree's crown is all of it its tree's, sprigs, twigs and limbs: a bush is no tree.
        float meshRootX = mesh.rootX, meshRootY = mesh.rootY, woodRootX = wood.rootX, woodRootY = wood.rootY;
        if (sprig != SHRUB) {
            mesh.rooted((int) Math.floor(x), (int) Math.floor(y));
            mesh.rootY += autumnCode(hue, turned);
            wood.rooted((int) Math.floor(x), (int) Math.floor(y));
        }
        float across = SpriteGeometry.TILES_PER_PIXEL_ACROSS / frame.scale;
        float up = ViewCamera.LEVEL_HEIGHT / SpriteGeometry.PIXELS_PER_LEVEL / frame.scale;
        float widest = sizes[WIDEST], lowest = sizes[LOWEST], highest = sizes[HIGHEST], tall = sizes[TALL], under = sizes[UNDER];
        boolean shrub = sprig == SHRUB;
        float size = (shrub ? Math.clamp(widest * 0.8f, SHRUB_SMALLEST, SHRUB_LARGEST) : Math.clamp(widest * 0.36f, SPRIG_SMALLEST, SPRIG_LARGEST))
                / (float) Math.sqrt(detail);
        // As many as cover the crown's outside CROWN_FILL times over, were they laid flat on it.
        float wanted = CROWN_FILL * widest * Math.max(highest - lowest, widest) / (size * size);
        // A great crown asks for more sprigs than its sprite has places for: then each is larger, and they cover as much.
        if (wanted > count * CROWN_MOST_OF_PLACES) {
            size *= (float) Math.sqrt(wanted / (count * CROWN_MOST_OF_PLACES));
            wanted = count * CROWN_MOST_OF_PLACES;
        }
        float filled = wanted * (1f + CLOSE_FILL * close);
        float middle = lowest + (highest - lowest) * 0.45f;
        float reach = Math.max(widest, (highest - lowest) / 2f);
        boolean bare = sprig == BARE;
        // A crown that has lost leaves shows the wood that bore them: a bare tree's, with the leaves left on it.
        boolean wooded = bare || !shrub && leaf < WOOD_SHOWS;
        if (bare && boleRadius <= 0f) {
            return false;
        }
        mark(mesh, frame.open, SpriteGeometry.CARD, CROWN + sprig);
        int boughs = mix(seed, 0, 93);
        int lobes = shrub ? 2 + (boughs & 1) : 3 + (boughs & 0xff) % 3;
        float rows = shrub ? 1f : Math.clamp(1.6f * tall / Math.max(widest, 1e-3f), 1.5f, 4f);
        float turnOne = unit(boughs, 8) * 6.2832f, turnTwo = unit(boughs, 16) * 6.2832f, turnThree = unit(boughs, 24) * 6.2832f;
        // Each place is gone through once, and again for a crown near enough to be filled, whose places may each bear a second sprig.
        for (int go = 0; go < (bare ? 0 : close > 0f ? 2 * count : count); go++) {
            int i = go % count;
            int hash = mix(seed, go, 91);
            float high = (footV - places[i * 2 + 1]) * up;
            float drawn = unit(hash, 0) + go / count;
            if (high < under || drawn > filled / count) {
                continue;
            }
            // A tree that is losing its leaves has lost some sprigs whole, each on a day of its own, so a crown thins and no sprig comes and goes.
            if (leaf < 1f && unit(mix(seed, go, 98), 0) >= leaf) {
                continue;
            }
            // The leaves of a tree are its outside: most sprigs stand in a shell at the crown's
            // reach, each lying along the crown's surface there, give or take, so that from
            // any side they are seen flat on and cover what is behind them. The rest stand
            // further in, and are what shows, in shade, through the gaps of the first.
            boolean filling = drawn > wanted / count;
            boolean inner = filling || (hash >>> 29 & 3) == 0;
            int band = Math.min((int) ((high - lowest) / tall * CROWN_BANDS), CROWN_BANDS - 1);
            float round = unit(hash, 8) * 6.2832f, share = (high - lowest) / tall;
            // How far this place is up a bough's swell, 1 its top and 0 the hollow, and how that changes round the crown and up it.
            float swell = bough(round, share, lobes, rows, turnOne, turnTwo, turnThree);
            float swellRound = (bough(round + 0.05f, share, lobes, rows, turnOne, turnTwo, turnThree) - bough(round - 0.05f, share, lobes, rows, turnOne, turnTwo, turnThree)) / 0.1f;
            float swellUp = (bough(round, share + 0.03f, lobes, rows, turnOne, turnTwo, turnThree) - bough(round, share - 0.03f, lobes, rows, turnOne, turnTwo, turnThree)) / 0.06f;
            float full = Math.max((reachLeft[band] + reachRight[band]) / 2f, size * 0.2f);
            float surface = 1f - BOUGH_HOLLOW * (1f - swell);
            float out = full * (filling ? surface * (0.12f + 0.73f * unit(hash, 20)) : inner ? 0.3f + 0.3f * unit(hash, 20)
                    : surface * (0.86f + 0.14f * unit(hash, 20)));
            float cosRound = (float) Math.cos(round), sinRound = (float) Math.sin(round);
            float px = out * cosRound, py = out * sinRound, pz = high - middle;
            float far = (float) Math.sqrt(px * px + py * py + pz * pz);
            float ox = far > 0.01f ? px / far : 0f, oy = far > 0.01f ? py / far : 0f, oz = far > 0.01f ? pz / far : 1f;
            // The way the crown's outside faces here: out from its middle, turned down the swell's slope.
            float slopeRound = BOUGH_RELIEF * BOUGH_HOLLOW * swellRound / surface, slopeUp = BOUGH_RELIEF * BOUGH_HOLLOW * swellUp * full / tall;
            ox += sinRound * slopeRound;
            oy -= cosRound * slopeRound;
            oz -= slopeUp;
            float faces = Math.max((float) Math.sqrt(ox * ox + oy * oy + oz * oz), 1e-3f);
            ox /= faces;
            oy /= faces;
            oz /= faces;
            // Which way the sprig's flat faces: out of the crown, tilted any way by up to a third of a turn.
            int turn = mix(hash, i, 92);
            float ax = ox + 1.1f * (unit(turn, 0) - 0.5f), ay = oy + 1.1f * (unit(turn, 8) - 0.5f), az = oz + 1.1f * (unit(turn, 16) - 0.5f);
            float length = Math.max((float) Math.sqrt(ax * ax + ay * ay + az * az), 1e-3f);
            ax /= length;
            ay /= length;
            az /= length;
            // Two directions in its flat, square to each other.
            float bx = -ay, by = ax;
            float flat = (float) Math.sqrt(bx * bx + by * by);
            if (flat < 0.05f) {
                bx = 1f;
                by = 0f;
                flat = 1f;
            }
            bx /= flat;
            by /= flat;
            float cx = -az * by, cy = az * bx, cz = ax * by - ay * bx;
            double roll = unit(turn, 24) * 2.0 * Math.PI;
            float cos = (float) Math.cos(roll), sin = (float) Math.sin(roll);
            float each = size * (0.75f + 0.5f * unit(hash, 16));
            // Along the sprig from stem to tip, and half across it.
            float lx = (bx * cos + cx * sin) * each, ly = (by * cos + cy * sin) * each, lz = cz * sin * each;
            float rx = (cx * cos - bx * sin) * each / 2f, ry = (cy * cos - by * sin) * each / 2f, rz = cz * cos * each / 2f;
            float stemX = x + px - lx / 2f, stemY = y + py - ly / 2f, stemUp = base + high - lz / 2f;
            float tipX = stemX + lx, tipY = stemY + ly, tipUp = stemUp + lz;
            // Lit as the round crown it is part of, not as the flat card it is: from the side
            // the crown's middle is on, a little turned up.
            mesh.face(ox, oy, oz + 0.2f);
            // The middle of a crown is in the shade of its outside, and a hollow in that of the boughs either side.
            float shade = inner ? 0.5f : (0.8f + 0.2f * Math.min(far / reach, 1f)) * (1f + BOUGH_SHADE * (swell - 0.7f));
            float red = frame.red * shade, green = frame.green * shade, blue = frame.blue * shade;
            float u = (float) Math.rint((frame.uStart + (places[i * 2] - frame.left) / frame.width * (frame.uEnd - frame.uStart)) * CROWN_STEPS);
            float v = (float) Math.rint((frame.vStart + (places[i * 2 + 1] - frame.top) / frame.height * (frame.vEnd - frame.vStart)) * CROWN_STEPS);
            boolean mirrored = (hash >>> 28 & 1) == 1;
            float swayStem = Math.clamp((stemUp - base) * SWAY_PER_TILE, 0f, SWAY_MOST);
            float swayTip = Math.clamp((tipUp - base) * SWAY_PER_TILE, 0f, SWAY_MOST) + SPRIG_FLUTTER;
            if (Math.min(stemUp, tipUp) - Math.abs(rz) - base < ROOM_UNDER) {
                float swayed = SpriteGeometry.SWAY_REACH * swayTip;
                float shiftX = within(x + px, Math.abs(lx) / 2f + Math.abs(rx) + swayed, room[0], room[1]) - (x + px);
                float shiftY = within(y + py, Math.abs(ly) / 2f + Math.abs(ry) + swayed, room[2], room[3]) - (y + py);
                px += shiftX;
                py += shiftY;
                stemX += shiftX;
                tipX += shiftX;
                stemY += shiftY;
                tipY += shiftY;
            }
            for (int corner : QUAD) {
                boolean right = corner == 1 || corner == 2, tip = corner >= 2;
                float side = right ? 1f : -1f;
                mesh.sway = tip ? swayTip : swayStem;
                mesh.add((tip ? tipX : stemX) + rx * side, (tip ? tipY : stemY) + ry * side, (tip ? tipUp : stemUp) + rz * side,
                        u + (right != mirrored ? 1f - CROWN_INSET : CROWN_INSET), v + (tip ? CROWN_INSET : 1f - CROWN_INSET), red, green, blue);
            }
            if ((hash >>> 12 & 0xff) < 255f * TWIG_SHARE * close) {
                // The twig the sprig is on: from the middle of its leaves back and down into the crown, towards the bough it grew from.
                float back = Math.min(TWIG_LONG * each / Math.max(out, 1e-3f), 0.7f);
                float sway = Math.clamp(high * SWAY_PER_TILE, 0f, SWAY_MOST);
                mark(wood, frame.open, 0f, TRUNK);
                prism(wood, x + px * (1f - back), y + py * (1f - back), base + high - TWIG_DROP * TWIG_LONG * each, x + px, y + py, base + high,
                        TWIG_THICK, TWIG_TIP, TWIG_SIDES, BARK_UP * bark + WOOD_UP, sway, sway, hash, frame);
            }
        }
        mesh.sway = 0f;
        float inside = Math.clamp((leaf - CORE_GOES) / (1f - CORE_GOES), 0f, 1f);
        if (!bare && inside > 0f) {
            addCore(mesh, frame, places, x, y, base, footU, footV, across, up, lowest, tall, under, reachLeft, reachRight, lobes, rows, turnOne, turnTwo, turnThree, sprig, room,
                    inside, workings);
        }
        // The wood's own outline. Once a crown has lost leaves its wood is the bare tree's, grown
        // to the outline of the branches the tree's trunk sprite paints (woodShape), so it stands
        // the same from the first leaf lost to bare, whatever leaves the crown has left.
        float[] woodLeft = reachLeft, woodRight = reachRight;
        float woodLowest = lowest, woodTall = tall, woodWidest = widest, woodReach = reach, woodRows = rows;
        if (wooded && woodShape != null) {
            woodLeft = workings.woodLeft;
            woodRight = workings.woodRight;
            System.arraycopy(woodShape, SIZES, woodLeft, 0, CROWN_BANDS);
            System.arraycopy(woodShape, SIZES + CROWN_BANDS, woodRight, 0, CROWN_BANDS);
            woodLowest = woodShape[LOWEST];
            woodTall = woodShape[TALL];
            woodWidest = woodShape[WIDEST];
            woodReach = Math.max(woodWidest, (woodShape[HIGHEST] - woodLowest) / 2f);
            woodRows = Math.clamp(1.6f * woodTall / Math.max(woodWidest, 1e-3f), 1.5f, 4f);
        }
        boolean formed = kind != TreeForm.NONE;
        float leaderTop = formed ? TreeForm.LEADER_TOP[kind] : LEADER_TOP;
        if (boleRadius > 0f && !shrub && woodLowest + leaderTop * woodTall > boleTall + 0.2f) {
            mark(wood, frame.open, 0f, TRUNK);
            float topUp = woodLowest + leaderTop * woodTall, thick = boleRadius * TAPER;
            // With no leaves to hide it the wood is the whole tree's outline: more limbs, out to the outline.
            int limbs = wooded ? Math.clamp(Math.round(6f + 3f * woodWidest), 7, 12) : Math.clamp(Math.round(4f + 2.5f * woodWidest), 5, 9);
            if (formed) {
                limbs = Math.max(Math.round(limbs * TreeForm.LIMBS[kind]), 3);
            }
            float limbOut = wooded ? BARE_OUT : LIMB_OUT;
            // Up the middle from the bole's top, or, where the species parts into stems, up each, leaning apart.
            int stems = formed ? TreeForm.STEMS_LEAST[kind] + (seed >>> 4 & 0xff) % (TreeForm.STEMS_MOST[kind] - TreeForm.STEMS_LEAST[kind] + 1) : 1;
            float lean = formed ? (float) Math.tan(Math.toRadians(TreeForm.STEM_LEAN[kind])) : 0f;
            float stemThick = stems > 1 ? thick * 1.15f / (float) Math.sqrt(stems) : thick;
            float[] stemTop = workings.stemTop = atLeast(workings.stemTop, stems);
            // Where each stem is at each of its joints, across and along from the tree's middle: no trunk runs dead straight.
            // In as many pieces as keep each short: see STRAIGHT_MOST.
            // Bent at every joint near the eye; far off, where a bend is finer than a pixel, in fewer, longer pieces.
            float straight = STRAIGHT_MOST * woodTall * (piecesByDistance ? detail >= NEAR_BENDS ? 1f : detail > BARE_TWIGGED ? 1.8f : 3f : 1f);
            int leaderPieces = Math.max(LEADER_PIECES, (int) Math.ceil((topUp - boleTall) / straight));
            float[] stemX = workings.stemX = atLeast(workings.stemX, stems * (leaderPieces + 1)), stemY = workings.stemY = atLeast(workings.stemY, stems * (leaderPieces + 1));
            for (int stem = 0; stem < stems; stem++) {
                int own = mix(seed, stem, 99);
                float round = turnOne + stem * 6.2832f / stems + (stems > 1 ? 0.6f * (unit(own, 0) - 0.5f) : 0f);
                stemTop[stem] = stems > 1 ? topUp - 0.12f * woodTall * unit(own, 8) : topUp;
                float out = lean * (stemTop[stem] - boleTall);
                for (int joint = 0; joint <= leaderPieces; joint++) {
                    float a = (float) joint / leaderPieces;
                    int wavers = mix(own, joint, 101);
                    float waver = joint == 0 ? 0f : WAVER * woodTall;
                    stemX[stem * (leaderPieces + 1) + joint] = (float) Math.cos(round) * out * a + waver * (unit(wavers, 0) - 0.5f);
                    stemY[stem * (leaderPieces + 1) + joint] = (float) Math.sin(round) * out * a + waver * (unit(wavers, 8) - 0.5f);
                }
                float[] line = workings.leader = atLeast(workings.leader, (leaderPieces + 1) * 4);
                for (int joint = 0; joint <= leaderPieces; joint++) {
                    float a = (float) joint / leaderPieces;
                    int at = stem * (leaderPieces + 1) + joint;
                    tubePoint(line, joint, x + stemX[at], y + stemY[at], base + boleTall + (stemTop[stem] - boleTall) * a, stemThick + (LIMB_TIP - stemThick) * a);
                }
                tube(wood, line, leaderPieces + 1, LEADER_SIDES, BARK_UP * bark + WOOD_UP, base, boleTall, seed + leaderPieces * stem, 0f, frame, wooded, false, workings);
            }
            boolean paired = formed && TreeForm.OPPOSITE[kind];
            int nodes = paired ? (limbs + 1) / 2 : limbs;
            float limbRise = formed ? TreeForm.LIMB_RISE[kind] : 0.16f, branchRise = formed ? TreeForm.BRANCH_RISE[kind] : 0.22f;
            int twigs = formed ? TreeForm.TWIGS[kind] : BARE_TWIGS;
            float twigRise = formed ? TreeForm.TWIG_RISE[kind] : 1f;
            for (int limb = 0; limb < limbs; limb++) {
                // A species whose buds are in pairs has its limbs in pairs, one either side, each pair turned a quarter from the last.
                int node = paired ? limb / 2 : limb, stem = limb % stems;
                int own = mix(seed, node, 94);
                // Where up the leader or stem it leaves, the lowest limbs first, and where on the crown's outside it ends.
                float leaves = (node + 0.3f + 0.4f * unit(own, 0)) / nodes * 0.78f;
                float fromUp = boleTall + (stemTop[stem] - boleTall) * leaves;
                float round = paired ? turnOne + node * 1.5708f + (limb % 2) * 3.1416f + 0.3f * (unit(own, 8) - 0.5f)
                        : turnOne + limb * 2.39996f + 0.5f * (unit(own, 8) - 0.5f);
                if (stems > 1) {
                    // A stem of several bears its limbs on its outer side: the inside of the fork is the other stems'.
                    int stemEnd = stem * (leaderPieces + 1) + leaderPieces;
                    float stemRound = (float) Math.atan2(stemY[stemEnd], stemX[stemEnd]);
                    round = stemRound + 0.55f * (float) Math.atan2(Math.sin(round - stemRound), Math.cos(round - stemRound));
                }
                float share = Math.clamp((fromUp - woodLowest) / woodTall + limbRise + 0.22f * unit(own, 16), 0.08f, 0.94f);
                float reachAt = reachAt(woodLeft, woodRight, share) * (1f - BOUGH_HOLLOW * (1f - bough(round, share, lobes, woodRows, turnOne, turnTwo, turnThree)));
                float endOut = limbOut * reachAt, endUp = woodLowest + share * woodTall;
                float cos = (float) Math.cos(round), sin = (float) Math.sin(round);
                float limbThick = Math.max((stemThick + (LIMB_TIP - stemThick) * leaves) * LIMB_OF_LEADER, LIMB_TIP * 1.4f);
                // It leaves the stem nearly level and turns up to its end: a curve through a point out beside where it leaves.
                float bendOut = 0.7f * endOut, bendUp = fromUp + 0.1f * (endUp - fromUp);
                // It leaves the stem where the stem is at that height.
                float along = leaves * leaderPieces;
                int joint = Math.min((int) along, leaderPieces - 1);
                float part = along - joint;
                int at0 = stem * (leaderPieces + 1) + joint;
                float startX = stemX[at0] + (stemX[at0 + 1] - stemX[at0]) * part, startY = stemY[at0] + (stemY[at0 + 1] - stemY[at0]) * part;
                float lastX = x + startX, lastY = y + startY, lastUp = fromUp;
                int limbPieces = Math.max(LIMB_PIECES, (int) Math.ceil(1.15f * (float) Math.hypot(endOut, endUp - fromUp) / straight));
                float[] line = workings.limb = atLeast(workings.limb, (limbPieces + 1) * 4);
                tubePoint(line, 0, lastX, lastY, base + fromUp, limbThick);
                for (int piece = 1; piece <= limbPieces; piece++) {
                    float t = (float) piece / limbPieces, s = 1f - t;
                    float outAt = 2f * s * t * bendOut + t * t * endOut, upAt = s * s * fromUp + 2f * s * t * bendUp + t * t * endUp;
                    // It keeps its thickness most of its length, and thins fastest at its end.
                    float thickTo = limbThick + (LIMB_TIP - limbThick) * t * t;
                    // A piece that starts under the room's top is kept to it to its end, wherever that is.
                    float in = thickTo + SpriteGeometry.SWAY_REACH * limbSway(upAt, boleTall), fromUnder = Math.min(lastUp, upAt);
                    // It kinks a little at each joint, as wood grows from bud to bud.
                    float kink = piece < limbPieces ? KINK * endOut / limbPieces * (unit(mix(own, piece, 102 + limb % 2), 0) - 0.5f) : 0f;
                    float atX = keptX(x + cos * outAt + startX * s - sin * kink, fromUnder, in, room), atY = keptY(y + sin * outAt + startY * s + cos * kink, fromUnder, in, room);
                    tubePoint(line, piece, atX, atY, base + upAt, thickTo);
                    // Two branches at each quarter of the limb past its first, however many pieces it is made of.
                    int quarter = (int) Math.floor(t * LIMB_PIECES + 1e-4f), before = (int) Math.floor((float) (piece - 1) / limbPieces * LIMB_PIECES + 1e-4f);
                    if (quarter >= 2 && quarter != before) {
                        for (int side = 0; side < 2; side++) {
                            int which = mix(own + (paired ? 7 * (limb % 2) : 0), piece, 95 + side);
                            float toRound = round + (side == 0 ? 1f : -1f) * (0.35f + 0.45f * unit(which, 0));
                            float toShare = Math.clamp((upAt - woodLowest) / woodTall + branchRise * unit(which, 8), 0.05f, 0.97f);
                            float toOut = (wooded ? BARE_OUT : 0.85f) * reachAt(woodLeft, woodRight, toShare)
                                    * (1f - BOUGH_HOLLOW * (1f - bough(toRound, toShare, lobes, woodRows, turnOne, turnTwo, turnThree)));
                            float toUp = woodLowest + toShare * woodTall, toIn = BRANCH_TIP + SpriteGeometry.SWAY_REACH * limbSway(toUp, boleTall), toUnder = Math.min(upAt, toUp);
                            float endX = keptX(x + (float) Math.cos(toRound) * toOut, toUnder, toIn, room), endY = keptY(y + (float) Math.sin(toRound) * toOut, toUnder, toIn, room);
                            boolean twigged = wooded && detail > BARE_TWIGGED;
                            float[] last = addBranch(wood, atX, atY, upAt, endX, endY, toUp, base, Math.max(thickTo * BRANCH_OF_LIMB, BRANCH_TIP),
                                    straight, boleTall, bark, which, room, frame, wooded, twigged, workings);
                            if (twigged) {
                                // Further off, fewer twigs, and none forking: what forks is finer than a pixel there.
                                int twigsHere = twigsByDistance && detail < NEAR_BENDS ? Math.max(twigs - 1, 2) : twigs;
                                addBareTwigs(wood, last[0], last[1], last[2], endX, endY, toUp, base, woodReach, boleTall, bark, which, room, frame, twigsHere, twigRise,
                                        detail > (nearTwiglets ? TWIGLETS : 0.8f));
                            }
                        }
                    }
                    lastX = atX;
                    lastY = atY;
                    lastUp = upAt;
                }
                tube(wood, line, limbPieces + 1, LIMB_SIDES, BARK_UP * bark + WOOD_UP, base, boleTall, own + (paired ? 7 * (limb % 2) : 0), 0f, frame, wooded, false, workings);
            }
        }
        wood.sway = 0f;
        mesh.rootX = meshRootX;
        mesh.rootY = meshRootY;
        wood.rootX = woodRootX;
        wood.rootY = woodRootY;
        return true;
    }

    /**
     * The inside of the crown addCrown is making: rings from near its foot to near its top,
     * each as far out as CORE_OF_REACH of the crown there with the boughs' swells and hollows
     * in it, closed over at the top and drawn in underneath to a hole the limbs come in by. Each ring takes its colour from a place
     * the sprite paints leaves at that height, half way out from the plant's middle. A crown
     * thinning in autumn has it drawn in by share, 1 as far out as in full leaf.
     */
    private static void addCore(Mesh mesh, SpriteGeometry.Frame frame, float[] places, float x, float y, float base, float footU, float footV, float across, float up,
            float lowest, float tall, float under, float[] reachLeft, float[] reachRight, int lobes, float rows, float turnOne, float turnTwo,
            float turnThree, int sprig, float[] room, float drawnIn, Workings workings) {
        int count = places.length / 2;
        int[] placeOf = workings.placeOf;
        float[] nearest = workings.nearest;
        Arrays.fill(placeOf, -1);
        Arrays.fill(nearest, Float.MAX_VALUE);
        for (int i = 0; i < count; i++) {
            float high = (footV - places[i * 2 + 1]) * up;
            if (high < under) {
                continue;
            }
            float share = (high - lowest) / tall;
            int ring = Math.clamp((int) ((share - CORE_FOOT) / (CORE_HEAD - CORE_FOOT) * CORE_RINGS), 0, CORE_RINGS - 1);
            // Half way out to one side: up the sprite's middle is where it paints the trunk's shade, and at its rim the leaves' edges.
            float half = 0.5f * reachAt(reachLeft, reachRight, share) / across;
            float off = Math.abs(Math.abs(places[i * 2] - footU) - half);
            if (off < nearest[ring]) {
                nearest[ring] = off;
                placeOf[ring] = i;
            }
        }
        // A ring at whose height the sprite paints nothing has the colour of the ring nearest it that has one.
        for (int ring = 0; ring < CORE_RINGS; ring++) {
            for (int off = 1; placeOf[ring] < 0 && off < CORE_RINGS; off++) {
                int below = ring - off, above = ring + off;
                placeOf[ring] = below >= 0 && placeOf[below] >= 0 ? placeOf[below] : above < CORE_RINGS && placeOf[above] >= 0 ? placeOf[above] : -1;
            }
        }
        // Every face's corners are on the rings' heights and the same ways round: how far out the inside is at each is worked out once.
        int rings = CORE_RINGS, around = CORE_ROUND;
        float[] coreShare = workings.coreShare, coreReach = workings.coreReach, coreBough = workings.coreBough, coreCos = workings.coreCos, coreSin = workings.coreSin;
        for (int face = 0; face <= around; face++) {
            float round = face * 6.2832f / around;
            coreCos[face] = (float) Math.cos(round);
            coreSin[face] = (float) Math.sin(round);
        }
        for (int ring = 0; ring <= rings; ring++) {
            float share = CORE_FOOT + (CORE_HEAD - CORE_FOOT) * ring / rings;
            coreShare[ring] = share;
            coreReach[ring] = reachAt(reachLeft, reachRight, share);
            for (int face = 0; face <= around; face++) {
                coreBough[ring * (around + 1) + face] = 1f - BOUGH_HOLLOW * (1f - bough(face * 6.2832f / around, share, lobes, rows, turnOne, turnTwo, turnThree));
            }
        }
        mark(mesh, frame.open, SpriteGeometry.CARD, CORE);
        float red = frame.red * CORE_SHADE, green = frame.green * CORE_SHADE, blue = frame.blue * CORE_SHADE;
        float kind = (sprig + 0.5f) / CORE_KINDS;
        for (int ring = 0; ring < rings; ring++) {
            int place = placeOf[ring];
            float u = (float) Math.rint((frame.uStart + (places[place * 2] - frame.left) / frame.width * (frame.uEnd - frame.uStart)) * CROWN_STEPS) + kind;
            float v = (float) Math.rint((frame.vStart + (places[place * 2 + 1] - frame.top) / frame.height * (frame.vEnd - frame.vStart)) * CROWN_STEPS) + 0.5f;
            float shareLow = coreShare[ring];
            boolean top = ring == rings - 1;
            for (int face = 0; face < around; face++) {
                // The top ring closes to a point, which makes the second of a face's two triangles nothing: it is left out.
                for (int at = 0; at < (top ? 3 : QUAD.length); at++) {
                    int corner = QUAD[at];
                    boolean high = corner >= 2, to = corner == 1 || corner == 2;
                    int on = high ? ring + 1 : ring, way = to ? face + 1 : face;
                    float share = coreShare[on];
                    float out = top && high ? 0f
                            : (ring == 0 && !high ? CORE_UNDER : 1f) * CORE_OF_REACH * drawnIn * coreReach[on] * coreBough[on * (around + 1) + way];
                    float cos = coreCos[way], sin = coreSin[way];
                    float over = lowest + share * tall;
                    mesh.face(cos, sin, 0.1f + 1.4f * (share - 0.4f));
                    mesh.sway = Math.clamp(over * SWAY_PER_TILE, 0f, SWAY_MOST);
                    // A face is kept to the room by both its rings when its lower one is under the room's top.
                    float swayed = SpriteGeometry.SWAY_REACH * mesh.sway, faceFoot = lowest + shareLow * tall;
                    mesh.add(keptX(x + cos * out, faceFoot, swayed, room), keptY(y + sin * out, faceFoot, swayed, room), base + over, u, v, red, green, blue);
                }
            }
        }
        mesh.sway = 0f;
    }

    /**
     * What addCrown works a crown out in, so that a crown makes no garbage. It is kept with the
     * frame the crown is made with (SpriteGeometry.Frame.crownWorkings), and so is only ever
     * used on the one thread that frame is.
     */
    static final class Workings {
        final float[] reachLeft = new float[CROWN_BANDS], reachRight = new float[CROWN_BANDS], sizes = new float[SIZES];
        final float[] woodLeft = new float[CROWN_BANDS], woodRight = new float[CROWN_BANDS];
        float[] stemTop = new float[0], stemX = new float[0], stemY = new float[0];
        /** The points along a leader, a limb and a branch, which are made one inside another, and the rings of the one being made. */
        float[] leader = new float[0], limb = new float[0], branch = new float[0], ring = new float[0];
        final float[] branchStart = new float[3];
        final int[] placeOf = new int[CORE_RINGS];
        final float[] nearest = new float[CORE_RINGS];
        /** For a crown's inside: each ring's share of the way up the crown and how far out the crown is there, how far out of that the boughs' swells are at each way round each ring, and those ways. */
        final float[] coreShare = new float[CORE_RINGS + 1], coreReach = new float[CORE_RINGS + 1], coreBough = new float[(CORE_RINGS + 1) * (CORE_ROUND + 1)];
        final float[] coreCos = new float[CORE_ROUND + 1], coreSin = new float[CORE_ROUND + 1];
    }

    /** The array, or a larger one in its place when it holds fewer than size. */
    private static float[] atLeast(float[] array, int size) {
        return array.length >= size ? array : new float[Math.max(size, 2 * array.length)];
    }

    /** What a crown's sprigs and inside add to their tree's square's y for the colour its leaves turn and how far: see AUTUMN_CODES. */
    static float autumnCode(int hue, float turned) {
        return (hue * TURNS + Math.round(turned * (TURNS - 1)) - AUTUMN_CODES / 2) / AUTUMN_SPACING;
    }

    /**
     * The colour a deciduous tree's leaves turn in autumn, by its sprite's name, which is its
     * species, and which tree of its kind it is: red maples scarlet or orange, dogwood claret
     * or scarlet, hawthorn orange or scarlet, the rest yellow or gold.
     */
    static int autumnHue(String name, int treeHash) {
        return autumnHue(turnsAs(name), otherHue(treeHash));
    }

    /** Whether the tree whose square hashes to treeHash (Seasons.treeHash) turns the second of its kind's two colours. */
    static boolean otherHue(int treeHash) {
        return (treeHash >>> 8 & 3) == 0;
    }

    static int autumnHue(String name, boolean other) {
        return autumnHue(turnsAs(name), other);
    }

    /** Which species' autumn colours a tree's sprite's name says it turns: see autumnHue. */
    static final int TURNS_AS_MAPLE = 0, TURNS_AS_DOGWOOD = 1, TURNS_AS_HAWTHORN = 2, TURNS_AS_ANY = 3;

    static int turnsAs(String name) {
        return name.contains("maple") ? TURNS_AS_MAPLE : name.contains("dogwood") ? TURNS_AS_DOGWOOD : name.contains("hawthorn") ? TURNS_AS_HAWTHORN : TURNS_AS_ANY;
    }

    /** autumnHue for a tree whose name turnsAs says that. */
    static int autumnHue(int turnsAs, boolean other) {
        return switch (turnsAs) {
            case TURNS_AS_MAPLE -> other ? ORANGE_LEAF : SCARLET_LEAF;
            case TURNS_AS_DOGWOOD -> other ? SCARLET_LEAF : CLARET_LEAF;
            case TURNS_AS_HAWTHORN -> other ? SCARLET_LEAF : ORANGE_LEAF;
            default -> other ? GOLD_LEAF : YELLOW_LEAF;
        };
    }

    /** How far out a crown reaches at a share of the way up it: see addCrown. */
    private static float reachAt(float[] reachLeft, float[] reachRight, float share) {
        int band = Math.clamp((int) (share * CROWN_BANDS), 0, CROWN_BANDS - 1);
        return (reachLeft[band] + reachRight[band]) / 2f;
    }

    /**
     * Where something reaching extent either side of at goes to keep within low..high: where it
     * is if it fits, moved in the least that makes it fit, or the middle of a span too narrow for it.
     */
    static float within(float at, float extent, float low, float high) {
        float least = low + extent, most = high - extent;
        return least > most ? (low + high) / 2f : Math.clamp(at, least, most);
    }

    /** A point of a crown high over its foot, x along the map, kept in the room that far in from it if it is below the room's top: see ROOM_UNDER. */
    private static float keptX(float x, float high, float in, float[] room) {
        return high < ROOM_UNDER ? within(x, in, room[0], room[1]) : x;
    }

    private static float keptY(float y, float high, float in, float[] room) {
        return high < ROOM_UNDER ? within(y, in, room[2], room[3]) : y;
    }

    /** How much of the wind wood in a crown takes at a height over the plant's foot: what a sprig there takes, and nothing where it joins the bole, which stands still. */
    private static float limbSway(float high, float boleTall) {
        return Math.clamp(high * SWAY_PER_TILE, 0f, SWAY_MOST) * Math.clamp((high - boleTall) / 0.6f, 0f, 1f);
    }

    /**
     * A round piece of wood from one point to another, thickFrom at the first and thickTo at
     * the second, in the bark whose picture starts at up: open at both ends, which the next
     * piece or the leaves cover. A quarter of the bark's picture goes round it. It holds no
     * snow of its own: a twig among leaves, whose snow is on the leaves, or a stick on the
     * ground, which the snow on the ground buries. See woodCode.
     */
    private static void prism(Mesh mesh, float fromX, float fromY, float fromUp, float toX, float toY, float toUp, float thickFrom, float thickTo, int sides,
            float up, float swayFrom, float swayTo, int seed, SpriteGeometry.Frame frame) {
        prism(mesh, fromX, fromY, fromUp, toX, toY, toUp, thickFrom, thickTo, sides, up, swayFrom, swayTo, seed, frame, 0f, false, false, false);
    }

    /**
     * The same, its texture coordinate across marked as mark says (see FINE_MARK), holding snow
     * of its own as snowed says, with a ridge of it along its top at its first end if ridgedFrom
     * says and at its second if ridgedTo does: see woodCode.
     */
    private static void prism(Mesh mesh, float fromX, float fromY, float fromUp, float toX, float toY, float toUp, float thickFrom, float thickTo, int sides,
            float up, float swayFrom, float swayTo, int seed, SpriteGeometry.Frame frame, float mark, boolean snowed, boolean ridgedFrom, boolean ridgedTo) {
        float dx = toX - fromX, dy = toY - fromY, dz = toUp - fromUp;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-4f) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        // Two ways square to it and to each other. Near upright, the first is along x, which is not square to it, and the second is then short of 1.
        boolean upright = Math.abs(dz) > 0.95f;
        float ax = upright ? 1f : -dy, ay = upright ? 0f : dx;
        float flat = (float) Math.sqrt(ax * ax + ay * ay);
        ax /= flat;
        ay /= flat;
        float bx = -dz * ay, by = dz * ax, bz = dx * ay - dy * ax;
        float across = mark + BARK_ACROSS * (BARK_WRAPS_MOST * (seed >>> 16 & 1) + 2 * BARK_WRAPS_MOST * (seed >>> 17 & BARK_SHIFTS - 1));
        float span = Math.max((thickFrom + thickTo) * (float) Math.PI * 4f, 0.2f);
        float[] cosAround = WOOD_COS[sides], sinAround = WOOD_SIN[sides];
        for (int side = 0; side < sides; side++) {
            for (int corner : QUAD) {
                int on = corner == 1 || corner == 2 ? 1 : 0;
                boolean far = corner >= 2;
                float cos = cosAround[side + on], sin = sinAround[side + on];
                float outX = ax * cos + bx * sin, outY = ay * cos + by * sin, outUp = bz * sin;
                float thick = far ? thickTo : thickFrom;
                if (upright) {
                    mesh.face(outX, outY, outUp);
                } else {
                    mesh.faceUnit(outX, outY, outUp);
                }
                mesh.sway = far ? swayTo : swayFrom;
                mesh.wood = woodCode(thick, snowed, far ? ridgedTo : ridgedFrom);
                mesh.add((far ? toX : fromX) + outX * thick, (far ? toY : fromY) + outY * thick, (far ? toUp : fromUp) + outUp * thick,
                        across + (side + on) * 0.25f / sides, up + (far ? length / span : 0f), frame.red, frame.green, frame.blue);
            }
        }
        mesh.wood = 0f;
    }

    /**
     * Off the end of each branch of a bare tree, the twigs that fill its outline, going on
     * the way the branch went, fanned either side of it and turned up. Seen against the sky
     * they are what makes a bare crown read as a crown and not as a few sticks.
     */
    private static void addBareTwigs(Mesh wood, float fromX, float fromY, float fromUp, float endX, float endY, float endUp, float base, float reach,
            float boleTall, int bark, int seed, float[] room, SpriteGeometry.Frame frame, int twigs, float rises, boolean forked) {
        float dx = endX - fromX, dy = endY - fromY;
        float flat = (float) Math.sqrt(dx * dx + dy * dy);
        double way = flat > 1e-4f ? Math.atan2(dy, dx) : unit(seed, 0) * 2.0 * Math.PI;
        for (int twig = 0; twig < twigs; twig++) {
            int own = mix(seed, twig, 97);
            double turn = way + (twig - (twigs - 1) / 2f) * 0.55 + 0.3 * (unit(own, 0) - 0.5);
            float length = reach * (0.08f + 0.07f * unit(own, 8));
            float rise = length * (0.35f + 0.5f * unit(own, 16)) * rises;
            float toUp = endUp + rise, in = TWIG_TIP + SpriteGeometry.SWAY_REACH * limbSway(toUp, boleTall), under = Math.min(endUp, toUp);
            float toX = keptX(endX + (float) Math.cos(turn) * length, under, in, room), toY = keptY(endY + (float) Math.sin(turn) * length, under, in, room);
            prism(wood, endX, endY, base + endUp, toX, toY, base + toUp, BRANCH_TIP, forked ? (BRANCH_TIP + TWIG_TIP) / 2f : TWIG_TIP, TWIG_SIDES, BARK_UP * bark + WOOD_UP,
                    limbSway(endUp, boleTall), limbSway(toUp, boleTall), own, frame, fineMark(), true, true, forked);
            if (!forked) {
                continue;
            }
            // Each twig forks at its end into two shorter, finer ones, so a bare crown's edge is a haze of fine wood and not spikes.
            for (int fork = 0; fork < 2; fork++) {
                int each = mix(own, fork, 98);
                double split = turn + (fork == 0 ? 1.0 : -1.0) * (0.3 + 0.3 * unit(each, 0));
                float shorter = length * (0.45f + 0.25f * unit(each, 8));
                float tipUp = toUp + shorter * (0.3f + 0.6f * unit(each, 16)) * rises, tipIn = TWIG_TIP + SpriteGeometry.SWAY_REACH * limbSway(tipUp, boleTall);
                float tipX = keptX(toX + (float) Math.cos(split) * shorter, Math.min(toUp, tipUp), tipIn, room), tipY = keptY(toY + (float) Math.sin(split) * shorter, Math.min(toUp, tipUp), tipIn, room);
                prism(wood, toX, toY, base + toUp, tipX, tipY, base + tipUp, (BRANCH_TIP + TWIG_TIP) / 2f, TWIG_TIP, TWIG_SIDES, BARK_UP * bark + WOOD_UP,
                        limbSway(toUp, boleTall), limbSway(tipUp, boleTall), each, frame, fineMark(), true, true, false);
            }
        }
    }

    /**
     * A branch from (fromX, fromY, fromUp) out to (toX, toY, toUp), thick at its start and
     * BRANCH_TIP at its end, in pieces no longer than longest, each joint kinked a little aside and the whole bowed
     * up, as wood grows from bud to bud and not as a rod. Returns where its last piece starts,
     * for the twigs to go on the way it goes, in workings until the next branch; twigged says whether they do, and snowed whether
     * it holds snow of its own (see woodCode).
     */
    private static float[] addBranch(Mesh wood, float fromX, float fromY, float fromUp, float toX, float toY, float toUp, float base, float thick, float longest,
            float boleTall, int bark, int seed, float[] room, SpriteGeometry.Frame frame, boolean snowed, boolean twigged, Workings workings) {
        float dx = toX - fromX, dy = toY - fromY, dz = toUp - fromUp;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int pieces = Math.max(2, (int) Math.ceil(length / longest));
        float flat = Math.max((float) Math.sqrt(dx * dx + dy * dy), 1e-4f);
        float sideX = -dy / flat, sideY = dx / flat;
        float lastX = fromX, lastY = fromY, lastUp = fromUp, startX = fromX, startY = fromY, startUp = fromUp;
        float[] line = workings.branch = atLeast(workings.branch, (pieces + 1) * 4);
        tubePoint(line, 0, fromX, fromY, base + fromUp, thick);
        for (int piece = 1; piece <= pieces; piece++) {
            float t = (float) piece / pieces;
            float kink = piece < pieces ? KINK * length * (unit(mix(seed, piece, 103), 0) - 0.5f) : 0f;
            float bow = piece < pieces ? BOW * length * 4f * t * (1f - t) : 0f;
            float up = fromUp + dz * t + bow, in = BRANCH_TIP + SpriteGeometry.SWAY_REACH * limbSway(up, boleTall), under = Math.min(lastUp, up);
            float atX = keptX(fromX + dx * t + sideX * kink, under, in, room), atY = keptY(fromY + dy * t + sideY * kink, under, in, room);
            tubePoint(line, piece, atX, atY, base + up, thick + (BRANCH_TIP - thick) * t);
            startX = lastX;
            startY = lastY;
            startUp = lastUp;
            lastX = atX;
            lastY = atY;
            lastUp = up;
        }
        tube(wood, line, pieces + 1, BRANCH_SIDES, BARK_UP * bark + WOOD_UP, base, boleTall, seed, 0f, frame, snowed, twigged, workings);
        float[] start = workings.branchStart;
        start[0] = startX;
        start[1] = startY;
        start[2] = startUp;
        return start;
    }

    /**
     * What a ring of wood thick as a radius carries for the snow on it (SpriteGeometry.WOOD):
     * the radius where it bears a ridge of snow along its top, less than 0 where it holds snow
     * but bears no ridge, at a free end, and less than -SnowShader.UNSNOWED where it holds no
     * snow of its own: wood among leaves, whose snow is on the leaves, and wood on the ground.
     */
    static float woodCode(float thick, boolean snowed, boolean ridged) {
        return !snowed ? -(SnowShader.UNSNOWED + thick) : ridged ? thick : -thick;
    }

    private static void tubePoint(float[] line, int point, float x, float y, float up, float thick) {
        line[point * 4] = x;
        line[point * 4 + 1] = y;
        line[point * 4 + 2] = up;
        line[point * 4 + 3] = thick;
    }

    /**
     * A round piece of wood along the points of line, x, y, up and how thick, four floats a
     * point, in the bark whose picture starts at up: its rings turn with it from one piece to
     * the next, each square to the way the wood runs through it, so a bend has no crack or step,
     * and the bark runs on unbroken along it. Open at both ends. Wood sways as limbSway says
     * for the height of each ring over base. It carries a ridge of snow along its top (see
     * SnowShader.RIDGE) that runs out to nothing at its last ring unless carriedOn says wood
     * goes on from there, if snowed says it holds snow of its own: see woodCode.
     */
    private static void tube(Mesh mesh, float[] line, int points, int sides, float up, float base, float boleTall, int seed, float mark, SpriteGeometry.Frame frame,
            boolean snowed, boolean carriedOn, Workings workings) {
        float across = mark + BARK_ACROSS * (BARK_WRAPS_MOST * (seed >>> 16 & 1) + 2 * BARK_WRAPS_MOST * (seed >>> 17 & BARK_SHIFTS - 1));
        float span = Math.max((line[3] + line[(points - 1) * 4 + 3]) * (float) Math.PI * 4f, 0.2f);
        float[] ring = workings.ring = atLeast(workings.ring, points * 6);
        float ax = 0f, ay = 0f, az = 0f;
        for (int point = 0; point < points; point++) {
            int from = Math.max(point - 1, 0) * 4, to = Math.min(point + 1, points - 1) * 4;
            float tx = line[to] - line[from], ty = line[to + 1] - line[from + 1], tz = line[to + 2] - line[from + 2];
            float length = Math.max((float) Math.sqrt(tx * tx + ty * ty + tz * tz), 1e-6f);
            tx /= length;
            ty /= length;
            tz /= length;
            if (point == 0) {
                ax = Math.abs(tz) > 0.95f ? 1f : -ty;
                ay = Math.abs(tz) > 0.95f ? 0f : tx;
                az = 0f;
            }
            // The last ring's way across, made square to this ring's way along: it does not twist.
            float d = ax * tx + ay * ty + az * tz;
            ax -= d * tx;
            ay -= d * ty;
            az -= d * tz;
            float flat = Math.max((float) Math.sqrt(ax * ax + ay * ay + az * az), 1e-6f);
            ax /= flat;
            ay /= flat;
            az /= flat;
            ring[point * 6] = ax;
            ring[point * 6 + 1] = ay;
            ring[point * 6 + 2] = az;
            ring[point * 6 + 3] = ty * az - tz * ay;
            ring[point * 6 + 4] = tz * ax - tx * az;
            ring[point * 6 + 5] = tx * ay - ty * ax;
        }
        float run = 0f;
        float[] cosAround = WOOD_COS[sides], sinAround = WOOD_SIN[sides];
        for (int piece = 1; piece < points; piece++) {
            int a = (piece - 1) * 4, b = piece * 4;
            float dx = line[b] - line[a], dy = line[b + 1] - line[a + 1], dz = line[b + 2] - line[a + 2];
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            for (int side = 0; side < sides; side++) {
                for (int corner : QUAD) {
                    int on = corner == 1 || corner == 2 ? 1 : 0;
                    boolean far = corner >= 2;
                    int p = far ? piece : piece - 1, r = p * 6;
                    float cos = cosAround[side + on], sin = sinAround[side + on];
                    float outX = ring[r] * cos + ring[r + 3] * sin, outY = ring[r + 1] * cos + ring[r + 4] * sin, outUp = ring[r + 2] * cos + ring[r + 5] * sin;
                    float thick = line[p * 4 + 3];
                    mesh.faceUnit(outX, outY, outUp);
                    mesh.sway = limbSway(line[p * 4 + 2] - base, boleTall);
                    mesh.wood = woodCode(thick, snowed, p < points - 1 || carriedOn);
                    mesh.add(line[p * 4] + outX * thick, line[p * 4 + 1] + outY * thick, line[p * 4 + 2] + outUp * thick,
                            across + (side + on) * 0.25f / sides, up + (run + (far ? length : 0f)) / span, frame.red, frame.green, frame.blue);
                }
            }
            run += length;
        }
        mesh.wood = 0f;
    }

    /**
     * What a twig's texture coordinate across has added, which the shadow maps read as wood too
     * fine to throw a shadow a texel of them could draw, and leave out: see PipelineShaders.SHADOW_VERTEX.
     */
    static final float FINE_MARK = BARK_ACROSS * 2 * BARK_WRAPS_MOST * BARK_SHIFTS;

    private static float fineMark() {
        return fineOutOfShadow ? FINE_MARK : 0f;
    }

    /** How far up a bough's swell the crown's outside is at an angle round it and a share of the way up it: 0 in a hollow, 1 on a bough's top. */
    private static float bough(float round, float share, int lobes, float rows, float turnOne, float turnTwo, float turnThree) {
        float about = (float) Math.sin(lobes * round + turnOne + 2f * Math.sin(4f * share + turnTwo));
        float along = (float) Math.sin(Math.PI * rows * share + turnThree + 1.2f * Math.sin(round + turnTwo));
        return 0.5f + 0.5f * about * along;
    }

    /**
     * Which bark a tree has, by its sprite's name and which tree of its kind it is: most
     * species have one of their own, and the rest have one of two or three.
     */
    static int barkOf(String name, int seed) {
        return barkOf(barkedAs(name), seed);
    }

    /**
     * Which bark a tree's sprite's name says it has: the bark itself where its species has one
     * of its own, or which of the choices between two or three barks it has, each below 0.
     */
    static final int BARKED_AS_MAPLE = -1, BARKED_AS_YELLOWWOOD = -2, BARKED_AS_REDBUD = -3, BARKED_AS_ANY = -4;

    static int barkedAs(String name) {
        return name.contains("holly") ? BARK_HOLLY : name.contains("hemlock") ? BARK_HEMLOCK : name.contains("pine") ? BARK_PINE
                : name.contains("birch") ? BARK_BIRCH : name.contains("hawthorn") || name.contains("dogwood") ? BARK_DOGWOOD
                : name.contains("maple") ? BARKED_AS_MAPLE : name.contains("yellowwood") ? BARKED_AS_YELLOWWOOD
                : name.contains("redbud") ? BARKED_AS_REDBUD : BARKED_AS_ANY;
    }

    /** barkOf for a tree whose name barkedAs says that. */
    static int barkOf(int barkedAs, int seed) {
        int one = (seed >>> 9 & 0xff) % 3;
        return switch (barkedAs) {
            case BARKED_AS_MAPLE -> one == 0 ? BARK_OAK : BARK_MAPLE;
            case BARKED_AS_YELLOWWOOD -> one == 0 ? BARK_LINDEN : BARK_HOLLY;
            case BARKED_AS_REDBUD -> one == 0 ? BARK_MAPLE : BARK_DOGWOOD;
            case BARKED_AS_ANY -> one == 0 ? BARK_OAK : one == 1 ? BARK_MAPLE : BARK_LINDEN;
            default -> barkedAs;
        };
    }

    /**
     * Whether a tree, by its sprite's name, keeps its needles or leaves all year: the game paints
     * those with the trunk, in the tree's own sprite, and lays no leaves over it.
     */
    static boolean evergreen(String name) {
        return name != null && (name.contains("pine") || name.contains("hemlock") || name.contains("holly"));
    }

    /** Whether a sprite, by its name, is one of the game's dead trees: a bare trunk and branches the game draws as no tree. */
    static boolean deadTree(String name) {
        int place = name == null ? -1 : Sheets.place(name, "fencing_burnt_01_");
        return place >= 16 && place <= 19;
    }

    /**
     * Whether a sprite, by its name, is one of the wild plants the game paints as a clump of
     * broad leaves: of the weeds the map's erosion grows, the leafy ones, which are small bushes.
     * Its seedlings, its dried plants and its flowers are none.
     */
    static boolean leafyWeed(String name) {
        int place = name == null ? -1 : Sheets.place(name, "d_plants_1_");
        return place >= 16 && place <= 23 || place >= 32 && place <= 39 || place >= 48 && place <= 55 || place == 64;
    }

    /** Whether a sprite, by its name, is one of the ferns the game paints among its wild plants. */
    static boolean fernSprite(String name) {
        int place = name == null ? -1 : Sheets.place(name, "d_generic_1_");
        return place >= 48 && place <= 55 || place >= 80 && place <= 87;
    }

    /** Which sprig a plant bears, by its sprite's name: the game names a tree's sprites for its species. */
    static int sprigOf(String name, boolean bush) {
        if (bush) {
            return SHRUB;
        }
        return name.contains("holly") ? HOLLY : name.contains("hemlock") ? HEMLOCK : name.contains("pine") ? PINE : name.contains("maple") ? MAPLE
                : name.contains("birch") || name.contains("hawthorn") || name.contains("dogwood") ? BIRCH : LINDEN;
    }

    private static void mark(Mesh mesh, float open, float kind, float material) {
        mesh.open = open + kind + 32f * material;
    }

    /**
     * Litter over a piece of the ground: a convex polygon of count corners, the first count of
     * x and y on the map, each at its height lift and in its light red, green and blue, laid over
     * the ground and mixed into it. Each corner carries how deep fallen leaf lies there at the end
     * of autumn and how deep needles lie, which the world shader shows as much of as the season
     * says, and the leaf's kind (Litter.LEAF and on) in steps of LITTER_KIND_STEP above its depth,
     * the same at every corner.
     */
    static void addLitter(Mesh mesh, int count, float[] x, float[] y, float[] lift, float[] leaf, float[] needle, byte kind,
            float[] red, float[] green, float[] blue, float open) {
        mark(mesh, open, 0f, LITTER);
        // Newell's way to the facing of a polygon, which is any of its triangles' for one that is flat.
        float nx = 0f, ny = 0f, nz = 0f;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            nx += (y[i] - y[j]) * (lift[i] + lift[j]);
            ny += (lift[i] - lift[j]) * (x[i] + x[j]);
            nz += (x[i] - x[j]) * (y[i] + y[j]);
        }
        mesh.face(nz < 0f ? -nx : nx, nz < 0f ? -ny : ny, Math.abs(nz));
        for (int i = 1; i < count - 1; i++) {
            litterCorner(mesh, 0, x, y, lift, leaf, needle, kind, red, green, blue);
            litterCorner(mesh, i, x, y, lift, leaf, needle, kind, red, green, blue);
            litterCorner(mesh, i + 1, x, y, lift, leaf, needle, kind, red, green, blue);
        }
    }

    private static void litterCorner(Mesh mesh, int corner, float[] x, float[] y, float[] lift, float[] leaf, float[] needle, byte kind,
            float[] red, float[] green, float[] blue) {
        mesh.add(x[corner], y[corner], lift[corner], LITTER_KIND_STEP * kind + leaf[corner], needle[corner], red[corner], green[corner], blue[corner]);
    }

    /** A litter vertex's coordinate across carries its leaf's kind in steps of this above its depth. The world shader has the same number. */
    static final float LITTER_KIND_STEP = 2f;

    private static final int[] QUAD = {0, 1, 2, 0, 2, 3};

    /** Whether a sprite, by its name, is fallen twigs or branches the game paints lying on the ground. */
    static boolean fallenWood(String name) {
        int place = name == null ? -1 : Sheets.place(name, "d_generic_1_");
        return place == 8 || place >= 11 && place <= 15 || place == 44 || place == 45;
    }

    /**
     * Fallen sticks lying on the square (x, y), whose ground is base and as high again at its
     * corners as lift says, in the corner order of SpriteGeometry.lift: a few branches, each with
     * a fork off it, in bark, sunk a little into the ground they lie on. Returns how many sticks
     * it laid.
     */
    static int addFallenWood(Mesh wood, int x, int y, float base, float[] lift, int seed, SpriteGeometry.Frame frame) {
        mark(wood, frame.open, 0f, TRUNK);
        int sticks = FALLEN_STICKS_LEAST + (seed >>> 28 & 3);
        for (int stick = 0; stick < sticks; stick++) {
            int own = mix(seed, stick, 98);
            float length = 0.35f + 0.55f * unit(own, 0), thick = 0.012f + 0.02f * unit(own, 8);
            double turn = unit(own, 16) * Math.PI;
            float halfX = (float) Math.cos(turn) * length / 2f, halfY = (float) Math.sin(turn) * length / 2f;
            float midA = within(0.15f + 0.7f * unit(own, 4), Math.abs(halfX), 0.05f, 0.95f), midB = within(0.15f + 0.7f * unit(own, 12), Math.abs(halfY), 0.05f, 0.95f);
            float fromA = midA - halfX, fromB = midB - halfY, toA = midA + halfX, toB = midB + halfY;
            // Lying on the ground with its thicker end a little up on something: sunk by half its thickness.
            float fromUp = base + ground(lift, fromA, fromB) + thick * (0.5f + 1.5f * unit(own, 20)), toUp = base + ground(lift, toA, toB) + thick * 0.4f;
            int bark = barkOf("", own);
            prism(wood, x + fromA, y + fromB, fromUp, x + toA, y + toB, toUp, thick, thick * 0.4f, LIMB_SIDES, BARK_UP * bark + WOOD_UP, 0f, 0f, own, frame);
            // A fork off it part of the way along, lying on the ground too.
            float along = 0.3f + 0.4f * unit(own, 24);
            float forkA = fromA + (toA - fromA) * along, forkB = fromB + (toB - fromB) * along, forkUp = fromUp + (toUp - fromUp) * along;
            double off = turn + ((own >>> 7 & 1) == 0 ? 0.6 : -0.6);
            float forkLength = length * (0.25f + 0.25f * unit(own, 28));
            float endA = Math.clamp(forkA + (float) Math.cos(off) * forkLength, 0.02f, 0.98f), endB = Math.clamp(forkB + (float) Math.sin(off) * forkLength, 0.02f, 0.98f);
            prism(wood, x + forkA, y + forkB, forkUp, x + endA, y + endB, base + ground(lift, endA, endB) + TWIG_TIP, thick * 0.5f, TWIG_TIP, TWIG_SIDES,
                    BARK_UP * bark + WOOD_UP, 0f, 0f, own + 1, frame);
        }
        wood.sway = 0f;
        return sticks;
    }

    /** How high the ground is at (a, b) of a square whose corners are as high as lift says. */
    private static float ground(float[] lift, float a, float b) {
        return (lift[0] * (1f - a) + lift[1] * a) * (1f - b) + (lift[3] * (1f - a) + lift[2] * a) * b;
    }

    /**
     * Which way a bole faces at each place round it, worked out once: a tree has hundreds of
     * vertices and every one of them asks.
     */
    private static final float[] NEAR_COS = round(TRUNK_SIDES, 0.0, false), NEAR_SIN = round(TRUNK_SIDES, 0.0, true);
    private static final float[] FAR_COS = round(TRUNK_SIDES_FAR, 0.0, false), FAR_SIN = round(TRUNK_SIDES_FAR, 0.0, true);
    /** The same half a side on, where the faces of a near bole run up to its point. */
    private static final float[] SPIRE_COS = round(TRUNK_SIDES, 0.5, false), SPIRE_SIN = round(TRUNK_SIDES, 0.5, true);
    /** The same for wood in a crown and on the ground, by how many sides it has. */
    private static final float[][] WOOD_COS = new float[LEADER_SIDES + 1][], WOOD_SIN = new float[LEADER_SIDES + 1][];

    static {
        for (int sides = TWIG_SIDES; sides <= LEADER_SIDES; sides++) {
            WOOD_COS[sides] = round(sides, 0.0, false);
            WOOD_SIN[sides] = round(sides, 0.0, true);
        }
    }

    private static float[] round(int sides, double from, boolean sine) {
        float[] out = new float[sides + 1];
        for (int around = 0; around <= sides; around++) {
            double angle = (around + from) * 2.0 * Math.PI / sides;
            out[around] = (float) (sine ? Math.sin(angle) : Math.cos(angle));
        }
        return out;
    }

    /**
     * A flower standing tall high on ground at height ground, its head over (x, y): a square
     * card tipped to face half upward and half against the way turn points, since it is looked
     * down on from one side or another, on a stalk that leans the way the head nods. The stalk
     * is a card that turns to the eye, with the stalk drawn down its middle. Four triangles.
     *
     * thinned is which of the FLOWER_THINNINGS steps of distance the flower is left out of the
     * picture from. Head and stalk carry it, so the two go together.
     */
    static void addFlower(Mesh mesh, float x, float y, float ground, float tall, float half, double turn, int kind, int thinned,
            SpriteGeometry.Frame frame) {
        float wayX = (float) Math.cos(turn), wayY = (float) Math.sin(turn);
        float acrossX = -wayY * half, acrossY = wayX * half;
        float tipX = wayX * half * TIPPED_ALONG, tipY = wayY * half * TIPPED_ALONG, tipUp = half * TIPPED_UP;
        float up = ground + tall;
        mark(mesh, frame.open, SpriteGeometry.CARD, FLOWER);
        mesh.face(-wayX * TIPPED_UP, -wayY * TIPPED_UP, TIPPED_ALONG);
        mesh.sway = 1f;
        float pick = PICK_STEP * kind, thins = PICK_STEP * thinned;
        for (int corner : QUAD) {
            float a = corner == 1 || corner == 2 ? 1f : -1f, b = corner >= 2 ? 1f : -1f;
            mesh.add(x + acrossX * a + tipX * b, y + acrossY * a + tipY * b, up + tipUp * b, pick + (a + 1f) / 2f, thins + (b + 1f) / 2f,
                    frame.red, frame.green, frame.blue);
        }
        float footX = x + wayX * tall * STALK_LEANS, footY = y + wayY * tall * STALK_LEANS;
        mesh.faces = true;
        for (int corner : QUAD) {
            boolean top = corner >= 2;
            float a = corner == 1 || corner == 2 ? 1f : -1f;
            mesh.sway = top ? 1f : 0f;
            mesh.along = a * half * STALK_CARD;
            mesh.add(top ? x : footX, top ? y : footY, top ? up : ground - STALK_SUNK, PICK_STEP * STALK + (a + 1f) / 2f, thins + (top ? 1f : 0f),
                    frame.red, frame.green, frame.blue);
        }
        mesh.faces = false;
        mesh.along = 0f;
        mesh.sway = 0f;
    }

    private static final float TIPPED_ALONG = 0.766f, TIPPED_UP = 0.643f;
    /** How far a stalk's foot stands from under its head, as a share of the flower's height. */
    static final float STALK_LEANS = 0.12f;
    /**
     * Half the width of a stalk's card, as a share of half the head's: the stalk itself is the
     * middle quarter of it, and the world shader draws it wider than that where a quarter of
     * the card is finer than the picture can draw.
     */
    static final float STALK_CARD = 0.4f;
    /** How far below the ground a stalk starts, in tiles: the ground under it slopes. */
    static final float STALK_SUNK = 0.02f;

    /**
     * One leaf growing out from (x, y) at height up the way turn points, rising at the angle
     * rise: a weed's leaf, or a fallen one lying nearly flat, a twig or a cone. Two triangles.
     * step is from which step of Litter's spread it shows, 0 for always.
     */
    static void addLeaf(Mesh mesh, float x, float y, float up, float length, float width, double turn, double rise, int kind, int step,
            float sway, SpriteGeometry.Frame frame) {
        float wayX = (float) Math.cos(turn), wayY = (float) Math.sin(turn);
        float along = length * (float) Math.cos(rise), climb = length * (float) Math.sin(rise);
        float acrossX = -wayY * width / 2f, acrossY = wayX * width / 2f;
        mark(mesh, frame.open, SpriteGeometry.CARD, LEAF);
        mesh.face(-wayX * climb, -wayY * climb, along);
        float pick = PICK_STEP * kind;
        for (int corner : QUAD) {
            float a = corner == 1 || corner == 2 ? 1f : -1f, b = corner >= 2 ? 1f : 0f;
            mesh.sway = sway * b;
            mesh.add(x + acrossX * a + wayX * along * b, y + acrossY * a + wayY * along * b, up + climb * b, pick + (a + 1f) / 2f, PICK_STEP * step + b,
                    frame.red, frame.green, frame.blue);
        }
        mesh.sway = 0f;
    }

    /**
     * How far out from its root a fern of that size reaches at most along the line of a frond:
     * its longest frond climbing at its least steep and arching level. The width of the
     * frond's picture either side of its line is not counted.
     */
    static float fernReach(float size) {
        return size * 1.1f * (0.5f * (float) Math.cos(Math.toRadians(50.0)) + 0.5f);
    }

    /**
     * A fern at (x, y) on ground at height up: fronds growing out from one root all round,
     * each climbing steeply and then arching over level, so that looked down on it is a crown
     * of fronds seen flat and from the side a low mound. Four triangles a frond.
     */
    static int addFern(Mesh mesh, float x, float y, float up, float size, int fronds, int seed, SpriteGeometry.Frame frame) {
        mark(mesh, frame.open, SpriteGeometry.CARD, FROND);
        for (int frond = 0; frond < fronds; frond++) {
            int own = mix(seed, frond, 17);
            double turn = (frond + unit(own, 0) * 0.7) / fronds * 2.0 * Math.PI + unit(seed, 8) * Math.PI;
            float length = size * (0.75f + 0.35f * unit(own, 8));
            float wayX = (float) Math.cos(turn), wayY = (float) Math.sin(turn);
            boolean mirrored = (own >>> 16 & 1) == 1;
            float side = mirrored ? -1f : 1f;
            float acrossX = -wayY * side * length * FROND_WIDE, acrossY = wayX * side * length * FROND_WIDE;
            double steep = Math.toRadians(50.0 + 20.0 * unit(own, 17)), level = Math.toRadians(-8.0 + 20.0 * unit(own, 24));
            float midOut = length / 2f * (float) Math.cos(steep), midUp = length / 2f * (float) Math.sin(steep);
            float tipOut = midOut + length / 2f * (float) Math.cos(level), tipUp = midUp + length / 2f * (float) Math.sin(level);
            mesh.face(-wayX * 0.5f, -wayY * 0.5f, 1f);
            frondBand(mesh, x, y, up, wayX, wayY, acrossX, acrossY, 0f, 0f, midOut, midUp, 0f, 0.5f, frame);
            mesh.face(0f, 0f, 1f);
            frondBand(mesh, x, y, up, wayX, wayY, acrossX, acrossY, midOut, midUp, tipOut, tipUp, 0.5f, 1f, frame);
        }
        mesh.sway = 0f;
        return fronds * 4;
    }

    private static void frondBand(Mesh mesh, float x, float y, float up, float wayX, float wayY, float acrossX, float acrossY,
            float out0, float up0, float out1, float up1, float v0, float v1, SpriteGeometry.Frame frame) {
        for (int corner : QUAD) {
            boolean far = corner >= 2, right = corner == 1 || corner == 2;
            float out = far ? out1 : out0, climb = far ? up1 : up0, v = far ? v1 : v0;
            float across = right ? 1f - FROND_ROOT : -FROND_ROOT;
            mesh.sway = FROND_SWAY * v;
            mesh.add(x + wayX * out + acrossX * across, y + wayY * out + acrossY * across, up + climb, right ? 1f : 0f, v,
                    frame.red, frame.green, frame.blue);
        }
    }

    /**
     * The bole of a tree standing on (x, y), its foot on ground at height base: round, radius
     * thick a little way up and thinner at its top, height tall. Near the eye it spreads into
     * roots at the ground and runs on above its height to a point; further off it is a plain
     * six-sided post.
     *
     * It is covered in the bark that is bark's picture, at the same size round it as up it
     * and near enough the same size on a sapling as on a great tree: so many quarters of the
     * picture go round as come nearest BARK_SPAN, and the shader joins the two ends where they
     * meet. No two boles show the same piece of it the same way: where round the bole the
     * picture starts, which way up it is and where on it the foot is are each tree's own.
     * Returns the triangles added.
     */
    int addTrunk(Mesh mesh, float x, float y, float base, float radius, float height, boolean near, int bark, int seed, SpriteGeometry.Frame frame) {
        return addTrunk(mesh, x, y, base, radius, height, near, true, bark, seed, frame);
    }

    /** The same, where spire is false with a low dome over the bole's top in place of the point: stems part from there (see TreeForm.footed). */
    int addTrunk(Mesh mesh, float x, float y, float base, float radius, float height, boolean near, boolean spire, int bark, int seed, SpriteGeometry.Frame frame) {
        int sides = near ? TRUNK_SIDES : TRUNK_SIDES_FAR;
        int rings = rings(x, y, radius, height, near, seed, 0f);
        float around = 2f * (float) Math.PI * radius;
        int quarters = Math.clamp(Math.round(around / BARK_SPAN * 4f), 1, BARK_WRAPS_MOST);
        float wraps = quarters / 4f, span = around / wraps;
        int own = mix(seed, bark, 19);
        int turn = (own >>> 8 & 0xff) * sides >>> 8;
        float across = BARK_ACROSS * (quarters - 1 + BARK_WRAPS_MOST * (own >>> 16 & 1) + 2 * BARK_WRAPS_MOST * (own >>> 17 & BARK_SHIFTS - 1));
        // How far up the bark each ring is, measured along the bole's own surface: the roots run out more than they rise.
        float out = 0f;
        for (int ring = 0; ring < rings; ring++) {
            float mean = 0f;
            for (int side = 0; side < sides; side++) {
                int at = ring * (TRUNK_SIDES + 1) + side;
                mean += (float) Math.hypot(ringX[at] - x, ringY[at] - y) / sides;
            }
            ringRun[ring] = ring == 0 ? 0f : ringRun[ring - 1] + (float) Math.hypot(ringUp[ring] - ringUp[ring - 1], mean - out);
            out = mean;
        }
        float up = BARK_UP * bark;
        float[] cos = near ? NEAR_COS : FAR_COS, sin = near ? NEAR_SIN : FAR_SIN;
        mark(mesh, frame.open, 0f, TRUNK);
        mesh.sway = 0f;
        for (int ring = 0; ring + 1 < rings; ring++) {
            for (int side = 0; side < sides; side++) {
                for (int corner : QUAD) {
                    int on = corner == 1 || corner == 2 ? 1 : 0, above = corner >= 2 ? 1 : 0;
                    int place = (side + turn) % sides + on;
                    int at = (ring + above) * (TRUNK_SIDES + 1) + place;
                    mesh.face(cos[place], sin[place], ringSlope[at]);
                    mesh.add(ringX[at], ringY[at], base + ringUp[ring + above], across + (side + on) * wraps / sides, up + ringRun[ring + above] / span,
                            frame.red, frame.green, frame.blue);
                }
            }
        }
        if (!near) {
            return (rings - 1) * sides * 2;
        }
        // A bole does not end in a flat top like a stump. It runs on up into the crown, thinning
        // to nothing, and the lowest boughs hide where: many trees stand lower than the eye, and
        // their tops are looked down on.
        int top = (rings - 1) * (TRUNK_SIDES + 1);
        float rise = spire ? height * SPIRE : radius * TAPER * 0.6f;
        float topUp = ringUp[rings - 1], tip = topUp + rise;
        float lean = radius * TAPER / rise;
        float tipRun = ringRun[rings - 1] + (float) Math.hypot(rise, radius * TAPER);
        for (int side = 0; side < sides; side++) {
            int place = (side + turn) % sides;
            mesh.face(SPIRE_COS[place], SPIRE_SIN[place], lean);
            mesh.add(x, y, base + tip, across + (side + 0.5f) * wraps / sides, up + tipRun / span, frame.red, frame.green, frame.blue);
            for (int next = 0; next <= 1; next++) {
                mesh.face(cos[place + next], sin[place + next], lean);
                mesh.add(ringX[top + place + next], ringY[top + place + next], base + topUp, across + (side + next) * wraps / sides,
                        up + ringRun[rings - 1] / span, frame.red, frame.green, frame.blue);
            }
        }
        return (rings - 1) * sides * 2 + sides;
    }

    /**
     * Ivy up the bole addTrunk makes from the same numbers: a sleeve just clear of the bark
     * from above the roots to tall, cut into leaves by the picture, which also frays its ends.
     * Each vertex says how far up the sleeve it is where a plant says how far it sways.
     */
    int addIvy(Mesh mesh, float x, float y, float base, float radius, float height, float tall, int seed, SpriteGeometry.Frame frame) {
        int rings = rings(x, y, radius, height, true, seed, IVY_STANDS_OFF);
        float girth = 2f * (float) Math.PI * radius;
        int repeats = Math.max(1, Math.round(girth / IVY_SPAN));
        // The picture goes round a whole number of times, and is as large up the bole as that makes it round it.
        float span = girth / repeats;
        mark(mesh, frame.open, 0f, IVY);
        float foot = ringUp[1];
        for (int ring = 1; ring + 1 < rings; ring++) {
            for (int side = 0; side < TRUNK_SIDES; side++) {
                for (int corner : QUAD) {
                    int upper = corner >= 2 ? 1 : 0, around = side + (corner == 1 || corner == 2 ? 1 : 0);
                    int at = (ring + upper) * (TRUNK_SIDES + 1) + around;
                    // The top ring is brought down to where the ivy ends, along the bole's own line.
                    float climb = Math.min(ringUp[ring + upper], tall);
                    float share = ring + upper == rings - 1 ? (climb - ringUp[ring]) / (ringUp[ring + 1] - ringUp[ring]) : 1f;
                    int below = ring * (TRUNK_SIDES + 1) + around;
                    float px = upper == 1 ? ringX[below] + (ringX[at] - ringX[below]) * share : ringX[at];
                    float py = upper == 1 ? ringY[below] + (ringY[at] - ringY[below]) * share : ringY[at];
                    mesh.face(NEAR_COS[around], NEAR_SIN[around], ringSlope[at]);
                    mesh.sway = Math.clamp((climb - foot) / (tall - foot), 0f, 1f);
                    mesh.add(px, py, base + climb, (float) around * repeats / TRUNK_SIDES, climb / span, frame.red, frame.green, frame.blue);
                }
            }
        }
        mesh.sway = 0f;
        return (rings - 2) * TRUNK_SIDES * 2;
    }

    /**
     * The ivy that climbs a bole, spread over the ground round its foot as well: a patch lying
     * on the ground from the bole out to about reach, further in some ways than others, thick at
     * the bole and going out in sprays at its rim, cut into leaves by the same picture as the
     * ivy up the bole. (x, y) is the bole's middle, on the square whose top-left corner is
     * (squareX, squareY), whose ground is base and as high again at its corners as lift says, in
     * the corner order of SpriteGeometry.lift. It keeps within lowX..highX and lowY..highY.
     * Returns the triangles added.
     */
    static int addGroundIvy(Mesh mesh, float x, float y, int squareX, int squareY, float base, float[] lift, float radius, float reach,
            float lowX, float highX, float lowY, float highY, int seed, SpriteGeometry.Frame frame) {
        mark(mesh, frame.open, 0f, IVY);
        mesh.face(0f, 0f, 1f);
        float[] rim = new float[GROUND_IVY_SIDES + 1];
        double turnOne = unit(seed, 0) * 2.0 * Math.PI, turnTwo = unit(seed, 8) * 2.0 * Math.PI;
        int lobes = 2 + (seed >>> 16 & 1);
        for (int side = 0; side <= GROUND_IVY_SIDES; side++) {
            double round = side * 2.0 * Math.PI / GROUND_IVY_SIDES;
            // Runners reach out further one way than another: a lobe or two, and a ragged edge between.
            float lobe = (float) (0.5 + 0.5 * Math.sin(lobes * round + turnOne)) * (float) (0.75 + 0.25 * Math.sin(5.0 * round + turnTwo));
            rim[side] = side == GROUND_IVY_SIDES ? rim[0] : reach * (0.45f + 0.55f * lobe) * (0.85f + 0.3f * unit(mix(seed, side, 99), 0));
        }
        for (int ring = 0; ring < GROUND_IVY_RINGS; ring++) {
            for (int side = 0; side < GROUND_IVY_SIDES; side++) {
                for (int corner : QUAD) {
                    int out = ring + (corner >= 2 ? 1 : 0), at = side + (corner == 1 || corner == 2 ? 1 : 0);
                    float share = (float) out / GROUND_IVY_RINGS;
                    double round = at * 2.0 * Math.PI / GROUND_IVY_SIDES;
                    float from = radius + GROUND_IVY_STANDS_OFF + (rim[at] - radius) * share;
                    float px = Math.clamp(x + (float) Math.cos(round) * from, lowX, highX), py = Math.clamp(y + (float) Math.sin(round) * from, lowY, highY);
                    // Heaped a little against the bole, and lying on the ground at its rim.
                    float up = base + ground(lift, Math.clamp(px - squareX, 0f, 1f), Math.clamp(py - squareY, 0f, 1f)) + GROUND_IVY_LIFT + GROUND_IVY_HEAP * (1f - share);
                    mesh.sway = GROUND_IVY_THINS * share;
                    mesh.add(px, py, up, px / IVY_SPAN, py / IVY_SPAN, frame.red, frame.green, frame.blue);
                }
            }
        }
        mesh.sway = 0f;
        return GROUND_IVY_RINGS * GROUND_IVY_SIDES * 2;
    }

    /**
     * Works out the rings of a bole, each clear further out than the bark, into ringX, ringY,
     * ringUp and ringSlope, the last being how far each ring's surface leans back from upright,
     * as the up part of its normal. A ring has a vertex more than it has sides: the first
     * again, to close it. Returns how many rings.
     */
    private int rings(float x, float y, float radius, float height, boolean near, int seed, float clear) {
        int sides = near ? TRUNK_SIDES : TRUNK_SIDES_FAR;
        int rings = near ? RINGS : 2;
        int roots = 3 + (seed >>> 4 & 1);
        // The first root runs out along one of the bole's corners, and so then do the rest: between corners a root has no ridge to it.
        double first = Math.floor(unit(seed, 8) * TRUNK_SIDES) * 2.0 * Math.PI / TRUNK_SIDES;
        if (near) {
            ringUp[0] = -SUNK;
            ringUp[1] = Math.min(0.14f, height * 0.2f);
            ringUp[2] = Math.min(0.42f, height * 0.45f);
            ringUp[3] = height;
        } else {
            ringUp[0] = -SUNK;
            ringUp[1] = height;
        }
        for (int ring = 0; ring < rings; ring++) {
            for (int side = 0; side <= sides; side++) {
                double angle = (side % sides) * 2.0 * Math.PI / sides;
                float wide;
                if (!near) {
                    wide = ring == 0 ? 1.1f : TAPER;
                } else if (ring >= 2) {
                    wide = ring == 2 ? 1f : TAPER;
                } else {
                    // A root is where the bole runs out along the ground: every tree has three or
                    // four, each its own length.
                    double turned = (angle - first) * roots / (2.0 * Math.PI);
                    int root = Math.floorMod((int) Math.round(turned), roots);
                    float lobe = (float) Math.pow(0.5 + 0.5 * Math.cos((angle - first) * roots), 2.0) * (0.55f + 0.45f * unit(mix(seed, root, 18), 0));
                    wide = ring == 0 ? 1.25f + 1.3f * lobe : 1.08f + 0.42f * lobe;
                }
                int at = ring * (TRUNK_SIDES + 1) + side;
                float out = radius * wide + clear;
                ringX[at] = x + (float) Math.cos(angle) * out;
                ringY[at] = y + (float) Math.sin(angle) * out;
                ringSlope[at] = out;
            }
        }
        // Until here ringSlope holds each vertex's distance out. A ring leans as the band above
        // it does, and the top ring as the band below.
        for (int ring = 0; ring < rings; ring++) {
            int lower = Math.min(ring, rings - 2);
            float rise = ringUp[lower + 1] - ringUp[lower];
            for (int side = 0; side <= sides; side++) {
                int at = lower * (TRUNK_SIDES + 1) + side;
                float lean = (ringSlope[at] - ringSlope[at + TRUNK_SIDES + 1]) / rise;
                slopes[ring * (TRUNK_SIDES + 1) + side] = lean;
            }
        }
        System.arraycopy(slopes, 0, ringSlope, 0, rings * (TRUNK_SIDES + 1));
        return rings;
    }

    private final float[] slopes = new float[RINGS * (TRUNK_SIDES + 1)];

    /**
     * A boulder on (x, y), its foot on ground at height base: a ball of flat faces, longer one
     * way than the other and each corner pushed in or out its own amount, halfWide across at
     * the ground and tall high. Its middle is near the ground, so it is broadest where it
     * meets it and what is under the ground is not seen. Each face is lit partly as the flat it
     * is and partly as the round the whole is: all flat, a boulder is a cut gem. Returns the
     * triangles added: eighty near the eye and twenty further off.
     */
    int addRock(Mesh mesh, float x, float y, float base, float halfWide, float tall, boolean near, int seed, SpriteGeometry.Frame frame) {
        double turn = unit(seed, 0) * 2.0 * Math.PI;
        float cos = (float) Math.cos(turn), sin = (float) Math.sin(turn);
        float midUp = base + tall * ROCK_MIDDLE;
        // One broad lump to one side, the same for every corner, and a little of each corner's own.
        double lumpTurn = unit(seed, 8) * 2.0 * Math.PI;
        float lumpX = (float) Math.cos(lumpTurn), lumpY = (float) Math.sin(lumpTurn);
        for (int vertex = 0; vertex < BALL.length / 3; vertex++) {
            float ballX = BALL[vertex * 3], ballY = BALL[vertex * 3 + 1], ballUp = BALL[vertex * 3 + 2];
            float out = 0.82f + 0.14f * unit(mix(seed, vertex, 19), 0) + 0.12f * (ballX * lumpX + ballY * lumpY + 0.5f * ballUp);
            float bx = ballX * out * halfWide, by = ballY * out * halfWide * (0.7f + 0.3f * unit(seed, 16));
            rock[vertex * 3] = x + bx * cos - by * sin;
            rock[vertex * 3 + 1] = y + bx * sin + by * cos;
            rock[vertex * 3 + 2] = midUp + ballUp * out * tall * (1f - ROCK_MIDDLE);
        }
        int[] faces = near ? FINE_FACES : COARSE_FACES;
        mark(mesh, frame.open, 0f, ROCK);
        mesh.sway = 0f;
        for (int face = 0; face < faces.length; face += 3) {
            int a = faces[face] * 3, b = faces[face + 1] * 3, c = faces[face + 2] * 3;
            float ux = rock[b] - rock[a], uy = rock[b + 1] - rock[a + 1], uz = rock[b + 2] - rock[a + 2];
            float vx = rock[c] - rock[a], vy = rock[c + 1] - rock[a + 1], vz = rock[c + 2] - rock[a + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            // Outward, whichever way round the face's corners were listed.
            if (nx * (rock[a] - x) + ny * (rock[a + 1] - y) + nz * (rock[a + 2] - midUp) < 0f) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            float flat = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            for (int corner = 0; corner < 3; corner++) {
                int at = faces[face + corner] * 3;
                float rx = rock[at] - x, ry = rock[at + 1] - y, rz = rock[at + 2] - midUp;
                float round = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
                mesh.face(nx / flat + ROCK_ROUNDED * rx / round, ny / flat + ROCK_ROUNDED * ry / round, nz / flat + ROCK_ROUNDED * rz / round);
                mesh.add(rock[at], rock[at + 1], rock[at + 2], 0f, 0f, frame.red, frame.green, frame.blue);
            }
        }
        return faces.length / 3;
    }

    /** How far up a boulder its middle is, as a share of its height, and how much of the round goes into the light on each flat face. */
    private static final float ROCK_MIDDLE = 0.12f, ROCK_ROUNDED = 0.8f;

    /** The corners of a ball of eighty faces, of which the first twelve are the corners of the twenty-faced one it was cut from. */
    private static final float[] BALL;
    private static final int[] COARSE_FACES, FINE_FACES;

    static {
        float golden = (1f + (float) Math.sqrt(5.0)) / 2f;
        float[][] twelve = {
            {-1, golden, 0}, {1, golden, 0}, {-1, -golden, 0}, {1, -golden, 0}, {0, -1, golden}, {0, 1, golden},
            {0, -1, -golden}, {0, 1, -golden}, {golden, 0, -1}, {golden, 0, 1}, {-golden, 0, -1}, {-golden, 0, 1}};
        COARSE_FACES = new int[] {
            0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1};
        float[] ball = new float[(12 + 30) * 3];
        for (int vertex = 0; vertex < 12; vertex++) {
            put(ball, vertex, twelve[vertex][0], twelve[vertex][1], twelve[vertex][2]);
        }
        HashMap<Integer, Integer> middles = new HashMap<>();
        int[] fine = new int[COARSE_FACES.length * 4];
        int count = 12, at = 0;
        for (int face = 0; face < COARSE_FACES.length; face += 3) {
            int[] corner = {COARSE_FACES[face], COARSE_FACES[face + 1], COARSE_FACES[face + 2]};
            int[] middle = new int[3];
            for (int edge = 0; edge < 3; edge++) {
                int from = corner[edge], to = corner[(edge + 1) % 3];
                int key = Math.min(from, to) * 64 + Math.max(from, to);
                Integer known = middles.get(key);
                if (known == null) {
                    put(ball, count, ball[from * 3] + ball[to * 3], ball[from * 3 + 1] + ball[to * 3 + 1], ball[from * 3 + 2] + ball[to * 3 + 2]);
                    known = count++;
                    middles.put(key, known);
                }
                middle[edge] = known;
            }
            int[] four = {corner[0], middle[0], middle[2], corner[1], middle[1], middle[0], corner[2], middle[2], middle[1],
                middle[0], middle[1], middle[2]};
            System.arraycopy(four, 0, fine, at, four.length);
            at += four.length;
        }
        BALL = ball;
        FINE_FACES = fine;
    }

    /** Puts a corner on the ball: the point's direction from the middle, one unit out. */
    private static void put(float[] ball, int vertex, float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        ball[vertex * 3] = x / length;
        ball[vertex * 3 + 1] = y / length;
        ball[vertex * 3 + 2] = z / length;
    }
}
