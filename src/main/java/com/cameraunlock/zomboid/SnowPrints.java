package com.cameraunlock.zomboid;

/**
 * Where the players at this screen have walked and driven while snow lay, for SnowShader to
 * press into it: a footprint at every stride, left and right by turns, and a rut under each
 * side of a vehicle along the way it went. The marks are in the snow, and every view shows
 * all of them, whoever made them. Each walker's way is their own: see Trail.
 *
 * Only the newest are kept, {@link #PRINTS} footprints and {@link #RUTS} stretches of rut: the
 * oldest goes as a new one is made. Whoever says where a walker is forgets them all when
 * no snow lies any more, so the next fall is unmarked.
 *
 * The game's logic thread says where each walker is and the picture's thread reads the marks.
 */
final class SnowPrints {
    /** How many footprints and how many stretches of rut are kept: the lengths of SnowShader's uSnowPrint and uSnowRut. */
    static final int PRINTS = 48, RUTS = 32;
    /** A stride, and how far to either side of the walker's middle a foot lands, in tiles. */
    static final float STRIDE = 0.5f, STANCE = 0.07f;
    /** How long a stretch of rut is, in tiles: short enough that a bend is made of straight pieces without showing it. */
    static final float RUT = 0.625f;
    /** Further than this from where the viewer last was, in tiles, the viewer did not walk or drive there: a new save, a jump across the map. */
    static final float JUMP = 4f;
    /** How far from its place a footprint reaches, and how far from its middle line a rut does, in tiles, each with room to spare. */
    private static final float REACH_OF_PRINT = 0.3f, REACH_OF_RUT = 0.9f;

    /**
     * A footprint is its place on the map, the way the foot pointed in radians and how high
     * its floor is on the game's flat map: each view lifts it by the lie of its own land.
     */
    private static final float[] prints = new float[PRINTS * 4];
    /** A stretch of rut is the place it runs from and the place it runs to, down the middle of the vehicle. */
    private static final float[] ruts = new float[RUTS * 4];
    /** How high the floor under each stretch of rut is on the game's flat map. */
    private static final float[] rutsUp = new float[RUTS];
    private static int printNext, printCount, rutNext, rutCount;

    /** One walker's way: which foot is next, whether they are on foot or at the wheel, and where they last were. */
    private static final class Trail {
        boolean leftFoot, afoot, awheel;
        float lastX, lastY;
    }

    /** A trail for each player at this screen, by the player's number. */
    private static final Trail[] trails = new Trail[View.COUNT];

    static {
        for (int walker = 0; walker < trails.length; walker++) {
            trails[walker] = new Trail();
        }
    }

    private SnowPrints() {}

    /** A walker is on foot at that place, on a floor that high on the flat map. */
    static synchronized void walked(int walker, float x, float y, float floorUp) {
        Trail trail = trails[walker];
        float alongX = x - trail.lastX, alongY = y - trail.lastY;
        double away = Math.hypot(alongX, alongY);
        if (!trail.afoot || away > JUMP) {
            trail.afoot = true;
            trail.awheel = false;
            trail.lastX = x;
            trail.lastY = y;
            return;
        }
        float gone = (float) away;
        if (gone < STRIDE) {
            return;
        }
        float side = trail.leftFoot ? STANCE : -STANCE;
        int at = printNext * 4;
        prints[at] = x - alongY / gone * side;
        prints[at + 1] = y + alongX / gone * side;
        prints[at + 2] = (float) Math.atan2(alongY, alongX);
        prints[at + 3] = floorUp;
        printNext = (printNext + 1) % PRINTS;
        printCount = Math.min(printCount + 1, PRINTS);
        trail.leftFoot = !trail.leftFoot;
        trail.lastX = x;
        trail.lastY = y;
    }

    /** A walker is in a vehicle whose middle is at that place, on a floor that high on the flat map. */
    static synchronized void drove(int walker, float x, float y, float floorUp) {
        Trail trail = trails[walker];
        double gone = Math.hypot(x - trail.lastX, y - trail.lastY);
        if (!trail.awheel || gone > JUMP) {
            trail.awheel = true;
            trail.afoot = false;
            trail.lastX = x;
            trail.lastY = y;
            return;
        }
        if (gone < RUT) {
            return;
        }
        int at = rutNext * 4;
        ruts[at] = trail.lastX;
        ruts[at + 1] = trail.lastY;
        ruts[at + 2] = x;
        ruts[at + 3] = y;
        rutsUp[rutNext] = floorUp;
        rutNext = (rutNext + 1) % RUTS;
        rutCount = Math.min(rutCount + 1, RUTS);
        trail.lastX = x;
        trail.lastY = y;
    }

    /** How many marks are kept, in words, for the log. */
    static synchronized String said() {
        return printCount + " footprints and " + rutCount + " stretches of rut";
    }

    /** A walker is nowhere they leave a mark: the next place they are at begins a new walk. */
    static synchronized void lifted(int walker) {
        trails[walker].afoot = false;
        trails[walker].awheel = false;
    }

    /** Every mark is gone: the save was left, or the snow has gone. */
    static synchronized void forget() {
        printCount = 0;
        printNext = 0;
        rutCount = 0;
        rutNext = 0;
        for (int walker = 0; walker < trails.length; walker++) {
            lifted(walker);
        }
    }

    /**
     * The marks as the world's shader takes them, for a picture drawn from that eye on that
     * lie of the land: places are counted from the corner the shader's vPlace is, which moves
     * sixty tiles at a time.
     *
     * @param printsOut  PRINTS times x, y, the foot's way and the ground's height
     * @param rutsOut    RUTS times the x and y a stretch runs from and the x and y it runs to
     * @param rutsUpOut  RUTS times how high the ground under a stretch stands
     * @param rest       how many footprints, how many stretches of rut, and the least x and y
     *                   and the greatest x and y of a box that holds every mark, the least
     *                   over the greatest when there are none
     */
    static synchronized void placed(Terrain terrain, float eyeX, float eyeY, float[] printsOut, float[] rutsOut, float[] rutsUpOut, float[] rest) {
        // As the picture's mod(uEye, 60.0), which for an eye west or north of the map's first square is not what % gives.
        float cornerX = -60f * (float) Math.floor(eyeX / 60f), cornerY = -60f * (float) Math.floor(eyeY / 60f);
        float leastX = Float.MAX_VALUE, leastY = Float.MAX_VALUE, mostX = -Float.MAX_VALUE, mostY = -Float.MAX_VALUE;
        for (int i = 0; i < printCount * 4; i += 4) {
            float x = prints[i] + cornerX, y = prints[i + 1] + cornerY;
            printsOut[i] = x;
            printsOut[i + 1] = y;
            printsOut[i + 2] = prints[i + 2];
            printsOut[i + 3] = prints[i + 3] + terrain.height(prints[i], prints[i + 1]);
            leastX = Math.min(leastX, x - REACH_OF_PRINT);
            leastY = Math.min(leastY, y - REACH_OF_PRINT);
            mostX = Math.max(mostX, x + REACH_OF_PRINT);
            mostY = Math.max(mostY, y + REACH_OF_PRINT);
        }
        for (int i = 0; i < rutCount * 4; i += 2) {
            float x = ruts[i] + cornerX, y = ruts[i + 1] + cornerY;
            rutsOut[i] = x;
            rutsOut[i + 1] = y;
            leastX = Math.min(leastX, x - REACH_OF_RUT);
            leastY = Math.min(leastY, y - REACH_OF_RUT);
            mostX = Math.max(mostX, x + REACH_OF_RUT);
            mostY = Math.max(mostY, y + REACH_OF_RUT);
        }
        for (int i = 0; i < rutCount; i++) {
            rutsUpOut[i] = rutsUp[i] + terrain.height(ruts[i * 4 + 2], ruts[i * 4 + 3]);
        }
        rest[0] = printCount;
        rest[1] = rutCount;
        rest[2] = leastX;
        rest[3] = leastY;
        rest[4] = mostX;
        rest[5] = mostY;
    }
}
