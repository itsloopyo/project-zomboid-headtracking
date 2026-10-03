package com.cameraunlock.zomboid;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import zombie.core.SpriteRenderer;
import zombie.core.textures.TextureDraw;

/**
 * Draws a reference scene through ViewCamera behind the main menu and saves
 * two frames of it, a quarter turn apart. It runs only when the JVM is started
 * with -Dcameraunlock.zomboid.renderCheck=<directory>, and exists to show that
 * the agent can draw on the game's render thread and leave its OpenGL state as
 * it found it, without needing a save to load.
 *
 * The scene is a checkered ground with posts: white four tiles ahead along +x,
 * green two tiles to its right (+y), red two to its left (-y), and blue four
 * tiles along +y, which is ahead and to the left after the quarter turn.
 */
public final class RenderCheck {
    static final String PROPERTY = "cameraunlock.zomboid.renderCheck";

    static Path outputDirectory;

    private static final int GL_QUADS = 0x0007;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_DEPTH_TEST = 0x0B71;
    private static final int GL_CULL_FACE = 0x0B44;
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_ALPHA_TEST = 0x0BC0;
    private static final int GL_SCISSOR_TEST = 0x0C11;
    private static final int GL_STENCIL_TEST = 0x0B90;
    private static final int GL_MODELVIEW = 0x1700;
    private static final int GL_PROJECTION = 0x1701;
    private static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF;
    private static final int GL_VIEWPORT = 0x0BA2;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_VENDOR = 0x1F00;
    private static final int GL_RENDERER = 0x1F01;
    private static final int GL_VERSION = 0x1F02;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_PACK_ALIGNMENT = 0x0D05;

    private static final int FIRST_CAPTURE_FRAME = 60;
    private static final int SECOND_CAPTURE_FRAME = 120;
    private static final float EYE_HEIGHT = 1.6f;
    private static final float POST_DISTANCE = 4f;
    private static final int GROUND_HALF_SIZE = 20;

    private static final Scene scene = new Scene();

    private RenderCheck() {}

    /** Called on the game's main thread from MainScreenState.render. */
    public static void queue() {
        SpriteRenderer.instance.drawGeneric(scene);
    }

    private static final class Scene extends TextureDraw.GenericDrawer {
        private int frame;

        @Override
        public void render() {
            frame++;
            if (frame > SECOND_CAPTURE_FRAME) {
                return;
            }
            if (frame == 1) {
                Log.info("render thread " + Thread.currentThread().getName() + ", OpenGL "
                        + GL11.glGetString(GL_VERSION) + ", " + GL11.glGetString(GL_VENDOR) + " "
                        + GL11.glGetString(GL_RENDERER));
            }

            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL_VIEWPORT, viewport);
            float yaw = frame < SECOND_CAPTURE_FRAME ? 0f : (float) (Math.PI / 2);

            int program = GL11.glGetInteger(GL_CURRENT_PROGRAM);
            GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
            GL11.glMatrixMode(GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadMatrixf(ViewCamera.projection((float) Math.toRadians(70), (float) viewport[2] / viewport[3], 0.05f, 200f));
            GL11.glMatrixMode(GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadMatrixf(ViewCamera.view(0f, 0f, EYE_HEIGHT, yaw, 0f));
            GL20.glUseProgram(0);
            GL11.glDisable(GL_TEXTURE_2D);
            GL11.glDisable(GL_DEPTH_TEST);
            GL11.glDisable(GL_CULL_FACE);
            GL11.glDisable(GL_BLEND);
            GL11.glDisable(GL_ALPHA_TEST);
            GL11.glDisable(GL_SCISSOR_TEST);
            GL11.glDisable(GL_STENCIL_TEST);

            drawGround();
            drawPost(POST_DISTANCE, 0f, 1f, 1f, 1f);
            drawPost(POST_DISTANCE, 2f, 0f, 1f, 0f);
            drawPost(POST_DISTANCE, -2f, 1f, 0f, 0f);
            drawPost(2f, POST_DISTANCE, 0f, 0f, 1f);

            if (frame == FIRST_CAPTURE_FRAME || frame == SECOND_CAPTURE_FRAME) {
                capture(viewport, frame == FIRST_CAPTURE_FRAME ? "render-check-facing-x.png" : "render-check-facing-y.png");
            }

            GL20.glUseProgram(program);
            GL11.glMatrixMode(GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();

            int error = GL11.glGetError();
            if (error != 0) {
                throw new IllegalStateException("OpenGL error 0x" + Integer.toHexString(error) + " in the render check, frame " + frame);
            }
        }

        private static void drawGround() {
            GL11.glBegin(GL_QUADS);
            for (int x = -GROUND_HALF_SIZE; x < GROUND_HALF_SIZE; x++) {
                for (int y = -GROUND_HALF_SIZE; y < GROUND_HALF_SIZE; y++) {
                    float shade = ((x + y) & 1) == 0 ? 0.25f : 0.45f;
                    GL11.glColor3f(shade, shade, shade);
                    GL11.glVertex3f(x, y, 0f);
                    GL11.glVertex3f(x + 1, y, 0f);
                    GL11.glVertex3f(x + 1, y + 1, 0f);
                    GL11.glVertex3f(x, y + 1, 0f);
                }
            }
            GL11.glEnd();
        }

        private static void drawPost(float x, float y, float r, float g, float b) {
            float half = 0.15f;
            GL11.glBegin(GL_QUADS);
            GL11.glColor3f(r, g, b);
            GL11.glVertex3f(x - half, y - half, 0f);
            GL11.glVertex3f(x + half, y + half, 0f);
            GL11.glVertex3f(x + half, y + half, ViewCamera.LEVEL_HEIGHT);
            GL11.glVertex3f(x - half, y - half, ViewCamera.LEVEL_HEIGHT);
            GL11.glVertex3f(x - half, y + half, 0f);
            GL11.glVertex3f(x + half, y - half, 0f);
            GL11.glVertex3f(x + half, y - half, ViewCamera.LEVEL_HEIGHT);
            GL11.glVertex3f(x - half, y + half, ViewCamera.LEVEL_HEIGHT);
            GL11.glEnd();
        }

        private static void capture(int[] viewport, String fileName) {
            int width = viewport[2];
            int height = viewport[3];
            ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
            GL11.glPixelStorei(GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(viewport[0], viewport[1], width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    int at = (row * width + column) * 4;
                    int rgb = (pixels.get(at) & 0xFF) << 16 | (pixels.get(at + 1) & 0xFF) << 8 | (pixels.get(at + 2) & 0xFF);
                    image.setRGB(column, height - 1 - row, rgb);
                }
            }
            Path file = outputDirectory.resolve(fileName);
            try {
                Files.createDirectories(outputDirectory);
                ImageIO.write(image, "png", file.toFile());
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            Log.info("render check captured " + file + " (" + width + "x" + height + ")");
        }
    }
}
