package com.cameraunlock.zomboid;

import java.util.HashMap;

/** Tests of which sprites are cloth the map hangs out, of the run a strung banner's lengths make, and of where on its plane a sprite's dot is. */
final class BannerTests {
    private BannerTests() {}

    static void run() {
        Checks.run("bannersAreToldByTheirSheetAndClothOnAWallByItsFacing", BannerTests::bannersAreToldByTheirSheetAndClothOnAWallByItsFacing);
        Checks.run("dotOfALengthIsWhereTheViewHasItOnTheSquaresEdge", BannerTests::dotOfALengthIsWhereTheViewHasItOnTheSquaresEdge);
        Checks.run("dotOfAPostedBannerIsInThePlaneThroughThePost", BannerTests::dotOfAPostedBannerIsInThePlaneThroughThePost);
        Checks.run("everyLengthFindsTheSameRunFromEndToEnd", BannerTests::everyLengthFindsTheSameRunFromEndToEnd);
        Checks.run("runAlongXStopsAtALengthThatRunsAcrossIt", BannerTests::runAlongXStopsAtALengthThatRunsAcrossIt);
        Checks.run("runTheMapStopsShortOfSaysSo", BannerTests::runTheMapStopsShortOfSaysSo);
        Checks.run("cableIsReadWhereItLeavesTheEndSquares", BannerTests::cableIsReadWhereItLeavesTheEndSquares);
        Checks.run("bannerTakesNoWindAtItsFixingsAndMostAtItsFoot", BannerTests::bannerTakesNoWindAtItsFixingsAndMostAtItsFoot);
    }

    private static SpriteGeometry.Frame frame(float left, float top, float width, float height) {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.left = left;
        frame.top = top;
        frame.width = width;
        frame.height = height;
        return frame;
    }

    private static void bannersAreToldByTheirSheetAndClothOnAWallByItsFacing() {
        check(Banners.kind("signs_one-off_09_312", null, false) == Banners.STRUNG, "the first length of the strung banner is one");
        check(Banners.kind("signs_one-off_09_335", null, false) == Banners.STRUNG, "the cable's last length is part of the run");
        check(Banners.kind("signs_one-off_09_336", null, false) == Banners.POSTED, "a lamp post's banner hangs from its post");
        check(Banners.kind("signs_one-off_09_337", null, false) == Banners.POSTED, "and so does the one that hangs the other way");
        check(Banners.kind("signs_one-off_09_311", null, false) == Banners.NONE, "the sprite before the banner on its sheet is not one");
        check(Banners.kind("signs_one-off_09_338", null, false) == Banners.NONE, "nor the one after the posts' banners");
        check(Banners.kind("signs_one-off_09_3120", null, false) == Banners.NONE, "nor one whose number only starts the same");
        check(Banners.kind("walls_decoration_01_64", "Bunting", false) == Banners.ON_WALL, "bunting the game marks for no wall hangs on one");
        check(Banners.kind("location_military_knox_01_11", "Flag", false) == Banners.ON_WALL, "and so does a flag");
        check(Banners.kind("walls_decoration_01_16", "Flag", true) == Banners.NONE, "a flag the game fixes to a wall itself is left to the game's mark");
        check(Banners.kind("street_decoration_01_16", "Pole", false) == Banners.NONE, "a flag pole is a pole");
        check(Banners.kind(null, null, false) == Banners.NONE, "a sprite with no name is nothing");
    }

    /** On a sheet along y on the square's west edge: the frame's middle column is the square's north corner, a tile along is 64 dots left and 32 down, a level 192 up. */
    private static void dotOfALengthIsWhereTheViewHasItOnTheSquaresEdge() {
        SpriteGeometry.Frame frame = frame(0f, 60f, 64f, 120f);
        check(!Banners.alongX(frame), "a sprite painted in the left half of its frame runs along y");
        float[] at = new float[2];
        Banners.place(frame, false, 0f, 64f, 192f, at);
        near(at[0], 0f, 1e-5f, "the frame's origin is the square's corner");
        near(at[1], 0f, 1e-5f, "at the floor");
        Banners.place(frame, false, 0f, 0f, 224f, at);
        near(at[0], 1f, 1e-5f, "a tile along y is 64 dots to the left");
        near(at[1], 0f, 1e-5f, "and 32 down, still at the floor");
        Banners.place(frame, false, 0f, 32f, 16f, at);
        near(at[0], 0.5f, 1e-5f, "half a tile along");
        near(at[1], 1f, 1e-5f, "a level up is 192 dots up from the floor there");
        SpriteGeometry.Frame east = frame(64f, 60f, 64f, 120f);
        check(Banners.alongX(east), "a sprite painted in the right half of its frame runs along x");
        Banners.place(east, true, 0f, 128f, 224f, at);
        near(at[0], 1f, 1e-5f, "a tile along x is 64 dots to the right");
        near(at[1], 0f, 1e-5f, "and 32 down");
    }

    private static void dotOfAPostedBannerIsInThePlaneThroughThePost() {
        SpriteGeometry.Frame frame = frame(9f, 130f, 55f, 108f);
        float[] at = new float[2];
        Banners.place(frame, false, Banners.POST_AXIS, 64f, 224f, at);
        near(at[0], 0.5f, 1e-5f, "the frame's middle column is the post's own place along");
        near(at[1], 0f, 1e-5f, "and the middle of the square's floor is 32 dots under the origin");
        Banners.place(frame, false, Banners.POST_AXIS, 32f, 144f, at);
        near(at[0], 1f, 1e-5f, "half a tile out from the post");
        near(at[1], 0.5f, 1e-5f, "half a level up");
    }

    /** A made-up map: the squares that hold a length, each with how high its cable is at its two ends and whether it runs along x, and the squares along y it is loaded between. */
    private static final class Map implements Banners.Hung {
        final HashMap<Long, float[]> lengths = new HashMap<>();
        int loadedFrom = Integer.MIN_VALUE, loadedTo = Integer.MAX_VALUE;

        void length(int x, int y, float topAtStart, float topAtEnd, boolean alongX) {
            lengths.put((long) x << 32 | y & 0xFFFFFFFFL, new float[] {topAtStart, topAtEnd, alongX ? 1f : 0f});
        }

        @Override
        public int strung(int x, int y, boolean alongX) {
            if (y < loadedFrom || y >= loadedTo) {
                return -1;
            }
            float[] length = lengths.get((long) x << 32 | y & 0xFFFFFFFFL);
            return length != null && length[2] != 0f == alongX ? 1 : 0;
        }

        @Override
        public float top(int x, int y, boolean alongX, boolean atStart) {
            return lengths.get((long) x << 32 | y & 0xFFFFFFFFL)[atStart ? 0 : 1];
        }
    }

    private static Map street() {
        Map map = new Map();
        for (int y = 5271; y <= 5294; y++) {
            float from = 1f - 2f * (1f - Math.abs(y - 5283) / 12f) * 0.25f, to = 1f - 2f * (1f - Math.abs(y + 1 - 5283) / 12f) * 0.25f;
            map.length(6511, y, from, to, false);
        }
        // Another banner over the next street, which is no part of this one.
        map.length(6513, 5280, 0.9f, 0.9f, false);
        return map;
    }

    private static void everyLengthFindsTheSameRunFromEndToEnd() {
        Map map = street();
        for (int y = 5271; y <= 5294; y++) {
            float[] run = Banners.find(map, 6511, y, false);
            near(run[Banners.FROM], 5271f, 0f, "the run starts at the first square from " + y);
            near(run[Banners.TO], 5295f, 0f, "and ends past the last from " + y);
            near(run[Banners.TOP_FROM], 1f, 1e-5f, "its cable is at the wall's top where it starts, from " + y);
            near(run[Banners.TOP_TO], 1f, 1e-5f, "and where it ends, from " + y);
            check(!Banners.wanting(run), "the whole of it is on the map from " + y);
        }
        float[] other = Banners.find(map, 6513, 5280, false);
        near(other[Banners.FROM], 5280f, 0f, "a length on its own is a run of one square");
        near(other[Banners.TO], 5281f, 0f, "that ends a square on");
        near(Banners.along(Banners.find(map, 6511, 5280, false), 5283f), 0.5f, 1e-5f, "the middle of the run is half way along it");
    }

    private static void runTheMapStopsShortOfSaysSo() {
        Map map = street();
        map.loadedTo = 5288;
        float[] run = Banners.find(map, 6511, 5280, false);
        check(Banners.wanting(run), "a run the map is not loaded to the end of is wanting");
        near(run[Banners.FROM], 5271f, 0f, "it still starts where it starts");
        near(run[Banners.TO], 5288f, 0f, "and is held where the map stops");
        map.loadedTo = Integer.MAX_VALUE;
        check(!Banners.wanting(Banners.find(map, 6511, 5280, false)), "and is whole once the map has come in");
        map.loadedFrom = 5276;
        run = Banners.find(map, 6511, 5280, false);
        check(Banners.wanting(run), "one the map does not reach the start of is wanting too");
        near(run[Banners.FROM], 5276f, 0f, "and starts where the map does");
    }

    /** A banner along x over one street that ends beside a length of another that hangs along y: on squares of negative x, where a division rounds the other way. */
    private static void runAlongXStopsAtALengthThatRunsAcrossIt() {
        Map map = new Map();
        for (int x = -6; x <= 3; x++) {
            map.length(x, 40, x == -6 ? 1f : 0.8f, x == 3 ? 0.9f : 0.8f, true);
        }
        map.length(4, 40, 0.5f, 0.5f, false);
        map.length(-7, 40, 0.5f, 0.5f, false);
        for (int x = -6; x <= 3; x++) {
            float[] run = Banners.find(map, x, 40, true);
            near(run[Banners.FROM], -6f, 0f, "the run along x starts at its own first square from " + x);
            near(run[Banners.TO], 4f, 0f, "and ends past its own last from " + x);
            near(run[Banners.TOP_FROM], 1f, 0f, "its cable is read on its own first length from " + x);
            near(run[Banners.TOP_TO], 0.9f, 0f, "and its own last from " + x);
            check(!Banners.wanting(run), "a length across it is no want of map from " + x);
        }
        near(Banners.find(map, 4, 40, false)[Banners.TO], 41f, 0f, "the length across it is a run of its own along y");
    }

    /** A cable painted as a line a dot thick that falls a quarter of a level along the square: read at each end, with the end column itself left unpainted. */
    private static void cableIsReadWhereItLeavesTheEndSquares() {
        SpriteGeometry.Frame frame = frame(0f, 0f, 64f, 256f);
        SpriteShape.Painted line = (u, v) -> {
            if (u < 1 || u > 62) {
                return false;
            }
            // Along y: the column 64 is the start, at one level, and the column 0 the end, at 0.75.
            float along = (64f - (u + 0.5f)) / 64f;
            int row = Math.round(192f + along * 32f - (1f - 0.25f * along) * 192f);
            return v == row;
        };
        float start = Banners.top(line, frame, false, true), end = Banners.top(line, frame, false, false);
        near(start, 1f, 0.02f, "the cable is read a level up where the square starts");
        near(end, 0.75f, 0.02f, "and three quarters of one where it ends");
        check(Float.isNaN(Banners.top((u, v) -> false, frame, false, true)), "a sprite that paints nothing has no cable to read");
        // Along x: the column 64 is the start, at 0.75 of a level, and the column 128 the end, at one.
        SpriteGeometry.Frame east = frame(64f, 0f, 64f, 256f);
        SpriteShape.Painted rising = (u, v) -> {
            if (u < 65 || u > 126) {
                return false;
            }
            float along = (u + 0.5f - 64f) / 64f;
            return v == Math.round(192f + along * 32f - (0.75f + 0.25f * along) * 192f);
        };
        near(Banners.top(rising, east, true, true), 0.75f, 0.02f, "along x the start is read at the frame's middle");
        near(Banners.top(rising, east, true, false), 1f, 0.02f, "and the end a tile to the right of it");
    }

    private static void bannerTakesNoWindAtItsFixingsAndMostAtItsFoot() {
        float[] run = Banners.find(street(), 6511, 5280, false);
        near(Banners.sway(run, 0f, 1f), 0f, 1e-6f, "nothing where the cable is made fast");
        near(Banners.sway(run, 1f, 1f), 0f, 1e-6f, "at either end");
        float cable = Banners.sway(run, 0.5f, 0.5f), foot = Banners.sway(run, 0.5f, 0f);
        check(cable > 0f && foot > cable, "in the middle the cable swings a little and the cloth's foot more: " + cable + " " + foot);
        check(foot <= Banners.MOST_SWAY, "and never more than the most");
        near(Banners.sway(run, 0.5f, 1.2f), 0f, 1e-6f, "what is above the line between the fixings is not pushed back the other way");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void near(float is, float wanted, float within, String what) {
        check(Math.abs(is - wanted) <= within, what + ": " + is + ", wanted " + wanted);
    }
}
