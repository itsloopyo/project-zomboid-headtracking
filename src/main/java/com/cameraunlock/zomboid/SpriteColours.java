package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.GLCapabilities;
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
    private static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE_BINDING_2D = 0x8069, GL_TEXTURE_WIDTH = 0x1000, GL_TEXTURE_HEIGHT = 0x1001, GL_TEXTURE_COMPRESSED = 0x86A1;
    /** A compressed sheet is copied back in whole blocks of this many texels a side. */
    private static final int BLOCK = 4;
    private static final int GL_RGBA = 0x1908, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_PIXEL_PACK_BUFFER = 0x88EB, GL_PIXEL_PACK_BUFFER_BINDING = 0x88ED;
    private static final int GL_PACK_ROW_LENGTH = 0x0D02, GL_PACK_SKIP_ROWS = 0x0D03, GL_PACK_SKIP_PIXELS = 0x0D04, GL_PACK_ALIGNMENT = 0x0D05;

    private static final float[] ASKED = new float[0], UNPAINTED = new float[0];
    private static final ConcurrentHashMap<Texture, float[]> known = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Texture> asked = new ConcurrentLinkedQueue<>();
    private static final ArrayList<Texture> waiting = new ArrayList<>();
    /** The copy of the part of the sheet being read, and the memory it is in, kept while sprites go on being asked for and for a few seconds after. */
    private static ByteBuffer page;
    private static OffHeap.Block pageBlock;
    private static long pageUsedNanos;
    /** A walk past new houses asks for a sprite every few frames: the page is let go only once nothing has been read for this long. */
    private static final long PAGE_KEPT_NANOS = 5_000_000_000L;
    /** How many frames running a sheet with no size on the graphics card is waited for before its sprites are kept as painting nothing. */
    private static final int WAITED_FOR_A_SHEET = 240;
    private static int sizeless;
    /** Whether the graphics card copies back part of a sheet, asked once on the first reading. */
    private static boolean cardAsked, inParts, errorLeftLogged;
    /** OpenGL keeps at most one flag for each kind of error it has. */
    private static final int GL_ERROR_FLAGS = 8;
    private static final int GL_STREAM_READ = 0x88E1, GL_SYNC_GPU_COMMANDS_COMPLETE = 0x9117, GL_ALREADY_SIGNALED = 0x911A, GL_CONDITION_SATISFIED = 0x911C;
    /**
     * A copy of part of a sheet the graphics card has been asked to make into a buffer of its
     * own, fenced, and the sprites it is for. Copied straight into the PC's memory, the render
     * thread waited for the card to finish everything handed it before it: 8 to 20 ms a sheet
     * while driving past new houses. The copy is taken up once the card has passed the fence,
     * a frame or two later.
     */
    private static boolean copying, copyDropped;
    private static long copyFence;
    private static int copyFromX, copyFromY, copyAcross, copyDown, copyWidth, copyHeight;
    private static final ArrayList<Texture> copied = new ArrayList<>();
    /** The buffer copies are made into, kept for the session once made, and its size. Render thread. */
    private static int copyBuffer, copyBufferBytes;

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

    /**
     * Frees the page. Called by the logic thread too, as first person is switched off and no
     * more frames are read for: a copy still being made is dropped by the next frame drawn,
     * which is the thread that can let go of its fence.
     */
    static synchronized void letGoOfThePage() {
        copyDropped = copying;
        OffHeap.Block held = pageBlock;
        pageBlock = null;
        page = null;
        if (held != null) {
            held.free();
        }
    }

    /**
     * Reads the sprites asked for since the last frame. The game keeps many sprites on one
     * sheet, and every sprite waiting on one sheet is read from one copy of the part of it that
     * holds them all. With OpenGL 4.5 or ARB_get_texture_sub_image only that part is copied
     * back; without, the sheet whole, which is tens of megabytes. One sheet a frame. A frame in
     * which two players' pictures are drawn is still one, and the sheet is read for the first of them.
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
        if (!cardAsked) {
            GLCapabilities card = GL.getCapabilities();
            cardAsked = true;
            inParts = card.OpenGL45 || card.GL_ARB_get_texture_sub_image;
            if (!inParts) {
                Log.info("this graphics card has neither OpenGL 4.5 nor ARB_get_texture_sub_image: the colours of sprites are read from a copy of their whole sheet");
            }
        }
        pageUsedNanos = System.nanoTime();
        if (copying) {
            takeCopy();
            return;
        }
        // What OpenGL has flagged before anything here is asked of it is not this reading's: one flag a kind of error at most, and a lost context flags for ever.
        for (int flag = 0, error = GL11.glGetError(); flag < GL_ERROR_FLAGS && error != 0; flag++, error = GL11.glGetError()) {
            if (!errorLeftLogged) {
                errorLeftLogged = true;
                Log.info(String.format(Locale.ROOT, "sprite colours: OpenGL error 0x%X was left by the drawing before a sheet was read (said once)", error));
            }
        }
        int sheet = waiting.get(0).getID();
        int was = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
        int packBuffer = GL11.glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        GL11.glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL_PACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL_PACK_ALIGNMENT, 1);
        GL11.glBindTexture(GL_TEXTURE_2D, sheet);
        int width = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        // The part of the sheet copied, and its width: every sprite's place is read from it less its corner.
        int fromX = 0, fromY = 0, across = width, down = height, copyError = 0;
        if (width > 0 && height > 0) {
            if (inParts) {
                int left = width, top = height, right = 0, bottom = 0;
                for (int i = 0; i < waiting.size(); i++) {
                    Texture texture = waiting.get(i);
                    if (texture.getID() == sheet) {
                        place(texture, width, height);
                        left = Math.min(left, placed[0]);
                        top = Math.min(top, placed[1]);
                        right = Math.max(right, placed[2]);
                        bottom = Math.max(bottom, placed[3]);
                    }
                }
                fromX = Math.min(left, right);
                fromY = Math.min(top, bottom);
                if (GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_COMPRESSED) != 0) {
                    // OpenGL copies part of a compressed sheet only from a block's corner to a block's corner or the sheet's edge.
                    fromX = fromX / BLOCK * BLOCK;
                    fromY = fromY / BLOCK * BLOCK;
                    right = Math.min((right + BLOCK - 1) / BLOCK * BLOCK, width);
                    bottom = Math.min((bottom + BLOCK - 1) / BLOCK * BLOCK, height);
                }
                across = right - fromX;
                down = bottom - fromY;
            }
            int bytes = Math.max(across * down * 4, 1);
            if (copyBuffer == 0) {
                copyBuffer = GL15.glGenBuffers();
            }
            GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, copyBuffer);
            if (copyBufferBytes < bytes) {
                GL15.glBufferData(GL_PIXEL_PACK_BUFFER, bytes, GL_STREAM_READ);
                copyBufferBytes = bytes;
            }
            if (inParts) {
                if (across > 0 && down > 0) {
                    GL45.glGetTextureSubImage(sheet, 0, fromX, fromY, 0, across, down, 1, GL_RGBA, GL_UNSIGNED_BYTE, bytes, 0L);
                }
            } else {
                GL11.glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            }
            copyError = GL11.glGetError();
        }
        GL11.glBindTexture(GL_TEXTURE_2D, was);
        GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
        if (copyError != 0) {
            throw new IllegalStateException(String.format(Locale.ROOT, "OpenGL error 0x%X copying back %d by %d at %d, %d of the sprite sheet %d (%d by %d)", copyError, across, down,
                    fromX, fromY, sheet, width, height));
        }
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
        // The sprites of this sheet waiting now are the ones the copy is for: any asked for later may lie outside it, and wait for a copy of their own.
        int kept = 0;
        for (int i = 0; i < waiting.size(); i++) {
            Texture texture = waiting.get(i);
            if (texture.getID() == sheet) {
                copied.add(texture);
            } else {
                waiting.set(kept++, texture);
            }
        }
        while (waiting.size() > kept) {
            waiting.remove(waiting.size() - 1);
        }
        copyFromX = fromX;
        copyFromY = fromY;
        copyAcross = across;
        copyDown = down;
        copyWidth = width;
        copyHeight = height;
        if (width > 0 && height > 0) {
            copyFence = GL32.glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            copying = true;
        } else {
            // A sheet that never had a size is kept as painting nothing, as it was: there is no copy to wait for.
            readCopied();
        }
    }

    /** Takes up the copy being made once the graphics card has made it, and reads its sprites. */
    private static void takeCopy() {
        if (copyDropped) {
            GL32.glDeleteSync(copyFence);
            copying = copyDropped = false;
            waiting.addAll(copied);
            copied.clear();
            return;
        }
        int waited = GL32.glClientWaitSync(copyFence, 0, 0L);
        if (waited != GL_ALREADY_SIGNALED && waited != GL_CONDITION_SATISFIED) {
            return;
        }
        GL32.glDeleteSync(copyFence);
        copying = false;
        int bytes = Math.max(copyAcross * copyDown * 4, 1);
        if (page == null || page.capacity() < bytes) {
            OffHeap.Block held = pageBlock;
            pageBlock = OffHeap.take(bytes);
            page = pageBlock.bytes;
            if (held != null) {
                held.free();
            }
        }
        page.clear();
        page.limit(copyAcross * copyDown * 4);
        int packBuffer = GL11.glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, copyBuffer);
        GL15.glGetBufferSubData(GL_PIXEL_PACK_BUFFER, 0L, page);
        GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
        readCopied();
    }

    /** Reads every sprite the copy was made for out of the page. */
    private static void readCopied() {
        int fromX = copyFromX, fromY = copyFromY, across = copyAcross, width = copyWidth, height = copyHeight;
        for (int i = 0; i < copied.size(); i++) {
            Texture texture = copied.get(i);
            float[] colour = new float[WallSides.MEAN + 3];
            place(texture, width, height);
            int left = placed[0] - fromX, top = placed[1] - fromY, right = placed[2] - fromX, bottom = placed[3] - fromY;
            boolean painted = WallSides.usualColour(page, across, left, top, right, bottom, colour);
            if (painted) {
                WallSides.meanColour(page, across, left, top, right, bottom, colour);
            }
            known.put(texture, painted ? colour : UNPAINTED);
            Windows.read(texture, page, across, left, top, right, bottom);
            Drapes.read(texture, page, across, left, top, right, bottom);
            Doors.read(texture, page, across, left, top, right, bottom);
            Papers.read(texture, page, across, left, top, right, bottom);
            WallTiles.read(texture, page, across, left, top, right, bottom);
            Tiles.read(texture, page, across, left, top, right, bottom);
            Solids.read(texture, page, across, left, top, right, bottom);
            Flats.read(texture, page, across, left, top, right, bottom);
            Rugs.read(texture, page, across, left, top, right, bottom);
            Trims.read(texture, page, across, left, top, right, bottom);
            RoadPaint.read(texture, page, across, left, top, right, bottom);
            Masonry.read(texture, page, across, left, top, right, bottom);
        }
        copied.clear();
    }

    /** Where a sprite lies on its sheet, in texels: left, top, right and bottom, into placed. */
    private static void place(Texture texture, int width, int height) {
        placed[0] = Math.clamp(Math.round(Math.min(texture.getXStart(), texture.getXEnd()) * width), 0, width);
        placed[2] = Math.clamp(Math.round(Math.max(texture.getXStart(), texture.getXEnd()) * width), 0, width);
        placed[1] = Math.clamp(Math.round(Math.min(texture.getYStart(), texture.getYEnd()) * height), 0, height);
        placed[3] = Math.clamp(Math.round(Math.max(texture.getYStart(), texture.getYEnd()) * height), 0, height);
    }
    private static final int[] placed = new int[4];
}
