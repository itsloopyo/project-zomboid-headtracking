package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import zombie.core.textures.Texture;

/**
 * The colour most of each sprite is painted in.
 *
 * A sprite's pixels are held by OpenGL and can only be read on the thread that draws, while
 * the scene is built on the game's own. So the build asks, and is told nothing until the next
 * frame drawn has read the sprite; it asks again every time it builds, and what it is told
 * then is kept for good.
 */
final class SpriteColours {
    private static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE_BINDING_2D = 0x8069, GL_TEXTURE_WIDTH = 0x1000, GL_TEXTURE_HEIGHT = 0x1001;
    private static final int GL_RGBA = 0x1908, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_PIXEL_PACK_BUFFER = 0x88EB, GL_PIXEL_PACK_BUFFER_BINDING = 0x88ED;
    private static final int GL_PACK_ROW_LENGTH = 0x0D02, GL_PACK_SKIP_ROWS = 0x0D03, GL_PACK_SKIP_PIXELS = 0x0D04, GL_PACK_ALIGNMENT = 0x0D05;

    private static final float[] ASKED = new float[0], UNPAINTED = new float[0];
    private static final ConcurrentHashMap<Texture, float[]> known = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Texture> asked = new ConcurrentLinkedQueue<>();
    private static final ArrayList<Texture> waiting = new ArrayList<>();
    /** The copy of the sheet being read, and the memory it is in, kept while sprites go on being asked for and for a few seconds after. */
    private static ByteBuffer page;
    private static OffHeap.Block pageBlock;
    private static long pageUsedNanos;
    /** A walk past new houses asks for a sprite every few frames: the page is let go only once nothing has been read for this long. */
    private static final long PAGE_KEPT_NANOS = 5_000_000_000L;
    /** How many frames running a sheet with no size on the graphics card is waited for before its sprites are kept as painting nothing. */
    private static final int WAITED_FOR_A_SHEET = 240;
    private static int sizeless;

    private SpriteColours() {}

    /**
     * The sprite's usual red, green and blue and its usual brightness, each from 0 to 1, and
     * after them, from WallSides.MEAN, the mean red, green and blue of all it paints. Null
     * until a frame has been drawn since it was first asked for, and for a sprite with nothing
     * painted on it.
     */
    static float[] of(Texture texture) {
        float[] colour = known.get(texture);
        if (colour == null) {
            if (known.putIfAbsent(texture, ASKED) == null) {
                asked.add(texture);
            }
            return null;
        }
        return colour.length == 0 ? null : colour;
    }

    /** Whether the sprite has still to be read: of() gives null for it, and may not once a frame has been drawn. */
    static boolean unread(Texture texture) {
        float[] colour = known.get(texture);
        return colour == null || colour == ASKED;
    }

    /** Has the sprite's sheet read on the next frame drawn though its colour is known: for Windows, which measures a sprite from the same copy. */
    static void readAgain(Texture texture) {
        asked.add(texture);
    }

    /** Frees the page. Called by the logic thread too, as first person is switched off and no more frames are read for. */
    static synchronized void letGoOfThePage() {
        OffHeap.Block held = pageBlock;
        pageBlock = null;
        page = null;
        if (held != null) {
            held.free();
        }
    }

    /**
     * Reads the sprites asked for since the last frame. The game keeps many sprites on one
     * sheet, and OpenGL hands back a sheet whole, so every sprite waiting on one sheet is read
     * from one copy of it. One sheet a frame: a sheet is tens of megabytes to copy, and a
     * street's walls are on a handful of them. A frame in which two players' pictures are
     * drawn is still one, and the sheet is read for the first of them.
     *
     * @param view the number of the player whose picture is about to be drawn
     */
    static synchronized void read(int view) {
        if (!Rounds.DRAWS.begin(view)) {
            return;
        }
        for (Texture texture = asked.poll(); texture != null; texture = asked.poll()) {
            waiting.add(texture);
        }
        if (waiting.isEmpty()) {
            if (System.nanoTime() - pageUsedNanos > PAGE_KEPT_NANOS) {
                letGoOfThePage();
            }
            return;
        }
        pageUsedNanos = System.nanoTime();
        int sheet = waiting.get(0).getID();
        int was = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
        // With a buffer bound to pack into, OpenGL would write the sheet into that buffer and
        // take the address given here as a place in it.
        int packBuffer = GL11.glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        GL11.glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL_PACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL_PACK_ALIGNMENT, 1);
        GL11.glBindTexture(GL_TEXTURE_2D, sheet);
        int width = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        if (width > 0 && height > 0) {
            if (page == null || page.capacity() < width * height * 4) {
                letGoOfThePage();
                pageBlock = OffHeap.take(width * height * 4);
                page = pageBlock.bytes;
            }
            GL11.glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, page);
            if (FirstPerson.TRACE) {
                Log.info(String.format(java.util.Locale.ROOT, "sprite colours: a sheet of %d by %d copied back in %.2f ms", width, height, (System.nanoTime() - pageUsedNanos) / 1e6));
            }
        }
        GL11.glBindTexture(GL_TEXTURE_2D, was);
        GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
        if (width > 0 && height > 0) {
            sizeless = 0;
        } else if (++sizeless <= WAITED_FOR_A_SHEET) {
            // The sheet is not on the graphics card yet. Its sprites wait behind the others: read now, each would be kept as painting nothing.
            for (int i = waiting.size() - 1; i >= 0; i--) {
                if (waiting.get(i).getID() == sheet) {
                    waiting.add(waiting.remove(i));
                }
            }
            return;
        }

        int kept = 0;
        for (int i = 0; i < waiting.size(); i++) {
            Texture texture = waiting.get(i);
            if (texture.getID() != sheet) {
                waiting.set(kept++, texture);
                continue;
            }
            float[] colour = new float[WallSides.MEAN + 3];
            int left = Math.clamp(Math.round(Math.min(texture.getXStart(), texture.getXEnd()) * width), 0, width);
            int right = Math.clamp(Math.round(Math.max(texture.getXStart(), texture.getXEnd()) * width), 0, width);
            int top = Math.clamp(Math.round(Math.min(texture.getYStart(), texture.getYEnd()) * height), 0, height);
            int bottom = Math.clamp(Math.round(Math.max(texture.getYStart(), texture.getYEnd()) * height), 0, height);
            boolean painted = WallSides.usualColour(page, width, left, top, right, bottom, colour);
            if (painted) {
                WallSides.meanColour(page, width, left, top, right, bottom, colour);
            }
            known.put(texture, painted ? colour : UNPAINTED);
            Windows.read(texture, page, width, left, top, right, bottom);
            Drapes.read(texture, page, width, left, top, right, bottom);
            Doors.read(texture, page, width, left, top, right, bottom);
            Papers.read(texture, page, width, left, top, right, bottom);
            Tiles.read(texture, page, width, left, top, right, bottom);
            Solids.read(texture, page, width, left, top, right, bottom);
            Flats.read(texture, page, width, left, top, right, bottom);
            RoadPaint.read(texture, page, width, left, top, right, bottom);
            Masonry.read(texture, page, width, left, top, right, bottom);
        }
        while (waiting.size() > kept) {
            waiting.remove(waiting.size() - 1);
        }
    }
}
