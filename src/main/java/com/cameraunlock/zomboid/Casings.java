package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * What a door's casing is, read off its sprite.
 *
 * The game paints the casing round a doorway as two posts and a head, each six dots wide with a
 * line down it, and the picture stood that sprite flat on the wall: from a pace away each dot
 * was a block a centimetre across, soft and stepped along the slant it was painted on. Laid flat
 * on its wall as a window is (see Windows), the casing's posts are the runs of solid dots across
 * a row low down the sprite, and its head is the solid rows over the doorway between them. The
 * picture builds the casing from those, as boards with a moulded edge, in the sprite's colour.
 */
final class Casings {
    /** How high up the sprite its posts are looked for, in levels. */
    private static final float POSTS_AT = 0.3f;
    /** A post is at least this many dots wide, and a run of dots wider than a share of the wall this is no post. */
    private static final int THINNEST = 2;
    private static final float WIDEST = 0.2f;
    /** A post comes down to within this share of a level of the floor, and a head is at least this high, in levels. */
    private static final float FOOT_MOST = 0.06f, HEAD_LEAST = 0.5f;

    private record Asked(boolean north, float originU, float originV, float scale, float left, float top, float width, float height) {}

    private static final Windows.Shape NONE = new Windows.Shape();
    private static final ConcurrentHashMap<Texture, Asked> asked = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Texture, Windows.Shape> known = new ConcurrentHashMap<>();

    private Casings() {}

    /**
     * The casing the sprite paints on the north or west edge of its square, as a window's frame with
     * no foot: start and end are the posts' outsides, startWide and endWide how wide each post is (0
     * for a casing with no post at that end), foot where the posts stand, head the head's top and
     * headHigh how deep it is. Null until a frame has been drawn since it was first asked for, and for
     * a sprite that is no casing of upright posts under a level head.
     */
    static Windows.Shape of(Texture texture, SpriteGeometry.Frame frame, boolean north) {
        Windows.Shape shape = known.get(texture);
        if (shape != null) {
            return shape == NONE || shape.north != north ? null : shape;
        }
        if (asked.putIfAbsent(texture, new Asked(north, frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height)) == null) {
            SpriteColours.readAgain(texture);
        }
        return null;
    }

    /** Whether the sprite has still to be read. */
    static boolean unread(Texture texture) {
        return !known.containsKey(texture);
    }

    /** Reads the sprite if it was asked for: see Windows.read. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        Asked wanted = asked.get(texture);
        if (wanted == null || known.containsKey(texture)) {
            return;
        }
        int across = Windows.across(wanted.scale), high = Windows.high(wanted.scale), margin = Math.round(Windows.SPILL * wanted.scale);
        int[] at = new int[(across + 2 * margin) * high];
        byte[] dots = Windows.laid(sheet, width, left, top, right, bottom, wanted.north, false, wanted.originU, wanted.originV, wanted.scale,
                wanted.left, wanted.top, wanted.width, wanted.height, margin, at);
        Windows.Shape shape = measure(dots, across, margin, high);
        if (FirstPerson.TRACE) {
            Log.info("casing " + texture.getName() + (wanted.north ? " north" : " west") + (shape == null ? " is no casing"
                    : " posts " + shape.start + "+" + shape.startWide + " .. " + shape.end + "-" + shape.endWide + " foot " + shape.foot + " head " + shape.head + "-" + shape.headHigh));
        }
        if (shape == null) {
            known.put(texture, NONE);
            return;
        }
        Windows.colour(sheet, dots, at, shape);
        shape.north = wanted.north;
        shape.originU = wanted.originU;
        shape.originV = wanted.originV;
        shape.scale = wanted.scale;
        known.put(texture, shape);
    }

    /**
     * The casing in a sprite laid flat on its wall: dots holds across + 2 * margin dots a row, the
     * first margin of them before the square, the top row first, each Windows.EMPTY, GLASS or FRAME.
     * Places along are shares of the square, from its start. Null for what is no casing.
     */
    static Windows.Shape measure(byte[] dots, int across, int margin, int high) {
        int wide = across + 2 * margin;
        int row = Math.round((1f - POSTS_AT) * high);
        int[] runs = new int[4];
        int count = 0;
        for (int column = 0; column < wide; ) {
            if (dots[row * wide + column] != Windows.FRAME) {
                column++;
                continue;
            }
            int from = column;
            while (column < wide && dots[row * wide + column] == Windows.FRAME) {
                column++;
            }
            if (count == 2) {
                return null;
            }
            runs[2 * count] = from;
            runs[2 * count + 1] = column;
            count++;
        }
        if (count == 0) {
            return null;
        }
        for (int run = 0; run < count; run++) {
            int postWide = runs[2 * run + 1] - runs[2 * run];
            if (postWide < THINNEST || postWide > WIDEST * across) {
                return null;
            }
        }
        // The head is read down the column halfway across the doorway: between two posts, or from a
        // lone post to the square's far end.
        int middle;
        if (count == 2) {
            middle = (runs[1] + runs[2]) / 2;
        } else {
            boolean postAtStart = runs[0] + runs[1] < wide;
            middle = postAtStart ? (runs[1] + margin + across) / 2 : (margin + runs[0]) / 2;
        }
        int headTop = 0;
        while (headTop < row && dots[headTop * wide + middle] != Windows.FRAME) {
            headTop++;
        }
        int headBottom = headTop;
        while (headBottom < row && dots[headBottom * wide + middle] == Windows.FRAME) {
            headBottom++;
        }
        if (headBottom - headTop < THINNEST || headBottom >= row) {
            return null;
        }
        float head = 1f - (float) headTop / high;
        if (head < HEAD_LEAST) {
            return null;
        }
        for (int run = 0; run < count; run++) {
            int lowest = row;
            for (int down = row; down < high; down++) {
                for (int column = runs[2 * run]; column < runs[2 * run + 1]; column++) {
                    if (dots[down * wide + column] == Windows.FRAME) {
                        lowest = down;
                    }
                }
            }
            if (1f - (float) (lowest + 1) / high > FOOT_MOST) {
                return null;
            }
        }
        Windows.Shape shape = new Windows.Shape();
        boolean startPost = count == 2 || runs[0] + runs[1] < wide;
        boolean endPost = count == 2 || !startPost;
        int endRun = count == 2 ? 1 : 0;
        shape.start = startPost ? (float) (runs[0] - margin) / across : 0f;
        shape.startWide = startPost ? (float) (runs[1] - runs[0]) / across : 0f;
        shape.end = endPost ? (float) (runs[2 * endRun + 1] - margin) / across : 1f;
        shape.endWide = endPost ? (float) (runs[2 * endRun + 1] - runs[2 * endRun]) / across : 0f;
        shape.foot = 0f;
        shape.head = head;
        shape.headHigh = (float) (headBottom - headTop) / high;
        return shape;
    }
}
