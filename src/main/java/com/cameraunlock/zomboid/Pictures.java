package com.cameraunlock.zomboid;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
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
        ByteBuffer pixels = read(path);
        if (heights != null) {
            int pictureWide = wide, pictureHigh = high;
            float first = gain;
            ByteBuffer raised = read(heights);
            if (wide != pictureWide || high != pictureHigh) {
                throw new IOException(heights + " is " + wide + " by " + high + ", and " + path + " is " + pictureWide + " by " + pictureHigh);
            }
            for (int at = 0; at < pictureWide * pictureHigh * 4; at += 4) {
                pixels.put(at + 3, raised.get(at));
            }
            gain = first;
        }
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, wide, high, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        finish(GL_TEXTURE_2D, repeats);
        return texture;
    }

    /**
     * One texture of the jar's pictures at paths laid one behind another, all of them the size
     * of the first and all repeating: a shader picks one by its number. The gain is the first's.
     */
    static int loadLayers(String[] paths) throws IOException {
        ByteBuffer pixels = read(paths[0]);
        int layerWide = wide, layerHigh = high;
        float first = gain;
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, texture);
        GL12.glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, layerWide, layerHigh, paths.length, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        for (int layer = 0; layer < paths.length; layer++) {
            if (layer > 0) {
                pixels = read(paths[layer]);
                if (wide != layerWide || high != layerHigh) {
                    throw new IOException(paths[layer] + " is " + wide + " by " + high + ", and " + paths[0] + " is " + layerWide + " by " + layerHigh);
                }
            }
            GL12.glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, layerWide, layerHigh, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        }
        gain = first;
        finish(GL_TEXTURE_2D_ARRAY, true);
        return texture;
    }

    private static int wide, high;

    /** The jar's picture at path as four bytes a pixel, its size in wide and high and its gain in gain. */
    private static ByteBuffer read(String path) throws IOException {
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
        wide = image.getWidth();
        high = image.getHeight();
        int[] row = new int[wide];
        ByteBuffer pixels = ByteBuffer.allocateDirect(wide * high * 4);
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
            throw new IOException(path + " is black or cut away all over");
        }
        gain = 255f * 1000f * solid / light;
        pixels.flip();
        return pixels;
    }

    /** Gives the texture bound to target its smaller copies and says how it is read. */
    private static void finish(int target, boolean repeats) {
        GL30.glGenerateMipmap(target);
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
