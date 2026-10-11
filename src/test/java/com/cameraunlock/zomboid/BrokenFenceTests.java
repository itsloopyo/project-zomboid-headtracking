package com.cameraunlock.zomboid;

/** Tests of which edge of its square a broken length of fence stands on, from the outline of its picture. */
final class BrokenFenceTests {
    private BrokenFenceTests() {}

    static void run() {
        Checks.run("aBrokenFenceStandsOnTheEdgeItsPictureIsPaintedAlong", BrokenFenceTests::aBrokenFenceStandsOnTheEdgeItsPictureIsPaintedAlong);
    }

    /** A frame of the 2x sprites, with the opaque part of the picture from left to left + width. */
    private static SpriteGeometry.Frame frame(float left, float width) {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.left = left;
        frame.width = width;
        return frame;
    }

    private static void aBrokenFenceStandsOnTheEdgeItsPictureIsPaintedAlong() {
        Checks.check(BrokenFences.is("fencing_damaged_01_36") && !BrokenFences.is("fencing_01_36"), "only the broken fences' sheet is one");
        // Outlines read off the game's sprites: wire along y with its post at the corner, boards along x, iron railings along each.
        SpriteGeometry.Frame wire = frame(2f, 66f), boards = frame(57f, 66f), railingX = frame(58f, 64f), railingY = frame(0f, 65f);
        Checks.check(BrokenFences.onWest(wire) && !BrokenFences.onNorth(wire), "wire with its post at the corner is on the west edge alone");
        Checks.check(BrokenFences.onNorth(boards) && !BrokenFences.onWest(boards), "boards along x are on the north edge alone");
        Checks.check(BrokenFences.onNorth(railingX) && !BrokenFences.onWest(railingX), "railings along x are on the north edge");
        Checks.check(BrokenFences.onWest(railingY) && !BrokenFences.onNorth(railingY), "railings along y are on the west edge");
        SpriteGeometry.Frame corner = frame(0f, 128f);
        Checks.check(BrokenFences.onWest(corner) && BrokenFences.onNorth(corner), "a corner piece is on both edges");
    }
}
