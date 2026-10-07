package com.cameraunlock.zomboid;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * The mod's own pictures of materials and plants, kept in its jar as PNG and JPEG files of
 * whatever size each was made at, and given to the graphics card with smaller copies of
 * themselves for when they are seen far off or at a slant.
 */
final class Pictures {
    static final int GL_TEXTURE_2D_ARRAY = 0x8C1A;
    private static final int GL_TEXTURE_2D = 0x0DE1, GL_RGBA = 0x1908, GL_RGBA8 = 0x8058, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_LINEAR = 0x2601, GL_LINEAR_MIPMAP_LINEAR = 0x2703, GL_REPEAT = 0x2901, GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_TEXTURE_MAX_ANISOTROPY = 0x84FE;
    /** Ground seen from standing height is seen at a slant: this many samples along the slant keep it sharp into the distance. */
    private static final int ANISOTROPY = 8;
    /**
     * What the picture last loaded is multiplied by to be 1 on average over what of it is solid:
     * laid over a surface so, it changes the surface's texture and not how light it is.
     */
    static float gain;
    /**
     * Whether pictures are read at half their width and height: a quarter of the memory, for a
     * graphics mode that draws fewer dots than the window has and shows no depth in surfaces.
     * Set before reading begins and not while any is being read.
     */
    static volatile boolean small;

    private Pictures() {}

    /**
     * The texture made of the jar's picture at path, which repeats or not. Thrown when the jar
     * has no such picture or it cannot be read.
     */
    static int load(String path, boolean repeats) throws IOException {
        return load(path, null, repeats);
    }

    /**
     * The same, of a picture all of which is solid, with the jar's picture at heights beside
     * it: how far each point of the surface stands out of it, white the top and black the
     * deepest, kept as the fourth number of each texel where a cut-out keeps how solid it is.
     * The gain is the first picture's. heights may be null, for a surface that has none.
     */
    static int load(String path, String heights, boolean repeats) throws IOException {
        Decoded picture = read(path);
        try {
            if (heights != null) {
                Decoded raised = read(heights);
                try {
                    if (raised.wide != picture.wide || raised.high != picture.high) {
                        throw new IOException(heights + " is " + raised.wide + " by " + raised.high + ", and " + path + " is " + picture.wide + " by " + picture.high);
                    }
                    for (int at = 0; at < picture.wide * picture.high * 4; at += 4) {
                        picture.pixels.put(at + 3, raised.pixels.get(at));
                    }
                } finally {
                    raised.memory.free();
                }
            }
            gain = picture.gain;
            int texture = GL11.glGenTextures();
            GL11.glBindTexture(GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, picture.wide, picture.high, 0, GL_RGBA, GL_UNSIGNED_BYTE, picture.pixels);
            finish(GL_TEXTURE_2D, texture, repeats, path);
            return texture;
        } finally {
            picture.memory.free();
        }
    }

    /**
     * One texture of the jar's pictures at paths laid one behind another, all of them the size
     * of the first and all repeating: a shader picks one by its number. The gain is the first's.
     */
    static int loadLayers(String[] paths) throws IOException {
        int wide = 0, high = 0;
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, texture);
        for (int layer = 0; layer < paths.length; layer++) {
            Decoded picture = read(paths[layer]);
            try {
                if (layer == 0) {
                    wide = picture.wide;
                    high = picture.high;
                    gain = picture.gain;
                    GL12.glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, wide, high, paths.length, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
                }
                if (picture.wide != wide || picture.high != high) {
                    throw new IOException(paths[layer] + " is " + picture.wide + " by " + picture.high + ", and " + paths[0] + " is " + wide + " by " + high);
                }
                GL12.glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, wide, high, 1, GL_RGBA, GL_UNSIGNED_BYTE, picture.pixels);
            } finally {
                picture.memory.free();
            }
        }
        finish(GL_TEXTURE_2D_ARRAY, texture, true, paths[0]);
        return texture;
    }

    /**
     * A picture as four bytes a pixel, with what it is multiplied by to be 1 on average over what of it is solid. The
     * pixels are sixteen megabytes for a picture 2048 square, and are given back as soon as the graphics card has them.
     */
    private record Decoded(ByteBuffer pixels, OffHeap.Block memory, int wide, int high, float gain) {}

    /** The pictures read ahead and not yet asked for, by path, and a count that goes up each time they are all let go. Both under the map's lock. */
    private static final HashMap<String, Decoded> readAhead = new HashMap<>();
    private static final HashSet<String> begunAhead = new HashSet<>();
    private static int letGoes;

    /** Begins reading the jar's picture at path on another thread. */
    static void readAhead(String path) {
        int begunAt;
        synchronized (readAhead) {
            begunAt = letGoes;
            begunAhead.add(path);
        }
        Ahead.begin(path, () -> {
            Decoded read = decode(path);
            synchronized (readAhead) {
                // Let go of while it was being read: nobody will ask for this one.
                if (letGoes != begunAt) {
                    read.memory.free();
                } else {
                    readAhead.put(path, read);
                }
            }
            return null;
        });
    }

    /** Whether the picture at path has been read ahead and is waiting to be asked for. */
    static boolean isRead(String path) {
        return Ahead.done(path);
    }

    /**
     * Gives back every picture read ahead and not asked for, and has those still being read
     * given back as they are done: the pieces that would have asked for them will not be made.
     */
    static void letGoOfReadAhead() {
        synchronized (readAhead) {
            letGoes++;
            for (Decoded each : readAhead.values()) {
                each.memory.free();
            }
            readAhead.clear();
            for (String path : begunAhead) {
                Ahead.forget(path);
            }
            begunAhead.clear();
        }
    }

    private static Decoded read(String path) throws IOException {
        // Waits for the reading begun ahead, or does it here if no thread has started on it.
        Ahead.take(path, () -> null);
        Decoded ahead;
        synchronized (readAhead) {
            ahead = readAhead.remove(path);
            begunAhead.remove(path);
        }
        return ahead != null ? ahead : decode(path);
    }

    private static Decoded decode(String path) throws IOException {
        BufferedImage image;
        try (InputStream in = Pictures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("the jar has no " + path);
            }
            image = ImageIO.read(in);
        }
        if (image == null) {
            throw new IOException(path + " is not a picture this Java can read");
        }
        int wide = image.getWidth(), high = image.getHeight();
        int[] row = new int[wide];
        OffHeap.Block memory = OffHeap.take(wide * high * 4);
        ByteBuffer pixels = memory.bytes;
        long light = 0, solid = 0;
        for (int y = 0; y < high; y++) {
            image.getRGB(0, y, wide, 1, row, 0, wide);
            for (int argb : row) {
                pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
                if (argb >>> 24 > 127) {
                    light += 299 * (argb >> 16 & 0xff) + 587 * (argb >> 8 & 0xff) + 114 * (argb & 0xff);
                    solid++;
                }
            }
        }
        if (light == 0) {
            memory.free();
            throw new IOException(path + " is black or cut away all over");
        }
        pixels.flip();
        Decoded whole = new Decoded(pixels, memory, wide, high, 255f * 1000f * solid / light);
        if (!small || wide % 2 != 0 || high % 2 != 0) {
            return whole;
        }
        OffHeap.Block half = halved(pixels, wide, high);
        memory.free();
        return new Decoded(half.bytes, half, wide / 2, high / 2, whole.gain);
    }

    /** A picture of four bytes a dot at half its width and height, each dot the mean of the four it stands for. */
    static OffHeap.Block halved(ByteBuffer whole, int wide, int high) {
        int halfWide = wide / 2, halfHigh = high / 2;
        OffHeap.Block half = OffHeap.take(halfWide * halfHigh * 4);
        ByteBuffer to = half.bytes;
        for (int y = 0; y < halfHigh; y++) {
            int upper = y * 2 * wide * 4, lower = upper + wide * 4;
            for (int x = 0; x < halfWide; x++) {
                int left = x * 8;
                for (int part = 0; part < 4; part++) {
                    int sum = (whole.get(upper + left + part) & 0xff) + (whole.get(upper + left + 4 + part) & 0xff)
                            + (whole.get(lower + left + part) & 0xff) + (whole.get(lower + left + 4 + part) & 0xff);
                    to.put((y * halfWide + x) * 4 + part, (byte) (sum + 2 >> 2));
                }
            }
        }
        return half;
    }

    /** Gives the texture bound to target its smaller copies and says how it is read. */
    private static void finish(int target, int texture, boolean repeats, String path) throws IOException {
        GL30.glGenerateMipmap(target);
        // Asked here, before the setting below whose refusal is thrown away: a graphics card with no room for the
        // picture says so now, and whoever asked keeps the picture they had.
        int error = GL11.glGetError();
        if (error != 0) {
            GL11.glDeleteTextures(texture);
            throw new IOException("OpenGL error 0x" + Integer.toHexString(error) + " making the texture of " + path);
        }
        GL11.glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(target, GL_TEXTURE_WRAP_S, repeats ? GL_REPEAT : GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(target, GL_TEXTURE_WRAP_T, repeats ? GL_REPEAT : GL_CLAMP_TO_EDGE);
        sharpenAtSlant(target);
    }

    /** Has the texture bound now read along the slant it is seen at, where the driver can. */
    static void sharpenAtSlant() {
        sharpenAtSlant(GL_TEXTURE_2D);
    }

    private static void sharpenAtSlant(int target) {
        GL11.glTexParameteri(target, GL_TEXTURE_MAX_ANISOTROPY, ANISOTROPY);
        // A driver without the means refuses the setting, which leaves the texture as it was: that is all it costs.
        GL11.glGetError();
    }
}
