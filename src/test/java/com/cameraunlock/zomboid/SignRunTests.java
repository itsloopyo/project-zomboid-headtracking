package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import zombie.core.textures.Texture;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;

/** Tests of a sign several squares long laid on its wall as one piece: see EdgeRuns. */
final class SignRunTests {
    private SignRunTests() {}

    static void run() {
        Checks.run("garageSignPiecesAreReadWhereTheyPaintAlongTheWall", SignRunTests::garageSignPiecesAreReadWhereTheyPaintAlongTheWall);
        Checks.run("piecesOfOneSignMeetAndAThingOnItsOwnSquareIsNoRun", SignRunTests::piecesOfOneSignMeetAndAThingOnItsOwnSquareIsNoRun);
        Checks.run("aWestWallsSignIsReadAlongItsY", SignRunTests::aWestWallsSignIsReadAlongItsY);
        Checks.run("onlyLetteringAndSignsRunOn", SignRunTests::onlyLetteringAndSignsRunOn);
        Checks.run("aPieceLaidOverAllItPaintsKeepsItsFirstQuarter", SignRunTests::aPieceLaidOverAllItPaintsKeepsItsFirstQuarter);
        Checks.run("aBoardPaintedBelowItsFloorIsLaidDownToItsFoot", SignRunTests::aBoardPaintedBelowItsFloorIsLaidDownToItsFoot);
        Checks.run("aBoardOverAnAwningRestsOnItsCloth", SignRunTests::aBoardOverAnAwningRestsOnItsCloth);
        Checks.run("aBoardLongerThanItsAwningIsLiftedWhole", () -> TestMap.withEverything(SignRunTests::aBoardLongerThanItsAwningIsLiftedWhole));
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

    // signs_miscbrands_01_32 and _36, the first piece of Mandy's and of Eatery, on the level above the shop's awning.
    private static final float[] MANDYS = {52f, 162f, 72f, 80f}, EATERY = {60f, 163f, 64f, 72f};

    private static void aBoardPaintedBelowItsFloorIsLaidDownToItsFoot() {
        float[] upright = new float[4];
        EdgeRuns.upright(trimmed(MANDYS[0], MANDYS[1], MANDYS[2], MANDYS[3]), 1f, 0f, upright);
        check(upright[WallHung.BOTTOM] < -0.09f && upright[WallHung.BOTTOM] > -0.11f, "the board's face reaches down to its foot, a tenth of a level below the floor: " + upright[WallHung.BOTTOM]);
        check(upright[WallHung.TOP] == 1f, "and up to the top of its level: " + upright[WallHung.TOP]);
        EdgeRuns.upright(trimmed(128f - EATERY[0] - EATERY[2], EATERY[1], EATERY[2], EATERY[3]), 0f, 1f, upright);
        check(upright[WallHung.BOTTOM] < -0.05f, "on a west wall too: " + upright[WallHung.BOTTOM]);
        float[] garage = GARAGE[1];
        EdgeRuns.upright(trimmed(garage[0], garage[1], garage[2], garage[3]), 1f, 0f, upright);
        check(upright[WallHung.BOTTOM] == 0f && upright[WallHung.TOP] == 1f, "a sign painted within its level stands on its floor: " + upright[WallHung.BOTTOM] + " to " + upright[WallHung.TOP]);

        SpriteGeometry.Frame frame = trimmed(MANDYS[0], MANDYS[1], MANDYS[2], MANDYS[3]);
        EdgeRuns.upright(frame, 1f, 0f, upright);
        float foot = upright[WallHung.BOTTOM];
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        geometry.addFace(mesh, frame, 5, 7, 1, 0f, 0f, foot, 1f, 0f, 0f, 0f, 0f, upright[WallHung.TOP] - foot);
        float lowest = Float.MAX_VALUE;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            lowest = Math.min(lowest, mesh.vertices[at + 2]);
        }
        float floor = ViewCamera.LEVEL_HEIGHT;
        check(lowest < floor - 0.08f * ViewCamera.LEVEL_HEIGHT, "laid, the board's face goes below the floor of its level: " + lowest + " against " + floor);
    }

    private static void aBoardOverAnAwningRestsOnItsCloth() {
        float[] upright = new float[4];
        EdgeRuns.upright(trimmed(MANDYS[0], MANDYS[1], MANDYS[2], MANDYS[3]), 1f, 0f, upright);
        float foot = upright[WallHung.BOTTOM];
        Awnings.Hung awning = Awnings.of("walls_decoration_01_84", "Canopy", null, "S");
        float up = EdgeRuns.onAwning(foot, awning.wall);
        check(Math.abs(1f + foot + up - awning.wall) < 1e-5f, "Mandy's board is hung up to the top of the awning's cloth at the wall: foot " + (1f + foot + up) + ", cloth " + awning.wall);
        check(up > 0.06f, "which is most of a tenth of a level up: " + up);
        check(EdgeRuns.onAwning(foot, 0f) == 0f, "with no awning below it hangs where it is painted");
        check(EdgeRuns.onAwning(0.05f, awning.wall) == 0f, "and a board painted clear of the cloth stays where it is");
        check(Awnings.of("walls_decoration_01_145", "Canopy", null, "S").wall > awning.wall, "a dome meets the wall higher than a sloped awning");
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

    /**
     * A board of three pieces along the north wall of level 1 at x 0 to 2, each painted from a
     * quarter of a square before its own to three quarters into it, so they meet, and a fifth of
     * a level below its floor,
     * over an awning hung on that wall a level down under x 0 and 1 only, as WallBuilder lays
     * each piece: the lowest point of each piece's face.
     */
    private static void aBoardLongerThanItsAwningIsLiftedWhole() {
        TestMap map = new TestMap();
        Texture board = new Texture(9, 64, 100);
        board.paint(0, 0, 64, 100);
        IsoSprite piece = map.spriteWith("signs_miscbrands_01_32", IsoObjectType.normal, board, "attachedN", "");
        for (int x = 0; x <= 2; x++) {
            IsoObject laid = map.cell.square(x, 0, 1).add(piece);
            laid.offsetX = 16f;
            laid.offsetY = 30f;
        }
        IsoSprite awning = map.spriteWith("walls_decoration_01_84", IsoObjectType.normal, map.painted, "CustomName", "Canopy", "Facing", "S");
        map.cell.square(0, 0, 0).add(awning);
        map.cell.square(1, 0, 0).add(awning);
        float[] feet = new float[3];
        for (int x = 0; x <= 2; x++) {
            WorldScene.frameOf(map.build.frame, map.cell.getGridSquare(x, 0, 1).getObjects().get(0), board);
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            map.build.wallBuilder.sign = true;
            map.build.wallBuilder.addEdge(mesh, board, false, false, x, 0, 1, 0f, 1f, 0f);
            map.build.wallBuilder.sign = false;
            feet[x] = Float.MAX_VALUE;
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                feet[x] = Math.min(feet[x], mesh.vertices[at + 2]);
            }
        }
        check(feet[0] < Float.MAX_VALUE && feet[2] < Float.MAX_VALUE, "every piece is laid");
        check(Math.abs(feet[0] - feet[1]) < 1e-4f && Math.abs(feet[1] - feet[2]) < 1e-4f,
                "the board is one straight board: each piece is lifted as far, the one past the awning's end too: " + feet[0] + " " + feet[1] + " " + feet[2]);
        check(feet[0] > ViewCamera.LEVEL_HEIGHT * (1f - 0.2f) + 0.01f, "lifted onto the awning's cloth, not hung below its floor as painted: " + feet[0]);
    }
}
