package com.cameraunlock.zomboid;

/** Tests of what a scope shows and from where, of its shape, and of where the eye is put behind it. */
final class ScopeTests {
    private ScopeTests() {}

    static void run() {
        onlyTheThreeMagnifyingScopesAreKnown();
        scopeShowsTheWorldItsPowerTimesLargerThanTheUnaidedView();
        pictureIsDrawnWideEnoughForTheWholeField();
        wholeFieldIsLitFromTheExitPupil();
        eyeOffTheAxisLosesThePicture();
        eyeTooNearSeesATunnel();
        shadowComesInFromOneSide();
        noPictureIsDrawnForAnEyeThatCannotSeeOne();
        eyeBoxIsMeasuredInTheHeadsOwnMillimetres();
        leanInStopsWhereHalfTheFieldIsLeft();
        mouseMovesTheScopesPictureAsFarAsTheHipsView();
        shapeIsWholeAndStandsOnTheRifle();
        eyeIsHeldOnTheAxisTheReliefBehindTheEyepiece();
    }

    private static void onlyTheThreeMagnifyingScopesAreKnown() {
        check(ScopeOptics.of("Base.x2Scope").power() == 2f && ScopeOptics.of("Base.x4Scope").power() == 4f && ScopeOptics.of("Base.x8Scope").power() == 8f,
                "each scope has the power its name says");
        check(ScopeOptics.of("Base.RedDot") == null && ScopeOptics.of("Base.TritiumSights") == null, "a red dot and night sights magnify nothing");
    }

    private static void scopeShowsTheWorldItsPowerTimesLargerThanTheUnaidedView() {
        for (ScopeOptics.Glass glass : ScopeOptics.KNOWN) {
            for (float zoom : new float[] {1f, FieldOfView.AIMED_ZOOM, 0.5f}) {
                // The picture round the scope is 1 / zoom times the unaided view, and the
                // scope shows the world its magnification times larger than that picture.
                float shown = ScopeOptics.magnification(glass, zoom) / zoom;
                check(Math.abs(shown - glass.power()) < 1e-4f, glass.part() + " shows the world " + shown + " times larger at zoom " + zoom);
            }
        }
    }

    private static void pictureIsDrawnWideEnoughForTheWholeField() {
        for (ScopeOptics.Glass glass : ScopeOptics.KNOWN) {
            float zoom = FieldOfView.AIMED_ZOOM;
            // The lens draws the edge of the field 3.5% further out and parts its colours by 0.6% more.
            float edge = ScopeOptics.FIELD / ScopeOptics.magnification(glass, zoom) * 1.035f * 1.006f;
            check(edge < ScopeOptics.pictureTan(glass, zoom), "the edge of the field of " + glass.part() + " is inside its picture");
            check(ScopeOptics.pictureTan(glass, zoom) < edge * 1.1f, "and the picture is not much wider than the field");
        }
    }

    private static void wholeFieldIsLitFromTheExitPupil() {
        for (ScopeOptics.Glass glass : ScopeOptics.KNOWN) {
            for (float slope = -ScopeOptics.FIELD; slope <= ScopeOptics.FIELD; slope += ScopeOptics.FIELD / 4f) {
                check(ScopeOptics.lit(glass, 0f, 0f, ScopeOptics.RELIEF, slope, -slope, 1f) == 1f, "from the exit pupil of " + glass.part() + " every line is lit");
            }
        }
    }

    private static void eyeOffTheAxisLosesThePicture() {
        for (ScopeOptics.Glass glass : ScopeOptics.KNOWN) {
            float clear = glass.exitPupil() + ScopeOptics.PUPIL + 0.0005f;
            check(ScopeOptics.lit(glass, clear, 0f, ScopeOptics.RELIEF, 0f, 0f, 1f) == 0f, "an eye clear of the exit pupil of " + glass.part() + " sees none of it");
            check(ScopeOptics.lit(glass, 0f, clear, ScopeOptics.RELIEF, 0.1f, 0.1f, 1f) == 0f, "along any line");
        }
        ScopeOptics.Glass strong = ScopeOptics.of("Base.x8Scope"), weak = ScopeOptics.of("Base.x2Scope");
        check(ScopeOptics.lit(weak, 0.005f, 0f, ScopeOptics.RELIEF, 0f, 0f, 1f) > ScopeOptics.lit(strong, 0.005f, 0f, ScopeOptics.RELIEF, 0f, 0f, 1f),
                "a weaker scope forgives an eye more off its axis");
    }

    private static void eyeTooNearSeesATunnel() {
        ScopeOptics.Glass glass = ScopeOptics.of("Base.x8Scope");
        float near = ScopeOptics.RELIEF - 0.045f;
        check(ScopeOptics.lit(glass, 0f, 0f, near, 0f, 0f, 1f) == 1f, "the middle of the field is lit from too near");
        check(ScopeOptics.lit(glass, 0f, 0f, near, ScopeOptics.FIELD, 0f, 1f) == 0f, "and its edge is dark");
    }

    private static void shadowComesInFromOneSide() {
        ScopeOptics.Glass glass = ScopeOptics.of("Base.x4Scope");
        float behind = ScopeOptics.RELIEF + 0.02f, across = 0.004f;
        float towards = ScopeOptics.lit(glass, across, 0f, behind, -0.15f, 0f, 1f), away = ScopeOptics.lit(glass, across, 0f, behind, 0.15f, 0f, 1f);
        check(towards > away, "with the eye to one side and too far back, the side of the field it is on is the darker: " + towards + " against " + away);
    }

    private static void noPictureIsDrawnForAnEyeThatCannotSeeOne() {
        ScopeOptics.Glass glass = ScopeOptics.of("Base.x4Scope");
        check(ScopeOptics.shows(glass, 0f, 0f, ScopeOptics.RELIEF, 1f), "from the exit pupil there is a picture");
        check(ScopeOptics.shows(glass, 0.003f, 0f, ScopeOptics.RELIEF - 0.03f, 1f), "and from a little off it");
        check(!ScopeOptics.shows(glass, 0.13f, -0.14f, 0.27f, 1f), "from the hip there is none");
        check(!ScopeOptics.shows(glass, 0f, 0f, -0.05f, 1f), "nor from in front of the eyepiece");
        // Whatever the answer, it never says no where a line of sight is lit.
        for (float across = 0f; across < 0.05f; across += 0.001f) {
            for (float behind = 0.01f; behind < 0.3f; behind += 0.01f) {
                boolean any = false;
                float most = Math.min(ScopeOptics.FIELD, (ScopeOptics.EYE_LENS + across) / behind);
                for (float slope = -most; slope <= most; slope += most / 16f) {
                    any |= ScopeOptics.lit(glass, across, 0f, behind, slope, 0f, 1f) > 0f;
                }
                check(!any || ScopeOptics.shows(glass, across, 0f, behind, 1f), "a lit line " + across + " off the axis and " + behind + " behind has a picture drawn for it");
            }
        }
    }

    private static void eyeBoxIsMeasuredInTheHeadsOwnMillimetres() {
        ScopeOptics.Glass glass = ScopeOptics.of("Base.x4Scope");
        // A scope drawn at three quarters of its size, and a head 5 mm off its axis: that is
        // 6.7 mm in the scope's own measure, and the light lost is that of 5 mm.
        float drawn = 0.75f, head = 0.005f;
        float small = ScopeOptics.lit(glass, head / drawn, 0f, ScopeOptics.RELIEF, 0f, 0f, drawn);
        float whole = ScopeOptics.lit(glass, head, 0f, ScopeOptics.RELIEF, 0f, 0f, 1f);
        check(Math.abs(small - whole) < 1e-5f && whole > 0f && whole < 1f, "the same head movement loses the same light whatever size the scope is drawn: " + small + " and " + whole);
        check(ScopeOptics.shows(glass, 0.0065f / drawn, 0f, ScopeOptics.RELIEF, drawn) && !ScopeOptics.shows(glass, 0.0075f / drawn, 0f, ScopeOptics.RELIEF, drawn),
                "and the picture is gone 7 mm of the head off the axis of a scope whose exit pupil is 4 mm");
    }

    private static void leanInStopsWhereHalfTheFieldIsLeft() {
        for (ScopeOptics.Glass glass : ScopeOptics.KNOWN) {
            float drawn = 0.7436f, clear = 0.02f;
            float stop = ScopeOptics.forwardStop(glass, drawn, clear);
            check(stop > 0f && stop <= ScopeOptics.RELIEF * drawn - clear + 1e-6f, "the eye of " + glass.part() + " is stopped short of the glass");
            float behind = ScopeOptics.RELIEF - stop / drawn;
            check(ScopeOptics.lit(glass, 0f, 0f, behind, 0f, 0f, drawn) == 1f, "at the stop the middle of the field is whole");
            float half = ScopeOptics.lit(glass, 0f, 0f, behind, 0.5f * ScopeOptics.FIELD, 0f, drawn);
            check(half >= 0.5f - 1e-3f, "and half way out it has half its light or more, not " + half);
        }
        check(ScopeOptics.forwardStop(ScopeOptics.of("Base.x8Scope"), 0.7436f, 0.02f) < ScopeOptics.forwardStop(ScopeOptics.of("Base.x2Scope"), 0.7436f, 0.02f),
                "a stronger scope has the shorter lean in");
    }

    private static void mouseMovesTheScopesPictureAsFarAsTheHipsView() {
        float aimed = FieldOfView.AIMED_ZOOM;
        check(ScopeOptics.turn(null, aimed, aimed) == 1f, "with no scope the mouse is left alone");
        for (ScopeOptics.Glass glass : ScopeOptics.KNOWN) {
            check(ScopeOptics.turn(glass, 1f, aimed) == 1f, "at the hip the mouse is left alone");
            // A turn of the mouse is made smaller by the zoom already. Behind the scope the
            // picture it aims with is larger than the unaided view by the scope's power.
            float carried = aimed * ScopeOptics.turn(glass, aimed, aimed) * glass.power();
            check(Math.abs(carried - 1f) < 1e-5f, "the mouse carries the picture in " + glass.part() + " " + carried + " times as far as the view at the hip");
            float between = ScopeOptics.turn(glass, 0.5f * (1f + aimed), aimed);
            check(between < 1f && between > ScopeOptics.turn(glass, aimed, aimed), "and it comes in with the zoom");
        }
    }

    private static void shapeIsWholeAndStandsOnTheRifle() {
        for (ScopeOptics.Glass glass : ScopeOptics.KNOWN) {
            ScopeModel shape = new ScopeModel(glass);
            int vertices = shape.vertices.length / ScopeModel.FLOATS;
            check(shape.vertices.length % (3 * ScopeModel.FLOATS) == 0, "the shape of " + glass.part() + " is whole triangles");
            check(shape.first[ScopeModel.BODY] == 0 && shape.first[ScopeModel.EYE_GLASS] == shape.count[ScopeModel.BODY]
                    && shape.first[ScopeModel.FRONT_GLASS] + shape.count[ScopeModel.FRONT_GLASS] == vertices, "its three parts follow one another and fill it");
            float lowest = Float.MAX_VALUE, back = Float.MAX_VALUE, front = -Float.MAX_VALUE;
            for (int vertex = 0; vertex < vertices; vertex++) {
                int at = vertex * ScopeModel.FLOATS;
                float y = shape.vertices[at + 1], z = shape.vertices[at + 2];
                float nx = shape.vertices[at + 3], ny = shape.vertices[at + 4], nz = shape.vertices[at + 5];
                check(Math.abs(nx * nx + ny * ny + nz * nz - 1f) < 1e-3f, "every surface of " + glass.part() + " says which way it faces, at vertex " + vertex);
                lowest = Math.min(lowest, z);
                back = Math.min(back, y);
                front = Math.max(front, y);
            }
            check(Math.abs(lowest - ScopeModel.SEAT) < 1e-6f, "it stands on the receiver, not in it or over it");
            check(Math.abs(back) < 1e-6f && Math.abs(front - glass.length()) < 1e-6f, "it is as long as it says");
            for (int vertex = shape.first[ScopeModel.EYE_GLASS]; vertex < shape.first[ScopeModel.FRONT_GLASS]; vertex++) {
                int at = vertex * ScopeModel.FLOATS;
                check(shape.vertices[at + 1] == shape.eyeGlassY && shape.vertices[at + 4] == -1f, "the eyepiece's glass is flat, in its housing, and faces the eye");
                float x = shape.vertices[at], z = shape.vertices[at + 2] - ScopeModel.AXIS_UP;
                check(Math.sqrt(x * x + z * z) <= ScopeOptics.EYE_LENS + 1e-6f, "and is no wider than the optics take it to be");
            }
        }
    }

    private static void eyeIsHeldOnTheAxisTheReliefBehindTheEyepiece() {
        // A rifle the game has turned and moved, read by rows, and its scope fixed where the
        // hunting rifle's is: a little ahead of the grip and over it.
        float c = (float) Math.cos(0.4), s = (float) Math.sin(0.4);
        float[] weapon = {c, -s, 0f, 0.3f, s, c, 0f, -0.2f, 0f, 0f, 1f, 0.9f, 0f, 0f, 0f, 1f};
        float[] part = weapon.clone();
        float ox = 0f, oy = 0.0398f, oz = 0.0491f;
        part[3] = weapon[0] * ox + weapon[1] * oy + weapon[2] * oz + weapon[3];
        part[7] = weapon[4] * ox + weapon[5] * oy + weapon[6] * oz + weapon[7];
        part[11] = weapon[8] * ox + weapon[9] * oy + weapon[10] * oz + weapon[11];
        float[] mount = new float[16];
        ScopeOptics.mount(part, weapon, Scope.UNITS_PER_METRE, mount);
        check(Math.abs(mount[12] - ox) < 1e-5f && Math.abs(mount[13] - oy) < 1e-5f && Math.abs(mount[14] - oz) < 1e-5f, "the scope is fixed where the game fixes it");
        check(Math.abs(mount[5] - Scope.UNITS_PER_METRE) < 1e-5f && Math.abs(mount[4]) < 1e-5f, "a metre along the scope is a metre along the barrel");

        float finalScale = 1.1f, scale = 1.5f;
        float[] origin = {weapon[3] * finalScale, weapon[7] * finalScale, weapon[11] * finalScale};
        float[] forward = {weapon[1], weapon[5], weapon[9]}, top = {weapon[2], weapon[6], weapon[10]};
        float my = 0.4968f, mz = 0.0308f;
        float[] end = {(weapon[1] * my + weapon[2] * mz + weapon[3]) * finalScale, (weapon[5] * my + weapon[6] * mz + weapon[7]) * finalScale,
                (weapon[9] * my + weapon[10] * mz + weapon[11]) * finalScale};
        float[] sighted = new float[2];
        ScopeOptics.sighted(mount, weapon, finalScale, origin, forward, top, end, scale, sighted);
        float[] held = new float[16];
        check(ViewModelMath.hold(origin, forward, top, end, scale, 0f, sighted[0], sighted[1], held), "the rifle can be held");

        // The exit pupil, which is where the eye belongs, in the scope's axes, the weapon's,
        // the character's and then the view's: it is the view's origin.
        float[] pupil = new float[3], seen = new float[3];
        ViewModelMath.place(mount, 0f, ScopeModel.EYE_GLASS_IN - ScopeOptics.RELIEF, ScopeModel.AXIS_UP, pupil);
        float px = (weapon[0] * pupil[0] + weapon[1] * pupil[1] + weapon[2] * pupil[2] + weapon[3]) * finalScale;
        float py = (weapon[4] * pupil[0] + weapon[5] * pupil[1] + weapon[6] * pupil[2] + weapon[7]) * finalScale;
        float pz = (weapon[8] * pupil[0] + weapon[9] * pupil[1] + weapon[10] * pupil[2] + weapon[11]) * finalScale;
        ViewModelMath.place(held, px, py, pz, seen);
        check(Math.abs(seen[0]) < 1e-5f && Math.abs(seen[1]) < 1e-5f && Math.abs(seen[2]) < 1e-5f,
                "the eye is on the exit pupil, not (" + seen[0] + ", " + seen[1] + ", " + seen[2] + ") from it");
        check(sighted[0] < 0f, "the barrel is under the eye");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
