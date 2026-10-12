package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of a door's raised panels as OpeningBuilder builds them. */
final class DoorTests {
    private DoorTests() {}

    static void run() {
        Checks.run("raisedPanelHasNoGapsBetweenItsPieces", DoorTests::raisedPanelHasNoGapsBetweenItsPieces);
        Checks.run("raisedPanelFieldStandsOutOfEitherFace", DoorTests::raisedPanelFieldStandsOutOfEitherFace);
        Checks.run("raisedPanelBevelsFaceAwayFromItsMiddle", DoorTests::raisedPanelBevelsFaceAwayFromItsMiddle);
        Checks.run("narrowPanelKeepsAField", DoorTests::narrowPanelKeepsAField);
        Checks.run("joineryIsDrawnUnderItsOwnNumber", DoorTests::joineryIsDrawnUnderItsOwnNumber);
    }

    private static final float LEVEL = ViewCamera.LEVEL_HEIGHT, FACE = 0.022f;

    /** Corner k of quad q: along, up and across, with up in tiles. */
    private static float[] corner(float[] quads, int q, int k) {
        int at = q * OpeningBuilder.PANEL_QUAD + 3 * k;
        return new float[] {quads[at], quads[at + 1] * LEVEL, quads[at + 2]};
    }

    /** The way quad q faces, from its corners' turn, made to point out of the face the panel is on. */
    private static float[] facing(float[] quads, int q, float face) {
        float[] c0 = corner(quads, q, 0), c1 = corner(quads, q, 1), c3 = corner(quads, q, 3);
        float ux = c1[0] - c0[0], uy = c1[1] - c0[1], uz = c1[2] - c0[2];
        float vx = c3[0] - c0[0], vy = c3[1] - c0[1], vz = c3[2] - c0[2];
        float[] n = {uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
        float sign = Math.signum(face);
        return new float[] {n[0] * sign, n[1] * sign, n[2] * sign};
    }

    private static boolean same(float[] a, float[] b) {
        return Math.abs(a[0] - b[0]) < 1e-6f && Math.abs(a[1] - b[1]) < 1e-6f && Math.abs(a[2] - b[2]) < 1e-6f;
    }

    /** Whether point p is a corner of quad q. */
    private static boolean cornerOf(float[] quads, int q, float[] p) {
        for (int k = 0; k < 4; k++) {
            if (same(corner(quads, q, k), p)) {
                return true;
            }
        }
        return false;
    }

    private static void raisedPanelHasNoGapsBetweenItsPieces() {
        float[] quads = OpeningBuilder.raisedPanel(0.2f, 0.45f, 0.1f, 0.3f, FACE);
        check(quads.length == 9 * OpeningBuilder.PANEL_QUAD, "a raised panel is a groove of four, four bevels and a field");
        // Every bevel shares its two outer corners with the groove's inside and its two inner ones with the field.
        for (int bevel = 4; bevel < 8; bevel++) {
            int onField = 0, onGroove = 0;
            for (int k = 0; k < 4; k++) {
                float[] p = corner(quads, bevel, k);
                onField += cornerOf(quads, 8, p) ? 1 : 0;
                boolean grooved = false;
                for (int g = 0; g < 4; g++) {
                    grooved |= cornerOf(quads, g, p);
                }
                onGroove += grooved ? 1 : 0;
            }
            check(onField == 2 && onGroove == 2, "bevel " + bevel + " meets the field along one edge and the groove along the other");
        }
    }

    private static void raisedPanelFieldStandsOutOfEitherFace() {
        for (float face : new float[] {FACE, -FACE}) {
            float[] quads = OpeningBuilder.raisedPanel(0.2f, 0.45f, 0.1f, 0.3f, face);
            float field = corner(quads, 8, 0)[2], groove = corner(quads, 0, 0)[2];
            check(Math.abs(field) > Math.abs(groove) && Math.abs(groove) >= Math.abs(face), "the field stands further out of the face at " + face + " than the groove, which is not inside the leaf");
        }
    }

    private static void raisedPanelBevelsFaceAwayFromItsMiddle() {
        for (float face : new float[] {FACE, -FACE}) {
            float[] quads = OpeningBuilder.raisedPanel(0.2f, 0.45f, 0.1f, 0.3f, face);
            for (int q = 0; q < 9; q++) {
                // Turned along into up, as raisedPanel says: out of the leaf is then the face's own way.
                check(facing(quads, q, face)[2] * Math.signum(face) > 0f, "the corners of piece " + q + " of a panel on the face at " + face + " turn along into up");
            }
            check(facing(quads, 4, face)[1] < 0f, "the foot's bevel faces down, on the face at " + face);
            check(facing(quads, 5, face)[1] > 0f, "the head's bevel faces up, on the face at " + face);
            check(facing(quads, 6, face)[0] < 0f, "the bevel at the panel's start faces back along the leaf, on the face at " + face);
            check(facing(quads, 7, face)[0] > 0f, "the bevel at the panel's end faces on along the leaf, on the face at " + face);
        }
    }

    private static void narrowPanelKeepsAField() {
        // The top panels of a six-panel door are a little more than a hand high.
        float[] quads = OpeningBuilder.raisedPanel(0.2f, 0.45f, 0.7f, 0.74f, FACE);
        float[] c0 = corner(quads, 8, 0), c2 = corner(quads, 8, 2);
        check(c2[0] - c0[0] > 0f && c2[1] - c0[1] > 0f, "a panel a tenth of a tile high still has a field between its bevels");
    }

    private static void joineryIsDrawnUnderItsOwnNumber() {
        String wanted = "vMaterial > " + (OpeningBuilder.JOINERY - 0.5f) + " && vMaterial < " + (OpeningBuilder.JOINERY + 0.5f);
        check(PipelineShaders.WORLD_FRAGMENT_MAIN.contains(wanted), "the world's fragment program gives joinery its grain under OpeningBuilder.JOINERY");
    }
}
