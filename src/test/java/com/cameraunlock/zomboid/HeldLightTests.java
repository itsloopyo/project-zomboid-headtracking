package com.cameraunlock.zomboid;

import java.util.HashSet;
import java.util.Set;

/** Tests of which square what the viewer holds is lit as standing in, and of the light it is then given at each hour. */
final class HeldLightTests {
    private HeldLightTests() {}

    static void run() {
        Checks.run("whatIsHeldIsLitByTheSquareBeforeTheEye", HeldLightTests::whatIsHeldIsLitByTheSquareBeforeTheEye);
        Checks.run("whatTheEyeCannotPassKeepsTheEyesOwnSquare", HeldLightTests::whatTheEyeCannotPassKeepsTheEyesOwnSquare);
        Checks.run("anEyeOverAirIsLitByWhereTheViewerStands", HeldLightTests::anEyeOverAirIsLitByWhereTheViewerStands);
        Checks.run("seatedTheSquareSatOnLightsWhatIsHeldAndTheCabin", HeldLightTests::seatedTheSquareSatOnLightsWhatIsHeldAndTheCabin);
        Checks.run("whatIsHeldIsAsDarkAsTheWorldAtNight", HeldLightTests::whatIsHeldIsAsDarkAsTheWorldAtNight);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** A map of seen squares, with the edges between them the eye cannot pass. */
    private static final class Map implements HeldLight.Squares {
        final Set<Long> seen = new HashSet<>();
        final Set<String> walls = new HashSet<>();

        Map see(int fromX, int fromY, int toX, int toY) {
            for (int x = fromX; x <= toX; x++) {
                for (int y = fromY; y <= toY; y++) {
                    seen.add(((long) x << 32) ^ (y & 0xffffffffL));
                }
            }
            return this;
        }

        Map wall(int fromX, int fromY, int toX, int toY) {
            walls.add(fromX + "," + fromY + ">" + toX + "," + toY);
            walls.add(toX + "," + toY + ">" + fromX + "," + fromY);
            return this;
        }

        @Override
        public boolean seen(int x, int y) {
            return seen.contains(((long) x << 32) ^ (y & 0xffffffffL));
        }

        @Override
        public boolean between(int fromX, int fromY, int toX, int toY) {
            check(Math.abs(toX - fromX) + Math.abs(toY - fromY) == 1, "only an edge between squares side by side is asked about");
            return walls.contains(fromX + "," + fromY + ">" + toX + "," + toY);
        }
    }

    /** On foot with the viewer standing where the eye is. */
    private static boolean takes(Map map, float eyeX, float eyeY, float wayX, float wayY, int x, int y) {
        int[] square = new int[2];
        return HeldLight.choose(map, eyeX, eyeY, eyeX, eyeY, wayX, wayY, false, square) && square[0] == x && square[1] == y;
    }

    private static void whatIsHeldIsLitByTheSquareBeforeTheEye() {
        Map open = new Map().see(8, 8, 12, 12);
        check(takes(open, 10.5f, 10.5f, 1f, 0f, 10, 10), "from the middle of a square what is held is in the same square");
        check(takes(open, 10.9f, 10.5f, 1f, 0f, 11, 10), "near the edge it is in the square ahead");
        check(takes(open, 10.6f, 10.5f, 1f, 0f, 10, 10), "but not from 0.4 of a tile short of it: what is held reaches 0.3 before the eye");
        check(takes(open, 10.1f, 10.5f, -3f, 0f, 9, 10), "the way is any length: only which way it goes counts");
        check(takes(open, 10.9f, 10.9f, 1f, 1f, 11, 11), "and diagonally, the square on the corner");
        check(takes(open, 10.9f, 10.9f, 0f, 0f, 10, 10), "aimed straight up or down, what is held is under the eye, on its own square");
        check(takes(open, 10.9f, 10.9f, 1e-5f, 0f, 10, 10), "and so it is aimed all but straight up or down");
    }

    private static void whatTheEyeCannotPassKeepsTheEyesOwnSquare() {
        Map walled = new Map().see(8, 8, 12, 12).wall(10, 10, 11, 10);
        check(takes(walled, 10.9f, 10.5f, 1f, 0f, 10, 10), "a wall between keeps the eye's own square");
        check(takes(walled, 10.5f, 10.9f, 0f, 1f, 10, 11), "and leaves the other ways open");
        check(takes(walled, 10.9f, 10.9f, 1f, 1f, 11, 11), "round a corner one way open is enough");
        walled.wall(10, 10, 10, 11).wall(10, 11, 11, 11);
        check(takes(walled, 10.9f, 10.9f, 1f, 1f, 10, 10), "with both ways round the corner shut, the eye's own square");
        Map halfShut = new Map().see(8, 8, 12, 12).wall(10, 10, 11, 10).wall(10, 11, 11, 11);
        check(takes(halfShut, 10.9f, 10.9f, 1f, 1f, 10, 10), "each way round a corner must be open the whole way");
        check(takes(new Map().see(10, 10, 10, 10), 10.9f, 10.5f, 1f, 0f, 10, 10), "a square ahead the viewer has not seen, or that is not there, keeps the eye's own");
    }

    private static void anEyeOverAirIsLitByWhereTheViewerStands() {
        Map balcony = new Map().see(10, 10, 10, 10);
        int[] square = new int[2];
        check(HeldLight.choose(balcony, 10.8f, 10.5f, 11.2f, 10.5f, 1f, 0f, false, square) && square[0] == 10 && square[1] == 10,
                "an eye leant out over air the game has no square for takes the square the viewer stands on");
        check(!HeldLight.choose(new Map(), 10.8f, 10.5f, 11.2f, 10.5f, 1f, 0f, false, square), "with neither seen, no square is said");
    }

    private static void seatedTheSquareSatOnLightsWhatIsHeldAndTheCabin() {
        Map road = new Map().see(8, 8, 12, 12);
        int[] square = new int[2];
        check(HeldLight.choose(road, 10.5f, 10.5f, 10.9f, 10.9f, 1f, 1f, true, square) && square[0] == 10 && square[1] == 10,
                "seated, the square sat on, whichever way the aim and the lean go");
        float[] gathered = {1f, 0.9f, 0.8f, 0.7f, 1f};
        float[] dusk = new float[HeldLight.LIGHTS];
        HeldLight.light(gathered, 0.9f, 0.9f, 0.9f, 0.3f, 1f, 0f, dusk);
        check(dusk[HeldLight.CABIN] == 0.9f && dusk[HeldLight.CABIN + 1] == 0.8f && dusk[HeldLight.CABIN + 2] == 0.7f,
                "the cabin takes the square's own light: its lighting takes the evening from the sky");
        check(Math.abs(dusk[HeldLight.LIGHT] - 0.8f * 0.3f) < 1e-5f, "what is held takes the square's light with the daylight the hour keeps");
        check(dusk[HeldLight.OUTSIDE] == 1f, "and is out of doors where the square is");
    }

    private static void whatIsHeldIsAsDarkAsTheWorldAtNight() {
        float[] none = new float[5];
        float[] noon = new float[HeldLight.LIGHTS];
        HeldLight.light(none, 1f, 1f, 1f, 1f, 1f, 0f, noon);
        float[] night = new float[HeldLight.LIGHTS];
        HeldLight.light(none, 0.18f, 0.18f, 0.18f, 0.2f, 1f, 0f, night);
        check(noon[HeldLight.LIGHT] == 1f && noon[HeldLight.OUTSIDE] == 1f, "with no square found, what is held at noon is lit as the open ground round it");
        check(night[HeldLight.LIGHT] < 0.05f, "and at night it is as dark as the open ground, never lit up");
        check(night[HeldLight.CABIN] == 0.18f, "and a cabin with no square found takes the open ground's light, not a full one");
        float[] room = new float[HeldLight.LIGHTS];
        HeldLight.light(new float[] {1f, 0.5f, 0.5f, 0.5f, 0f}, 0.9f, 0.9f, 0.9f, 0.3f, 0.5f, 0f, room);
        check(Math.abs(room[HeldLight.LIGHT] - 0.5f * 0.15f) < 1e-5f && room[HeldLight.OUTSIDE] == 0f, "a room at dusk keeps less of the daylight than the open ground does");
        float[] lamp = new float[HeldLight.LIGHTS];
        HeldLight.light(new float[] {1f, 0.8f, 0.8f, 0.8f, 0f}, 0.18f, 0.18f, 0.18f, 0.2f, 1f, 0f, lamp);
        check(lamp[HeldLight.LIGHT] == Evening.dimmed(0.8f, 0.18f, 0.2f) && lamp[HeldLight.LIGHT] > 0.8f - 0.18f, "what a lamplit room has over the open ground is the lamp's, and stays at night");
    }
}
