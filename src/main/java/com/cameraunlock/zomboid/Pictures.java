package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GLCapabilities;

/**
 * The mod's own pictures of materials, plants and models, kept in its jar as the graphics
 * card holds them: in BC7 blocks, a quarter of the memory of four bytes a texel, each with
 * the smaller copies of itself for when it is seen far off or at a slant. tools/textures
 * makes the files, and says how one is laid out.
 */
final class Pictures {
    static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE_2D_ARRAY = 0x8C1A;
    private static final int GL_RGBA = 0x1908, GL_RGBA8 = 0x8058, GL_SRGB8_ALPHA8 = 0x8C43, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_COMPRESSED_RGBA_BPTC_UNORM = 0x8E8C, GL_COMPRESSED_SRGB_ALPHA_BPTC_UNORM = 0x8E8D;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_LINEAR = 0x2601, GL_LINEAR_MIPMAP_LINEAR = 0x2703, GL_REPEAT = 0x2901, GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_TEXTURE_MAX_ANISOTROPY = 0x84FE;
    /** Ground seen from standing height is seen at a slant: this many samples along the slant keep it sharp into the distance. */
    private static final int ANISOTROPY = 8;
    /** A file's head: "CUBC", then its format, width, height, layers, levels and whether its colour is a screen's, then its gain. */
    private static final int HEAD = 32, MAGIC = 0x43425543, FORMAT = 4, WIDE = 8, HIGH = 12, LAYERS = 16, LEVELS = 20, SCREEN = 24, GAIN = 28;
    private static final int PLAIN = 0, BC7 = 7;
    /** Dev only: the graphics card is taken to read no BC7. */
    private static final boolean NO_BC7 = Boolean.getBoolean("cameraunlock.zomboid.noBc7");
    /**
     * What the picture last loaded is multiplied by to be 1 on average over what of it is solid:
     * laid over a surface so, it changes the surface's texture and not how light it is.
     */
    static float gain;
    /**
     * Whether pictures are read without their largest level, at half their width and height: a
     * quarter of the memory, for a graphics mode that draws fewer dots than the window has and
     * shows no depth in surfaces. Set before reading begins and not while any is being read.
     */
    static volatile boolean small;
    /** Whether the graphics card has been asked what it reads, and whether BC7 has to be unpacked for it. */
    private static boolean cardAsked;
    private static volatile boolean unpacks;
    /** Whether the card reads a texture along the slant it is seen at. Without, the setting is an error, and it is not made. */
    private static volatile boolean slants;

    private Pictures() {}

    /**
     * Asks the graphics card, once, whether it reads BC7 blocks: OpenGL 4.2 does, and an older
     * one with ARB_texture_compression_bptc. For a card that does not, every picture is
     * unpacked as it is read and takes the four bytes a texel it took before the pictures
     * were compressed. Render thread, before anything is read.
     */
    static void askCard() {
        if (cardAsked) {
            return;
        }
        VideoMemory.read();
        GLCapabilities card = GL.getCapabilities();
        cardAsked = true;
        slants = card.OpenGL46 || card.GL_EXT_texture_filter_anisotropic;
        if (NO_BC7) {
            unpacks = true;
            Log.info("cameraunlock.zomboid.noBc7 is set: the mod's own pictures are unpacked as they are read, as for a graphics card that reads no BC7");
        } else if (!card.OpenGL42 && !card.GL_ARB_texture_compression_bptc) {
            unpacks = true;
            Log.info("this graphics card reads no BC7 texture compression (it has neither OpenGL 4.2 nor ARB_texture_compression_bptc): "
                    + "the mod's own pictures are unpacked as they are read, and take four times the video memory they would");
        }
    }

    /** The texture of the jar's picture at path, which repeats or not. Thrown when the jar has no such picture or it cannot be read. */
    static int load(String path, boolean repeats) throws IOException {
        OffHeap.Block file = read(path);
        try {
            if (file.bytes.getInt(LAYERS) != 1) {
                throw new IOException(path + " is " + file.bytes.getInt(LAYERS) + " pictures one behind another, where one is read");
            }
            gain = file.bytes.getFloat(GAIN);
            return texture(file, GL_TEXTURE_2D, repeats, ANISOTROPY, path);
        } finally {
            file.free();
        }
    }

    /**
     * One texture of the jar's pictures at path, laid one behind another and all repeating: a
     * shader picks one by its number. The gain is the first's.
     */
    static int loadLayers(String path) throws IOException {
        OffHeap.Block file = read(path);
        try {
            gain = file.bytes.getFloat(GAIN);
            return texture(file, GL_TEXTURE_2D_ARRAY, true, ANISOTROPY, path);
        } finally {
            file.free();
        }
    }

    /** The pictures read ahead and not yet asked for, by path, and a count that goes up each time they are all let go. Both under the map's lock. */
    private static final HashMap<String, OffHeap.Block> readAhead = new HashMap<>();
    private static final HashSet<String> begunAhead = new HashSet<>();
    private static int letGoes;

    /** Begins reading the jar's picture at path on another thread. */
    static void readAhead(String path) {
        int begunAt;
        synchronized (readAhead) {
            // Still being read, or read and not yet asked for: a second read would drop the first unfreed.
            if (!begunAhead.add(path)) {
                return;
            }
            begunAt = letGoes;
        }
        Ahead.begin(path, () -> {
            OffHeap.Block read = packed(path, small);
            synchronized (readAhead) {
                // Let go of while it was being read: nobody will ask for this one.
                if (letGoes != begunAt) {
                    read.free();
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
            for (OffHeap.Block each : readAhead.values()) {
                each.free();
            }
            readAhead.clear();
            for (String path : begunAhead) {
                Ahead.forget(path);
            }
            begunAhead.clear();
        }
        CabinModel.picturesLetGo();
    }

    private static OffHeap.Block read(String path) throws IOException {
        // Waits for the reading begun ahead, or does it here if no thread has started on it.
        Ahead.take(path, () -> null);
        OffHeap.Block ahead;
        synchronized (readAhead) {
            ahead = readAhead.remove(path);
            begunAhead.remove(path);
        }
        return ahead != null ? ahead : packed(path, small);
    }

    private static int levelBytes(int format, int wide, int high, int layers, int level) {
        int across = Math.max(1, wide >> level), down = Math.max(1, high >> level);
        return (format == BC7 ? (across + 3) / 4 * ((down + 3) / 4) * 16 : across * down * 4) * layers;
    }

    /**
     * The jar's picture at path as its file holds it, a head and then the levels, in memory
     * outside the heap that whoever takes it frees: read straight into that memory, with no
     * array of the file on the heap. Without its largest level where half says so and it has a
     * smaller, and unpacked to four bytes a texel for a graphics card that reads no BC7. The
     * head says what the memory holds, not what the file did. Any thread, once askCard has run.
     */
    static OffHeap.Block packed(String path, boolean half) throws IOException {
        try (InputStream in = Pictures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("the jar has no " + path);
            }
            ByteBuffer head = ByteBuffer.wrap(in.readNBytes(HEAD)).order(ByteOrder.LITTLE_ENDIAN);
            if (head.capacity() != HEAD || head.getInt(0) != MAGIC) {
                throw new IOException(path + " is not one of the mod's packed pictures");
            }
            int format = head.getInt(FORMAT), wide = head.getInt(WIDE), high = head.getInt(HIGH), layers = head.getInt(LAYERS), levels = head.getInt(LEVELS);
            if (format != PLAIN && format != BC7 || wide < 1 || high < 1 || wide > 4096 || high > 4096 || layers < 1 || layers > 16
                    || levels != 32 - Integer.numberOfLeadingZeros(Math.max(wide, high))) {
                throw new IOException(path + " has a head that makes no sense: format " + format + ", " + wide + " by " + high + ", " + layers + " layers, " + levels + " levels");
            }
            int left = half && levels > 1 ? 1 : 0;
            int passed = left > 0 ? levelBytes(format, wide, high, layers, 0) : 0, bytes = 0;
            for (int level = left; level < levels; level++) {
                bytes += levelBytes(format, wide, high, layers, level);
            }
            OffHeap.Block memory = OffHeap.take(HEAD + bytes);
            try {
                byte[] some = new byte[1 << 16];
                // Counted from the end of the largest level where that is left out: what is read before it goes nowhere.
                int at = -passed;
                for (int read = in.read(some); read >= 0; read = in.read(some)) {
                    if (at + read > bytes) {
                        throw new IOException(path + " holds more than the " + (passed + bytes) + " bytes its head says");
                    }
                    int kept = Math.min(read, at + read);
                    if (kept > 0) {
                        memory.bytes.put(HEAD + at + read - kept, some, read - kept, kept);
                    }
                    at += read;
                }
                if (at != bytes) {
                    throw new IOException(path + " holds " + (passed + at) + " bytes after its head, which says " + (passed + bytes));
                }
                memory.bytes.order(ByteOrder.LITTLE_ENDIAN).put(0, head, 0, HEAD);
                memory.bytes.putInt(WIDE, Math.max(1, wide >> left)).putInt(HIGH, Math.max(1, high >> left)).putInt(LEVELS, levels - left);
                if (format == BC7 && unpacks) {
                    return unpacked(memory);
                }
                OffHeap.Block whole = memory;
                memory = null;
                return whole;
            } finally {
                if (memory != null) {
                    memory.free();
                }
            }
        }
    }

    /** A picture held in BC7 blocks as one of four bytes a texel, level for level. */
    static OffHeap.Block unpacked(OffHeap.Block blocks) {
        ByteBuffer from = blocks.bytes;
        int wide = from.getInt(WIDE), high = from.getInt(HIGH), layers = from.getInt(LAYERS), levels = from.getInt(LEVELS);
        int bytes = 0;
        for (int level = 0; level < levels; level++) {
            bytes += levelBytes(PLAIN, wide, high, layers, level);
        }
        OffHeap.Block plain = OffHeap.take(HEAD + bytes);
        ByteBuffer to = plain.bytes.order(ByteOrder.LITTLE_ENDIAN);
        to.put(0, from, 0, HEAD).putInt(FORMAT, PLAIN);
        int read = HEAD, written = HEAD;
        for (int level = 0; level < levels; level++) {
            int across = Math.max(1, wide >> level), down = Math.max(1, high >> level);
            for (int layer = 0; layer < layers; layer++) {
                Bc7.unpack(from, read, across, down, to, written);
                read += levelBytes(BC7, wide, high, 1, level);
                written += levelBytes(PLAIN, wide, high, 1, level);
            }
        }
        return plain;
    }

    /**
     * The texture of a picture as packed left it, every level of it: of the target given,
     * which repeats or not and is read along a slant with so many samples. Thrown when the
     * graphics card refuses it, which Pipeline is told of. The caller frees the file.
     */
    static int texture(OffHeap.Block file, int target, boolean repeats, int anisotropy, String path) throws IOException {
        ByteBuffer bytes = file.bytes;
        int format = bytes.getInt(FORMAT), wide = bytes.getInt(WIDE), high = bytes.getInt(HIGH), layers = bytes.getInt(LAYERS), levels = bytes.getInt(LEVELS);
        boolean screen = bytes.getInt(SCREEN) != 0;
        int kept = format == BC7 ? screen ? GL_COMPRESSED_SRGB_ALPHA_BPTC_UNORM : GL_COMPRESSED_RGBA_BPTC_UNORM : screen ? GL_SRGB8_ALPHA8 : GL_RGBA8;
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(target, texture);
        int at = HEAD;
        for (int level = 0; level < levels; level++) {
            int across = Math.max(1, wide >> level), down = Math.max(1, high >> level);
            int size = levelBytes(format, wide, high, layers, level);
            ByteBuffer texels = bytes.slice(at, size);
            if (target == GL_TEXTURE_2D_ARRAY) {
                if (format == BC7) {
                    GL13.glCompressedTexImage3D(target, level, kept, across, down, layers, 0, texels);
                } else {
                    GL12.glTexImage3D(target, level, kept, across, down, layers, 0, GL_RGBA, GL_UNSIGNED_BYTE, texels);
                }
            } else if (format == BC7) {
                GL13.glCompressedTexImage2D(target, level, kept, across, down, 0, texels);
            } else {
                GL11.glTexImage2D(target, level, kept, across, down, 0, GL_RGBA, GL_UNSIGNED_BYTE, texels);
            }
            at += size;
        }
        // A graphics card with no room for the picture says so now.
        int error = GL11.glGetError();
        Pipeline.noted(error);
        if (error != 0) {
            GL11.glDeleteTextures(texture);
            throw new IOException("OpenGL error 0x" + Integer.toHexString(error) + " making the texture of " + path);
        }
        GL11.glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(target, GL_TEXTURE_WRAP_S, repeats ? GL_REPEAT : GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(target, GL_TEXTURE_WRAP_T, repeats ? GL_REPEAT : GL_CLAMP_TO_EDGE);
        if (slants) {
            GL11.glTexParameteri(target, GL_TEXTURE_MAX_ANISOTROPY, anisotropy);
        }
        return texture;
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

    /** The least power of two at or over this many dots, as the game sizes a texture (PZMath.smallestEncompassingPowerOfTwo). */
    static int encompassing(int dots) {
        return dots <= 1 ? 1 : 1 << 32 - Integer.numberOfLeadingZeros(dots - 1);
    }

    /**
     * A picture of four bytes a dot in the corner of one as wide and as high as the texture the
     * game makes for it (encompassing), the rest clear, as the game pads one. The picture handed
     * in is itself where it is that size already, and freed where it is not.
     */
    static OffHeap.Block padded(OffHeap.Block picture, int wide, int high) {
        int across = encompassing(wide), down = encompassing(high);
        if (across == wide && down == high) {
            return picture;
        }
        OffHeap.Block whole = OffHeap.take(across * down * 4);
        ByteBuffer to = whole.bytes;
        byte[] clear = new byte[across * 4];
        for (int y = 0; y < down; y++) {
            if (y < high) {
                to.put(y * across * 4, picture.bytes, y * wide * 4, wide * 4);
                to.put(y * across * 4 + wide * 4, clear, 0, (across - wide) * 4);
            } else {
                to.put(y * across * 4, clear);
            }
        }
        picture.free();
        return whole;
    }

    /** Has the texture bound now read along the slant it is seen at, where the card can. Render thread, once askCard has run. */
    static void sharpenAtSlant() {
        if (slants) {
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY, ANISOTROPY);
        }
    }
}
