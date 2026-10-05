package com.cameraunlock.zomboid;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The mod's own pictures of materials and plants, kept in its jar as PNG and JPEG files of
 * whatever size each was made at, and given to the graphics card with smaller copies of
 * themselves for when they are seen far off or at a slant.
 */
final class Pictures {
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
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, wide, high, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        GL30.glGenerateMipmap(GL_TEXTURE_2D);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, repeats ? GL_REPEAT : GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, repeats ? GL_REPEAT : GL_CLAMP_TO_EDGE);
        sharpenAtSlant();
        return texture;
    }

    /** Has the texture bound now read along the slant it is seen at, where the driver can. */
    static void sharpenAtSlant() {
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY, ANISOTROPY);
        // A driver without the means refuses the setting, which leaves the texture as it was: that is all it costs.
        GL11.glGetError();
    }
}
