package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
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
    private static final int GL_RGBA = 0x1908, GL_RGBA8 = 0x8058, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_NEAREST = 0x2600;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_DRAW_FRAMEBUFFER = 0x8CA9, GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0, GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6, GL_READ_FRAMEBUFFER_BINDING = 0x8CAA, GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_ACTIVE_TEXTURE = 0x84E0, GL_TEXTURE0 = 0x84C0, GL_ALL_ATTRIB_BITS = 0x000FFFFF;
    private static final int GL_SCISSOR_TEST = 0x0C11, GL_BLEND = 0x0BE2, GL_DEPTH_TEST = 0x0B71, GL_STENCIL_TEST = 0x0B90;
    private static final int GL_CULL_FACE = 0x0B44, GL_ALPHA_TEST = 0x0BC0, GL_FRAMEBUFFER_SRGB = 0x8DB9, GL_TRIANGLE_STRIP = 0x0005;
    private static final int GL_PIXEL_PACK_BUFFER = 0x88EB, GL_PIXEL_PACK_BUFFER_BINDING = 0x88ED;
    private static final int GL_PACK_ROW_LENGTH = 0x0D02, GL_PACK_SKIP_ROWS = 0x0D03, GL_PACK_SKIP_PIXELS = 0x0D04, GL_PACK_ALIGNMENT = 0x0D05;

    private static final float[] ASKED = new float[0], UNPAINTED = new float[0];
    private static final ConcurrentHashMap<Texture, float[]> known = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Ask> asked = new ConcurrentLinkedQueue<>();
    private static final ArrayList<Ask> waiting = new ArrayList<>();
    /** Where the build asking for sprites now stands, the middle of its chunk, and whose view it is built for: no place (NaN) outside a chunk's build. Logic thread. */
    private static volatile int askView;
    private static volatile float askX = Float.NaN, askY = Float.NaN;
    /** Where each view's eye was as its last frame was drawn, x then y: no place (NaN) before its first. Render thread. */
    private static final float[] eyes = new float[View.COUNT * 2];

    static {
        Arrays.fill(eyes, Float.NaN);
    }

    /**
     * A sprite asked to be read, by the build of the chunk whose middle is at (x, y) for a view,
     * from no place (NaN), or after everything else (infinite): see next.
     */
    record Ask(Texture texture, int view, float x, float y) {
        /** How far the place it was asked from is from its view's eye, squared: none from no place, or while the eye is not known. */
        float away(float[] eyes) {
            if (Float.isNaN(x) || Float.isNaN(eyes[view * 2])) {
                return 0f;
            }
            float dx = x - eyes[view * 2], dy = y - eyes[view * 2 + 1];
            return dx * dx + dy * dy;
        }
    }
    /** The copy of the part of the sheet being read, and the memory it is in, kept while sprites go on being asked for and for a few seconds after. */
    private static ByteBuffer page;
    private static OffHeap.Block pageBlock;
    private static long pageUsedNanos;
    /** A walk past new houses asks for a sprite every few frames: the page is let go only once nothing has been read for this long. */
    private static final long PAGE_KEPT_NANOS = 5_000_000_000L;
    /** How many frames running a sheet with no size on the graphics card is waited for before its sprites are kept as painting nothing. */
    private static final int WAITED_FOR_A_SHEET = 240;
    private static int sizeless;
    /**
     * Whether the shader that unpacks the part of a sheet read into a target of its own linked
     * (see copying), else whether the graphics card copies back part of a sheet, asked once on
     * the first reading.
     */
    private static boolean cardAsked, unpacks, inParts, errorLeftLogged;
    /** OpenGL keeps at most one flag for each kind of error it has. */
    private static final int GL_ERROR_FLAGS = 8;
    private static final int GL_STREAM_READ = 0x88E1, GL_SYNC_GPU_COMMANDS_COMPLETE = 0x9117, GL_ALREADY_SIGNALED = 0x911A, GL_CONDITION_SATISFIED = 0x911C;
    /**
     * A copy of part of a sheet the graphics card has been asked to make into a buffer of its
     * own, fenced, and the sprites it is for. Copied straight into the PC's memory, the render
     * thread waited for the card to finish everything handed it before it: 8 to 20 ms a sheet
     * while driving past new houses. The copy is taken up once the card has passed the fence,
     * a frame or two later.
     *
     * The game's sheets are compressed (DXT5) when its texture compression option is on. A
     * compressed sheet copied back as it is stored had the driver unpack it on the render thread
     * before the call returned: 5 ms for a median part, up to 49 ms for a whole 1024 sheet. So
     * the part is drawn texel for texel into a target of four bytes a texel, unpacked by the card
     * as when the sheet is drawn, and that target is copied into the buffer.
     */
    private static boolean copying, copyDropped;
    private static long copyFence;
    private static int copyFromX, copyFromY, copyAcross, copyDown, copyWidth, copyHeight;
    private static final ArrayList<Ask> copied = new ArrayList<>();
    /** The buffer copies are made into, kept while sprites go on being asked for as the page is, and its size. Render thread. */
    private static int copyBuffer, copyBufferBytes;
    /** What the part of the sheet is drawn into before it is copied, kept as the buffer is, and its size. Render thread. */
    private static int unpacked, unpackedTarget, unpackedWide, unpackedHigh;
    /** Read through with no mipmaps: under its own min filter a sheet that asks for mipmaps it lacks is incomplete, and reads as black. */
    private static int unpackSampler;
    private static final String UNPACK_VERTEX = """
            #version 130
            void main() {
                gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);
            }
            """;
    private static final String UNPACK_FRAGMENT = """
            #version 130
            uniform sampler2D uSheet;
            uniform ivec2 uFrom;

            void main() {
                gl_FragColor = texelFetch(uSheet, ivec2(gl_FragCoord.xy) + uFrom, 0);
            }
            """;
    static final SmallProgram unpack = new SmallProgram("sprite sheet unpacking",
            "sprite sheets are copied back as stored, which waits for the driver to unpack a compressed one", UNPACK_VERTEX, UNPACK_FRAGMENT);
    /** Whether the page was let go and no frame has read since: what the graphics card holds for it is then given back by giveBackIdle. */
    private static boolean letGo;

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
                asked.add(new Ask(texture, askView, askX, askY));
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
        asked.add(new Ask(texture, askView, askX, askY));
    }

    /** Says where the build that asks for sprites from now on stands, for the view: NaN for no place. Logic thread. */
    static void askingFrom(int view, float x, float y) {
        askView = view;
        askX = x;
        askY = y;
    }

    /**
     * The waiting sprite whose sheet is read next: the one asked for nearest its view's eye, and
     * the first asked of those as near. At eight frames a second hundreds of sprites on dozens of
     * sheets wait, a sheet read every other frame, and read as they were asked the room the eye
     * had just come into waited ten seconds behind the street it had left, its wainscots, which a
     * wall's build leaves out until they are read, not drawn at all.
     */
    static int next(ArrayList<Ask> waiting, float[] eyes) {
        int best = 0;
        float least = Float.POSITIVE_INFINITY;
        for (int i = 0; i < waiting.size(); i++) {
            float away = waiting.get(i).away(eyes);
            if (away < least) {
                least = away;
                best = i;
            }
        }
        return best;
    }

    /**
     * Frees the page. Called by the logic thread too, as first person is switched off and no
     * more frames are read for: a copy still being made is dropped by the next frame drawn,
     * which is the thread that can let go of its fence.
     */
    static synchronized void letGoOfThePage() {
        letGo = true;
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
     * holds them all. One sheet a frame. A frame in which two players' pictures are drawn is
     * still one, and the sheet is read for the first of them.
     *
     * @param view the number of the player whose picture is about to be drawn
     * @param eyeX where that player's eye is, across the map
     * @param eyeY and down it
     */
    static synchronized void read(int view, float eyeX, float eyeY) {
        eyes[view * 2] = eyeX;
        eyes[view * 2 + 1] = eyeY;
        if (!Rounds.DRAWS.begin(view)) {
            return;
        }
        letGo = false;
        for (Ask ask = asked.poll(); ask != null; ask = asked.poll()) {
            waiting.add(ask);
        }
        if (copying) {
            pageUsedNanos = System.nanoTime();
            takeCopy();
            return;
        }
        if (waiting.isEmpty()) {
            if (System.nanoTime() - pageUsedNanos > PAGE_KEPT_NANOS) {
                letGoOfThePage();
                deleteCopyBuffer();
            }
            return;
        }
        if (!cardAsked) {
            GLCapabilities card = GL.getCapabilities();
            cardAsked = true;
            int previous = GL11.glGetInteger(GL_CURRENT_PROGRAM);
            unpacks = unpack.use();
            Program.useOther(previous);
            inParts = card.OpenGL45 || card.GL_ARB_get_texture_sub_image;
            if (unpacks) {
                Log.info("sprite colours: each part of a sprite sheet read is unpacked by the graphics card into a target of its own and copied back from there");
            } else {
                Log.info("the driver refused the shader that unpacks sprite sheets: the colours of sprites are copied back as the sheet is stored, which waits for the driver"
                        + " to unpack a compressed sheet");
            }
            if (!unpacks && !inParts) {
                Log.info("this graphics card has neither OpenGL 4.5 nor ARB_get_texture_sub_image: the colours of sprites are read from a copy of their whole sheet");
            }
        }
        pageUsedNanos = System.nanoTime();
        // What OpenGL has flagged before anything here is asked of it is not this reading's: one flag a kind of error at most, and a lost context flags for ever.
        for (int flag = 0, error = GL11.glGetError(); flag < GL_ERROR_FLAGS && error != 0; flag++, error = GL11.glGetError()) {
            if (!errorLeftLogged) {
                errorLeftLogged = true;
                Log.info(String.format(Locale.ROOT, "sprite colours: OpenGL error 0x%X was left by the drawing before a sheet was read (said once)", error));
            }
        }
        int sheet = waiting.get(next(waiting, eyes)).texture().getID();
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
            int left = width, top = height, right = 0, bottom = 0;
            for (int i = 0; i < waiting.size(); i++) {
                Texture texture = waiting.get(i).texture();
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
            if (!unpacks && !inParts) {
                fromX = fromY = 0;
                right = width;
                bottom = height;
            } else if (!unpacks && GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_COMPRESSED) != 0) {
                // OpenGL copies part of a compressed sheet only from a block's corner to a block's corner or the sheet's edge.
                fromX = fromX / BLOCK * BLOCK;
                fromY = fromY / BLOCK * BLOCK;
                right = Math.min((right + BLOCK - 1) / BLOCK * BLOCK, width);
                bottom = Math.min((bottom + BLOCK - 1) / BLOCK * BLOCK, height);
            }
            across = right - fromX;
            down = bottom - fromY;
            int bytes = Math.max(across * down * 4, 1);
            if (copyBuffer == 0) {
                copyBuffer = GL15.glGenBuffers();
            }
            GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, copyBuffer);
            if (copyBufferBytes < bytes) {
                GL15.glBufferData(GL_PIXEL_PACK_BUFFER, bytes, GL_STREAM_READ);
                copyBufferBytes = bytes;
            }
            if (across > 0 && down > 0) {
                if (unpacks) {
                    unpackInto(sheet, fromX, fromY, across, down);
                } else if (inParts) {
                    GL45.glGetTextureSubImage(sheet, 0, fromX, fromY, 0, across, down, 1, GL_RGBA, GL_UNSIGNED_BYTE, bytes, 0L);
                } else {
                    GL11.glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
                }
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
            int kept = 0, behind = waiting.size();
            for (int i = 0; i < behind; i++) {
                Ask ask = waiting.get(i);
                if (ask.texture().getID() == sheet) {
                    waiting.add(new Ask(ask.texture(), ask.view(), Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY));
                } else {
                    waiting.set(kept++, ask);
                }
            }
            waiting.subList(kept, behind).clear();
            return;
        }
        // The sprites of this sheet waiting now are the ones the copy is for: any asked for later may lie outside it, and wait for a copy of their own.
        int kept = 0;
        for (int i = 0; i < waiting.size(); i++) {
            Ask ask = waiting.get(i);
            if (ask.texture().getID() == sheet) {
                copied.add(ask);
            } else {
                waiting.set(kept++, ask);
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

    /**
     * Gives back the buffer copies are made into, and the fence of a copy still being made, once
     * the page has been let go with first person and no frame has read since. Render thread.
     */
    static synchronized void giveBackIdle() {
        if (!letGo) {
            return;
        }
        if (copying) {
            // letGoOfThePage marked it dropped: its fence goes, and its sprites wait to be read again.
            takeCopy();
        }
        int bytes = copyBufferBytes;
        if (copyBuffer != 0) {
            deleteCopyBuffer();
            Log.info(String.format(Locale.ROOT, "sprite colours: the %d byte copy buffer is given back to the graphics card", bytes));
        }
    }

    private static void deleteCopyBuffer() {
        if (copyBuffer != 0) {
            GL15.glDeleteBuffers(copyBuffer);
            copyBuffer = 0;
            copyBufferBytes = 0;
        }
        if (unpacked != 0) {
            GL30.glDeleteFramebuffers(unpackedTarget);
            GL11.glDeleteTextures(unpacked);
            unpacked = unpackedTarget = unpackedWide = unpackedHigh = 0;
        }
        if (unpackSampler != 0) {
            GL33.glDeleteSamplers(unpackSampler);
            unpackSampler = 0;
        }
    }

    /**
     * Draws across by down texels of the sheet from (fromX, fromY) into the unpacked target and
     * copies them into the pixel pack buffer bound, the sheet's first row first, as they lie on
     * it. The drawing state, bindings and program are left as they were found. Called from the
     * picture's frame, which has bound no vertex array, array buffer or sampler (Pipeline.draw).
     */
    private static void unpackInto(int sheet, int fromX, int fromY, int across, int down) {
        int drawTo = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFrom = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int previous = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        int unit = GL11.glGetInteger(GL_ACTIVE_TEXTURE);
        int remembered = Texture.lastTextureID;
        GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
        GL13.glActiveTexture(GL_TEXTURE0);
        int onUnit = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            if (unpackSampler == 0) {
                unpackSampler = GL33.glGenSamplers();
                GL33.glSamplerParameteri(unpackSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                GL33.glSamplerParameteri(unpackSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            }
            GL33.glBindSampler(0, unpackSampler);
            if (unpackedWide < across || unpackedHigh < down) {
                if (unpacked != 0) {
                    GL30.glDeleteFramebuffers(unpackedTarget);
                    GL11.glDeleteTextures(unpacked);
                }
                unpackedWide = Math.max(unpackedWide, across);
                unpackedHigh = Math.max(unpackedHigh, down);
                unpacked = GL11.glGenTextures();
                GL11.glBindTexture(GL_TEXTURE_2D, unpacked);
                GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, unpackedWide, unpackedHigh, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
                GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                unpackedTarget = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL_FRAMEBUFFER, unpackedTarget);
                GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, unpacked, 0);
                int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
                if (status != GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException(String.format(Locale.ROOT, "the driver would not make the %d by %d target sprite sheets are unpacked into (status 0x%X)",
                            unpackedWide, unpackedHigh, status));
                }
            }
            if (!unpack.use()) {
                throw new IllegalStateException("the driver refused the shader that unpacks sprite sheets, so no sprite's colours can be read");
            }
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, unpackedTarget);
            GL11.glDisable(GL_SCISSOR_TEST);
            GL11.glDisable(GL_DEPTH_TEST);
            GL11.glDisable(GL_STENCIL_TEST);
            GL11.glDisable(GL_CULL_FACE);
            GL11.glDisable(GL_ALPHA_TEST);
            GL11.glDisable(GL_BLEND);
            GL11.glDisable(GL_FRAMEBUFFER_SRGB);
            GL11.glColorMask(true, true, true, true);
            GL11.glViewport(0, 0, across, down);
            GL11.glBindTexture(GL_TEXTURE_2D, sheet);
            GL20.glUniform1i(unpack.at("uSheet"), 0);
            GL20.glUniform2i(unpack.at("uFrom"), fromX, fromY);
            GL11.glBegin(GL_TRIANGLE_STRIP);
            GL11.glVertex2f(-1f, -1f);
            GL11.glVertex2f(1f, -1f);
            GL11.glVertex2f(-1f, 1f);
            GL11.glVertex2f(1f, 1f);
            GL11.glEnd();
            GL11.glReadPixels(0, 0, across, down, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
        } finally {
            GL33.glBindSampler(0, 0);
            GL11.glBindTexture(GL_TEXTURE_2D, onUnit);
            GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawTo);
            GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, readFrom);
            GL11.glPopAttrib();
            GL13.glActiveTexture(unit);
            Program.useOther(previous);
            Texture.lastTextureID = remembered;
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
            Texture texture = copied.get(i).texture();
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
            Casings.read(texture, page, across, left, top, right, bottom);
            Papers.read(texture, page, across, left, top, right, bottom);
            WallTiles.read(texture, page, across, left, top, right, bottom);
            Tiles.read(texture, page, across, left, top, right, bottom);
            Solids.read(texture, page, across, left, top, right, bottom);
            SeeThrough.read(texture, page, across, left, top, right, bottom);
            Flats.read(texture, page, across, left, top, right, bottom);
            Rugs.read(texture, page, across, left, top, right, bottom);
            Trims.read(texture, page, across, left, top, right, bottom);
            RoadPaint.read(texture, page, across, left, top, right, bottom);
            Masonry.read(texture, page, across, left, top, right, bottom);
            Stains.read(texture, page, across, left, top, right, bottom);
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
