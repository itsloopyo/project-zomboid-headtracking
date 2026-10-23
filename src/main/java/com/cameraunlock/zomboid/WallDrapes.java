package com.cameraunlock.zomboid;

/**
 * The red drape a church hangs down the wall behind its altar.
 *
 * The game paints it a square and a level at a time: the length with the gold fringe at its
 * foot (location_community_church_small_01_86 on a west wall, _76 on a north one), the plain
 * length over it a level up (_84), and in its place now and then a length with a cross on it
 * (_85, _77). Each sprite has folds of its own that start and stop at its square's ends and a
 * pale cord down its middle, so laid on its wall as painted a run of them was a row of panels
 * with a light stripe and a seam every square, and a seam again at the level line.
 *
 * So the foot length hangs one drape of the mod's own, from its foot to the top of the length
 * over it: the colour of the foot's sprite all the way up, folds placed by where they are along
 * the wall's line and not along the square, so one square's last fold runs on into the next
 * square's first, and the fringe along its foot. The plain length over it adds nothing. A
 * length with a cross on it is a banner hung over the drape, and stays its sprite, stood out in
 * front of the folds.
 */
final class WallDrapes {
    /** What a sprite is: none of these, the length at a drape's foot, the plain length over it, or a banner with a cross. */
    static final int NONE = 0, FOOT = 1, HEAD = 2, BANNER = 3;
    private static final String SHEET = "location_community_church_small_01_";

    /** How many folds a square's length of the drape hangs in: a whole number, so a square's ends are where folds meet. */
    static final int FOLDS = 6;
    /** How many strips a fold is made of. */
    static final int FOLD_STRIPS = 6;
    /**
     * How far the drape's plane hangs clear of the wall's skin, and how deep its deepest fold is either side of that
     * plane at its head, in tiles. Louisville's church has a scaffold standing on the drape's edge behind it
     * (construction_01_29 at 12755, 1670..1681): nearer the edge, its bars came through the folds.
     */
    static final float CLEAR = 0.12f, DEEP = 0.035f;
    /** How much deeper a fold is at the drape's foot than at its head: cloth spreads a little as it falls. */
    static final float SPREAD = 0.3f;
    /** How much of the foot length's height the fringe is, read off _86 and _76: 22 of the 162 dots their middle column paints. */
    static final float FRINGE = 0.135f;
    /** The fringe's colour for each of red, green and blue, as a share of the cloth's red: (104, 88, 48) under (112, 40, 40) on _86, (152, 128, 72) under (152, 64, 56) on _76. */
    static final float FRINGE_RED = 0.93f, FRINGE_GREEN = 0.79f, FRINGE_BLUE = 0.43f;
    /** How far in front of the drape's plane a banner over it hangs, past the deepest fold, in tiles. */
    static final float BANNER_PROUD = DEEP * (1f + SPREAD) + 0.01f;

    private WallDrapes() {}

    /** What a sprite is, by its name. */
    static int kind(String name) {
        return switch (name == null ? -1 : Sheets.place(name, SHEET)) {
            case 76, 86 -> FOOT;
            case 84 -> HEAD;
            case 77, 85 -> BANNER;
            default -> NONE;
        };
    }

    /**
     * How far the drape stands out of its plane at a place along its wall, in tiles, towards
     * the room: square is the square's place along the wall's line and t the share along it,
     * line names the line (see line), up is the share of the drape's height from its foot.
     * Every fold is a bulge out and a hollow in, and is in the plane at both its ends, so folds
     * that hang alike or not meet without a step, and the squares' ends are among those places.
     */
    static float out(long line, int square, float t, float up) {
        int inSquare = (int) Math.floor(t * FOLDS);
        long fold = (long) square * FOLDS + inSquare;
        float within = t * FOLDS - inSquare;
        float much = DEEP * (0.5f + 0.5f * chance(line * 0x9E3779B97F4A7C15L + fold));
        return much * (1f + SPREAD * (1f - up)) * (float) Math.sin(2.0 * Math.PI * within);
    }

    /** The line a wall is on, for out: by which wall, its place across the map and its level. */
    static long line(boolean north, int across, int z) {
        return ((long) across << 8 ^ z) << 1 | (north ? 1L : 0L);
    }

    private static float chance(long seed) {
        seed = (seed ^ seed >>> 33) * 0xFF51AFD7ED558CCDL;
        seed = (seed ^ seed >>> 33) * 0xC4CEB9FE1A85EC53L;
        return ((seed ^ seed >>> 33) >>> 40) / (float) (1L << 24);
    }
}
