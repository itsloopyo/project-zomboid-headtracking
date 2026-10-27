package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * Where a tree's leaf sprite paints wood: its trunk, and its limbs where they show between the
 * leaves. A crown's sprig takes its colour from its place on the sprite, and one placed on the
 * wood was a clump of bark-brown leaves in a green crown, with the twig that bore it bare beside
 * it once it was left out. So a tree's crown has no places on its wood at all. Wood is a dull,
 * dark brown: redder than green, but not as red as a berry or an autumn leaf, and as blue as it
 * is green near enough, where a gold or orange leaf has little blue. Read with the sprite's
 * colours: see SpriteColours.
 */
final class CrownWood {
    /** Wood is redder than green by this much, of 1. */
    static final float REDDER = 0.03f;
    /** At least this share of its red is green, and of its green blue: a berry, a blossom and an autumn leaf have less. */
    static final float GREEN_OF_RED = 0.6f, BLUE_OF_GREEN = 0.7f;
    /** Its brightness is under this, of 1. */
    static final float DARK = 0.4f;
    /**
     * A sprig's colour is the mean of its place's dot and the dots this many either way across
     * and down (PipelineShaders, the crown's sprigs). A place is wood where that mean is, and
     * where its own dot is: at a limb's edge the mean is half leaves, and a sprig there was brown.
     */
    static final int SPREAD = 4;

    /** For each sprite read: whether each of its dots is wood, row by row from its top left. */
    private static final ConcurrentHashMap<Texture, Grid> known = new ConcurrentHashMap<>();
    /** The sprites a crown has asked for: no other sprite is measured. */
    private static final Set<Texture> wanted = ConcurrentHashMap.newKeySet();
    /** For each sprite read, the places last asked about and the ones of them kept. */
    private static final ConcurrentHashMap<Texture, float[][]> kept = new ConcurrentHashMap<>();

    /** Which of a sprite's dots, width across and height down, are wood. */
    record Grid(int width, int height, boolean[] wood) {}

    private CrownWood() {}

    /**
     * The places, in frame pixels of the frame, that are not on the sprite's wood. Null until a
     * frame has been drawn since the sprite was first asked for.
     */
    static float[] leaves(Texture texture, SpriteGeometry.Frame frame, float[] places) {
        Grid grid = known.get(texture);
        if (grid == null) {
            // A sprite whose colours were read before a crown wanted it is read again.
            if (wanted.add(texture)) {
                SpriteColours.of(texture);
                if (!SpriteColours.unread(texture)) {
                    SpriteColours.readAgain(texture);
                }
            }
            return null;
        }
        float[][] last = kept.get(texture);
        if (last != null && last[0] == places) {
            return last[1];
        }
        float[] leaves = leaves(grid, frame.left, frame.top, frame.width, frame.height, places);
        kept.put(texture, new float[][] {places, leaves});
        return leaves;
    }

    /** As leaves(), for a grid over the rectangle of frame pixels from (left, top), width by height. */
    static float[] leaves(Grid grid, float left, float top, float width, float height, float[] places) {
        float[] leaves = new float[places.length];
        int count = 0;
        for (int i = 0; i < places.length; i += 2) {
            int x = Math.clamp((int) ((places[i] - left) / width * grid.width), 0, grid.width - 1);
            int y = Math.clamp((int) ((places[i + 1] - top) / height * grid.height), 0, grid.height - 1);
            if (!grid.wood[y * grid.width + x]) {
                leaves[count++] = places[i];
                leaves[count++] = places[i + 1];
            }
        }
        return Arrays.copyOf(leaves, count);
    }

    /** Whether the sprite has still to be read: leaves() gives null for it, and may not once a frame has been drawn. */
    static boolean unread(Texture texture) {
        return !known.containsKey(texture);
    }

    /** Reads the sprite, if a crown asked for it: see Masonry.read. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        if (wanted.remove(texture)) {
            known.put(texture, measure(sheet, width, left, top, right, bottom));
        }
    }

    /** The grid of the rectangle from (left, top) to before (right, bottom) of a sheet width dots wide, held as bytes of red, green, blue and cover. */
    static Grid measure(ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        int across = Math.max(right - left, 0), down = Math.max(bottom - top, 0);
        boolean[] wood = new boolean[across * down];
        for (int y = 0; y < down; y++) {
            for (int x = 0; x < across; x++) {
                if (cover(sheet, width, left + x, top + y) < 0.6f) {
                    continue;
                }
                float red = 0f, green = 0f, blue = 0f, covered = 0f;
                for (int tap = 0; tap < 5; tap++) {
                    int tx = Math.clamp(x + (tap == 1 ? SPREAD : tap == 2 ? -SPREAD : 0), 0, across - 1) + left;
                    int ty = Math.clamp(y + (tap == 3 ? SPREAD : tap == 4 ? -SPREAD : 0), 0, down - 1) + top;
                    int at = (ty * width + tx) * 4;
                    float a = (sheet.get(at + 3) & 255) / 255f;
                    red += (sheet.get(at) & 255) / 255f * a;
                    green += (sheet.get(at + 1) & 255) / 255f * a;
                    blue += (sheet.get(at + 2) & 255) / 255f * a;
                    covered += a;
                }
                int at = ((top + y) * width + left + x) * 4;
                wood[y * across + x] = wood(red / covered, green / covered, blue / covered)
                        || wood((sheet.get(at) & 255) / 255f, (sheet.get(at + 1) & 255) / 255f, (sheet.get(at + 2) & 255) / 255f);
            }
        }
        return new Grid(across, down, wood);
    }

    /** Whether a colour is the game's paint for wood. */
    static boolean wood(float red, float green, float blue) {
        return red > green + REDDER && green >= GREEN_OF_RED * red && blue >= BLUE_OF_GREEN * green
                && 0.299f * red + 0.587f * green + 0.114f * blue < DARK;
    }

    private static float cover(ByteBuffer sheet, int width, int x, int y) {
        return (sheet.get((y * width + x) * 4 + 3) & 255) / 255f;
    }
}
