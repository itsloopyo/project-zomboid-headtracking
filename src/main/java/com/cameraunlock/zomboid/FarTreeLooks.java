package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * How each kind of far tree looks, as the trees of that kind the built world has made last
 * looked: the shape of the crown, how thick the bole, and the colour of the leaves, which
 * follows the season as the game's trees do. A kind not yet seen looks as the same species
 * at another size does, made larger or smaller, or failing that as the trees seen so far do
 * on the whole, or failing that as a plain tree of its size.
 *
 * The logic thread notes what it builds (saw), and the render thread writes it for the far
 * trees' program (write), a row of ROW texels a kind.
 */
final class FarTreeLooks {
    /** Texels a kind: leaves, bark, the bole and crown's heights, then the crown's half width at each of Flora.CROWN_BANDS heights. */
    static final int ROW = 4 + Flora.CROWN_BANDS;
    /** How long a kind seen is not measured again, though the built world goes on making trees of it. */
    private static final long SEEN_FOR_NANOS = 4_000_000_000L;
    private static final float[] BARK = {0.30f, 0.25f, 0.21f};

    /**
     * A kind as last seen: crown from lowest to highest over the foot, its half width at each
     * band, the bole's radius, and the picture of its leaves, whose colour is read on the thread
     * that draws (SpriteColours) and so may only come a frame or more later.
     */
    record Look(float lowest, float highest, float[] reach, float bole, Texture leaves, long nanos) {}

    private static final ConcurrentHashMap<String, Look> seen = new ConcurrentHashMap<>();

    private FarTreeLooks() {}

    /** Logic thread: whether a tree of the kind its tile names wants measuring. */
    static boolean wants(String tile, long now) {
        String name = nameOf(tile);
        if (name == null) {
            return false;
        }
        Look look = seen.get(name);
        return look == null || now - look.nanos >= SEEN_FOR_NANOS;
    }

    /** The kind's name of a tree's tile, as FarTreeKinds.name gives it, or null for a tile that is no tree's. */
    static String nameOf(String tile) {
        String species = FarTreeKinds.speciesOf(tile);
        if (species == null) {
            return null;
        }
        int size = FarTreeKinds.sizeOf(tile);
        return "e_" + species + (size == FarTreeKinds.JUMBO_XXL ? "JUMBOXXL" : size == FarTreeKinds.JUMBO_XL ? "JUMBOXL" : size == FarTreeKinds.JUMBO ? "JUMBO" : "");
    }

    /**
     * Logic thread: a tree of the kind its tile names, as the built world has just measured its
     * crown (Flora.measure: sizes and the reach at each band to either side), with the picture of its leaves.
     */
    static void saw(String tile, float[] sizes, float[] reachLeft, float[] reachRight, float bole, Texture leaves, long now) {
        String name = nameOf(tile);
        if (name == null) {
            return;
        }
        float[] reach = new float[Flora.CROWN_BANDS];
        for (int band = 0; band < reach.length; band++) {
            reach[band] = (reachLeft[band] + reachRight[band]) / 2f;
        }
        seen.put(name, new Look(sizes[Flora.LOWEST], sizes[Flora.HIGHEST], reach, bole, leaves, now));
    }

    /** Logic thread, as a save is let go of: the next one's trees are seen afresh. */
    static void forget() {
        seen.clear();
    }

    /** How tall a plain tree of each size is, in tiles, and how wide its crown, for a kind nothing like it has been seen of. */
    private static final float[] PLAIN_TALL = {4.5f, 6.5f, 8.5f, 11f}, PLAIN_WIDE = {1.2f, 1.8f, 2.6f, 3.6f};

    /**
     * Render thread: every kind's row, in the kinds' order, as floats into to: red, green,
     * blue and one more a texel. A kind whose leaves' colour is still to be read has the colour
     * of the leaves seen so far on the whole.
     */
    static void write(FarTreeKinds kinds, ByteBuffer to) {
        float lowestSum = 0f, wideSum = 0f, red = 0f, green = 0f, blue = 0f;
        int count = 0, coloured = 0;
        for (Look look : seen.values()) {
            lowestSum += look.lowest / look.highest;
            wideSum += widest(look) / look.highest;
            count++;
            float[] colour = SpriteColours.of(look.leaves);
            if (colour != null) {
                red += colour[WallSides.MEAN];
                green += colour[WallSides.MEAN + 1];
                blue += colour[WallSides.MEAN + 2];
                coloured++;
            }
        }
        float[] usual = coloured == 0 ? new float[] {0.22f, 0.3f, 0.14f} : new float[] {red / coloured, green / coloured, blue / coloured};
        List<String> species = kinds.species;
        for (int kind = 0; kind < species.size() * FarTreeKinds.SIZES; kind++) {
            Look look = seen.get(kinds.name(kind));
            float scale = 1f;
            if (look == null) {
                // The same species at the nearest other size seen, grown or shrunk to this size's height.
                int size = kind % FarTreeKinds.SIZES;
                for (int off = 1; off < FarTreeKinds.SIZES && look == null; off++) {
                    for (int other : new int[] {size - off, size + off}) {
                        if (look == null && other >= 0 && other < FarTreeKinds.SIZES) {
                            look = seen.get(kinds.name(kind - size + other));
                            if (look != null) {
                                scale = PLAIN_TALL[size] / PLAIN_TALL[other];
                            }
                        }
                    }
                }
            }
            if (look == null) {
                plain(kind % FarTreeKinds.SIZES, count == 0 ? 0.4f : lowestSum / count, count == 0 ? 1f : wideSum / count * PLAIN_TALL[kind % FarTreeKinds.SIZES], usual, to);
                continue;
            }
            float[] colour = SpriteColours.of(look.leaves);
            float[] leaves = colour == null ? usual : new float[] {colour[WallSides.MEAN], colour[WallSides.MEAN + 1], colour[WallSides.MEAN + 2]};
            to.putFloat(leaves[0]).putFloat(leaves[1]).putFloat(leaves[2]).putFloat(1f);
            to.putFloat(BARK[0]).putFloat(BARK[1]).putFloat(BARK[2]).putFloat(1f);
            to.putFloat(look.bole * scale).putFloat(look.lowest * scale).putFloat(look.highest * scale).putFloat(0f);
            to.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f);
            for (float reach : look.reach) {
                to.putFloat(reach * scale).putFloat(0f).putFloat(0f).putFloat(0f);
            }
        }
    }

    private static float widest(Look look) {
        float widest = 0f;
        for (float reach : look.reach) {
            widest = Math.max(widest, reach);
        }
        return widest;
    }

    /** A crown round as an egg, from lowest (a share of the height) to the top, with its leaves the colour given. */
    private static void plain(int size, float lowShare, float wide, float[] leaves, ByteBuffer to) {
        float tall = PLAIN_TALL[size], lowest = tall * lowShare;
        to.putFloat(leaves[0]).putFloat(leaves[1]).putFloat(leaves[2]).putFloat(1f);
        to.putFloat(BARK[0]).putFloat(BARK[1]).putFloat(BARK[2]).putFloat(1f);
        to.putFloat(0.12f + 0.05f * size).putFloat(lowest).putFloat(tall).putFloat(0f);
        to.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f);
        for (int band = 0; band < Flora.CROWN_BANDS; band++) {
            float t = (band + 0.5f) / Flora.CROWN_BANDS;
            to.putFloat(wide * (float) Math.sqrt(Math.max(0f, 1f - (2f * t - 0.8f) * (2f * t - 0.8f) / 1.44f))).putFloat(0f).putFloat(0f).putFloat(0f);
        }
    }
}
