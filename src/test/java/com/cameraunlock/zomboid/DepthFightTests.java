package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.ArrayList;
import java.util.Map;

/** Tests of the dev audit that finds surfaces fighting for depth. */
final class DepthFightTests {
    private DepthFightTests() {}

    static void run() {
        Checks.run("planesInOneLayerFightFromAnywhere", DepthFightTests::planesInOneLayerFightFromAnywhere);
        Checks.run("aLayerApartNeverFights", DepthFightTests::aLayerApartNeverFights);
        Checks.run("aFixedGapFightsOnlyFromFarEnough", DepthFightTests::aFixedGapFightsOnlyFromFarEnough);
        Checks.run("overlappingSquaresInOnePlaneAreFoundAndTouchingOnesAreNot", DepthFightTests::overlappingSquaresInOnePlaneAreFoundAndTouchingOnesAreNot);
    }

    private static void planesInOneLayerFightFromAnywhere() {
        check(DepthFights.nearestFight(0, 0, 0) == DepthFights.DISTANCES[0], "two faces in one plane fight from the nearest distance tried");
    }

    private static void aLayerApartNeverFights() {
        check(DepthFights.nearestFight(0, 1, 0) == Float.MAX_VALUE, "one layer apart is told apart from every distance");
        check(DepthFights.nearestFight(0, 0, 1) == Float.MAX_VALUE, "one layer of cards apart is told apart from every distance");
    }

    private static void aFixedGapFightsOnlyFromFarEnough() {
        float from = DepthFights.nearestFight(0.001, 0, 0);
        check(from > 16f && from <= 24f, "a millimetre apart fights from about 22 tiles: " + from);
        check(DepthFights.nearestFight(0.05, 0, 0) == Float.MAX_VALUE, "five centimetres apart never fights within the world's reach");
    }

    private static void overlappingSquaresInOnePlaneAreFoundAndTouchingOnesAreNot() {
        ArrayList<DepthFights.Tri> tris = new ArrayList<>();
        // A wall face on x = 3 from y 0 to 1, another from y 0.9 to 1.9 overlapping it by a tenth, and a third from 1.9 to 2.9 only touching the second.
        square(tris, 0f, "a", 1);
        square(tris, 0.9f, "b", 2);
        square(tris, 1.9f, "c", 3);
        Map<String, DepthFights.Kind> found = DepthFights.pairs(tris, 0f, 0f, 0f);
        check(found.size() == 1, "one kind of pair: " + found.keySet());
        DepthFights.Kind kind = found.values().iterator().next();
        check(kind.pairs == 3, "the overlap is three pairs of triangles: " + kind.pairs);
        check(Math.abs(kind.area - 0.1 * 2.4) < 1e-6, "the overlap is a tenth of a tile by the face's height: " + kind.area);
    }

    private static void square(ArrayList<DepthFights.Tri> into, float y, String label, int source) {
        float[][] corners = {{3, y, 0}, {3, y + 1, 0}, {3, y + 1, 2.4f}, {3, y, 2.4f}};
        int[][] tris = {{0, 1, 2}, {0, 2, 3}};
        for (int[] t : tris) {
            DepthFights.Tri tri = new DepthFights.Tri();
            for (int c = 0; c < 3; c++) {
                tri.p[c * 3] = corners[t[c]][0];
                tri.p[c * 3 + 1] = corners[t[c]][1];
                tri.p[c * 3 + 2] = corners[t[c]][2];
            }
            check(DepthFights.plane(tri), "a square's half has a plane");
            tri.label = label;
            tri.source = source;
            into.add(tri);
        }
    }
}
