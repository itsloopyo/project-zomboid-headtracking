package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.nio.ByteBuffer;

/** Which sprites are glass: SeeThrough. */
final class SeeThroughTests {
    private SeeThroughTests() {}

    static void run() {
        Checks.run("aSpritePaintedMostlyClearIsGlass", SeeThroughTests::aSpritePaintedMostlyClearIsGlass);
        Checks.run("aSolidSpriteWithASoftEdgeIsNot", SeeThroughTests::aSolidSpriteWithASoftEdgeIsNot);
        Checks.run("aSpeckIsNot", SeeThroughTests::aSpeckIsNot);
        Checks.run("onlyTheSpritesRectangleIsRead", SeeThroughTests::onlyTheSpritesRectangleIsRead);
    }

    private static final int WIDE = 32, HIGH = 16;

    /** A sheet WIDE by HIGH with every pixel at the cover given. */
    private static ByteBuffer sheet(int cover) {
        ByteBuffer sheet = ByteBuffer.allocate(WIDE * HIGH * 4);
        for (int i = 0; i < WIDE * HIGH; i++) {
            sheet.put(i * 4, (byte) 120).put(i * 4 + 1, (byte) 140).put(i * 4 + 2, (byte) 160).put(i * 4 + 3, (byte) cover);
        }
        return sheet;
    }

    private static void cover(ByteBuffer sheet, int left, int top, int right, int bottom, int cover) {
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                sheet.put((y * WIDE + x) * 4 + 3, (byte) cover);
            }
        }
    }

    private static void aSpritePaintedMostlyClearIsGlass() {
        ByteBuffer pane = sheet(110);
        cover(pane, 0, 0, WIDE, 2, 255);
        cover(pane, 0, 0, 2, HIGH, 255);
        check(SeeThrough.glass(pane, WIDE, 0, 0, WIDE, HIGH), "panes at 110 of 255 in a solid frame are glass");
    }

    private static void aSolidSpriteWithASoftEdgeIsNot() {
        ByteBuffer slope = sheet(255);
        cover(slope, 0, 0, WIDE, 3, 128);
        check(!SeeThrough.glass(slope, WIDE, 0, 0, WIDE, HIGH), "tiles painted solid, with a soft row along their top, are no glass");
    }

    private static void aSpeckIsNot() {
        ByteBuffer speck = sheet(0);
        cover(speck, 4, 4, 9, 9, 100);
        check(!SeeThrough.glass(speck, WIDE, 0, 0, WIDE, HIGH), "25 clear pixels and nothing else are a speck, no glass");
    }

    private static void onlyTheSpritesRectangleIsRead() {
        ByteBuffer two = sheet(255);
        cover(two, 16, 0, WIDE, HIGH, 90);
        check(SeeThrough.glass(two, WIDE, 16, 0, WIDE, HIGH) && !SeeThrough.glass(two, WIDE, 0, 0, 16, HIGH),
                "a pane beside a solid sprite on one sheet is glass and the solid sprite is not");
    }
}
