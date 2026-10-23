package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * Whether a sprite is glass: painted mostly with less than full cover, as the glass roofs and
 * skylights of roofs_02_32..63 are, where a slope of tiles is painted solid. Read with the
 * sprite's colours: see SpriteColours.
 */
final class SeeThrough {
    /** A pixel with at least this cover, of 255, is painted solid. */
    static final int SOLID = 240;
    /** A sprite with no more painted pixels than this is a speck, and no glass. */
    static final int FEWEST = 50;

    private static final ConcurrentHashMap<Texture, Boolean> asked = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Texture, Boolean> known = new ConcurrentHashMap<>();

    private SeeThrough() {}

    /** Whether the sprite is glass. Null until a frame has been drawn since it was first asked for. */
    static Boolean of(Texture texture) {
        Boolean glass = known.get(texture);
        if (glass == null && asked.putIfAbsent(texture, Boolean.TRUE) == null) {
            SpriteColours.readAgain(texture);
        }
        return glass;
    }

    /** Reads the sprite if it was asked for: see Windows.read. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        if (!asked.containsKey(texture) || known.containsKey(texture)) {
            return;
        }
        known.put(texture, glass(sheet, width, left, top, right, bottom));
    }

    /**
     * Whether the rectangle from (left, top) to before (right, bottom) of a sheet width dots
     * wide, held as bytes of red, green, blue and cover, is glass: more than FEWEST pixels
     * painted, and over half of them with less than SOLID cover.
     */
    static boolean glass(ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        int painted = 0, clear = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int cover = sheet.get((y * width + x) * 4 + 3) & 0xff;
                if (cover > 0) {
                    painted++;
                    clear += cover < SOLID ? 1 : 0;
                }
            }
        }
        return painted > FEWEST && clear * 2 > painted;
    }
}
