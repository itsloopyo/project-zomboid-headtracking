package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.List;

/** Tests of what makes a building one thing: its walls' finish, its roof's planes, and what hangs along its walls. */
final class BuildingTests {
    private BuildingTests() {}

    static void run() {
        finishIsWhatMostWallsShow();
        slopeOfUnevenTilesIsOnePlane();
        twoSlopesMeetAtTheirRidge();
        slopeClimbsFromOneLevelIntoTheNext();
        lowerRoofBesideAHigherOneKeepsItsOwnPlane();
        tileFarFromItsSlopeLeavesTheSlopeAsRead();
        shallowRoofKeepsItsOwnPitch();
        unclearTileTakesThePlaneOfItsRow();
        unclearTileWhereTwoSlopesMeetIsLeftAlone();
        pipeAlongAWallMeetsAcrossTwoSprites();
        wallTopTakesItsTexelsFromBelowTheSeal();
        farSlopeIsTheNearOneOverTheRidge();
        farSlopeStartsPastARoofsLevelTop();
        farSlopeStopsWhereTheBuildingDoesAndAtAnUpperStorey();
        roofWithBothItsSlopesGetsNoMore();
    }

    /** A site with the building under squares from one x to another, a floor at one level over a run of x, and rooms at a level from an x on. */
    private static RoofFit.Site site(int builtFrom, int builtTo, int floorLevel, int floorFrom, int floorTo, int roomLevel, int roomTo) {
        return new RoofFit.Site() {
            @Override
            public boolean built(int x, int y) {
                return x >= builtFrom && x <= builtTo;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return z == floorLevel && x >= floorFrom && x <= floorTo;
            }

            @Override
            public boolean taken(int x, int y, int z) {
                return z == roomLevel && x <= roomTo || floored(x, y, z) || floored(x, y, z + 1);
            }
        };
    }

    /** A slope falling east from height top at the west edge of square x, a third of a level a tile, deep tiles deep, on rows 0 and 1. */
    private static List<RoofFit.Tile> eastSlope(int x, float top, int deep) {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int y = 0; y < 2; y++) {
            for (int i = 0; i < deep; i++) {
                float high = top - i * RoofFit.PITCH;
                tile(roof, x + i, y, (int) Math.floor(high - RoofFit.PITCH + 0.01f), RoofFit.ALONG_X, true, high, high - RoofFit.PITCH);
            }
        }
        RoofFit.fit(roof);
        return roof;
    }

    private static RoofFit.Tile at(List<RoofFit.Tile> tiles, int x, int y) {
        for (RoofFit.Tile tile : tiles) {
            if (tile.x == x && tile.y == y) {
                return tile;
            }
        }
        return null;
    }

    private static void farSlopeIsTheNearOneOverTheRidge() {
        // The map's half of a gable roof: three tiles falling east from a ridge at 2 along x = 3.
        List<RoofFit.Tile> backs = RoofFit.backs(eastSlope(3, 2f, 3), site(0, 5, -1, 0, 0, -1, 0));
        check(backs.size() == 6, "each row of the slope is carried over the ridge, three tiles a row: " + backs.size());
        RoofFit.Tile first = at(backs, 2, 0), last = at(backs, 0, 1);
        check(first != null && near(first.fitHigh, 2f) && near(first.fitLow, 2f - RoofFit.PITCH) && first.fitAxis == RoofFit.ALONG_X,
                "the far slope starts at the ridge and falls away from it");
        check(last != null && near(last.fitLow, 1f) && near(last.fitHigh, 1f + RoofFit.PITCH) && last.z == 1,
                "and comes down to the height the near slope's foot is at, on the same level");
        check(first.z == 1 && first.fitted, "its tiles are laid as fitted ones are");
    }

    private static void farSlopeStartsPastARoofsLevelTop() {
        // Two tiles of slope up to a level top that is the floor of level 2 from x = 4 to 9.
        List<RoofFit.Tile> backs = RoofFit.backs(eastSlope(10, 2f, 2), site(2, 11, 2, 4, 9, -1, 0));
        check(backs.size() == 4 && at(backs, 9, 0) == null && at(backs, 4, 0) == null, "nothing is laid over the level top: " + backs.size());
        RoofFit.Tile first = at(backs, 3, 0), last = at(backs, 2, 0);
        check(first != null && near(first.fitHigh, 2f) && near(first.fitLow, 2f - RoofFit.PITCH), "the far slope falls from the far edge of the top");
        check(last != null && near(last.fitLow, 2f - 2f * RoofFit.PITCH), "as far down as the near one");
    }

    private static void farSlopeStopsWhereTheBuildingDoesAndAtAnUpperStorey() {
        List<RoofFit.Tile> backs = RoofFit.backs(eastSlope(3, 2f, 3), site(2, 5, -1, 0, 0, -1, 0));
        check(backs.size() == 2 && at(backs, 2, 0) != null && at(backs, 1, 0) == null, "no roof is laid past the building's wall: " + backs.size());
        // A lean-to against a house a storey higher: the rooms of level 1 are where its far slope would be.
        backs = RoofFit.backs(eastSlope(3, 2f, 3), site(0, 5, -1, 0, 0, 1, 2));
        check(backs.isEmpty(), "no roof is laid through an upper storey: " + backs.size());
    }

    private static void roofWithBothItsSlopesGetsNoMore() {
        List<RoofFit.Tile> roof = eastSlope(3, 2f, 3);
        for (int y = 0; y < 2; y++) {
            for (int i = 0; i < 3; i++) {
                tile(roof, i, y, 1, RoofFit.ALONG_X, true, 1f + i * RoofFit.PITCH, 1f + (i + 1) * RoofFit.PITCH);
            }
        }
        check(RoofFit.backs(roof, site(0, 5, -1, 0, 0, -1, 0)).isEmpty(), "a roof the map has both sides of is left as it is");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static void finishIsWhatMostWallsShow() {
        WallSides.Votes votes = new WallSides.Votes();
        check(votes.isEmpty() && votes.commonest() == -1, "walls that show nothing have no finish");
        for (int vote : new int[] {4, 7, 7, 2, 7, 4, 2, 2, 7}) {
            votes.add(vote);
        }
        check(!votes.isEmpty() && votes.commonest() == 7, "the finish is the sprite most of the walls show");
        WallSides.Votes tied = new WallSides.Votes();
        for (int vote : new int[] {5, 3, 3, 5}) {
            tied.add(vote);
        }
        check(tied.commonest() == 5, "of two shown equally often it is the one met first, so every wall of a building gets the same answer");
        WallSides.Votes many = new WallSides.Votes();
        for (int i = 0; i < 100; i++) {
            many.add(i % 3 == 0 ? 9 : i);
        }
        check(many.commonest() == 9, "a long wall's votes are all counted");
    }

    private static RoofFit.Tile tile(List<RoofFit.Tile> roof, int x, int y, int z, int axis, boolean sure, float low, float high) {
        RoofFit.Tile tile = new RoofFit.Tile(x, y, z, axis, sure, low, high);
        roof.add(tile);
        return tile;
    }

    private static void slopeOfUnevenTilesIsOnePlane() {
        // A slope three tiles deep and four wide, falling south from 2 to 1, each tile read a little out.
        List<RoofFit.Tile> roof = new ArrayList<>();
        float[] out = {0.04f, -0.03f, 0.05f, -0.05f, 0.02f, 0.06f, -0.04f, 0.03f, -0.02f, 0.05f, 0.01f, -0.06f};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                float high = 2f - row / 3f, wrong = out[row * 4 + column];
                tile(roof, 10 + column, 20 + row, 1, RoofFit.ALONG_Y, true, high + wrong, high - 1f / 3f - wrong / 2f);
            }
        }
        RoofFit.fit(roof);
        for (RoofFit.Tile tile : roof) {
            check(tile.fitted, "every tile of the slope is put on its plane");
            float high = 2f - (tile.y - 20) / 3f;
            check(near(tile.fitLow, high) && near(tile.fitHigh, high - 1f / 3f), "and the plane is the game's pitch, meeting the tile edges at thirds of a level");
        }
        for (RoofFit.Tile tile : roof) {
            for (RoofFit.Tile other : roof) {
                if (other.x == tile.x && other.y == tile.y + 1) {
                    check(tile.fitHigh == other.fitLow, "a tile's far edge is at the height of the next tile's near edge: no step down the slope");
                }
                if (other.y == tile.y && other.x == tile.x + 1) {
                    check(tile.fitLow == other.fitLow && tile.fitHigh == other.fitHigh, "and tiles side by side are at one height: no step along it");
                }
            }
        }
    }

    private static void twoSlopesMeetAtTheirRidge() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        // A ridge along x at y = 23: three rows climb to it from the north and three fall from it to the south.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                tile(roof, column, 20 + row, 1, RoofFit.ALONG_Y, true, 1.03f + row / 3f, 1.36f + row / 3f);
                tile(roof, column, 23 + row, 1, RoofFit.ALONG_Y, true, 1.95f - row / 3f, 1.64f - row / 3f);
            }
        }
        RoofFit.fit(roof);
        float northAtRidge = Float.NaN, southAtRidge = Float.NaN;
        for (RoofFit.Tile tile : roof) {
            check(tile.fitted, "both slopes are fitted");
            if (tile.y == 22) {
                northAtRidge = tile.fitHigh;
            }
            if (tile.y == 23) {
                southAtRidge = tile.fitLow;
            }
        }
        check(near(northAtRidge, 2f) && northAtRidge == southAtRidge, "the two slopes come to one height along the ridge");
    }

    private static void slopeClimbsFromOneLevelIntoTheNext() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        // Rising north: three tiles on level 1, then three more on level 2.
        for (int row = 0; row < 6; row++) {
            float high = 3f - row / 3f;
            tile(roof, 5, 30 + row, row < 3 ? 2 : 1, RoofFit.ALONG_Y, true, high + 0.03f, high - 1f / 3f + 0.03f);
        }
        RoofFit.fit(roof);
        for (RoofFit.Tile tile : roof) {
            float high = 3f - (tile.y - 30) / 3f;
            check(tile.fitted && near(tile.fitLow, high), "a slope that goes on in the next level is one plane across both");
        }
    }

    private static void lowerRoofBesideAHigherOneKeepsItsOwnPlane() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        RoofFit.Tile[] main = new RoofFit.Tile[3], porch = new RoofFit.Tile[2];
        for (int row = 0; row < 3; row++) {
            main[row] = tile(roof, 0, row, 1, RoofFit.ALONG_Y, true, 2f - row / 3f, 2f - (row + 1) / 3f);
        }
        // A porch roof beside the house's, sloping the same way, half a level lower.
        for (int row = 0; row < 2; row++) {
            porch[row] = tile(roof, 1, 1 + row, 1, RoofFit.ALONG_Y, true, 1.16f - row * 0.3f, 0.86f - row * 0.3f);
        }
        RoofFit.fit(roof);
        check(near(main[1].fitLow, 5f / 3f) && near(main[2].fitHigh, 1f), "the house's slope is fitted by its own tiles");
        check(porch[0].fitted && Math.abs(porch[0].fitLow - 1.16f) < 0.05f && porch[0].fitHigh == porch[1].fitLow,
                "and the porch's by its own: it is not pulled up into the house's plane");
    }

    private static void tileFarFromItsSlopeLeavesTheSlopeAsRead() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        RoofFit.Tile first = tile(roof, 0, 0, 1, RoofFit.ALONG_X, true, 1f, 1.33f);
        // Joined at the edge it shares with the first, and then far steeper than any roof.
        RoofFit.Tile second = tile(roof, 1, 0, 1, RoofFit.ALONG_X, true, 1.35f, 3f);
        RoofFit.fit(roof);
        check(!first.fitted && !second.fitted && first.fitLow == 1f && second.fitHigh == 3f,
                "tiles that do not lie near one plane are not forced onto one");
    }

    private static void shallowRoofKeepsItsOwnPitch() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int column = 0; column < 4; column++) {
            tile(roof, column, 0, 1, RoofFit.ALONG_X, true, 1.1f + column * 0.15f, 1.25f + column * 0.15f);
        }
        RoofFit.fit(roof);
        for (RoofFit.Tile tile : roof) {
            check(tile.fitted && near(tile.fitLow, 1.1f + tile.x * 0.15f) && near(tile.fitHigh - tile.fitLow, 0.15f),
                    "a roof shallower than the usual pitch is left at its own");
        }
    }

    private static void unclearTileTakesThePlaneOfItsRow() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                tile(roof, column, row, 1, RoofFit.ALONG_Y, true, 2f - row / 3f, 2f - (row + 1) / 3f);
            }
        }
        // At the end of the middle row, a tile whose sprite reads as level with the row above.
        RoofFit.Tile unclear = tile(roof, 3, 1, 1, RoofFit.ALONG_Y, false, 2.4f, 2.1f);
        RoofFit.fit(roof);
        check(unclear.fitted && unclear.fitAxis == RoofFit.ALONG_Y && near(unclear.fitLow, 5f / 3f) && near(unclear.fitHigh, 4f / 3f),
                "a tile whose sprite does not say how it lies takes the plane of the tile beside it in its row");
    }

    private static void unclearTileWhereTwoSlopesMeetIsLeftAlone() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tile(roof, i, 2, 1, RoofFit.ALONG_Y, true, 1.33f, 1f);
            tile(roof, 2, i, 1, RoofFit.ALONG_X, true, 1.33f, 1f);
        }
        RoofFit.Tile hip = tile(roof, 2, 2, 1, RoofFit.ALONG_Y, false, 1.3f, 1f);
        RoofFit.fit(roof);
        check(!hip.fitted && hip.fitLow == 1.3f, "a tile with a slope each way beside it is a hip, and is not laid on either");
    }

    /** A frame at tile scale 2 whose texture holds only the rows from top for height. */
    private static SpriteGeometry.Frame trimmed(float top, float height) {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.left = 0f;
        frame.top = top;
        frame.width = 128f;
        frame.height = height;
        frame.uStart = 0f;
        frame.vStart = 0f;
        frame.uEnd = 1f;
        frame.vEnd = 1f;
        return frame;
    }

    /** The level line of the sprite a face on the north edge of its square shows at the height up, in tiles, at one end of the square. */
    private static float rowShownAt(SpriteGeometry.Frame frame, boolean atEnd, float up) {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        check(new SpriteGeometry().addFace(mesh, frame, 0, 0, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f) > 0, "the sprite covers part of the wall");
        // Along the face's upright edge the row shown runs evenly with the height, so two of its vertices give every other.
        float[] f = mesh.vertices;
        float lowUp = Float.NaN, lowRow = 0f, highUp = Float.NaN, highRow = 0f;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            if (!near(f[at], atEnd ? 1f : 0f)) {
                continue;
            }
            // A level line runs across the sprite on the slant, half a row down for every pixel across. With that
            // taken out, every point of a level line has the one row.
            float row = frame.top + f[at + 4] * frame.height - 0.5f * (frame.left + f[at + 3] * frame.width);
            if (Float.isNaN(lowUp) || f[at + 2] < lowUp) {
                lowUp = f[at + 2];
                lowRow = row;
            }
            if (Float.isNaN(highUp) || f[at + 2] > highUp) {
                highUp = f[at + 2];
                highRow = row;
            }
        }
        check(highUp - lowUp > 0.1f, "the face has an upright edge at that end");
        return lowRow + (highRow - lowRow) * (up - lowUp) / (highUp - lowUp);
    }

    private static void pipeAlongAWallMeetsAcrossTwoSprites() {
        // Two squares of a wall with a pipe along it. One square's sprite is a short strip
        // round the pipe, the next one's runs on down to a meter.
        SpriteGeometry.Frame strip = trimmed(20f, 60f), deep = trimmed(20f, 130f);
        // The pipe leaves the first square at its far end and enters the next at its near end,
        // at one height: 30 pixels below the top of a face 192 high.
        float up = ViewCamera.LEVEL_HEIGHT * (1f - 30f / 192f);
        float leaves = rowShownAt(strip, true, up), enters = rowShownAt(deep, false, up);
        // To within the half pixel a texel is kept inside its sprite's edge by: a pipe is several pixels thick.
        check(Math.abs(leaves - enters) < 0.5f, "both sprites show the same level line at the height they meet at, so the pipe does not step: "
                + leaves + " and " + enters);
    }

    private static void wallTopTakesItsTexelsFromBelowTheSeal() {
        SpriteGeometry.Frame frame = trimmed(0f, 256f);
        SpriteGeometry geometry = new SpriteGeometry();
        geometry.sealTop = 4f;
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        geometry.addFace(mesh, frame, 0, 0, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
        boolean top = false;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float[] f = mesh.vertices;
            if (near(f[at + 2], ViewCamera.LEVEL_HEIGHT) && near(f[at], 0f)) {
                // The wall's top at its start is drawn on row 0 of this frame: 3 pixels of inset and 4 of seal below it.
                check(Math.abs(f[at + 4] * 256f - 7f - 1.5f) < 0.01f, "the top edge takes its texels from below what is not painted, at row " + f[at + 4] * 256f);
                top = true;
            }
            if (near(f[at + 2], 0f) && near(f[at], 0f)) {
                check(Math.abs(f[at + 4] * 256f - (192f - 3f + 1.5f)) < 0.01f, "and the foot is moved by the usual inset alone");
            }
        }
        check(top, "the wall reaches its level's top in a straight edge");
    }
}
