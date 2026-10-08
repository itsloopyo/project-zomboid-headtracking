package com.cameraunlock.zomboid;

/** Tests of where a kerb runs: the line Kerbs draws along the stair of squares a street's edge is on the map. */
final class KerbTests {
    private static final int TILES = Terrain.CHUNK_TILES, FINE = Kerbs.FINE, POINTS = Kerbs.POINTS;

    private KerbTests() {}

    private interface Map {
        boolean street(int x, int y);
    }

    static void run() {
        Checks.run("straightKerbLiesOnTheEdgeOfItsSquares", KerbTests::straightKerbLiesOnTheEdgeOfItsSquares);
        Checks.run("evenStairIsOneStraightSlant", KerbTests::evenStairIsOneStraightSlant);
        Checks.run("stairOfLongTreadsIsOneStraightSlant", KerbTests::stairOfLongTreadsIsOneStraightSlant);
        Checks.run("stairOfTwoByTwoIsOneStraightSlant", KerbTests::stairOfTwoByTwoIsOneStraightSlant);
        Checks.run("squareCornerIsRoundedAndKeepsTheMiddleOfItsSquare", KerbTests::squareCornerIsRoundedAndKeepsTheMiddleOfItsSquare);
        Checks.run("jogAtTheEndOfALongKerbIsNotASlant", KerbTests::jogAtTheEndOfALongKerbIsNotASlant);
        Checks.run("lineKeepsOutOfAHouse", KerbTests::lineKeepsOutOfAHouse);
        Checks.run("chunksAgreeAlongTheEdgeTheyShare", KerbTests::chunksAgreeAlongTheEdgeTheyShare);
        Checks.run("islandOneSquareWideIsStillThere", KerbTests::islandOneSquareWideIsStillThere);
        Checks.run("streetAgainstWaterHasNoKerb", KerbTests::streetAgainstWaterHasNoKerb);
        Checks.run("chunkWithNoStreetHasNoField", KerbTests::chunkWithNoStreetHasNoField);
        Checks.run("floorAKerbCrossesIsLaidAtTwoHeightsAndIsAllThere", KerbTests::floorAKerbCrossesIsLaidAtTwoHeightsAndIsAllThere);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** The field of the chunk whose corner is at (originX, originY) of a map, with nothing on it but street and made ground. */
    private static float[] field(Map map, int originX, int originY, Map hard, Map water) {
        byte[] kinds = new byte[Kerbs.SPAN * Kerbs.SPAN];
        boolean[] houses = new boolean[kinds.length];
        for (int i = 0; i < Kerbs.SPAN; i++) {
            for (int j = 0; j < Kerbs.SPAN; j++) {
                int x = originX - Kerbs.MARGIN + i, y = originY - Kerbs.MARGIN + j;
                kinds[i * Kerbs.SPAN + j] = water != null && water.street(x, y) ? Terrain.WATER : map.street(x, y) ? Terrain.STREET : Terrain.MADE;
                houses[i * Kerbs.SPAN + j] = hard != null && hard.street(x, y);
            }
        }
        return Kerbs.field(kinds, houses, originX, originY);
    }

    private static float[] field(Map map) {
        return field(map, 0, 0, null, null);
    }

    private static void straightKerbLiesOnTheEdgeOfItsSquares() {
        float[] field = field((x, y) -> x < 3);
        for (int p = 0; p < POINTS; p++) {
            for (int q = 0; q < POINTS; q++) {
                float away = 3f - (float) p / FINE;
                if (Math.abs(away) < 0.99f) {
                    check(Math.abs(field[p * POINTS + q] - away) < 1e-5f, "beside a straight kerb the field is how far the kerb's edge of squares is, at " + p + ", " + q);
                } else {
                    check(field[p * POINTS + q] == Math.signum(away), "far from a kerb the field says only which side, at " + p + ", " + q);
                }
            }
        }
    }

    /** Checks that the field of a map is, near its kerb, how far the line y = slope * x + at is, the street below it on the map. */
    private static void slants(Map map, float slope, float at, float within, String what) {
        float[] field = field(map);
        float across = (float) Math.sqrt(1f + slope * slope);
        for (int p = 0; p < POINTS; p++) {
            for (int q = 0; q < POINTS; q++) {
                float away = ((float) q / FINE - slope * p / FINE - at) / across;
                if (Math.abs(away) < 0.9f) {
                    check(Math.abs(field[p * POINTS + q] - away) < within, what + ": at " + p + ", " + q + " the line is " + field[p * POINTS + q] + " off and not " + away);
                }
            }
        }
    }

    private static void evenStairIsOneStraightSlant() {
        slants((x, y) -> y > x, 1f, 0.5f, 1e-4f, "a stair of single squares stands for a kerb at forty-five degrees through the middle of every edge");
    }

    private static void stairOfLongTreadsIsOneStraightSlant() {
        slants((x, y) -> y > Math.floorDiv(x, 4), 0.25f, 0.5f, 1e-3f, "a stair of treads four squares long stands for a kerb at one in four");
    }

    private static void stairOfTwoByTwoIsOneStraightSlant() {
        slants((x, y) -> Math.floorDiv(y, 2) > Math.floorDiv(x, 2), 1f, 1f, 1e-3f, "a stair two squares to a tread and two to a riser stands for a kerb at forty-five degrees");
    }

    private static void squareCornerIsRoundedAndKeepsTheMiddleOfItsSquare() {
        // Land in the quarter whose corner is at (4, 4), street round it.
        float[] field = field((x, y) -> x >= 4 || y >= 4);
        float corner = field[4 * FINE * POINTS + 4 * FINE];
        check(corner > 0.3f && corner < 0.45f, "the point of a square corner is cut off by a third of a square and a little more, not " + corner);
        check(Kerbs.at(field, 3.5f, 3.5f) < -0.25f, "what stands in the middle of the corner's square is still well up on the pavement");
        check(Math.abs(field[2 * FINE * POINTS + 4 * FINE]) < 1e-3f && Math.abs(field[4 * FINE * POINTS + 2 * FINE]) < 1e-3f,
                "two squares from the corner the kerb is back on the edge of its squares");
        // Round the corner the kerb turns evenly: the line is never more than a hand from a quarter circle.
        for (int step = 0; step <= 16; step++) {
            double turn = Math.PI / 2.0 * step / 16;
            float x = 4f - 0.93f + 0.93f * (float) Math.cos(turn), y = 4f - 0.93f + 0.93f * (float) Math.sin(turn);
            check(Math.abs(Kerbs.at(field, x, y)) < 0.06f, "the corner is a quarter turn of a little under a square: at step " + step + " it is " + Kerbs.at(field, x, y) + " off");
        }
    }

    private static void jogAtTheEndOfALongKerbIsNotASlant() {
        // A kerb that runs along y = 2, steps down to y = 6 at x = 4 and runs on: four squares of riser between long treads.
        float[] field = field((x, y) -> y >= (x < 4 ? 2 : 6));
        check(Math.abs(field[4 * FINE * POINTS + 4 * FINE]) < 1e-3f, "the riser of a jog stays where its squares are");
    }

    private static void lineKeepsOutOfAHouse() {
        float[] field = field((x, y) -> x >= 4 || y >= 4, 0, 0, (x, y) -> x < 4 && y < 4, null);
        for (int p = 0; p <= 4 * FINE; p++) {
            for (int q = 0; q <= 4 * FINE; q++) {
                check(field[p * POINTS + q] <= 0f, "no point of a house is on the street's side of the kerb along its wall");
            }
        }
        check(field[4 * FINE * POINTS + 4 * FINE] == 0f, "the kerb turns the corner of the house at the corner of the house");
        check(field[(4 * FINE + 1) * POINTS + 4 * FINE + 1] > 0f, "and the street begins there");
    }

    private static boolean town(int x, int y) {
        return Math.sin(x * 0.35) + Math.cos(y * 0.27) + Math.sin((x + y) * 0.19) > 0.3;
    }

    private static void chunksAgreeAlongTheEdgeTheyShare() {
        int checked = 0;
        for (int chunk = 0; chunk < 6; chunk++) {
            float[] west = field(KerbTests::town, chunk * TILES, 40, null, null), east = field(KerbTests::town, (chunk + 1) * TILES, 40, null, null);
            float[] north = field(KerbTests::town, 40, chunk * TILES, null, null), south = field(KerbTests::town, 40, (chunk + 1) * TILES, null, null);
            for (int along = 0; along < POINTS; along++) {
                if (west != null && east != null) {
                    check(west[(POINTS - 1) * POINTS + along] == east[along], "two chunks side by side have the very same kerb where they meet");
                    checked++;
                }
                if (north != null && south != null) {
                    check(north[along * POINTS + POINTS - 1] == south[along * POINTS], "two chunks one above the other have the very same kerb where they meet");
                    checked++;
                }
            }
        }
        check(checked > 4 * POINTS, "the made-up town has streets that cross from chunk to chunk");
    }

    private static void islandOneSquareWideIsStillThere() {
        float[] field = field((x, y) -> !(y == 3 && x >= 2 && x < 6));
        check(Kerbs.at(field, 4f, 3.5f) < -0.45f, "an island one square wide between two streets is as wide as it was along its middle");
        check(Kerbs.at(field, 2.5f, 3.5f) < 0f && Kerbs.at(field, 2.02f, 3.02f) > 0f, "and has its end rounded, not taken off");
    }

    private static void streetAgainstWaterHasNoKerb() {
        float[] field = field((x, y) -> x < 3, 0, 0, null, (x, y) -> x >= 3);
        for (int p = 0; p <= 3 * FINE; p++) {
            for (int q = 0; q < POINTS; q++) {
                check(field[p * POINTS + q] > 0f, "a street that runs to the water's edge has no kerb there");
            }
        }
    }

    private static void chunkWithNoStreetHasNoField() {
        check(field((x, y) -> x > TILES + 2) == null, "a chunk with no street on it or beside it has no kerbs");
    }

    /** How much of what a mesh holds lies at one height, in squares of the map, and fails if any of it is at neither height. */
    private static float areaAt(SpriteGeometry.Mesh mesh, float height, float other) {
        float area = 0f;
        for (int at = 0; at < mesh.floats; at += 3 * SpriteGeometry.VERTEX_FLOATS) {
            float[] v = mesh.vertices;
            int b = at + SpriteGeometry.VERTEX_FLOATS, c = b + SpriteGeometry.VERTEX_FLOATS;
            for (int corner = at; corner <= c; corner += SpriteGeometry.VERTEX_FLOATS) {
                check(Math.abs(v[corner + 2] - height) < 1e-5f || Math.abs(v[corner + 2] - other) < 1e-5f, "a floor a kerb crosses is at the land's height or the street's and nowhere between");
            }
            check(Math.abs(v[at + 2] - v[b + 2]) < 1e-5f && Math.abs(v[at + 2] - v[c + 2]) < 1e-5f, "and no piece of it climbs the kerb");
            if (Math.abs(v[at + 2] - height) < 1e-5f) {
                area += Math.abs((v[b] - v[at]) * (v[c + 1] - v[at + 1]) - (v[b + 1] - v[at + 1]) * (v[c] - v[at])) / 2f;
            }
        }
        return area;
    }

    private static void floorAKerbCrossesIsLaidAtTwoHeightsAndIsAllThere() {
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 1f;
        frame.originU = SpriteGeometry.PIXELS_ACROSS_PER_TILE;
        frame.width = 2f * SpriteGeometry.PIXELS_ACROSS_PER_TILE;
        frame.height = 2f * SpriteGeometry.PIXELS_DOWN_PER_TILE;
        frame.uEnd = 1f;
        frame.vEnd = 1f;
        frame.light(1f, 1f, 1f);
        java.util.Arrays.fill(geometry.lift, 1f);
        // The pavement's square in the corner of the made-up crossing: the kerb takes its point off.
        geometry.kerb = field((x, y) -> x >= 4 || y >= 4);
        geometry.kerbX = 3;
        geometry.kerbY = 3;
        geometry.kerbStreet = false;
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        geometry.addFace(mesh, frame, 3, 3, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        // Each corner of a piece worked out once is each corner worked out for every triangle of the piece.
        SpriteGeometry.Mesh longWay = new SpriteGeometry.Mesh();
        geometry.afresh = true;
        geometry.addFace(longWay, frame, 3, 3, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        geometry.afresh = false;
        check(mesh.floats > 6 * SpriteGeometry.VERTEX_FLOATS && java.util.Arrays.equals(java.util.Arrays.copyOf(mesh.vertices, mesh.floats), java.util.Arrays.copyOf(longWay.vertices, longWay.floats)),
                "the pieces of a floor a kerb crosses are the same floats made the short way and the long");
        float land = areaAt(mesh, 1f, 1f - Terrain.KERB), street = areaAt(mesh, 1f - Terrain.KERB, 1f);
        check(Math.abs(land + street - 1f) < 1e-4f, "the pieces of a floor a kerb crosses make up the whole square, not " + (land + street));
        check(street > 0.15f && street < 0.22f, "the street takes the point of the corner's square and no more, not " + street);
        check(Math.abs(geometry.liftAt(0.98f, 0.98f) - (1f - Terrain.KERB)) < 1e-6f && geometry.liftAt(0.5f, 0.5f) == 1f,
                "what stands on such a square stands on the side of the kerb it is on");

        mesh.clear();
        geometry.kerbKeep = SpriteGeometry.LAND;
        geometry.addFace(mesh, frame, 3, 3, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        check(Math.abs(areaAt(mesh, 1f, 1f) - land) < 1e-5f, "a floor that ends at the kerb is the land's part alone");
    }
}
