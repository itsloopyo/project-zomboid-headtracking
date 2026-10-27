package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.nio.ByteBuffer;

/** Tests of the rose beds grown out of the game's painted ones: see Flowerbeds. */
final class FlowerbedTests {
    private static final int WIDE = 128, HIGH = 256;
    private static final int F = SpriteGeometry.VERTEX_FLOATS;

    private FlowerbedTests() {}

    static void run() {
        Checks.run("bedsAreToldByName", FlowerbedTests::bedsAreToldByName);
        Checks.run("aBedsDotsAreToldByColour", FlowerbedTests::aBedsDotsAreToldByColour);
        Checks.run("aBedIsMeasuredFromItsRectangleOfTheSheet", FlowerbedTests::aBedIsMeasuredFromItsRectangleOfTheSheet);
        Checks.run("rosesGrowOnlyWhereTheBedIsPaintedWithPlants", FlowerbedTests::rosesGrowOnlyWhereTheBedIsPaintedWithPlants);
        Checks.run("leavesAndFlowersTakeTheirColoursFromLeavesAndBlooms", FlowerbedTests::leavesAndFlowersTakeTheirColoursFromLeavesAndBlooms);
        Checks.run("aBedOfStonesGrowsNothing", FlowerbedTests::aBedOfStonesGrowsNothing);
        Checks.run("rosesThinWithDistance", FlowerbedTests::rosesThinWithDistance);
        Checks.run("rosesFlowerFromLateSpringToEarlyAutumn", FlowerbedTests::rosesFlowerFromLateSpringToEarlyAutumn);
        Checks.run("rosesAreBareCanesInWinter", FlowerbedTests::rosesAreBareCanesInWinter);
    }

    private static void bedsAreToldByName() {
        for (int place : new int[] {16, 19, 23, 28, 31, 39, 44, 46}) {
            check(Flowerbeds.is("vegetation_ornamental_01_" + place), place + " is a bed of roses");
        }
        for (int place : new int[] {0, 9, 15, 24, 27, 40, 43, 47, 50}) {
            check(!Flowerbeds.is("vegetation_ornamental_01_" + place), place + " is a hedge, a flamingo, a gnome or a bird bath, and no bed");
        }
        check(!Flowerbeds.is(null) && !Flowerbeds.is("vegetation_gardening_01_16"), "no other sheet holds them");
    }

    /** Colours read off the game's beds: vivid, pale and coral roses, blue flowers, their leaves, the edging stones and the soil. */
    private static void aBedsDotsAreToldByColour() {
        int[][] blooms = {{214, 52, 120}, {236, 120, 160}, {214, 170, 176}, {200, 110, 100}, {104, 136, 206}, {150, 170, 214}, {120, 40, 76}};
        for (int[] c : blooms) {
            check(Flowerbeds.dot(c[0], c[1], c[2], 255) == Flowerbeds.BLOOM, rgb(c) + " is a bloom");
        }
        int[][] leaves = {{60, 100, 50}, {40, 72, 40}, {88, 120, 70}};
        for (int[] c : leaves) {
            check(Flowerbeds.dot(c[0], c[1], c[2], 255) == Flowerbeds.LEAF, rgb(c) + " is a leaf");
        }
        int[][] stones = {{170, 170, 168}, {130, 132, 134}, {200, 198, 196}};
        for (int[] c : stones) {
            check(Flowerbeds.dot(c[0], c[1], c[2], 255) == Flowerbeds.STONE, rgb(c) + " is a stone");
        }
        int[][] nothing = {{92, 60, 34}, {70, 48, 30}, {40, 40, 40}};
        for (int[] c : nothing) {
            check(Flowerbeds.dot(c[0], c[1], c[2], 255) == Flowerbeds.NOTHING, rgb(c) + " is soil or shadow, and grows nothing");
        }
        check(Flowerbeds.dot(214, 52, 120, 100) == Flowerbeds.NOTHING, "a dot barely covered is the sprite's empty corner");
    }

    private static String rgb(int[] c) {
        return c[0] + "," + c[1] + "," + c[2];
    }

    private static void aBedIsMeasuredFromItsRectangleOfTheSheet() {
        int width = 8;
        ByteBuffer sheet = ByteBuffer.allocate(width * 4 * 4);
        for (int at = 0; at < width * 4; at++) {
            sheet.put(at * 4, (byte) 170).put(at * 4 + 1, (byte) 170).put(at * 4 + 2, (byte) 170).put(at * 4 + 3, (byte) 255);
        }
        int bloom = (1 * width + 3) * 4, leaf = (2 * width + 4) * 4;
        sheet.put(bloom, (byte) 214).put(bloom + 1, (byte) 52).put(bloom + 2, (byte) 120);
        sheet.put(leaf, (byte) 60).put(leaf + 1, (byte) 100).put(leaf + 2, (byte) 50);
        Flowerbeds.Bed bed = Flowerbeds.measure(sheet, width, 2, 1, 6, 3);
        check(bed.width() == 4 && bed.height() == 2, "the bed is its rectangle: " + bed.width() + " by " + bed.height());
        check(bed.dots()[1] == Flowerbeds.BLOOM && bed.dots()[4 + 2] == Flowerbeds.LEAF && bed.dots()[0] == Flowerbeds.STONE, "each dot where it lies in the rectangle");
    }

    /** A 2x floor sprite, its square's diamond from row 192 to the foot. */
    private static SpriteGeometry.Frame frame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
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

    /** Where on its square the sprite paints its dot (u, v): a, then b. */
    private static float a(int u, int v) {
        return 0.5f * ((u + 0.5f - 64f) / 64f + (v + 0.5f - 192f) / 32f);
    }

    private static float b(int u, int v) {
        return 0.5f * ((v + 0.5f - 192f) / 32f - (u + 0.5f - 64f) / 64f);
    }

    /** A bed painted with stones over the square's west half and roses over the rest, a leaf at every third dot among blooms. */
    private static Flowerbeds.Bed halfStones() {
        byte[] dots = new byte[WIDE * HIGH];
        for (int v = 192; v < HIGH; v++) {
            for (int u = 0; u < WIDE; u++) {
                float a = a(u, v), b = b(u, v);
                if (a < 0f || a > 1f || b < 0f || b > 1f) {
                    continue;
                }
                dots[v * WIDE + u] = a < 0.5f ? Flowerbeds.STONE : (u + v) % 3 == 0 ? Flowerbeds.LEAF : Flowerbeds.BLOOM;
            }
        }
        return new Flowerbeds.Bed(WIDE, HIGH, dots);
    }

    private static SpriteGeometry.Mesh grown(Flowerbeds.Bed bed, float away) {
        return grown(bed, away, Seasons.SUMMER, 0.5f);
    }

    private static SpriteGeometry.Mesh grown(Flowerbeds.Bed bed, float away, int season, float progression) {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        Flowerbeds.grow(new SpriteGeometry(), mesh, frame(), bed, 30, 40, away, 0.04f, 0.96f, 0.04f, 0.96f, season, progression);
        return mesh;
    }

    /** How many strips of a form the mesh holds, counted by their feet. */
    private static int strips(SpriteGeometry.Mesh mesh, int form) {
        int feet = 0;
        for (int at = 0; at < mesh.floats; at += F) {
            if (mesh.vertices[at + 10] == 2f * form) {
                feet++;
            }
        }
        return feet;
    }

    private static void rosesFlowerFromLateSpringToEarlyAutumn() {
        check(!Flowerbeds.flowering(Seasons.SPRING, 0.2f, 0f) && Flowerbeds.flowering(Seasons.SPRING, 0.7f, 1f), "no bush flowers early in spring, and every one by its end");
        check(Flowerbeds.flowering(Seasons.SUMMER, 0f, 1f) && Flowerbeds.flowering(Seasons.SUMMER2, 1f, 0f), "every bush flowers all summer");
        check(Flowerbeds.flowering(Seasons.AUTUMN, 0.05f, 0f) && !Flowerbeds.flowering(Seasons.AUTUMN, 0.5f, 1f), "every bush flowers into autumn, and none past its middle");
        check(!Flowerbeds.flowering(Seasons.WINTER, 0.5f, 0.5f), "none in winter");
        Flowerbeds.Bed bed = halfStones();
        int summer = strips(grown(bed, 3f), Grass.BLOOM), spring = strips(grown(bed, 3f, Seasons.SPRING, 0.55f), Grass.BLOOM);
        check(summer > 0 && spring > 0 && spring < summer, "a bed comes into flower a bush at a time: " + spring + " flowers late in spring, " + summer + " in summer");
        check(strips(grown(bed, 3f, Seasons.AUTUMN, 0.9f), Grass.BLOOM) == 0, "late in autumn it has none");
    }

    private static void rosesAreBareCanesInWinter() {
        Flowerbeds.Bed bed = halfStones();
        SpriteGeometry.Mesh winter = grown(bed, 3f, Seasons.WINTER, 0.5f), summer = grown(bed, 3f);
        int canes = strips(winter, Grass.BLADE + Grass.CROP);
        check(canes > 0 && canes == strips(summer, Grass.BLADE + Grass.CROP), "the bushes stand through the winter, every cane of them: " + canes);
        check(strips(winter, Grass.BROAD + Grass.CROP) == 0 && strips(winter, Grass.BLOOM) == 0, "with no leaf and no flower on them");
        check(strips(summer, Grass.BROAD + Grass.CROP) > 0, "which they have in summer");
    }

    private static void rosesGrowOnlyWhereTheBedIsPaintedWithPlants() {
        SpriteGeometry.Mesh mesh = grown(halfStones(), 3f);
        check(mesh.floats > 0, "roses grow");
        float highest = 0f;
        for (int at = 0; at < mesh.floats; at += F) {
            float[] v = mesh.vertices;
            float rootX = v[at] + v[at + 12], rootY = v[at + 1] + v[at + 13];
            check(rootX > 30.3f && rootX < 31.15f && rootY > 39.85f && rootY < 41.15f, "every bush stands on the roses of its square, not the stones: " + rootX + ", " + rootY);
            highest = Math.max(highest, v[at + 2]);
        }
        check(highest > 0.25f && highest < 0.6f, "knee high, over the lawn: " + highest);
    }

    private static void leavesAndFlowersTakeTheirColoursFromLeavesAndBlooms() {
        Flowerbeds.Bed bed = halfStones();
        SpriteGeometry.Frame frame = frame();
        SpriteGeometry.Mesh mesh = grown(bed, 3f);
        int flowers = 0;
        for (int at = 0; at < mesh.floats; at += F) {
            float[] v = mesh.vertices;
            int u = Math.round((v[at + 3] - frame.uStart) / (frame.uEnd - frame.uStart) * frame.width - 0.5f);
            int down = Math.round((v[at + 4] - frame.vStart) / (frame.vEnd - frame.vStart) * frame.height - 0.5f);
            boolean flower = (int) Math.floor(v[at + 10] * 0.5f) == Grass.BLOOM;
            byte dot = bed.dots()[down * WIDE + u];
            check(dot == (flower ? Flowerbeds.BLOOM : Flowerbeds.LEAF), (flower ? "a flower" : "a leaf or a stem") + " is coloured from " + dot + " at " + u + ", " + down);
            flowers += flower ? 1 : 0;
        }
        check(flowers > 0, "the bushes are in flower");
    }

    private static void aBedOfStonesGrowsNothing() {
        Flowerbeds.Bed half = halfStones();
        byte[] dots = half.dots().clone();
        for (int at = 0; at < dots.length; at++) {
            if (dots[at] != Flowerbeds.NOTHING) {
                dots[at] = Flowerbeds.STONE;
            }
        }
        check(grown(new Flowerbeds.Bed(WIDE, HIGH, dots), 3f).floats == 0, "stones all over: nothing grows");
        for (int at = 0; at < dots.length; at++) {
            if (dots[at] != Flowerbeds.NOTHING) {
                dots[at] = Flowerbeds.LEAF;
            }
        }
        check(grown(new Flowerbeds.Bed(WIDE, HIGH, dots), 3f).floats == 0, "leaves and no bloom to colour a flower from: nothing grows");
    }

    private static void rosesThinWithDistance() {
        SpriteGeometry.Mesh near = grown(halfStones(), 3f), far = grown(halfStones(), 18f);
        check(far.floats > 0 && far.floats * 3 < near.floats, "far off the bed is fewer and simpler bushes: " + far.floats / F + " against " + near.floats / F);
    }
}
