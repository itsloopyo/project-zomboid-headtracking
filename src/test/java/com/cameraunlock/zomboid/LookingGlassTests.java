package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tests of where a mirror's glass is, which mirrors the eye sees, and the eye behind the glass that draws what they show. */
final class LookingGlassTests {
    private static final float TAN = (float) Math.tan(Math.toRadians(32.5)), ASPECT = 16f / 9f, NEAR = 0.05f;
    private static final String[] MIRRORS = {"frame_tall", "frame_square", "canvas", "medicine_cabinet", "dresser_mirror"};

    private LookingGlassTests() {}

    static void run() {
        Checks.run("glassLooksOutOfTheFrontOfItsPieceAtEveryTurn", LookingGlassTests::glassLooksOutOfTheFrontOfItsPieceAtEveryTurn);
        Checks.run("glassIsTheFaceOfThePaneAsStood", LookingGlassTests::glassIsTheFaceOfThePaneAsStood);
        Checks.run("nothingOfAMirrorStandsWithinTheClearanceBeforeItsGlass", LookingGlassTests::nothingOfAMirrorStandsWithinTheClearanceBeforeItsGlass);
        Checks.run("glassIsSeenOnlyFromBeforeItAndInThePicture", LookingGlassTests::glassIsSeenOnlyFromBeforeItAndInThePicture);
        Checks.run("glassPassingBehindTheEyeIsCutAtNear", LookingGlassTests::glassPassingBehindTheEyeIsCutAtNear);
        Checks.run("glassReachingPastThePictureIsAtItsEdge", LookingGlassTests::glassReachingPastThePictureIsAtItsEdge);
        Checks.run("aMirrorsPictureFadesInOverTheLastTileOfItsReach", LookingGlassTests::aMirrorsPictureFadesInOverTheLastTileOfItsReach);
        Checks.run("thePlaneTakingMostOfThePictureIsChosenWithAllItsGlass", LookingGlassTests::thePlaneTakingMostOfThePictureIsChosenWithAllItsGlass);
        Checks.run("onlyThePlanesTakingMostOfThePictureAreChosen", LookingGlassTests::onlyThePlanesTakingMostOfThePictureAreChosen);
        Checks.run("aPlaneKeepsItsPlaceFromFrameToFrame", LookingGlassTests::aPlaneKeepsItsPlaceFromFrameToFrame);
        Checks.run("thePlaneIsTheForemostOfItsGlass", LookingGlassTests::thePlaneIsTheForemostOfItsGlass);
        Checks.run("eyeBehindTheGlassSeesWhatTheGlassShows", LookingGlassTests::eyeBehindTheGlassSeesWhatTheGlassShows);
        Checks.run("aPaneThrowsBackMoreOfTheStreetByDayThanOfItsRoom", LookingGlassTests::aPaneThrowsBackMoreOfTheStreetByDayThanOfItsRoom);
    }

    /** A piece of the model stood at the middle of square (10, 20), turned, and its glass. */
    private static float[] stood(Models.Instances placed, String name, int turns, float scale, float scaleUp) {
        placed.clear();
        placed.add(Models.get(name), new SpriteGeometry.Frame(), 10, 20, 10.5f, 20.5f, 0.3f, turns, scale, scaleUp, WorldScene.PLAIN, 0, 0f, 0f, 0, 0f, 0f);
        placed.glass(Models.get(name));
        check(placed.glassCount == 1, name + ": one glass noted");
        return placed.glasses;
    }

    private static void glassLooksOutOfTheFrontOfItsPieceAtEveryTurn() {
        Models.Instances placed = new Models.Instances();
        float[] back = new float[2];
        for (String name : MIRRORS) {
            for (int turns = 0; turns < 4; turns++) {
                float[] glass = stood(placed, name, turns, Furniture.TILES_PER_METRE, Furniture.TILES_PER_METRE);
                // The model's back, +Y, is its wall.
                Models.turn(turns, 0f, 1f, back);
                check(Math.abs(glass[LookingGlass.FACING] + back[0]) < 1e-5f && Math.abs(glass[LookingGlass.FACING + 1] + back[1]) < 1e-5f
                        && glass[LookingGlass.FACING + 2] == 0f, name + " turned " + turns + ": the glass looks away from the wall behind the piece");
                // The edge across and the edge up lie in the glass's plane.
                float across = dot(glass, LookingGlass.ACROSS, glass, LookingGlass.FACING), up = dot(glass, LookingGlass.UP, glass, LookingGlass.FACING);
                check(Math.abs(across) < 1e-5f && Math.abs(up) < 1e-5f, name + " turned " + turns + ": the glass's edges lie in its plane");
                check(glass[LookingGlass.UP + 2] > 0f && glass[LookingGlass.UP] == 0f && glass[LookingGlass.UP + 1] == 0f,
                        name + " turned " + turns + ": the edge up stands upright");
            }
        }
    }

    private static void glassIsTheFaceOfThePaneAsStood() {
        Models.Instances placed = new Models.Instances();
        for (String name : MIRRORS) {
            Models.Model model = Models.get(name);
            float scale = 0.8f, scaleUp = 1.3f;
            float[] glass = stood(placed, name, 1, scale, scaleUp);
            float[] pane = Decor.pane(model);
            float wide = (float) Math.sqrt(dot(glass, LookingGlass.ACROSS, glass, LookingGlass.ACROSS));
            float high = glass[LookingGlass.UP + 2];
            check(Math.abs(wide - (pane[1] - pane[0]) * scale) < 0.01f * scale, name + ": the glass is as wide as the pane, " + wide);
            check(Math.abs(high - (pane[3] - pane[2]) * scaleUp) < 0.01f * scaleUp, name + ": and as high, " + high);
            // Every vertex of the pane is at or behind the glass's plane: the glass is its face.
            float[] plane = new float[4], at = new float[3];
            LookingGlass.plane(glass, 0, plane);
            float nearest = Float.MAX_VALUE;
            for (int vertex = model.first[Models.PANE]; vertex < model.first[Models.PANE + 1]; vertex++) {
                int v = vertex * Models.VERTEX_FLOATS;
                placed.stand(0, model.vertices[v], model.vertices[v + 1], model.vertices[v + 2], at, 0);
                nearest = Math.min(nearest, plane[3] - (plane[0] * at[0] + plane[1] * at[1] + plane[2] * at[2]));
            }
            check(Math.abs(nearest) < 1e-4f, name + ": the pane's face lies in the glass's plane and nothing of it before it, " + nearest);
        }
    }

    private static void nothingOfAMirrorStandsWithinTheClearanceBeforeItsGlass() {
        Models.Instances placed = new Models.Instances();
        for (String name : MIRRORS) {
            Models.Model model = Models.get(name);
            float[] glass = stood(placed, name, 0, Furniture.TILES_PER_METRE, Furniture.TILES_PER_METRE);
            float[] plane = new float[4], at = new float[3];
            LookingGlass.plane(glass, 0, plane);
            for (int vertex = 0; vertex < model.first[Models.SURFACES]; vertex++) {
                if (vertex >= model.first[Models.PANE] && vertex < model.first[Models.PANE + 1]) {
                    continue;
                }
                int v = vertex * Models.VERTEX_FLOATS;
                placed.stand(0, model.vertices[v], model.vertices[v + 1], model.vertices[v + 2], at, 0);
                float before = plane[0] * at[0] + plane[1] * at[1] + plane[2] * at[2] - plane[3];
                check(before < 1e-4f || before >= LookingGlass.CLEAR, name + ": a vertex " + before + " before the glass is cut from what the glass shows");
            }
        }
    }

    private static void thePlaneIsTheForemostOfItsGlass() {
        LookingGlass looking = new LookingGlass();
        ViewCamera.Pose camera = eye(20.5f, 27f, toward(20.5f, 27f, 20.5f, 30f));
        float[] glasses = new float[2 * LookingGlass.FLOATS];
        System.arraycopy(wallGlass(19.6f), 0, glasses, 0, LookingGlass.FLOATS);
        // A smaller mirror beside it whose glass stands 3 mm further out from the wall.
        float[] proud = wallGlass(20.8f);
        proud[LookingGlass.CORNER + 1] = 29.997f;
        proud[LookingGlass.ACROSS] = 0.3f;
        System.arraycopy(proud, 0, glasses, LookingGlass.FLOATS, LookingGlass.FLOATS);
        looking.begin();
        looking.consider(glasses, 0, false, camera, TAN, ASPECT, NEAR);
        looking.consider(glasses, 1, false, camera, TAN, ASPECT, NEAR);
        check(looking.end() == 1 && looking.planes[0].count == 2, "both glasses are in one plane");
        check(Math.abs(looking.planes[0].plane[3] + 29.997f) < 1e-5f, "which is the proud glass's, so it is not left before the plane: " + looking.planes[0].plane[3]);
    }

    /** An eye at (x, y), at a height of 1.6, looking along the map by the given angle. */
    private static ViewCamera.Pose eye(float x, float y, float headingDegrees) {
        ViewCamera.Pose camera = new ViewCamera.Pose();
        ViewCamera.clean(camera, x, y, 1.6f, (float) Math.toRadians(headingDegrees), 0f);
        return camera;
    }

    /** A glass a tile wide on the plane y = 30, from x = 20 to 21 and 1 to 2 up, looking towards -y. */
    private static float[] wallGlass(float fromX) {
        float[] glass = new float[LookingGlass.FLOATS];
        glass[LookingGlass.CORNER] = fromX;
        glass[LookingGlass.CORNER + 1] = 30f;
        glass[LookingGlass.CORNER + 2] = 1f;
        glass[LookingGlass.ACROSS] = 1f;
        glass[LookingGlass.UP + 2] = 1f;
        glass[LookingGlass.FACING + 1] = -1f;
        return glass;
    }

    /** The angle that has the eye look at (x, y) from (fromX, fromY). */
    private static float toward(float fromX, float fromY, float x, float y) {
        ViewCamera.Pose probe = new ViewCamera.Pose();
        for (int degrees = 0; degrees < 360; degrees++) {
            ViewCamera.clean(probe, fromX, fromY, 1.6f, (float) Math.toRadians(degrees), 0f);
            float dx = x - fromX, dy = y - fromY, length = (float) Math.hypot(dx, dy);
            if ((probe.forwardX * dx + probe.forwardY * dy) / length > 0.9999f) {
                return degrees;
            }
        }
        throw new IllegalStateException("no heading looks from (" + fromX + ", " + fromY + ") to (" + x + ", " + y + ")");
    }

    private static void glassIsSeenOnlyFromBeforeItAndInThePicture() {
        LookingGlass looking = new LookingGlass();
        float[] glass = wallGlass(20f), rect = new float[4];
        float at = toward(20.5f, 27f, 20.5f, 30f);
        check(looking.seen(glass, 0, eye(20.5f, 27f, at), TAN, ASPECT, NEAR, rect), "a glass three tiles before the eye, looked at, is seen");
        check(rect[0] < 0f && rect[2] > 0f && rect[1] < 0.2f && rect[3] > -0.2f, "about the middle of the picture");
        check(!looking.seen(glass, 0, eye(20.5f, 33f, toward(20.5f, 33f, 20.5f, 30f)), TAN, ASPECT, NEAR, rect), "nor from behind its wall");
        check(!looking.seen(glass, 0, eye(20.5f, 27f, at + 180f), TAN, ASPECT, NEAR, rect), "and not with the eye turned away from it");
        check(!looking.seen(glass, 0, eye(20.5f, 30f - LookingGlass.REACH - 1f, at), TAN, ASPECT, NEAR, rect), "nor from past its reach");
        check(!looking.seen(glass, 0, eye(20.5f, 30f - LookingGlass.NEAREST / 2f, at), TAN, ASPECT, NEAR, rect), "nor with the eye as good as in it");
    }

    private static void glassPassingBehindTheEyeIsCutAtNear() {
        LookingGlass looking = new LookingGlass();
        // A long glass the eye stands near the end of, looking along it at a slant: part of it is behind the eye.
        float[] glass = wallGlass(10f), rect = new float[4];
        glass[LookingGlass.ACROSS] = 20f;
        check(looking.seen(glass, 0, eye(15f, 29f, toward(15f, 29f, 25f, 30f)), TAN, ASPECT, NEAR, rect), "the part of the glass before the eye is seen");
        check(rect[0] >= -1f && rect[2] <= 1f && rect[1] >= -1f && rect[3] <= 1f, "and kept to the picture");
    }

    /** SightQuery counts glass at the picture's edge as showing, since a turn brings it in before any query sees it. */
    private static void glassReachingPastThePictureIsAtItsEdge() {
        LookingGlass looking = new LookingGlass();
        float[] glasses = wallGlass(20f);
        looking.begin();
        looking.consider(glasses, 0, false, eye(20.5f, 27f, toward(20.5f, 27f, 20.5f, 30f)), TAN, ASPECT, NEAR);
        check(looking.end() == 1 && !looking.planes[0].pastEdge, "a glass in the middle of the picture is not at its edge");
        glasses[LookingGlass.ACROSS] = 20f;
        glasses[LookingGlass.CORNER] = 10f;
        looking.begin();
        looking.consider(glasses, 0, false, eye(15f, 29f, toward(15f, 29f, 25f, 30f)), TAN, ASPECT, NEAR);
        check(looking.end() == 1 && looking.planes[0].pastEdge, "a long glass running past the side of the picture is at its edge, though its part of the picture is kept to it: "
                + looking.planes[0].rect[0] + " to " + looking.planes[0].rect[2]);
    }

    /** Walking up to a mirror, its own picture comes in over the trace a little at a time, with nothing to jump at the reach. */
    private static void aMirrorsPictureFadesInOverTheLastTileOfItsReach() {
        LookingGlass looking = new LookingGlass();
        float[] glasses = wallGlass(20f);
        float last = -1f;
        // From 3 tiles off to the last hundredth of a tile before the reach, and one past it.
        for (int step = 0; step <= 300; step++) {
            float off = step == 300 ? LookingGlass.REACH + 0.01f : 3f + step * 0.01f;
            looking.begin();
            looking.consider(glasses, 0, false, eye(20.5f, 30f - off, toward(20.5f, 30f - off, 20.5f, 30f)), TAN, ASPECT, NEAR);
            boolean kept = looking.end() == 1;
            float shown = kept ? looking.planes[0].shown[0] : 0f;
            float expected = off <= LookingGlass.REACH - LookingGlass.FADE ? 1f : off >= LookingGlass.REACH ? 0f : (LookingGlass.REACH - off) / LookingGlass.FADE;
            check(Math.abs(shown - expected) < 1e-3f, "a glass " + off + " tiles off shows " + shown + " of its own picture, not " + expected);
            check(kept == off < LookingGlass.REACH, "and its picture is drawn only within the reach, " + off + " tiles off: " + kept);
            check(last < 0f || Math.abs(shown - last) <= 0.01f / LookingGlass.FADE + 1e-3f, "a step of a hundredth of a tile changes it by a hundredth at most: " + last + " to " + shown);
            last = shown;
        }
        // Two glasses in one plane, one further off than the other: each fades by its own distance.
        float[] two = new float[2 * LookingGlass.FLOATS];
        System.arraycopy(wallGlass(20f), 0, two, 0, LookingGlass.FLOATS);
        System.arraycopy(wallGlass(24f), 0, two, LookingGlass.FLOATS, LookingGlass.FLOATS);
        ViewCamera.Pose slant = eye(20.5f, 25.3f, toward(20.5f, 25.3f, 22.5f, 30f));
        looking.begin();
        looking.consider(two, 0, false, slant, TAN, ASPECT, NEAR);
        looking.consider(two, 1, false, slant, TAN, ASPECT, NEAR);
        check(looking.end() == 1 && looking.planes[0].count == 2, "both glasses are kept");
        float near = LookingGlass.nearest(two, 0, slant), far = LookingGlass.nearest(two, LookingGlass.FLOATS, slant);
        float[] shown = looking.planes[0].shown;
        check(Math.abs(shown[0] - LookingGlass.shown(near)) < 1e-6f && Math.abs(shown[1] - LookingGlass.shown(far)) < 1e-6f
                && shown[0] == 1f && shown[1] < 1f && shown[1] > 0f,
                "the nearer, " + near + " off, shows " + shown[0] + " and the further, " + far + " off, " + shown[1]);
    }

    private static void thePlaneTakingMostOfThePictureIsChosenWithAllItsGlass() {
        LookingGlass looking = new LookingGlass();
        ViewCamera.Pose camera = eye(20.5f, 27f, toward(20.5f, 27f, 20.5f, 30f));
        float[] glasses = new float[3 * LookingGlass.FLOATS];
        System.arraycopy(wallGlass(19.6f), 0, glasses, 0, LookingGlass.FLOATS);
        System.arraycopy(wallGlass(20.8f), 0, glasses, LookingGlass.FLOATS, LookingGlass.FLOATS);
        // A small glass on the wall to the right, looking towards -x, further off.
        float[] side = new float[LookingGlass.FLOATS];
        side[LookingGlass.CORNER] = 23f;
        side[LookingGlass.CORNER + 1] = 29.5f;
        side[LookingGlass.CORNER + 2] = 1.4f;
        side[LookingGlass.ACROSS + 1] = -0.3f;
        side[LookingGlass.UP + 2] = 0.3f;
        side[LookingGlass.FACING] = -1f;
        System.arraycopy(side, 0, glasses, 2 * LookingGlass.FLOATS, LookingGlass.FLOATS);
        looking.begin();
        for (int i = 0; i < 3; i++) {
            looking.consider(glasses, i, false, camera, TAN, ASPECT, NEAR);
        }
        check(looking.end() == 2, "the eye sees mirrors in two planes");
        LookingGlass.Plane front = looking.planes[0], right = looking.planes[1];
        check(front.count == 2, "both glasses on the wall before the eye are kept in the first, which takes the most of the picture: " + front.count);
        check(Math.abs(front.plane[1] + 1f) < 1e-6f && Math.abs(front.plane[3] + 30f) < 1e-4f, "their plane is the wall's");
        float[] one = new float[4], two = new float[4];
        looking.seen(wallGlass(19.6f), 0, camera, TAN, ASPECT, NEAR, one);
        looking.seen(wallGlass(20.8f), 0, camera, TAN, ASPECT, NEAR, two);
        check(front.rect[0] == Math.min(one[0], two[0]) && front.rect[1] == Math.min(one[1], two[1])
                && front.rect[2] == Math.max(one[2], two[2]) && front.rect[3] == Math.max(one[3], two[3]), "and the part of the picture kept is what both take up");
        check(right.count == 1 && Math.abs(right.plane[0] + 1f) < 1e-6f && Math.abs(right.plane[3] + 23f) < 1e-4f,
                "and the glass on the side wall is kept too, in a plane of its own: " + right.count);
        check(looking.planes[2].count == 0, "the third place has no plane");
        looking.begin();
        check(looking.end() == 0, "with nothing considered the eye sees no mirror");
        for (LookingGlass.Plane plane : looking.planes) {
            check(plane.count == 0, "and no place keeps a plane");
        }
    }

    /** A glass a tile wide on the plane x = wallX, from y = 29 to 30 and 1 to 2 up, looking towards -x. */
    private static float[] sideGlass(float wallX) {
        float[] glass = new float[LookingGlass.FLOATS];
        glass[LookingGlass.CORNER] = wallX;
        glass[LookingGlass.CORNER + 1] = 29f;
        glass[LookingGlass.CORNER + 2] = 1f;
        glass[LookingGlass.ACROSS + 1] = 1f;
        glass[LookingGlass.UP + 2] = 1f;
        glass[LookingGlass.FACING] = -1f;
        return glass;
    }

    /** One more plane than are drawn, each smaller than the last: the smallest is the one left out. */
    private static void onlyThePlanesTakingMostOfThePictureAreChosen() {
        LookingGlass looking = new LookingGlass();
        ViewCamera.Pose camera = eye(20.5f, 27f, toward(20.5f, 27f, 20.5f, 30f));
        float[] glasses = new float[(LookingGlass.PLANES + 1) * LookingGlass.FLOATS];
        for (int i = 0; i <= LookingGlass.PLANES; i++) {
            // Each further off and narrower than the last, so smaller in the picture.
            float[] glass = wallGlass(20f);
            glass[LookingGlass.CORNER + 1] = 29f + i * 0.5f;
            glass[LookingGlass.ACROSS] = 1.2f - i * 0.25f;
            System.arraycopy(glass, 0, glasses, i * LookingGlass.FLOATS, LookingGlass.FLOATS);
        }
        looking.begin();
        for (int i = LookingGlass.PLANES; i >= 0; i--) {
            looking.consider(glasses, i, false, camera, TAN, ASPECT, NEAR);
        }
        check(looking.end() == LookingGlass.PLANES, "as many planes as are drawn are chosen");
        for (int i = 0; i < LookingGlass.PLANES; i++) {
            float[] plane = looking.planes[i].plane;
            check(looking.planes[i].count == 1 && Math.abs(plane[3] + 29f + i * 0.5f) < 1e-4f,
                    "the " + i + "th largest is in place " + i + ", at " + -plane[3]);
        }
    }

    /** A plane keeps its place while another comes into the picture and takes up more of it. */
    private static void aPlaneKeepsItsPlaceFromFrameToFrame() {
        LookingGlass looking = new LookingGlass();
        ViewCamera.Pose camera = eye(20.5f, 27f, toward(20.5f, 27f, 20.5f, 30f));
        float[] small = sideGlass(22f), large = wallGlass(19.6f);
        small[LookingGlass.ACROSS + 1] = 0.3f;
        large[LookingGlass.ACROSS] = 1.8f;
        looking.begin();
        looking.consider(small, 0, false, camera, TAN, ASPECT, NEAR);
        check(looking.end() == 1 && Math.abs(looking.planes[0].plane[0] + 1f) < 1e-6f, "the side wall's glass alone is in the first place");
        looking.begin();
        looking.consider(large, 0, false, camera, TAN, ASPECT, NEAR);
        looking.consider(small, 0, false, camera, TAN, ASPECT, NEAR);
        check(looking.end() == 2, "then both planes are chosen");
        check(Math.abs(looking.planes[0].plane[0] + 1f) < 1e-6f && looking.planes[0].count == 1, "the side wall's keeps the first place, though it takes less of the picture");
        check(Math.abs(looking.planes[1].plane[1] + 1f) < 1e-6f && looking.planes[1].count == 1, "and the wall before the eye takes the next");
        looking.begin();
        looking.consider(large, 0, false, camera, TAN, ASPECT, NEAR);
        check(looking.end() == 1 && looking.planes[0].count == 0 && Math.abs(looking.planes[1].plane[1] + 1f) < 1e-6f,
                "with the side wall's glass gone, the wall before the eye stays in its place and the first is empty");
    }

    private static void eyeBehindTheGlassSeesWhatTheGlassShows() {
        float[] glass = wallGlass(20f), plane = new float[4];
        LookingGlass.plane(glass, 0, plane);
        ViewCamera.Pose camera = eye(20.3f, 27.2f, toward(20.3f, 27.2f, 20.7f, 30f));
        ViewCamera.turn(camera, (float) Math.toRadians(12), (float) Math.toRadians(4), (float) Math.toRadians(9), true);
        ViewCamera.Pose behind = new ViewCamera.Pose();
        WaterMirror.mirror(camera, plane, behind);
        check(Math.abs(behind.eyeY - 32.8f) < 1e-4f && Math.abs(behind.eyeX - camera.eyeX) < 1e-6f, "the eye's image is as far behind the glass as the eye is before it");
        // A point in the room, before the glass, and its image behind it.
        float x = 19.6f, y = 28.4f, up = 1.2f;
        float[] ofImage = new float[2], ofPoint = new float[2];
        check(ViewCamera.project(camera, TAN, ASPECT, x, 2f * 30f - y, up, ofImage), "the point's image is before the eye");
        check(ViewCamera.project(behind, TAN, ASPECT, x, y, up, ofPoint), "and the point before the eye behind the glass");
        check(Math.abs(ofImage[0] - ofPoint[0]) < 1e-4f && Math.abs(ofImage[1] - ofPoint[1]) < 1e-4f,
                "at the same place in the picture, so the glass shows that picture pixel for pixel");
    }

    private static float dot(float[] a, int at, float[] b, int bt) {
        return a[at] * b[bt] + a[at + 1] * b[bt + 1] + a[at + 2] * b[bt + 2];
    }

    /**
     * Square on, the world shader's pane throws back its room's side at the share the room's picture is laid at
     * (THROWN), and the street by day at a share that keeps a window from reading as a hole in a sunny wall.
     */
    private static void aPaneThrowsBackMoreOfTheStreetByDayThanOfItsRoom() {
        Matcher world = Pattern.compile(Pattern.quote("thrown = min(1.0, 1.6 * (mix(") + "([0-9.]+), ([0-9.]+)"
                + Pattern.quote(", street * (1.0 - uDark)) + 0.87 *")).matcher(PipelineShaders.WORLD_FRAGMENT_MAIN);
        Matcher laid = Pattern.compile(Pattern.quote("1.6 * (") + "([0-9.]+)" + Pattern.quote(" + 0.87 *"))
                .matcher(LookingGlass.THROWN);
        check(world.find() && laid.find(), "the pane's share is read from both shaders");
        float room = Float.parseFloat(world.group(1)), street = Float.parseFloat(world.group(2));
        check(room == Float.parseFloat(laid.group(1)), "a pane's room side throws back what the room's picture is laid at: " + room + " and " + laid.group(1));
        check(1.6f * street >= 0.35f && 1.6f * street <= 0.45f, "square on, a pane throws back between 0.35 and 0.45 of the street by day: " + 1.6f * street);
    }
}
