package com.cameraunlock.zomboid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * The body of a fire's flames: a fire simulated in Blender's Mantaflow (tools/fire/volume.py), kept
 * as a loop of frames of how much flame there is in each cell of a box round it and how hot its
 * gas is, which FireDraw marches through along each pixel's ray. Seen from any side and from
 * above, it is the same fire, lit from within by the gas's own glow.
 *
 * The frames are laid side by side in one three-dimensional texture, ACROSS a row and DOWN rows,
 * each as high as the box: the heat at half the flame's cells each way, in a texture of its own
 * laid the same way.
 *
 * Render thread only, but for the reading.
 */
final class FlameVolume {
    static final String PICTURE = "/fire/flames.vol.gz";
    private static final int MAGIC = 0x4F564C46, VERSION = 1;
    private static final int GL_TEXTURE_3D = 0x806F, GL_R8 = 0x8229, GL_RED = 0x1903, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_LINEAR = 0x2601;
    private static final int GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803, GL_TEXTURE_WRAP_R = 0x8072, GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_UNPACK_ALIGNMENT = 0x0CF5;

    /**
     * A loop as read: its box in cells across (x), deep (y) and high (z), its frames and how they
     * are laid; how far its flame rises from one frame to the next on the mean, in cells; how high
     * its flames reach on the mean and how wide their root is, as shares of the box's height and
     * width; how many frames it plays a second at its own size; how big a cell is, in metres; how
     * far from the box's middle line and how high any frame has flame that shows, in cells; and its
     * flame and heat, a byte a cell each.
     */
    record Loop(int wide, int deep, int high, int frames, int across, int down, float rise, float reach, float root, float framesASecond, float cell,
            float filled, float filledHigh, ByteBuffer flame, ByteBuffer heat) {}

    private FlameVolume() {}

    /** The loop, off the jar. */
    static Loop read() {
        try (InputStream in = FlameVolume.class.getResourceAsStream(PICTURE)) {
            if (in == null) {
                throw new IOException("the jar has no " + PICTURE);
            }
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(PICTURE + " could not be read", e);
        }
    }

    static Loop read(InputStream packed) throws IOException {
        ByteArrayOutputStream whole = new ByteArrayOutputStream();
        try (GZIPInputStream in = new GZIPInputStream(packed)) {
            in.transferTo(whole);
        }
        ByteBuffer file = ByteBuffer.wrap(whole.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        if (file.remaining() < 13 * 4 || file.getInt() != MAGIC || file.getInt() != VERSION) {
            throw new IOException(PICTURE + " is not version " + VERSION + " of what tools/fire/volume.py writes");
        }
        int wide = file.getInt(), deep = file.getInt(), high = file.getInt(), frames = file.getInt(), across = file.getInt(), down = file.getInt();
        float rise = file.getFloat(), reach = file.getFloat(), root = file.getFloat(), framesASecond = file.getFloat(), cell = file.getFloat();
        if (wide % 2 != 0 || deep % 2 != 0 || high % 2 != 0 || across * down != frames) {
            throw new IOException(PICTURE + " has a box of " + wide + " by " + deep + " by " + high + " and " + frames + " frames laid " + across + " by " + down);
        }
        int flameBytes = wide * deep * high * frames, heatBytes = flameBytes / 8;
        if (file.remaining() != flameBytes + heatBytes) {
            throw new IOException(PICTURE + " holds " + file.remaining() + " bytes of cells, not " + (flameBytes + heatBytes));
        }
        ByteBuffer flame = ByteBuffer.allocateDirect(flameBytes);
        ByteBuffer heat = ByteBuffer.allocateDirect(heatBytes);
        flame.put(file.slice(file.position(), flameBytes)).flip();
        heat.put(file.slice(file.position() + flameBytes, heatBytes)).flip();
        // The march's box is what any frame fills, out to where its edge begins (FireDraw.EDGE_FROM) and as far again as
        // the noise stirs it, and a cell for the blend between cells; and up, as far as a frame's flame rises into the next.
        int least = (int) Math.ceil(FireDraw.EDGE_FROM * 255f);
        int rowWide = wide * across, layer = rowWide * deep * down;
        float furthest = 0f;
        int top = 0;
        for (int z = 0; z < high; z++) {
            for (int row = 0; row < deep * down; row++) {
                float dy = row % deep + 0.5f - deep * 0.5f;
                for (int column = 0; column < rowWide; column++) {
                    if ((flame.get(z * layer + row * rowWide + column) & 0xFF) >= least) {
                        float dx = column % wide + 0.5f - wide * 0.5f;
                        furthest = Math.max(furthest, dx * dx + dy * dy);
                        top = z + 1;
                    }
                }
            }
        }
        float margin = FireDraw.STIR * 0.5f + 1f;
        return new Loop(wide, deep, high, frames, across, down, rise, reach, root, framesASecond, cell, (float) Math.sqrt(furthest) + margin,
                Math.min(top + margin + rise, high), flame, heat);
    }

    private static final String AHEAD = "flame volume";
    private static Loop loop;
    private static boolean reading;
    private static int flameTexture, heatTexture;

    /** The loop, its textures made once it has been read on a worker, which the first asking begins; null until then. */
    static Loop ready() {
        if (loop != null) {
            return loop;
        }
        if (!reading) {
            Ahead.begin(AHEAD, FlameVolume::read);
            reading = true;
        }
        if (!Ahead.done(AHEAD)) {
            return null;
        }
        Loop read;
        try {
            read = Ahead.take(AHEAD, FlameVolume::read);
        } catch (IOException e) {
            throw new UncheckedIOException(PICTURE + " could not be read", e);
        }
        flameTexture = texture(read.wide * read.across, read.deep * read.down, read.high, read.flame);
        heatTexture = texture(read.wide / 2 * read.across, read.deep / 2 * read.down, read.high / 2, read.heat);
        // The cells are the graphics card's now: what is kept is the loop's shape.
        loop = new Loop(read.wide, read.deep, read.high, read.frames, read.across, read.down, read.rise, read.reach, read.root, read.framesASecond, read.cell,
                read.filled, read.filledHigh, null, null);
        reading = false;
        Log.info("first person fire: the flames' loop is on the graphics card, " + read.frames + " frames of " + read.wide + " by " + read.deep + " by "
                + read.high + " cells");
        return loop;
    }

    private static int texture(int wide, int deep, int high, ByteBuffer cells) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_3D, texture);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        GL12.glTexImage3D(GL_TEXTURE_3D, 0, GL_R8, wide, deep, high, 0, GL_RED, GL_UNSIGNED_BYTE, cells);
        GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        return texture;
    }

    static int flameTexture() {
        return flameTexture;
    }

    static int heatTexture() {
        return heatTexture;
    }

    /** Gives the textures back; the loop is read again the next time a fire is in sight. */
    static void letGo() {
        if (loop == null) {
            Ahead.forget(AHEAD);
            reading = false;
            return;
        }
        GL11.glDeleteTextures(flameTexture);
        GL11.glDeleteTextures(heatTexture);
        flameTexture = heatTexture = 0;
        loop = null;
        Log.info("first person fire: the flames' loop is given back");
    }
}
