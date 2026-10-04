package com.cameraunlock.zomboid;

/** Tests of what is read off a sprite's outline, and of the depth a place far across the map is drawn at. */
final class ShapeTests {
    private ShapeTests() {}

    static void run() {
        slabOverAShopFrontIsABeamAlongItsWall();
        sillAlongANorthWallIsABeamAtItsFloor();
        billboardSheetIsAPanelOnItsLine();
        endSheetStandsInThePlaneOfTheWholeOneBesideIt();
        rowOfLettersIsALowPanel();
        binAndPostAreNeitherPanelNorBeam();
        beamHasAnUndersideAndPanelIsOneUprightFace();
        depthFarAcrossTheMapKeepsItsOrderFromTheEye();
        lampProudOfItsWallIsFoundWhereItIsFixed();
        newModelsAreInTheJar();
        footOfAPostIsItsCornerNearestTheView();
    }

    private static void footOfAPostIsItsCornerNearestTheView() {
        // A post a sixth of a square thick against the middle of the west edge, taller than a level.
        SpriteGeometry.Frame frame = frame();
        SpriteShape.Painted post = box(frame, 0f, 0.42f, 0f, 0.16f, 0.58f, 1.1f);
        float[] foot = new float[3];
        check(SpriteShape.foot(post, frame, foot), "a post has a foot");
        check(near(foot[0], 0.16f, 0.06f) && near(foot[1], 0.58f, 0.06f), "it is the post's south-east corner on the floor: " + foot[0] + ", " + foot[1]);
        check(foot[2] < 0.3f, "and is as narrow as the post: " + foot[2]);
        frame = frame();
        SpriteShape.Painted north = box(frame, 0.42f, 0f, 0f, 0.58f, 0.16f, 1.1f);
        check(SpriteShape.foot(north, frame, foot) && near(foot[0], 0.58f, 0.06f) && near(foot[1], 0.16f, 0.06f), "a post against the north edge has its foot there");
        frame = frame();
        frame.left = 10f;
        frame.top = 10f;
        frame.width = 20f;
        frame.height = 20f;
        check(!SpriteShape.foot((u, v) -> false, frame, foot), "a sprite with nothing painted has none");
    }

    private static void newModelsAreInTheJar() {
        for (String name : new String[] {"cone_lamp", "globe_lamp", "lantern_lamp", "lantern_head", "cobra_head", "wall_bowl", "wall_lantern",
                "bulkhead_light", "wall_sink", "serving_counter"}) {
            check(Models.get(name).triangles() > 0, name + " is a model");
        }
        Models.Model arm = Models.get("cobra_head");
        check(arm.minY < -0.8f && arm.maxY < 0.1f && Math.abs(arm.minX + arm.maxX) < 0.01f, "a road lamp's arm reaches out to its front from the post at its origin");
        check(near(Models.get("serving_counter").width(), 1f, 1e-3f), "a length of serving counter is a square wide, so a run of them meets");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b, float within) {
        return Math.abs(a - b) <= within;
    }

    private static SpriteGeometry.Frame frame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.uStart = 0f;
        frame.uEnd = 1f;
        frame.vStart = 0f;
        frame.vEnd = 1f;
        return frame;
    }

    /** Whether the pixel is inside the picture of the flat face from (a, b, h) along two edges. */
    private static boolean onFace(SpriteGeometry.Frame frame, float u, float v, float a, float b, float h, float[] one, float[] two) {
        float u0 = SpriteGeometry.frameU(frame, a, b), v0 = SpriteGeometry.frameV(frame, a, b, h);
        float u1 = SpriteGeometry.frameU(frame, a + one[0], b + one[1]) - u0, v1 = SpriteGeometry.frameV(frame, a + one[0], b + one[1], h + one[2]) - v0;
        float u2 = SpriteGeometry.frameU(frame, a + two[0], b + two[1]) - u0, v2 = SpriteGeometry.frameV(frame, a + two[0], b + two[1], h + two[2]) - v0;
        float det = u1 * v2 - u2 * v1;
        float s = ((u - u0) * v2 - u2 * (v - v0)) / det, t = (u1 * (v - v0) - (u - u0) * v1) / det;
        return s >= 0f && s <= 1f && t >= 0f && t <= 1f;
    }

    /** The picture the game's view has of a box, as a sprite: the frame is trimmed to it. */
    private static SpriteShape.Painted box(SpriteGeometry.Frame frame, float a0, float b0, float h0, float a1, float b1, float h1) {
        float[] alongA = {a1 - a0, 0f, 0f}, alongB = {0f, b1 - b0, 0f}, up = {0f, 0f, h1 - h0};
        SpriteShape.Painted painted = (u, v) -> onFace(frame, u + 0.5f, v + 0.5f, a0, b0, h1, alongA, alongB)
                || onFace(frame, u + 0.5f, v + 0.5f, a0, b1, h0, alongA, up) || onFace(frame, u + 0.5f, v + 0.5f, a1, b0, h0, alongB, up);
        trim(frame, painted);
        return painted;
    }

    /** The picture of an upright sheet from (a0, b0) to (a1, b1) on the square, h0 to h1 up. */
    private static SpriteShape.Painted sheet(SpriteGeometry.Frame frame, float a0, float b0, float a1, float b1, float h0, float h1) {
        float[] along = {a1 - a0, b1 - b0, 0f}, up = {0f, 0f, h1 - h0};
        SpriteShape.Painted painted = (u, v) -> onFace(frame, u + 0.5f, v + 0.5f, a0, b0, h0, along, up);
        trim(frame, painted);
        return painted;
    }

    private static void trim(SpriteGeometry.Frame frame, SpriteShape.Painted painted) {
        int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE, top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
        for (int u = -64; u < 256; u++) {
            for (int v = -256; v < 320; v++) {
                if (painted.at(u, v)) {
                    left = Math.min(left, u);
                    right = Math.max(right, u);
                    top = Math.min(top, v);
                    bottom = Math.max(bottom, v);
                }
            }
        }
        frame.left = left;
        frame.top = top;
        frame.width = right - left + 1;
        frame.height = bottom - top + 1;
    }

    private static void slabOverAShopFrontIsABeamAlongItsWall() {
        SpriteGeometry.Frame frame = frame();
        SpriteShape shape = SpriteShape.read(box(frame, 0.67f, 0f, 0.94f, 1f, 1f, 1f), frame);
        check(shape.kind == SpriteShape.BEAM && !shape.alongX, "a slab a square long by a wall is a beam along that wall");
        check(near(shape.a0, 0.67f, 0.03f) && near(shape.a1, 1f, 0.03f), "it is as wide as painted, by the edge it was painted at: " + shape.a0 + " to " + shape.a1);
        check(near(shape.b0, 0f, 0.03f) && near(shape.b1, 1f, 0.03f), "it runs from one edge of its square to the other: " + shape.b0 + " to " + shape.b1);
        check(near(shape.h0, 0.94f, 0.015f) && near(shape.h1, 1f, 0.015f), "at the height of the ceiling it was painted at: " + shape.h0 + " to " + shape.h1);
    }

    private static void sillAlongANorthWallIsABeamAtItsFloor() {
        SpriteGeometry.Frame frame = frame();
        SpriteShape shape = SpriteShape.read(box(frame, 0f, 0.02f, 0.04f, 1f, 0.27f, 0.08f), frame);
        check(shape.kind == SpriteShape.BEAM && shape.alongX, "a sill a square long is a beam along x");
        check(near(shape.b0, 0.02f, 0.03f) && near(shape.b1, 0.27f, 0.03f) && near(shape.h1, 0.08f, 0.015f) && near(shape.h0, 0.04f, 0.015f),
                "where it was painted: b " + shape.b0 + " to " + shape.b1 + ", h " + shape.h0 + " to " + shape.h1);
    }

    private static void billboardSheetIsAPanelOnItsLine() {
        SpriteGeometry.Frame frame = frame();
        SpriteShape shape = SpriteShape.read(sheet(frame, 0f, 0.625f, 1f, 0.625f, 0.25f, 0.9f), frame);
        check(shape.kind == SpriteShape.PANEL && shape.alongX && shape.whole, "a sheet a square long is a whole panel along x");
        check(near(shape.b0, 0.625f, 0.02f) && near(shape.a0, 0f, 0.02f) && near(shape.a1, 1f, 0.02f), "on the line it was painted on: b " + shape.b0);
        check(near(shape.h0, 0.25f, 0.015f) && near(shape.h1, 0.9f, 0.015f), "from its foot to its top: " + shape.h0 + " to " + shape.h1);

        frame = frame();
        shape = SpriteShape.read(sheet(frame, 0.03f, 0f, 0.03f, 1f, 0.4f, 0.8f), frame);
        check(shape.kind == SpriteShape.PANEL && !shape.alongX && near(shape.a0, 0.03f, 0.02f) && near(shape.h1, 0.8f, 0.015f),
                "and one on a west wall is a panel along y: a " + shape.a0 + ", top " + shape.h1);
    }

    private static void endSheetStandsInThePlaneOfTheWholeOneBesideIt() {
        SpriteGeometry.Frame frame = frame();
        SpriteShape end = SpriteShape.read(sheet(frame, 0.24f, 0.625f, 1f, 0.625f, 0.25f, 0.9f), frame);
        check(end.kind == SpriteShape.PANEL && end.alongX && !end.whole, "a sheet three quarters of a square long does not say where it starts");
        SpriteShape placed = end.at(0.625f);
        check(placed.whole && near(placed.b0, 0.625f, 1e-4f) && near(placed.a0, 0.24f, 0.02f) && near(placed.a1, 1f, 0.02f),
                "in its neighbour's plane it is where it was painted: a " + placed.a0 + " to " + placed.a1);
        check(near(placed.h0, 0.25f, 0.015f) && near(placed.h1, 0.9f, 0.015f), "and as high: " + placed.h0 + " to " + placed.h1);
    }

    private static void rowOfLettersIsALowPanel() {
        SpriteGeometry.Frame frame = frame();
        SpriteShape shape = SpriteShape.read(sheet(frame, 0.03f, 0f, 0.03f, 1f, 0.02f, 0.16f), frame);
        check(shape.kind == SpriteShape.PANEL && !shape.alongX, "a strip standing on a wall's line is a panel, not a beam");
    }

    private static void binAndPostAreNeitherPanelNorBeam() {
        SpriteGeometry.Frame frame = frame();
        check(SpriteShape.read(box(frame, 0.3f, 0.3f, 0f, 0.7f, 0.7f, 0.45f), frame).kind == SpriteShape.CARD, "a bin is as wide as it is long");
        frame = frame();
        check(SpriteShape.read(box(frame, 0.45f, 0.45f, 0f, 0.55f, 0.55f, 1.1f), frame).kind == SpriteShape.CARD, "a post is neither");
        frame = frame();
        check(SpriteShape.read(box(frame, 0f, 0f, 0f, 1f, 1f, 0.3f), frame).kind == SpriteShape.CARD, "nor is a block that fills its square");
        frame = frame();
        SpriteShape.Painted disc = (u, v) -> (u - 64) * (u - 64) + (v - 100) * (v - 100) < 30 * 30;
        trim(frame, disc);
        check(SpriteShape.read(disc, frame).kind == SpriteShape.CARD, "nor is a disc");
    }

    private static void beamHasAnUndersideAndPanelIsOneUprightFace() {
        SpriteGeometry.Frame frame = frame();
        SpriteShape beam = SpriteShape.read(box(frame, 0.67f, 0f, 0.94f, 1f, 1f, 1f), frame);
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        geometry.addBeam(mesh, frame, 5, 7, 0, beam);
        float lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE;
        int under = 0;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float up = mesh.vertices[at + 2];
            lowest = Math.min(lowest, up);
            highest = Math.max(highest, up);
            check(mesh.vertices[at] > 5.6f && mesh.vertices[at] < 6.05f && mesh.vertices[at + 1] > 6.95f && mesh.vertices[at + 1] < 8.05f,
                    "every face of the beam is on the beam: " + mesh.vertices[at] + ", " + mesh.vertices[at + 1]);
        }
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS * 3) {
            float a = mesh.vertices[at + 2], b = mesh.vertices[at + SpriteGeometry.VERTEX_FLOATS + 2], c = mesh.vertices[at + 2 * SpriteGeometry.VERTEX_FLOATS + 2];
            if (near(a, lowest, 1e-4f) && near(b, lowest, 1e-4f) && near(c, lowest, 1e-4f)) {
                under++;
            }
        }
        check(near(highest, ViewCamera.LEVEL_HEIGHT, 0.04f) && near(lowest, 0.94f * ViewCamera.LEVEL_HEIGHT, 0.04f),
                "the beam is as deep as its sprite paints it: " + lowest + " to " + highest);
        check(under >= 2, "and has an underside, which is what is seen of an awning from the street");
        check(geometry.shownA == 0f && geometry.shownB == 0f && geometry.shownUp == 0f, "nothing added after it is moved as its hidden faces were");

        frame = frame();
        SpriteShape panel = SpriteShape.read(sheet(frame, 0f, 0.625f, 1f, 0.625f, 0.25f, 0.9f), frame);
        mesh = new SpriteGeometry.Mesh();
        geometry.addPanel(mesh, frame, 5, 7, 2, panel);
        check(mesh.triangles() >= 2, "a panel is drawn");
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            check(near(mesh.vertices[at + 1], 7.625f, 0.02f), "all of it on its line: " + mesh.vertices[at + 1]);
            check(mesh.vertices[at + 2] > 2.2f * ViewCamera.LEVEL_HEIGHT && mesh.vertices[at + 2] < 2.95f * ViewCamera.LEVEL_HEIGHT, "at its height on its level");
        }
    }

    private static void lampProudOfItsWallIsFoundWhereItIsFixed() {
        SpriteGeometry.Frame frame = frame();
        float out = 0.15f, along = 0.4f, high = 0.8f;
        for (int wall : new int[] {WallHung.NORTH, WallHung.WEST, WallHung.SOUTH, WallHung.EAST}) {
            // Where the lamp really is: out from its fixing into the square.
            float inA = wall == WallHung.WEST ? out : wall == WallHung.EAST ? -out : 0f, inB = wall == WallHung.NORTH ? out : wall == WallHung.SOUTH ? -out : 0f;
            float u = SpriteGeometry.frameU(frame, WallHung.a(wall, along) + inA, WallHung.b(wall, along) + inB);
            float v = SpriteGeometry.frameV(frame, WallHung.a(wall, along) + inA, WallHung.b(wall, along) + inB, high);
            // Where on the wall itself that pixel is: what painted reads off the sprite.
            float start = SpriteGeometry.frameU(frame, WallHung.a(wall, 0f), WallHung.b(wall, 0f));
            float read = (u - start) / (SpriteGeometry.frameU(frame, WallHung.a(wall, 1f), WallHung.b(wall, 1f)) - start);
            float readHigh = SpriteGeometry.heightDrawnAt(frame, WallHung.a(wall, read), WallHung.b(wall, read), v);
            check(near(read + WallHung.alongProud(wall, out), along, 1e-4f), "a lamp proud of wall " + wall + " is along it where it is fixed");
            check(near(readHigh + WallHung.upProud(wall, out), high, 1e-4f), "and as high as it is fixed, " + (readHigh + WallHung.upProud(wall, out)));
        }
    }

    /** What the picture's vertex program makes of a place, in the numbers the graphics card works in: its depth after the divide. */
    private static float depth(float[] matrix, float x, float y, float up) {
        float z = matrix[2] * x + matrix[6] * y + matrix[10] * up + matrix[14];
        float w = matrix[3] * x + matrix[7] * y + matrix[11] * up + matrix[15];
        return z / w;
    }

    private static float[] times(float[] projection, float[] view) {
        float[] out = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += projection[k * 4 + row] * view[column * 4 + k];
                }
                out[column * 4 + row] = sum;
            }
        }
        return out;
    }

    private static void depthFarAcrossTheMapKeepsItsOrderFromTheEye() {
        // A wall's skin and the back of a picture hung a finger's width in front of it, in a
        // house ten thousand tiles from the map's corner, seen from across the room.
        ViewCamera.Pose pose = new ViewCamera.Pose();
        float[] projection = ViewCamera.projection(0.7f, 16f / 9f, 0.05f, 200f);
        java.util.Random random = new java.util.Random(4);
        int wrongOnTheMap = 0, wrongFromTheEye = 0;
        for (int i = 0; i < 2000; i++) {
            float yaw = (float) (Math.PI + (random.nextFloat() - 0.5f) * 0.6f);
            ViewCamera.clean(pose, 10777.31f, 9765.43f, 1.6f, yaw, 0f);
            float y = 9766f + random.nextFloat() * 2f, up = 1f + random.nextFloat();
            float wall = 10774.025f, picture = 10774.037f;
            float[] onTheMap = times(projection, ViewCamera.view(pose));
            if (depth(onTheMap, wall, y, up) <= depth(onTheMap, picture, y, up)) {
                wrongOnTheMap++;
            }
            float[] fromTheEye = times(projection, ViewCamera.turned(pose));
            if (depth(fromTheEye, wall - pose.eyeX, y - pose.eyeY, up - pose.eyeUp) <= depth(fromTheEye, picture - pose.eyeX, y - pose.eyeY, up - pose.eyeUp)) {
                wrongFromTheEye++;
            }
            float[] whole = ViewCamera.view(pose), turn = ViewCamera.turned(pose);
            double far = whole[2] * (double) wall + whole[6] * (double) y + whole[10] * (double) up + whole[14];
            double near = turn[2] * (double) (wall - pose.eyeX) + turn[6] * (double) (y - pose.eyeY) + turn[10] * (double) (up - pose.eyeUp);
            check(Math.abs(far - near) < 0.01, "the turn alone, of a place less the eye, is the view of the place: " + far + " and " + near);
        }
        check(wrongOnTheMap > 20, "worked out on the map, the picture is behind its wall at some places: the fault this guards against, " + wrongOnTheMap);
        check(wrongFromTheEye == 0, "worked out from the eye, the picture is in front of its wall everywhere, and was behind it at " + wrongFromTheEye);
    }
}
