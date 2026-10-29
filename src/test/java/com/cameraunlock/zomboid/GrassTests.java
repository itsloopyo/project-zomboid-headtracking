package com.cameraunlock.zomboid;

/** Tests of the grass grown plant by plant: what a strip's vertices carry, and how a lawn thins with distance. */
final class GrassTests {
    private static final int F = SpriteGeometry.VERTEX_FLOATS;

    private GrassTests() {}

    static void run() {
        Checks.run("stripCarriesItsEdgeItsKindAndItsHeight", GrassTests::stripCarriesItsEdgeItsKindAndItsHeight);
        Checks.run("everyCornerOfAPlantFarAcrossTheMapFindsOneRoot", GrassTests::everyCornerOfAPlantFarAcrossTheMapFindsOneRoot);
        Checks.run("pointedStripEndsInOneCorner", GrassTests::pointedStripEndsInOneCorner);
        Checks.run("fanWidensFromItsFoot", GrassTests::fanWidensFromItsFoot);
        Checks.run("headStartsWhereItsStemEnds", GrassTests::headStartsWhereItsStemEnds);
        Checks.run("lawnIsTheSameEveryTimeItIsGrown", GrassTests::lawnIsTheSameEveryTimeItIsGrown);
        Checks.run("lawnFurtherOffIsFewerPlants", GrassTests::lawnFurtherOffIsFewerPlants);
        Checks.run("lowerDensityGrowsFewerPlantsOfTheSameGrass", GrassTests::lowerDensityGrowsFewerPlantsOfTheSameGrass);
        Checks.run("lawnBehindTheEyeIsNotGrown", GrassTests::lawnBehindTheEyeIsNotGrown);
        Checks.run("lawnKeepsInsideItsBounds", GrassTests::lawnKeepsInsideItsBounds);
        Checks.run("poorGroundGrowsLess", GrassTests::poorGroundGrowsLess);
        Checks.run("spreadGrassIsAsThickAtItsLawnAsTheLawn", GrassTests::spreadGrassIsAsThickAtItsLawnAsTheLawn);
        Checks.run("spreadGrassHardlyStandsWhereItHardlyCovers", GrassTests::spreadGrassHardlyStandsWhereItHardlyCovers);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void near(float got, float wanted, float within, String what) {
        check(Math.abs(got - wanted) <= within, what + ": got " + got + ", wanted " + wanted);
    }

    private static SpriteGeometry.Frame frame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 1f;
        frame.originU = 32f;
        frame.originV = 16f;
        frame.left = 0f;
        frame.top = 0f;
        frame.width = 64f;
        frame.height = 64f;
        frame.uStart = 0f;
        frame.vStart = 0f;
        frame.uEnd = 1f;
        frame.vEnd = 1f;
        frame.open = 1f;
        frame.light(0.8f, 0.8f, 0.8f);
        return LightSiteTests.sited(frame);
    }

    private static SpriteGeometry.Mesh strip(int steps, int shape, int form, float seed, float vigour) {
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        SpriteGeometry.Frame frame = frame();
        geometry.plant(frame, 3, 4, 0.5f, 0.5f);
        geometry.addStrip(mesh, frame, 1f, 0f, 0f, 0f, 0.2f, 0.6f, 0.3f, 0.1f, 0f, steps, shape, form, seed, vigour);
        return mesh;
    }

    /** As the world's vertex shader takes a strip's vertex apart. */
    private static float edgeOf(float carried) {
        return (float) Math.floor((carried + 3f) * 0.5f) - 1f;
    }

    private static void everyCornerOfAPlantFarAcrossTheMapFindsOneRoot() {
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        SpriteGeometry.Frame frame = frame();
        geometry.plant(frame, 10766, 9752, 0.37f, 0.81f);
        geometry.addStrip(mesh, frame, 0.6f, 0.8f, 0f, 0f, 0.5f, 0.9f, 0.6f, 0.08f, 0.4f, 4, SpriteGeometry.POINTED, Grass.BLADE, 0.37f, 0.5f);
        float[] v = mesh.vertices;
        float rootX = v[0] + v[12], rootY = v[1] + v[13];
        near(rootX, 10766.37f, 2e-3f, "the root is where the plant was stood");
        near(rootY, 9752.81f, 2e-3f, "the root is where the plant was stood");
        for (int at = 0; at < mesh.floats; at += F) {
            check(v[at] + v[at + 12] == rootX && v[at + 1] + v[at + 13] == rootY,
                    "ten thousand tiles across the map every corner of a plant still finds the same root, to the bit: one depth of snow for the whole plant");
        }
    }

    private static void stripCarriesItsEdgeItsKindAndItsHeight() {
        float seed = 0.37f, vigour = 0.5f;
        SpriteGeometry.Mesh mesh = strip(3, SpriteGeometry.EVEN, Grass.PANICLE, seed, vigour);
        check(mesh.triangles() == 6, "an even strip of three steps is six triangles, got " + mesh.triangles());
        float highest = 0f;
        for (int at = 0; at < mesh.floats; at += F) {
            float[] v = mesh.vertices;
            float edge = edgeOf(v[at + 9]);
            check(edge == -1f || edge == 1f, "a vertex of an even strip is on one edge or the other, got " + edge);
            near(v[at + 9] - 2f * edge, seed, 1e-5f, "the strip's own number rides with the edge");
            float form = (float) Math.floor(v[at + 10] * 0.5f), along = v[at + 10] - 2f * form;
            check(form == Grass.PANICLE, "every vertex says what the strip is, got " + form);
            check(along >= 0f && along <= 1f, "how far along the strip a vertex is runs from 0 to 1, got " + along);
            float level = (float) Math.floor(v[at + 11]), high = v[at + 11] - level;
            check(level == Math.floor(vigour * 15.99f), "how well the grass does is carried in sixteenths, got " + level);
            near(high, v[at + 2], 1e-5f, "a vertex carries its own height over the root, which the wind moves it by");
            check(v[at + 8] == 1f + SpriteGeometry.BLADES, "a strip is marked as one over its openness");
            check(v[at] + v[at + 12] == 3.5f && v[at + 1] + v[at + 13] == 4.5f && v[at + 14] == 0f,
                    "every vertex carries the way to the plant's root, to the bit: lying snow is asked for there, the same for the whole plant, got "
                            + (v[at] + v[at + 12]) + ", " + (v[at + 1] + v[at + 13]));
            highest = Math.max(highest, high);
        }
        check(highest > 0.15f && highest < 0.3f, "a strip 0.3 long that leans and bends over ends lower than its length, got " + highest);
    }

    private static void pointedStripEndsInOneCorner() {
        SpriteGeometry.Mesh mesh = strip(3, SpriteGeometry.POINTED, Grass.BLADE, 0.2f, 1f);
        check(mesh.triangles() == 5, "a blade of three steps is two bands and a point, five triangles, got " + mesh.triangles());
        int tips = 0;
        for (int at = 0; at < mesh.floats; at += F) {
            if (edgeOf(mesh.vertices[at + 9]) == 0f) {
                tips++;
                near(mesh.vertices[at + 10], 2f * Grass.BLADE + 1f, 1e-5f, "the point is the end of the blade");
            }
        }
        check(tips == 1, "one vertex of a blade is on neither edge: its point. Got " + tips);
    }

    private static void fanWidensFromItsFoot() {
        SpriteGeometry.Mesh mesh = strip(1, SpriteGeometry.FAN, Grass.TUFT, 0.2f, 1f);
        float[] v = mesh.vertices;
        // The first two vertices are the foot, left and right, and the third is the end's right.
        float foot = Math.abs(v[F + 1] - v[1]), end = 2f * Math.abs(v[2 * F + 1] - 4.5f);
        near(foot, 0.1f * SpriteGeometry.FAN_FOOT, 1e-5f, "a fan is FAN_FOOT of its width at its foot");
        near(end, 0.1f, 1e-5f, "and all of its width at its end");
    }

    private static void headStartsWhereItsStemEnds() {
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        SpriteGeometry.Frame frame = frame();
        geometry.plant(frame, 0, 0, 0.5f, 0.5f);
        geometry.addStrip(mesh, frame, 1f, 0f, 0f, 0f, 0.1f, 0.2f, 0.4f, 0.01f, 0f, 2, SpriteGeometry.EVEN, Grass.STEM, 0.5f, 1f);
        float stemTop = mesh.vertices[mesh.floats - F + 2];
        near(stemTop, geometry.endUp, 1e-5f, "a strip says how high it ended");
        near(geometry.endLean, 0.3f, 1e-5f, "and how far over it leant there");
        int before = mesh.floats;
        geometry.addStrip(mesh, frame, 1f, 0f, geometry.endAlong, geometry.endUp, geometry.endLean, 0f, 0.05f, 0.02f, 0f, 1, SpriteGeometry.EVEN, Grass.SPIKE,
                0.5f, 1f);
        near(mesh.vertices[before + 2], stemTop, 1e-5f, "a head starts at the height its stem stopped at");
    }

    private static int lawn(float eyeX, float eyeY, float lookX, float lookY, float low, float high, Grass.Ground ground, SpriteGeometry.Mesh mesh) {
        return lawn(eyeX, eyeY, lookX, lookY, low, high, ground, mesh, 1f);
    }

    private static int lawn(float eyeX, float eyeY, float lookX, float lookY, float low, float high, Grass.Ground ground, SpriteGeometry.Mesh mesh, float density) {
        SpriteGeometry geometry = new SpriteGeometry();
        Grass.lawn(geometry, mesh, frame(), 10, 20, eyeX, eyeY, lookX, lookY, low, high, low, high, ground, density);
        return mesh.triangles();
    }

    private static void lowerDensityGrowsFewerPlantsOfTheSameGrass() {
        int all = 0, half = 0, tenth = 0;
        // Near the eye and a few tiles off, where a lawn is grown in full and where it is already thinned by distance.
        float[][] eyes = {{10.5f, 20.5f}, {10.5f, 26.5f}};
        for (float[] eye : eyes) {
            all += lawn(eye[0], eye[1], 0f, -1f, 0f, 1f, (a, b) -> 1f, new SpriteGeometry.Mesh(), 1f);
            half += lawn(eye[0], eye[1], 0f, -1f, 0f, 1f, (a, b) -> 1f, new SpriteGeometry.Mesh(), 0.5f);
            tenth += lawn(eye[0], eye[1], 0f, -1f, 0f, 1f, (a, b) -> 1f, new SpriteGeometry.Mesh(), 0.1f);
        }
        check(half < 0.7f * all && half > 0.3f * all, "half the density grows about half the triangles: " + half + " of " + all);
        check(tenth > 0 && tenth < 0.25f * all, "a tenth grows a few, and some: " + tenth + " of " + all);
    }

    private static void lawnIsTheSameEveryTimeItIsGrown() {
        SpriteGeometry.Mesh first = new SpriteGeometry.Mesh(), second = new SpriteGeometry.Mesh();
        lawn(11f, 21f, 1f, 0f, 0f, 1f, (a, b) -> 1f, first);
        lawn(11f, 21f, 1f, 0f, 0f, 1f, (a, b) -> 1f, second);
        check(first.floats > 0 && first.floats == second.floats, "a square of lawn grows the same plants each time it is built");
        for (int at = 0; at < first.floats; at++) {
            if (first.vertices[at] != second.vertices[at]) {
                throw new AssertionError("a square of lawn grown twice differs at float " + at);
            }
        }
    }

    private static void lawnFurtherOffIsFewerPlants() {
        int near = lawn(10.5f, 20.5f, 1f, 0f, 0f, 1f, (a, b) -> 1f, new SpriteGeometry.Mesh());
        int mid = lawn(10.5f, 13.5f, 0f, 1f, 0f, 1f, (a, b) -> 1f, new SpriteGeometry.Mesh());
        int far = lawn(10.5f, 20.5f - Grass.LAWN_ENDS - 1f, 0f, 1f, 0f, 1f, (a, b) -> 1f, new SpriteGeometry.Mesh());
        check(near > 3 * mid && mid > 0, "a lawn seven tiles off is far fewer triangles than one underfoot: " + near + " and " + mid);
        check(far == 0, "past where a lawn's grass ends none is grown, got " + far);
    }

    private static void lawnBehindTheEyeIsNotGrown() {
        int ahead = lawn(10.5f, 14.5f, 0f, 1f, 0f, 1f, (a, b) -> 1f, new SpriteGeometry.Mesh());
        int behind = lawn(10.5f, 14.5f, 0f, -1f, 0f, 1f, (a, b) -> 1f, new SpriteGeometry.Mesh());
        int beside = lawn(10.5f, 14.5f, 1f, 0f, 0f, 1f, (a, b) -> 1f, new SpriteGeometry.Mesh());
        int underfoot = lawn(10.5f, 20.5f, 0f, -1f, 0f, 1f, (a, b) -> 1f, new SpriteGeometry.Mesh());
        check(ahead > 0 && behind == 0, "a square straight behind the eye grows nothing: " + ahead + " ahead, " + behind + " behind");
        check(beside == ahead, "a square off to one side is grown as one ahead is: the picture's edge, or soon to be");
        check(underfoot > 0, "the square the eye stands on is grown whichever way it looks");
    }

    private static void lawnKeepsInsideItsBounds() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        lawn(10.5f, 20.5f, 1f, 0f, 0.3f, 0.7f, (a, b) -> 1f, mesh);
        check(mesh.floats > 0, "a lawn kept in from its edges still grows");
        // A plant stands inside the bounds; its blades may reach a hand or two past them.
        for (int at = 0; at < mesh.floats; at += F) {
            float x = mesh.vertices[at] - 10f, y = mesh.vertices[at + 1] - 20f;
            check(x > -0.2f && x < 1.2f && y > -0.2f && y < 1.2f, "a blade of a lawn kept to the middle of its square reached " + x + ", " + y);
        }
    }

    private static void poorGroundGrowsLess() {
        int rich = lawn(10.5f, 20.5f, 1f, 0f, 0f, 1f, (a, b) -> 1f, new SpriteGeometry.Mesh());
        int poor = lawn(10.5f, 20.5f, 1f, 0f, 0f, 1f, (a, b) -> 0f, new SpriteGeometry.Mesh());
        check(poor > 0 && poor < rich / 2, "ground where grass barely grows has under half the lawn of good ground: " + poor + " against " + rich);
    }

    /** How much of a square grass spreading from the lawn along its west side covers, as a SpriteGeometry.GRID_COVER. */
    private static float[] spreadFromTheWest() {
        int[] around = new int[25];
        for (int i = 0; i < 25; i++) {
            around[i] = i % 5 < 2 ? FloorBuilder.SPREADING : FloorBuilder.OWN;
        }
        float[] grid = new float[SpriteGeometry.GRID_COVER];
        FloorBuilder.coverFrom(around, new float[4], grid);
        return grid;
    }

    /** The places Grass.lawn grows a plant on the square under the eye, a, b and how well it does there, three to a place. */
    private static java.util.List<float[]> sites(Grass.Ground ground) {
        java.util.List<float[]> sites = new java.util.ArrayList<>();
        lawn(10.5f, 20.5f, 1f, 0f, 0f, 1f, new Grass.Ground() {
            @Override
            public float vigour(float a, float b) {
                float vigour = ground.vigour(a, b);
                sites.add(new float[] {a, b, vigour});
                return vigour;
            }

            @Override
            public boolean grows(float a, float b) {
                return ground.grows(a, b);
            }
        }, new SpriteGeometry.Mesh());
        return sites;
    }

    private static Grass.Ground spreadOver(float[] grid) {
        return new Grass.Ground() {
            @Override
            public float vigour(float a, float b) {
                return PlantBuilder.spreadThrives(grid, a, b);
            }

            @Override
            public boolean grows(float a, float b) {
                return PlantBuilder.spreadGrows(grid, a, b);
            }
        };
    }

    private static void spreadGrassIsAsThickAtItsLawnAsTheLawn() {
        float[] grid = spreadFromTheWest();
        int spread = 0, lawn = 0;
        float least = 1f;
        for (float[] site : sites(spreadOver(grid))) {
            if (site[0] < 0.1f) {
                spread++;
                least = Math.min(least, site[2]);
            }
        }
        for (float[] site : sites((a, b) -> 1f)) {
            lawn += site[0] < 0.1f ? 1 : 0;
        }
        check(lawn > 0 && spread == lawn, "along the lawn it spreads from, spread grass grows every plant the lawn would: " + spread + " of " + lawn);
        check(least > 0.9f, "and grows them as well as the lawn does, the least " + least);
    }

    private static void spreadGrassHardlyStandsWhereItHardlyCovers() {
        float[] grid = spreadFromTheWest();
        int spread = 0, lawn = 0;
        for (float[] site : sites(spreadOver(grid))) {
            float cover = SpriteGeometry.gridCover(grid, site[0], site[1]);
            check(cover > 0.45f, "spread grass grows nothing where it covers under 0.45 of the ground, grew at " + site[0] + ", " + site[1] + " covering " + cover);
            spread += cover < 0.6f ? 1 : 0;
        }
        for (float[] site : sites((a, b) -> 1f)) {
            lawn += SpriteGeometry.gridCover(grid, site[0], site[1]) < 0.6f ? 1 : 0;
        }
        check(lawn > 0 && spread < 0.2f * lawn, "where the picture crumbles its edge it grows few of the lawn's plants: " + spread + " of " + lawn);
    }
}
