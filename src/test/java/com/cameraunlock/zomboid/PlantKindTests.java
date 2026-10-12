package com.cameraunlock.zomboid;

/**
 * Tests of the plants the game paints that the mod gives a shape of its own in place of the
 * game's cards.
 */
final class PlantKindTests {
    private static final int WIDE = 128, HIGH = 256;
    private static final float FOOT_U = 64f, FOOT_V = 240f;
    private static final int F = SpriteGeometry.VERTEX_FLOATS;

    private PlantKindTests() {}

    static void run() {
        Checks.run("evergreensAreToldByName", PlantKindTests::evergreensAreToldByName);
        Checks.run("anEvergreensPaintedShadowIsNoNeedles", PlantKindTests::anEvergreensPaintedShadowIsNoNeedles);
    }

    private static void evergreensAreToldByName() {
        for (String name : new String[] {"e_virginiapine_1_3", "e_virginiapineJUMBO_1_1", "e_canadianhemlock_1_2", "e_americanholly_1_0", "e_americanhollyJUMBOXL_1_0"}) {
            check(Flora.evergreen(name), name + " keeps its needles all year");
        }
        for (String name : new String[] {"e_redmapleJUMBO_1_1", "e_dogwood_1_2", "e_riverbirchJUMBOXL_1_0", "f_bushes_1_4", null}) {
            check(!Flora.evergreen(name), name + " is no evergreen");
        }
    }

    private static SpriteGeometry.Frame frame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 1f;
        frame.width = WIDE;
        frame.height = HIGH;
        frame.uStart = 0.25f;
        frame.uEnd = 0.5f;
        frame.vStart = 0.5f;
        frame.vEnd = 1f;
        frame.open = 1f;
        frame.light(0.8f, 0.8f, 0.8f);
        return frame;
    }

    private static boolean conifer(int u, int v) {
        float du = Math.abs(u - FOOT_U), up = FOOT_V - v;
        boolean shadow = Math.abs(up) < 14f && du < 50f;
        boolean needles = up > 20f && up < 200f && du < (200f - up) * 0.25f;
        return shadow || needles;
    }

    private static void anEvergreensPaintedShadowIsNoNeedles() {
        SpriteGeometry.Frame frame = frame();
        float[] all = Flora.leafPlaces(PlantKindTests::conifer, frame);
        float[] kept = Flora.above(all, frame, FOOT_V, 0.3f);
        float up = ViewCamera.LEVEL_HEIGHT / SpriteGeometry.PIXELS_PER_LEVEL;
        check(kept.length > 0 && kept.length < all.length, "some places are left, and not all");
        for (int i = 0; i < kept.length; i += 2) {
            check((FOOT_V - kept[i + 1]) * up >= 0.3f, "every place left is 0.3 tiles over the foot or more");
        }
        int needles = 0;
        for (int i = 0; i < all.length; i += 2) {
            needles += (FOOT_V - all[i + 1]) * up >= 0.3f ? 1 : 0;
        }
        check(kept.length / 2 == needles, "and every such place is kept: " + kept.length / 2 + " of " + needles);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}
