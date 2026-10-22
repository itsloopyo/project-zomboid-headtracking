package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * Whether a sprite the game lays over a wall is a stain: grime, worn paint, graffiti, a
 * message daubed on it. The game paints such a thing as a mist of dots each only partly
 * covered and mixes it into the wall by that cover. Kept solid where a dot is half covered, as
 * the picture keeps a wall's skin, a faint smudge of wear was a dark blot. A sprite is a stain
 * when most of its paint is so thin. It is read with the sprite's colours: see SpriteColours.
 */
final class Stains {
    /** A dot covered less than this, of 255, is a thin one. */
    private static final int THIN = 200;
    /** The share of a sprite's painted dots that are thin for it to be a stain. */
    private static final float MOSTLY = 0.5f;

    private static final ConcurrentHashMap<Texture, Boolean> known = new ConcurrentHashMap<>();

    private Stains() {}

    /** Whether the sprite is a stain. Null until a frame has been drawn since it was first asked for. */
    static Boolean of(Texture texture) {
        Boolean stain = known.get(texture);
        if (stain == null) {
            SpriteColours.of(texture);
        }
        return stain;
    }

    /** Reads the sprite: see Masonry.read. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        known.put(texture, measure(sheet, width, left, top, right, bottom));
    }

    /** As of(), for the rectangle of a sheet a sprite is. */
    static boolean measure(ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        int painted = 0, thin = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int cover = sheet.get((y * width + x) * 4 + 3) & 255;
                if (cover > 0) {
                    painted++;
                    if (cover < THIN) {
                        thin++;
                    }
                }
            }
        }
        return painted > 0 && thin > MOSTLY * painted;
    }
}
