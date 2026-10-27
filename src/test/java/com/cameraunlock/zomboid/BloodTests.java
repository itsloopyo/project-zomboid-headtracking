package com.cameraunlock.zomboid;

import java.util.Random;

/** Tests of where Blood lays a splat on a floor, how it cuts it to squares, and the colour and cover it gives blood. */
final class BloodTests {
    private static final int SCALE = 2;

    private BloodTests() {}

    static void run() {
        Checks.run("aSplatLiesOnTheFloorWhereTheGamesViewDrawsIt", BloodTests::aSplatLiesOnTheFloorWhereTheGamesViewDrawsIt);
        Checks.run("thePiecesOfASplatCoverItWithNoGapAndNoOverlap", BloodTests::thePiecesOfASplatCoverItWithNoGapAndNoOverlap);
        Checks.run("everyCornerOfAPieceTakesTheTexelThePictureHasThere", BloodTests::everyCornerOfAPieceTakesTheTexelThePictureHasThere);
        Checks.run("aSquareTheSplatMissesGetsNothing", BloodTests::aSquareTheSplatMissesGetsNothing);
        Checks.run("bloodDriesByTheHourOverThreeDays", BloodTests::bloodDriesByTheHourOverThreeDays);
        Checks.run("bloodCoversAsTheGamesDoesFreshAndDry", BloodTests::bloodCoversAsTheGamesDoesFreshAndDry);
        Checks.run("eachSplatIsAShadeOfItsOwnAndDarkensAsItDries", BloodTests::eachSplatIsAShadeOfItsOwnAndDarkensAsItDries);
        Checks.run("aShadeThatIsNoNumberIsNone", BloodTests::aShadeThatIsNoNumberIsNone);
        Checks.run("aTrailAgesInTheNextSaveThoughItsWorldIsYounger", BloodTests::aTrailAgesInTheNextSaveThoughItsWorldIsYounger);
    }

    /**
     * A save left at 500 game hours and one loaded at 100: the trail is aged once the world has
     * moved on a minute from when it last was, so what the last save left has to go with it.
     */
    private static void aTrailAgesInTheNextSaveThoughItsWorldIsYounger() throws ReflectiveOperationException {
        java.lang.reflect.Field aged = Bleeding.class.getDeclaredField("aged");
        aged.setAccessible(true);
        aged.setDouble(null, 500.0);
        Bleeding.forget();
        check(100.0 - aged.getDouble(null) >= 1.0 / 60.0, "the trail of a save loaded at 100 hours is aged after one left at 500: last aged at " + aged.getDouble(null));
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void near(double a, double b, double within, String what) {
        check(Math.abs(a - b) <= within, what + ": " + a + " against " + b);
    }

    /** A splat as laid, with a picture of the given size placed in its frame at (1, 3), and its texels at 0.2 to 0.6 across, 0.1 to 0.3 down. */
    private static float[] laid(float x, float y, float z, float width, float height) {
        float[] corners = new float[4 * Blood.CORNER];
        Blood.laid(x, y, z, SCALE, width, height, 1f, 3f, 0.2f, 0.1f, 0.6f, 0.3f, corners);
        return corners;
    }

    private static void aSplatLiesOnTheFloorWhereTheGamesViewDrawsIt() {
        Random random = new Random(7);
        for (int trial = 0; trial < 200; trial++) {
            float x = random.nextFloat() * 8f, y = random.nextFloat() * 8f, z = random.nextInt(3);
            float width = 20f + random.nextInt(100), height = 10f + random.nextInt(60);
            float[] corners = laid(x, y, z, width, height);
            // IsoSprite.renderBloodSplat's rectangle on the game's screen, from IsoUtils' projection.
            float pointU = (int) (x * 32f * SCALE - y * 32f * SCALE), pointV = (int) (y * 16f * SCALE + x * 16f * SCALE - z * 96f * SCALE);
            float left = pointU - width / 2f * SCALE + 1f, top = pointV - height / 2f * SCALE + 3f;
            float[][] expected = {{left, top}, {left + width, top}, {left + width, top + height}, {left, top + height}};
            for (int k = 0; k < 4; k++) {
                float onX = corners[k * Blood.CORNER], onY = corners[k * Blood.CORNER + 1];
                // The floor at the splat's height, seen by the game's view.
                double u = (onX - onY) * 32.0 * SCALE, v = ((onX + onY) * 16.0 - z * 96.0) * SCALE;
                near(u, expected[k][0], 1e-2, "corner " + k + " across");
                near(v, expected[k][1], 1e-2, "corner " + k + " down");
            }
        }
    }

    private static double area(float[] polygon, int count) {
        double twice = 0.0;
        for (int i = 0; i < count; i++) {
            int at = i * Blood.CORNER, next = (i + 1) % count * Blood.CORNER;
            twice += (double) polygon[at] * polygon[next + 1] - (double) polygon[next] * polygon[at + 1];
        }
        return Math.abs(twice) / 2.0;
    }

    private static void thePiecesOfASplatCoverItWithNoGapAndNoOverlap() {
        Random random = new Random(11);
        float[] piece = new float[Blood.MOST_CORNERS * Blood.CORNER], spare = new float[Blood.MOST_CORNERS * Blood.CORNER];
        for (int trial = 0; trial < 100; trial++) {
            float[] corners = laid(2f + random.nextFloat() * 4f, 2f + random.nextFloat() * 4f, 0f, 30f + random.nextInt(150), 20f + random.nextInt(90));
            double whole = area(corners, 4), pieces = 0.0;
            for (int x = -4; x < 12; x++) {
                for (int y = -4; y < 12; y++) {
                    int count = Blood.cut(corners, x, y, piece, spare);
                    if (count < 3) {
                        continue;
                    }
                    for (int k = 0; k < count; k++) {
                        float a = piece[k * Blood.CORNER], b = piece[k * Blood.CORNER + 1];
                        check(a >= -1e-5f && a <= 1f + 1e-5f && b >= -1e-5f && b <= 1f + 1e-5f, "a piece's corner is on its square: " + a + ", " + b);
                    }
                    pieces += area(piece, count);
                }
            }
            near(pieces, whole, whole * 1e-4, "the pieces' area is the splat's");
        }
    }

    private static void everyCornerOfAPieceTakesTheTexelThePictureHasThere() {
        float[] corners = laid(3.3f, 4.6f, 0f, 120f, 70f);
        // The picture's texel at a point of the floor, from the game's view: how far across and down its rectangle the point is.
        double left = (corners[0] - corners[1]) * 32.0 * SCALE, top = (corners[0] + corners[1]) * 16.0 * SCALE;
        float[] piece = new float[Blood.MOST_CORNERS * Blood.CORNER], spare = new float[Blood.MOST_CORNERS * Blood.CORNER];
        int pieces = 0;
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                int count = Blood.cut(corners, x, y, piece, spare);
                pieces += count >= 3 ? 1 : 0;
                for (int k = 0; k < count; k++) {
                    double onX = x + piece[k * Blood.CORNER], onY = y + piece[k * Blood.CORNER + 1];
                    double across = ((onX - onY) * 32.0 * SCALE - left) / 120.0, down = ((onX + onY) * 16.0 * SCALE - top) / 70.0;
                    near(piece[k * Blood.CORNER + 2], 0.2 + across * 0.4, 1e-4, "texel across");
                    near(piece[k * Blood.CORNER + 3], 0.1 + down * 0.2, 1e-4, "texel down");
                }
            }
        }
        check(pieces > 1, "the splat lies over more than one square: " + pieces);
    }

    private static void aSquareTheSplatMissesGetsNothing() {
        float[] corners = laid(3.5f, 3.5f, 0f, 40f, 20f);
        float[] piece = new float[Blood.MOST_CORNERS * Blood.CORNER], spare = new float[Blood.MOST_CORNERS * Blood.CORNER];
        check(Blood.cut(corners, 20, 20, piece, spare) < 3, "a square far off");
    }

    private static void bloodDriesByTheHourOverThreeDays() {
        near(Blood.freshness(0.0), 1.0, 0.0, "just spilt");
        near(Blood.freshness(0.99), 1.0, 0.0, "within its first hour");
        near(Blood.freshness(1.0), 1.0 - 1.0 / 72.0, 1e-6, "an hour old");
        near(Blood.freshness(71.9), 1.0 / 72.0, 1e-6, "the last hour");
        near(Blood.freshness(72.0), 0.0, 0.0, "three days old");
        near(Blood.freshness(500.0), 0.0, 0.0, "long dry");
        near(Blood.freshness(-1.0), 0.0, 0.0, "spilt after now, as the game has it");
    }

    private static void bloodCoversAsTheGamesDoesFreshAndDry() {
        near(Blood.floorCover(1f), 0.27, 1e-6, "fresh on a floor");
        near(Blood.floorCover(0f), 0.27 * 0.25, 1e-6, "dry on a floor");
        near(Blood.wallCover(1f), 0.4, 1e-6, "fresh on a wall");
        near(Blood.wallCover(0f), 0.15, 1e-6, "dry on a wall, which is no fainter than that");
        near(Blood.darkness(1f), 1.0, 1e-6, "fresh is its own colour");
        near(Blood.darkness(0f), 0.2, 1e-6, "dry is a fifth of it");
    }

    private static void eachSplatIsAShadeOfItsOwnAndDarkensAsItDries() {
        float[] fresh = new float[2], dry = new float[2], again = new float[2];
        Random random = new Random(3);
        int differing = 0;
        float[] last = null;
        for (int trial = 0; trial < 200; trial++) {
            float x = 0.01f + random.nextFloat() * 7.98f, y = 0.01f + random.nextFloat() * 7.98f;
            int type = random.nextInt(20);
            Blood.floorTint(x, y, type, 1f, fresh);
            Blood.floorTint(x, y, type, 1f, again);
            Blood.floorTint(x, y, type, 0f, dry);
            check(fresh[0] == again[0] && fresh[1] == again[1], "the same splat is the same shade");
            check(fresh[0] >= 0.5f - 1e-6f && fresh[0] <= 1f + 1f / 3f + 1e-6f, "red as the game's sum allows: " + fresh[0]);
            check(fresh[1] >= 0.5f - 1f / 3f - 1e-6f && fresh[1] <= 1f + 1e-6f, "green and blue as the game's sum allows: " + fresh[1]);
            near(dry[0], fresh[0] * 0.2, 1e-6, "dry red");
            near(dry[1], fresh[1] * 0.2, 1e-6, "dry green and blue");
            if (last != null && (last[0] != fresh[0] || last[1] != fresh[1])) {
                differing++;
            }
            last = fresh.clone();
        }
        check(differing > 150, "splats are shades of their own: " + differing);
    }

    private static void aShadeThatIsNoNumberIsNone() {
        float[] tint = new float[2];
        Blood.floorTint(0f, 0f, 3, 1f, tint);
        check(tint[0] == 0f && tint[1] == 0f, "a splat at the chunk's corner: " + tint[0] + ", " + tint[1]);
    }
}
