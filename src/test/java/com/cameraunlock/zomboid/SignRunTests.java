package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of a sign several squares long laid on its wall as one piece: see EdgeRuns. */
final class SignRunTests {
    private SignRunTests() {}

    static void run() {
        Checks.run("garageSignPiecesAreReadWhereTheyPaintAlongTheWall", SignRunTests::garageSignPiecesAreReadWhereTheyPaintAlongTheWall);
        Checks.run("piecesOfOneSignMeetAndAThingOnItsOwnSquareIsNoRun", SignRunTests::piecesOfOneSignMeetAndAThingOnItsOwnSquareIsNoRun);
        Checks.run("aWestWallsSignIsReadAlongItsY", SignRunTests::aWestWallsSignIsReadAlongItsY);
        Checks.run("onlyLetteringAndSignsRunOn", SignRunTests::onlyLetteringAndSignsRunOn);
        Checks.run("aPieceLaidOverAllItPaintsKeepsItsFirstQuarter", SignRunTests::aPieceLaidOverAllItPaintsKeepsItsFirstQuarter);
    }

    /** A frame as the game gives a sprite of a square, trimmed to the rectangle it paints. */
    private static SpriteGeometry.Frame trimmed(float left, float top, float width, float height) {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.uStart = 0f;
        frame.uEnd = 1f;
        frame.vStart = 0f;
        frame.vEnd = 1f;
        frame.left = left;
        frame.top = top;
        frame.width = width;
        frame.height = height;
        return frame;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.01f;
    }

    // signs_one-off_08_240..243, the four squares of the mechanics' sign over a Muldraugh garage, as the sheets trim them.
    private static final float[][] GARAGE = {{38f, 7f, 72f, 134f}, {46f, 8f, 66f, 134f}, {48f, 9f, 63f, 133f}, {47f, 8f, 80f, 139f}};

    private static float[] along(float[] piece, float da, float db) {
        float[] out = new float[2];
        EdgeRuns.along(trimmed(piece[0], piece[1], piece[2], piece[3]), da, db, out);
        return out;
    }

    private static void garageSignPiecesAreReadWhereTheyPaintAlongTheWall() {
        float[] second = along(GARAGE[1], 1f, 0f);
        check(near(second[0], -0.28f) && near(second[1], 0.75f), "a middle piece paints from a quarter before its square: " + second[0] + " to " + second[1]);
        check(EdgeRuns.overhangs(second[0], second[1]), "and its square's stretch of wall cuts it");
        float[] first = along(GARAGE[0], 1f, 0f);
        check(near(first[0], -0.41f), "the first piece starts further back still: " + first[0]);
    }

    private static void piecesOfOneSignMeetAndAThingOnItsOwnSquareIsNoRun() {
        for (int piece = 0; piece + 1 < GARAGE.length; piece++) {
            float[] here = along(GARAGE[piece], 1f, 0f), next = along(GARAGE[piece + 1], 1f, 0f);
            check(EdgeRuns.meets(here[0], here[1], next[0], next[1]), "piece " + piece + " ends where the next starts: " + here[1] + " and " + (next[0] + 1f));
        }
        // Two lengths of a picture each painted on its own square meet, and neither is cut: nothing to lay further.
        check(!EdgeRuns.meets(0.02f, 0.98f, 0.02f, 0.98f), "pieces within their squares are no run");
        // A lamp proud of its wall, painted to one side of its fixing, with a lamp on the next square too: they do not meet.
        check(!EdgeRuns.meets(-0.3f, 0.4f, -0.3f, 0.4f), "two lamps a square apart are no run");
    }

    private static void aWestWallsSignIsReadAlongItsY() {
        // The same piece as the garage's second, painted for a west wall: mirrored across the frame's middle.
        float[] piece = GARAGE[1];
        float[] west = along(new float[] {128f - piece[0] - piece[2], piece[1], piece[2], piece[3]}, 0f, 1f);
        check(near(west[0], -0.28f) && near(west[1], 0.75f), "on a west wall it is read along y the same: " + west[0] + " to " + west[1]);
    }

    private static void onlyLetteringAndSignsRunOn() {
        check(EdgeRuns.sign("signs_one-off_08_241", null), "the garage's sign");
        check(EdgeRuns.sign("location_restaurant_diner_01_52", "Sign"), "a diner's letters, which the game calls a sign");
        check(EdgeRuns.sign("location_business_bank_01_72", null), "a bank's letters, which it calls nothing");
        check(!EdgeRuns.sign("fixtures_bathroom_01_0", "Toilet"), "not a toilet");
        check(!EdgeRuns.sign("location_community_medical_01_88", "Cabinet"), "nor a cabinet on a sheet of signs");
        check(!EdgeRuns.sign("industry_02_193", null), "nor a cable along a wall");
        check(!EdgeRuns.sign("roofs_02_7", null), "nor a roof's edge");
        check(!EdgeRuns.sign(null, null), "nor a sprite with no name");
    }

    private static void aPieceLaidOverAllItPaintsKeepsItsFirstQuarter() {
        float[] piece = GARAGE[1];
        SpriteGeometry.Frame frame = trimmed(piece[0], piece[1], piece[2], piece[3]);
        float[] run = new float[2];
        EdgeRuns.along(frame, 1f, 0f, run);
        float from = Math.min(0f, run[0]), length = Math.max(1f, run[1]) - from;
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Mesh square = new SpriteGeometry.Mesh(), whole = new SpriteGeometry.Mesh();
        geometry.addFace(square, frame, 5, 7, 1, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
        geometry.addFace(whole, frame, 5, 7, 1, from, 0f, 0f, length, 0f, 0f, 0f, 0f, 1f);
        check(westmost(square) > 4.99f, "laid on its square alone, the piece starts at the square's edge: " + westmost(square));
        check(westmost(whole) < 4.73f && westmost(whole) > 4.70f, "laid over all it paints, a quarter of a square before it: " + westmost(whole));
        check(Math.abs(eastmost(whole) - eastmost(square)) < 0.01f, "and its other end is where it was, to within its inset: " + eastmost(whole) + " and " + eastmost(square));
    }

    private static float westmost(SpriteGeometry.Mesh mesh) {
        float least = Float.MAX_VALUE;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            least = Math.min(least, mesh.vertices[at]);
        }
        return least;
    }

    private static float eastmost(SpriteGeometry.Mesh mesh) {
        float most = -Float.MAX_VALUE;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            most = Math.max(most, mesh.vertices[at]);
        }
        return most;
    }
}
