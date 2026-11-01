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
 * The game's grime over a floor is read here too, for the haze the picture draws in its place: see floorGrime.
 */
final class Stains {
    /** A dot covered less than this, of 255, is a thin one. */
    private static final int THIN = 200;
    /** The share of a sprite's painted dots that are thin for it to be a stain. */
    private static final float MOSTLY = 0.5f;

    private static final ConcurrentHashMap<Texture, Boolean> known = new ConcurrentHashMap<>();
    /** For each sprite of the game's grime over a floor that has been read: see floorGrime. */
    private static final ConcurrentHashMap<Texture, FloorGrime> floors = new ConcurrentHashMap<>();
    /**
     * How far round each of the nine places of a square's grid its grime's cover is taken, in squares either way, and in
     * how many steps across that. The mean of a quarter of the square: the mist's dots and clots are lost in it, and the
     * stain's shape on its square is kept, a band along one edge, a patch in a corner.
     */
    private static final float FLOOR_REACH = 0.25f;
    private static final int FLOOR_STEPS = 5;

    private Stains() {}

    /** Whether the sprite is a stain. Null until a frame has been drawn since it was first asked for. */
    static Boolean of(Texture texture) {
        Boolean stain = known.get(texture);
        if (stain == null) {
            SpriteColours.of(texture);
        }
        return stain;
    }

    /** Whether the sprite is the game's grime on a wall, which the picture draws as grime of its own: see WallSides.STAIN. */
    static boolean grime(Texture texture) {
        return grime(texture.getName());
    }

    /** Whether a sprite of that name is the game's grime on a wall. */
    static boolean grime(String name) {
        return name != null && name.startsWith("overlay_grime_wall");
    }

    /** Whether a sprite of that name is the game's grime over a floor, which the picture draws as grime of its own: see WallSides.floorStain. */
    static boolean floorGrime(String name) {
        return name != null && name.startsWith("overlay_grime_floor");
    }

    /**
     * The game's grime over a floor as the picture draws it: the colour its dots are painted in, from 0 to 1, and how much
     * they cover, a byte a dot, of the across by down dots the sprite is trimmed to.
     */
    record FloorGrime(float red, float green, float blue, int across, int down, byte[] cover) {}

    /** The grime over a floor the sprite is, as read. Null until a frame has been drawn since it was first asked for. */
    static FloorGrime floorGrime(Texture texture) {
        FloorGrime grime = floors.get(texture);
        if (grime == null) {
            SpriteColours.of(texture);
        }
        return grime;
    }

    /**
     * Into grid, a SpriteGeometry.GRID_COVER, how much of the square the grime covers about each of the nine places, as
     * the frame holds the sprite: the mean cover of the square within FLOOR_REACH of the place. Not the sprite's own
     * cover there, the dots and clots that were dark worms near the eye.
     */
    static void floorCover(FloorGrime grime, SpriteGeometry.Frame frame, float[] grid) {
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                float a0 = Math.max(i * 0.5f - FLOOR_REACH, 0f), a1 = Math.min(i * 0.5f + FLOOR_REACH, 1f);
                float b0 = Math.max(j * 0.5f - FLOOR_REACH, 0f), b1 = Math.min(j * 0.5f + FLOOR_REACH, 1f);
                int sum = 0;
                for (int sj = 0; sj < FLOOR_STEPS; sj++) {
                    for (int si = 0; si < FLOOR_STEPS; si++) {
                        float a = a0 + (a1 - a0) * (si + 0.5f) / FLOOR_STEPS, b = b0 + (b1 - b0) * (sj + 0.5f) / FLOOR_STEPS;
                        int x = (int) Math.floor((SpriteGeometry.frameU(frame, a, b) - frame.left) / frame.width * grime.across());
                        int y = (int) Math.floor((SpriteGeometry.frameV(frame, a, b, 0f) - frame.top) / frame.height * grime.down());
                        if (x >= 0 && x < grime.across() && y >= 0 && y < grime.down()) {
                            sum += grime.cover()[y * grime.across() + x] & 255;
                        }
                    }
                }
                grid[j * 3 + i] = sum / (255f * FLOOR_STEPS * FLOOR_STEPS);
            }
        }
    }

    /** Reads the sprite: see Masonry.read. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        known.put(texture, measure(sheet, width, left, top, right, bottom));
        if (floorGrime(texture.getName())) {
            floors.put(texture, measureFloor(sheet, width, left, top, right, bottom));
        }
    }

    /** As floorGrime, for the rectangle of a sheet a sprite is: its dots' colour is held multiplied by their cover. */
    static FloorGrime measureFloor(ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        int across = Math.max(right - left, 0), down = Math.max(bottom - top, 0);
        byte[] cover = new byte[across * down];
        long red = 0, green = 0, blue = 0, covered = 0;
        for (int y = 0; y < down; y++) {
            for (int x = 0; x < across; x++) {
                int at = ((top + y) * width + left + x) * 4;
                cover[y * across + x] = sheet.get(at + 3);
                red += sheet.get(at) & 255;
                green += sheet.get(at + 1) & 255;
                blue += sheet.get(at + 2) & 255;
                covered += sheet.get(at + 3) & 255;
            }
        }
        float whole = Math.max(covered, 1L);
        return new FloorGrime(Math.min(red / whole, 1f), Math.min(green / whole, 1f), Math.min(blue / whole, 1f), across, down, cover);
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
