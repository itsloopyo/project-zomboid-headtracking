package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of the skirting boards, rails and cornices built along inside walls: see Trims. */
final class TrimTests {
    private static final int LONG = Flats.WALL_LONG, HIGH = Flats.WALL_HIGH;
    private static final Trims.Light WHITE = (x, y, into) -> {
        into[0] = 1f;
        into[1] = 1f;
        into[2] = 1f;
    };

    private TrimTests() {}

    static void run() {
        Checks.run("aSkirtingAndACorniceAreReadAsTwoBands", TrimTests::aSkirtingAndACorniceAreReadAsTwoBands);
        Checks.run("anEvenlyPaintedPanelIsABoardAndABrickOneIsLeftToItsSprite", TrimTests::anEvenlyPaintedPanelIsABoardAndABrickOneIsLeftToItsSprite);
        Checks.run("everyCorniceIsOneMouldingWhateverItsBand", TrimTests::everyCorniceIsOneMouldingWhateverItsBand);
        Checks.run("aBandsHeightsAreRoundedSoTwoEdgesMeet", TrimTests::aBandsHeightsAreRoundedSoTwoEdgesMeet);
        Checks.run("aSkirtingStopsWhereADoorwayIs", TrimTests::aSkirtingStopsWhereADoorwayIs);
        Checks.run("aBandsColourIsItsPaintNotItsOutline", TrimTests::aBandsColourIsItsPaintNotItsOutline);
        Checks.run("aSkirtingStandsOnTheRoomsSideOfItsWallWithNoUndersideOnTheFloor", TrimTests::aSkirtingStandsOnTheRoomsSideOfItsWallWithNoUndersideOnTheFloor);
        Checks.run("everyFaceOfAMouldingIsCutWithItsWallInTheOverheadView", TrimTests::everyFaceOfAMouldingIsCutWithItsWallInTheOverheadView);
        Checks.run("everyFaceOfAMouldingLooksAwayFromItsWall", TrimTests::everyFaceOfAMouldingLooksAwayFromItsWall);
        Checks.run("aMouldingIsLitByTheRoomAndPaintedByItsMesh", TrimTests::aMouldingIsLitByTheRoomAndPaintedByItsMesh);
    }

    /** A wall stood square with rows first to last painted from column from to column to, in grey. */
    private static void paint(byte[] dots, int first, int last, int from, int to, int grey) {
        for (int row = first; row <= last; row++) {
            for (int column = from; column <= to; column++) {
                int at = (row * LONG + column) * 4;
                dots[at] = dots[at + 1] = dots[at + 2] = (byte) grey;
                dots[at + 3] = (byte) 255;
            }
        }
    }

    private static void aSkirtingAndACorniceAreReadAsTwoBands() {
        byte[] dots = new byte[LONG * HIGH * 4];
        paint(dots, HIGH - 12, HIGH - 1, 0, LONG - 1, 230);
        paint(dots, 0, 9, 0, LONG - 1, 230);
        Trims.Band[] bands = Trims.bands(dots, LONG, HIGH, 1f);
        check(bands.length == 2, "two bands: " + bands.length);
        Trims.Band cornice = bands[0], skirting = bands[1];
        check(cornice.top() == 1f && Math.abs(cornice.foot() - 30f / 32f) < 1e-4f, "the cornice runs up to the ceiling: " + cornice);
        check(skirting.foot() == 0f && Math.abs(skirting.top() - 2f / 32f) < 1e-4f, "the skirting stands on the floor, its top rounded to a 32nd of a level: " + skirting);
        check(skirting.from() == 0f && skirting.to() == 1f, "and runs the wall's length: " + skirting);
    }

    private static void anEvenlyPaintedPanelIsABoardAndABrickOneIsLeftToItsSprite() {
        byte[] dots = new byte[LONG * HIGH * 4];
        paint(dots, HIGH - 56, HIGH - 1, 0, LONG - 1, 220);
        Trims.Band[] bands = Trims.bands(dots, LONG, HIGH, 1f);
        check(bands.length == 1 && bands[0].foot() == 0f && Math.abs(bands[0].top() - 9f / 32f) < 1e-4f, "a painted panel is one board from the floor up: " + bands[0]);
        // Courses of brick: a dark joint every sixth row and every sixteenth dot along.
        for (int row = HIGH - 56; row < HIGH; row += 6) {
            paint(dots, row, row, 0, LONG - 1, 70);
        }
        for (int column = 0; column < LONG; column += 16) {
            paint(dots, HIGH - 56, HIGH - 1, column, column, 70);
        }
        check(Trims.bands(dots, LONG, HIGH, 1f).length == 0, "a brick panel is a surface, left to its sprite");
    }

    private static void everyCorniceIsOneMouldingWhateverItsBand() {
        float[] wide = Trims.section(new Trims.Band(0.88f, 1f, 0f, 1f, 0.9f, 0.9f, 0.9f)), narrow = Trims.section(new Trims.Band(0.9f, 1f, 0f, 1f, 0.9f, 0.9f, 0.9f));
        check(java.util.Arrays.equals(wide, narrow), "two cornices read from bands of two depths are one moulding, so they meet where their squares do");
        float lowest = Float.MAX_VALUE;
        for (int i = 1; i < wide.length; i += 2) {
            lowest = Math.min(lowest, wide[i]);
        }
        check(Math.abs(ViewCamera.LEVEL_HEIGHT - lowest - Trims.CORNICE_FALL) < 1e-4f, "it falls CORNICE_FALL down the wall: " + (ViewCamera.LEVEL_HEIGHT - lowest));
    }

    private static void aBandsHeightsAreRoundedSoTwoEdgesMeet() {
        byte[] north = new byte[LONG * HIGH * 4], west = new byte[LONG * HIGH * 4];
        paint(north, HIGH - 12, HIGH - 1, 0, LONG - 1, 230);
        paint(west, HIGH - 13, HIGH - 1, 0, LONG - 1, 230);
        check(Trims.bands(north, LONG, HIGH, 1f)[0].top() == Trims.bands(west, LONG, HIGH, 1f)[0].top(), "a skirting a dot taller on one edge meets the other at one height");
    }

    private static void aSkirtingStopsWhereADoorwayIs() {
        byte[] dots = new byte[LONG * HIGH * 4];
        paint(dots, HIGH - 12, HIGH - 1, 0, 19, 230);
        paint(dots, HIGH - 12, HIGH - 1, 44, LONG - 1, 230);
        Trims.Band[] bands = Trims.bands(dots, LONG, HIGH, 1f);
        check(bands.length == 2, "a band either side of the doorway: " + bands.length);
        check(bands[0].from() == 0f && Math.abs(bands[0].to() - 20f / LONG) < 1e-4f, "the first stops at the doorway: " + bands[0]);
        check(Math.abs(bands[1].from() - 44f / LONG) < 1e-4f && bands[1].to() == 1f, "the second starts past it: " + bands[1]);
    }

    private static void aBandsColourIsItsPaintNotItsOutline() {
        byte[] dots = new byte[LONG * HIGH * 4];
        paint(dots, HIGH - 12, HIGH - 1, 0, LONG - 1, 230);
        paint(dots, HIGH - 2, HIGH - 1, 0, LONG - 1, 40);
        Trims.Band band = Trims.bands(dots, LONG, HIGH, 1f)[0];
        check(Math.abs(band.red() - 230f / 255f) < 1e-3f, "the dark line along its foot is not its colour: " + band.red());
    }

    private static final Trims.Band SKIRTING = new Trims.Band(0f, 0.06f, 0f, 1f, 0.9f, 0.9f, 0.9f);
    private static final Trims.Band CORNICE = new Trims.Band(0.95f, 1f, 0f, 1f, 0.9f, 0.9f, 0.9f);

    private static void aSkirtingStandsOnTheRoomsSideOfItsWallWithNoUndersideOnTheFloor() {
        // The north wall of square (4, 9), the room to the south of it, then the west wall, the room to the east.
        for (int edge = 0; edge < 2; edge++) {
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            float da = edge == 0 ? 1f : 0f, db = 1f - da;
            Trims.add(mesh, SKIRTING, 4f, 9f, da, db, db, da, 0f, 0f, WHITE);
            check(mesh.triangles() > 0, "a skirting is built");
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                float x = mesh.vertices[at], y = mesh.vertices[at + 1], up = mesh.vertices[at + 2];
                float out = edge == 0 ? y - 9f : x - 4f, along = edge == 0 ? x - 4f : y - 9f;
                check(out >= WorldScene.WALL_HALF - 1e-5f && out <= WorldScene.WALL_HALF + Trims.BOARD + 1e-5f,
                        "it stands out of the skin on the room's side, by a board's thickness: " + out);
                check(along >= -1e-5f && along <= 1f + 1e-5f, "along its own length: " + along);
                check(up >= -1e-5f && up <= SKIRTING.top() * ViewCamera.LEVEL_HEIGHT + 1e-5f, "from the floor to its top: " + up);
                check(!(up < 1e-5f && mesh.vertices[at + 11] < -0.5f), "no face of it lies face down on the floor");
            }
        }
    }

    private static void aMouldingIsLitByTheRoomAndPaintedByItsMesh() {
        Trims.Band wood = new Trims.Band(0f, 0.06f, 0f, 1f, 0.5f, 0.3f, 0.15f);
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        Trims.add(mesh, wood, 4f, 9f, 1f, 0f, 0f, 1f, 0f, 0f, (x, y, into) -> {
            into[0] = 0.8f;
            into[1] = 0.6f;
            into[2] = 0.4f;
        });
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            check(mesh.vertices[at + 5] == 0.8f && mesh.vertices[at + 6] == 0.6f && mesh.vertices[at + 7] == 0.4f, "each vertex is the light alone");
        }
        check(Trims.paint(wood) != Trims.paint(SKIRTING), "a wooden and a white moulding are two meshes");
    }

    private static void everyFaceOfAMouldingIsCutWithItsWallInTheOverheadView() {
        // PipelineShaders.cutAway for a face not hung: one facing more up or down than along the
        // floor goes with the north wall where it is within 0.06 of the north line; one facing more
        // along the floor goes with the north wall where it is within 0.3 of the line and faces
        // more along y than x. Ends of a band whole along its square and of one cut short by a door.
        for (Trims.Band band : new Trims.Band[] {SKIRTING, CORNICE, new Trims.Band(0.4f, 0.43f, 0f, 1f, 0.5f, 0.3f, 0.2f),
                new Trims.Band(0f, 0.12f, 0.3f, 0.7f, 0.9f, 0.9f, 0.9f)}) {
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            Trims.add(mesh, band, 4f, 9f, 1f, 0f, 0f, 1f, 0f, 0f, WHITE);
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                float x = mesh.vertices[at], y = mesh.vertices[at + 1];
                float nx = mesh.vertices[at + 9], ny = mesh.vertices[at + 10], nz = mesh.vertices[at + 11];
                float offY = Math.abs(y - Math.round(y)), offX = Math.abs(x - Math.round(x));
                boolean lying = Math.abs(nz) > Math.max(Math.abs(nx), Math.abs(ny));
                boolean north = lying ? offY < 0.06f : offY < 0.3f && Math.abs(ny) > Math.abs(nx);
                check(north, "every face of a moulding goes with its own wall: normal " + nx + " " + ny + " " + nz + " at " + x + " " + y
                        + " (off the west line " + offX + ")");
            }
        }
    }

    private static void everyFaceOfAMouldingLooksAwayFromItsWall() {
        for (Trims.Band band : new Trims.Band[] {SKIRTING, CORNICE, new Trims.Band(0.4f, 0.43f, 0f, 1f, 0.5f, 0.3f, 0.2f)}) {
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            Trims.add(mesh, band, 4f, 9f, 1f, 0f, 0f, 1f, 0f, 0f, WHITE);
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                float nx = mesh.vertices[at + 9], ny = mesh.vertices[at + 10];
                check(ny >= -1e-5f || Math.abs(nx) > 0.5f, "a face of " + band + " faces the room, an end or up or down, never the wall: " + ny);
            }
        }
    }
}
