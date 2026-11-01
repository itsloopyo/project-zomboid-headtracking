package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.Arrays;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoObjectType;

/**
 * What shots leave that the picture does not build into a wall's or a fence's own geometry: the
 * rims round holes, saved with them (BlastHoles.rim) and laid again from a save (Impacts.restore);
 * the marks rounds leave on the pickets of a fence the picture stands as a model (ModelFences.struck);
 * and the holes through a door drawn as its sprite (OpeningBuilder.cutCard).
 */
final class ShotMarkTests {
    private ShotMarkTests() {}

    static void run() {
        Checks.run("aRimSavedReadsBackAsTheHoleImpactsDraws", ShotMarkTests::aRimSavedReadsBackAsTheHoleImpactsDraws);
        Checks.run("anObjectKeepsItsNewestRims", ShotMarkTests::anObjectKeepsItsNewestRims);
        Checks.run("aListOfRimsNoShotLaysIsRefused", ShotMarkTests::aListOfRimsNoShotLaysIsRefused);
        Checks.run("aRimLaidAgainIsDrawnOnceAndThrowsUpNothing", ShotMarkTests::aRimLaidAgainIsDrawnOnceAndThrowsUpNothing);
        Checks.run("aRoundMeetsAPicketAndPassesBetweenThem", ShotMarkTests::aRoundMeetsAPicketAndPassesBetweenThem);
        Checks.run("aDoorDrawnAsItsSpriteIsHoledWhereItWasStruck", ShotMarkTests::aDoorDrawnAsItsSpriteIsHoledWhereItWasStruck);
    }

    /** A rim out on the map, where a float holds a place to a 1024th of a tile, on a fence's face struck by a pellet. */
    private static Impacts.Struck rim(float x) {
        return new Impacts.Struck(x, 8730.7134f, 1.2345678f, 0f, -1f, 0f, 0.3f, 0.95f, -0.1f, 0.47f, Impacts.WOOD, ImpactFinder.PELLET, true, 1f, 0f, 0f);
    }

    private static void aRimSavedReadsBackAsTheHoleImpactsDraws() {
        Impacts.Struck shot = rim(10256.371f);
        float[] rims = BlastHoles.withRim(new float[0], shot);
        float[] back = BlastHoles.parseRims(BlastHoles.writeRims(rims));
        check(Arrays.equals(rims, back), "what is saved reads back the same, to the bit: " + Arrays.toString(back));
        Impacts.Struck laid = BlastHoles.rimAt(back, 0);
        check(laid.x() == shot.x() && laid.y() == shot.y() && laid.up() == shot.up(), "the rim is laid again where it was");
        check(laid.normalX() == shot.normalX() && laid.normalY() == shot.normalY() && laid.normalUp() == shot.normalUp(), "facing as the face it is on");
        check(laid.kind() == shot.kind() && laid.size() == shot.size() && laid.hole(), "the same mark of the same round");
        check(laid.grainX() == shot.grainX() && laid.grainY() == shot.grainY() && laid.grainUp() == shot.grainUp(), "with the grain running as it did");
        check(BlastHoles.parseRims("11:").length == 0, "nothing saved is nothing");
    }

    private static void anObjectKeepsItsNewestRims() {
        float[] rims = new float[0];
        for (int i = 0; i < BlastHoles.MOST_RIMS + 3; i++) {
            rims = BlastHoles.withRim(rims, rim(i));
        }
        check(rims.length == BlastHoles.MOST_RIMS * BlastHoles.RIM_FLOATS, "the most rims are kept: " + rims.length / BlastHoles.RIM_FLOATS);
        check(rims[0] == 3f && rims[rims.length - BlastHoles.RIM_FLOATS] == BlastHoles.MOST_RIMS + 2, "and the oldest went first");
    }

    private static void aListOfRimsNoShotLaysIsRefused() {
        String one = BlastHoles.writeRims(BlastHoles.withRim(new float[0], rim(10f)));
        String list = one.substring(3);
        check(refused(list), "a list without what it starts with is refused");
        check(refused("11:" + list.replaceFirst("^[^,]*", "NaN")), "and one with a number that is not finite");
        check(refused("11:" + list.substring(0, list.lastIndexOf(','))), "and one a number short");
        String[] words = list.split(",");
        words[6] = "99.0";
        check(refused("11:" + String.join(",", words)), "and one of a kind of surface there is none of");
        words[6] = "1.0";
        words[7] = "0.0";
        check(refused("11:" + String.join(",", words)), "and one of a round of no size");
        check(refused("11:" + String.join(",", java.util.Collections.nCopies(BlastHoles.MOST_RIMS + 1, list))), "and one of more rims than an object keeps");
    }

    private static boolean refused(String saved) {
        try {
            BlastHoles.parseRims(saved);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static int flying() {
        int flying = 0;
        for (Impacts.Struck each : Impacts.held) {
            flying += each == null ? 0 : 1;
        }
        return flying;
    }

    private static void aRimLaidAgainIsDrawnOnceAndThrowsUpNothing() {
        Impacts.forget();
        Impacts.take();
        try {
            Impacts.Struck shot = rim(5f);
            Impacts.add(shot);
            Impacts.take();
            check(Impacts.holeCount == 1 && flying() == 1, "a shot leaves its rim and throws up what flies from it");
            // Its chunk let go of and loaded again in the same session.
            Impacts.restore(BlastHoles.rimAt(BlastHoles.withRim(new float[0], shot), 0));
            Impacts.take();
            check(Impacts.holeCount == 1, "the rim its chunk lays again as it loads once more is not laid twice: " + Impacts.holeCount);
            Impacts.restore(BlastHoles.rimAt(BlastHoles.withRim(new float[0], rim(6f)), 0));
            Impacts.take();
            check(Impacts.holeCount == 2 && flying() == 1, "a rim read from a save is laid, and nothing flies from it: " + Impacts.holeCount + " rims, " + flying() + " flying");
            // The save left, and the next one's chunks loaded before a frame takes what they lay.
            Impacts.forget();
            Impacts.restore(BlastHoles.rimAt(BlastHoles.withRim(new float[0], rim(7f)), 0));
            Impacts.take();
            check(Impacts.holeCount == 1 && Impacts.holes[0].x() == 7f && flying() == 0,
                    "the rims of the save left are gone and those the next save's chunks laid as they loaded are kept: " + Impacts.holeCount);
            boolean refused = false;
            try {
                Impacts.restore(new Impacts.Struck(1f, 1f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 0f, Impacts.WOOD, 1f, false, 1f, 0f, 0f));
            } catch (IllegalArgumentException expected) {
                refused = true;
            }
            check(refused, "only a hole is laid again from a save");
        } finally {
            Impacts.forget();
            Impacts.take();
        }
    }

    private static final float[] hit = new float[ModelFences.HIT_FLOATS];

    /** Whether a level line across the north (or west) edge of square (10, 20), that far along it and that high over the fence's foot, meets the fence. */
    private static boolean meets(ModelFences.Fence fence, boolean north, float along, float up) {
        ViewCamera.Pose line = new ViewCamera.Pose();
        line.eyeAt(north ? 10f + along : 9f, north ? 19f : 20f + along, up);
        line.forwardX = north ? 0f : 1f;
        line.forwardY = north ? 1f : 0f;
        line.forwardUp = 0f;
        if (!ModelFences.struck(fence, 10, 20, north, 0f, line, 2f, 0L, hit)) {
            return false;
        }
        int out = ModelFences.FACE_FLOATS;
        float in = north ? hit[1] : hit[0], through = north ? hit[out + 1] : hit[out];
        check(Math.abs(in - (north ? 20f : 10f)) < 0.1f, "a line meets the fence where it crosses its edge: " + in);
        check(through >= in && through - in < 0.1f, "and comes out of the picket or rail it went into, further on: " + in + " to " + through);
        float facesIn = north ? hit[4] : hit[3], facesOut = north ? hit[out + 4] : hit[out + 3];
        check(facesIn < 0f && facesOut > 0f, "the face gone in by looks back along the line, the one come out of on along it: " + facesIn + ", " + facesOut);
        float grain = hit[ModelFences.GRAIN] * hit[ModelFences.GRAIN] + hit[ModelFences.GRAIN + 1] * hit[ModelFences.GRAIN + 1]
                + hit[ModelFences.GRAIN + 2] * hit[ModelFences.GRAIN + 2];
        check(Math.abs(grain - 1f) < 1e-3f, "the grain is a way, made a unit: " + grain);
        return true;
    }

    /** How many pickets, at the most at any one height, a line crossing the edge at each place along it meets one after another with a gap between. */
    private static int picketsMet(ModelFences.Fence fence, boolean north) {
        int most = 0;
        for (float up = 0.2f; up < 1.2f; up += 0.1f) {
            int runs = 0;
            boolean was = false;
            for (float along = 0.01f; along < 0.99f; along += 0.004f) {
                boolean now = meets(fence, north, along, up);
                if (now && !was) {
                    runs++;
                }
                was = now;
            }
            most = Math.max(most, runs);
        }
        return most;
    }

    private static void aRoundMeetsAPicketAndPassesBetweenThem() {
        for (boolean north : new boolean[] {true, false}) {
            String edge = north ? "north" : "west";
            int white = picketsMet(ModelFences.WHITE, north), rounded = picketsMet(ModelFences.ROUNDED, north);
            check(white >= 5, "a round across the white picket fence on a " + edge + " edge meets its pickets and passes between them: " + white + " met");
            check(rounded >= 5, "and across the rounded one: " + rounded + " met");
            check(!meets(ModelFences.WHITE, north, 0.5f, 2f), "over the fence a round meets nothing");
        }
    }

    private static final float H = OpeningBuilder.DOOR_HALF;

    private static void aDoorDrawnAsItsSpriteIsHoledWhereItWasStruck() {
        TestMap map = new TestMap();
        WorldScene.Frame build = map.build;
        IsoObject door = map.cell.square(0, 1, 0).add(map.sprite("fixtures_doors_01_1", IsoObjectType.doorN, "doorN"));
        float up = 0.5f, tiles = up * ViewCamera.LEVEL_HEIGHT;
        try {
            Arrays.fill(build.geometry.lift, 0f);
            build.geometry.kerb = null;
            build.blastHoles = BlastHoles.with(new float[0], true, 0.3f, up, 0.06f, 7, 0);
            build.blastObject = door;
            build.doorSwung = false;
            card(build, true, 0f);
            check(!covered(build, true, 1f - H, 0.3f, tiles) && !covered(build, true, 1f + H, 0.3f, tiles), "shut, a door drawn as its sprite is holed through both faces where it was struck");
            check(covered(build, true, 1f - H, 0.7f, tiles) && covered(build, true, 1f + H, 0.7f, tiles), "and is whole away from the hole");
            build.solid.clear();
            build.laid.clear();
            // Swung open about its hinge at the square's north-west corner, its sprite stands on the square's west edge.
            build.doorSwung = true;
            card(build, false, OpeningBuilder.SWUNG_OUT);
            float out = OpeningBuilder.SWUNG_OUT;
            check(!covered(build, false, out - H, 1.3f, tiles) && !covered(build, false, out + H, 1.3f, tiles),
                    "swung open, it is holed as far from the hinge as it was struck on the leaf shut");
            check(covered(build, false, out - H, 1.7f, tiles) && covered(build, false, out + H, 1.7f, tiles), "and whole away from the hole");
            build.solid.clear();
            build.laid.clear();
            build.blastHoles = null;
            int[][] ends = build.openingBuilder.cardEnds();
            check(ends == null, "a door with no hole is not cut");
        } finally {
            build.blastHoles = null;
            build.blastObject = null;
            build.doorSwung = false;
            build.solid.clear();
            build.laid.clear();
        }
    }

    /** A door drawn as its sprite on the north or west edge of square (0, 1), its two faces off either side of the edge's line by DOOR_HALF, cut with its holes. */
    private static void card(WorldScene.Frame build, boolean north, float off) {
        int[][] ends = build.openingBuilder.cardEnds();
        SpriteGeometry.Mesh mesh = build.solid.get(WorldScene.PLAIN);
        mesh.lightStatic();
        float da = north ? 1f : 0f, db = north ? 0f : 1f, high = ViewCamera.LEVEL_HEIGHT;
        for (int side = -1; side <= 1; side += 2) {
            float across = off + side * H, x = db * across, y = 1f + da * across;
            float[] c = new float[12];
            WorldScene.set(c, 0, x, y, 0f);
            WorldScene.set(c, 1, x + da, y + db, 0f);
            WorldScene.set(c, 2, x + da, y + db, high);
            WorldScene.set(c, 3, x, y, high);
            SpriteGeometry.addPlainQuad(mesh, c, 1f, 1f, 1f);
        }
        build.openingBuilder.cutCard(ends, 0f, 1f, 0, da, db, off);
    }

    /** Whether a triangle of the build's meshes lying in the face that far across the north (y) or west (x) edge's line covers the place along it (x or y) and up. */
    private static boolean covered(WorldScene.Frame build, boolean north, float across, float along, float up) {
        int v = SpriteGeometry.VERTEX_FLOATS;
        for (SpriteGeometry.Mesh used : build.solid.used) {
            float[] f = used.vertices;
            for (int at = 0; at < used.floats; at += 3 * v) {
                boolean onFace = true;
                for (int corner = 0; corner < 3; corner++) {
                    onFace &= Math.abs(f[at + corner * v + (north ? 1 : 0)] - across) < 1e-4f;
                }
                int s = north ? 0 : 1;
                if (onFace && inTriangle(along, up, f[at + s], f[at + 2], f[at + v + s], f[at + v + 2], f[at + 2 * v + s], f[at + 2 * v + 2])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inTriangle(float s, float t, float s0, float t0, float s1, float t1, float s2, float t2) {
        float a = (s1 - s0) * (t - t0) - (t1 - t0) * (s - s0), b = (s2 - s1) * (t - t1) - (t2 - t1) * (s - s1), c = (s0 - s2) * (t - t2) - (t0 - t2) * (s - s2);
        return a >= 0f && b >= 0f && c >= 0f || a <= 0f && b <= 0f && c <= 0f;
    }
}
