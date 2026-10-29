package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelInstanceTextureCreator;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.core.textures.SmartTexture;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureCombiner;

/**
 * Dev only, with cameraunlock.zomboid.checkComposites set: whether a composite made while the
 * render thread waits for the frame (WaitingComposites) comes out as it would where the game
 * makes it. As the frame's draw comes to the model of one made in the wait
 * (ModelSlotRenderData.checkReady), its creator is marked unmade, and the game's own check makes
 * it again there from the same steps, as it does without the mod. Each skin made in the wait is
 * read back before, and the one made again after, and the two are compared byte for byte. The
 * draw wears the one made again, and the one made in the wait is handed back to the combiner,
 * which nothing else holds then. See DevPatches.
 */
public final class CompositeCheck {
    private CompositeCheck() {}

    /** GL_TEXTURE_2D, its binding, GL_TEXTURE_WIDTH, GL_TEXTURE_HEIGHT, GL_TEXTURE_INTERNAL_FORMAT, GL_RGBA and GL_UNSIGNED_BYTE. */
    private static final int TEXTURE_2D = 0x0DE1, TEXTURE_BINDING_2D = 0x8069, TEXTURE_WIDTH = 0x1000, TEXTURE_HEIGHT = 0x1001, TEXTURE_INTERNAL_FORMAT = 0x1003,
            RGBA = 0x1908, UNSIGNED_BYTE = 0x1401;
    /** GL_PIXEL_PACK_BUFFER and its binding, GL_CLIENT_PIXEL_STORE_BIT, and the pack state that shapes a read. */
    private static final int PIXEL_PACK_BUFFER = 0x88EB, PIXEL_PACK_BUFFER_BINDING = 0x88ED, CLIENT_PIXEL_STORE_BIT = 0x1, PACK_ALIGNMENT = 0x0D05,
            PACK_ROW_LENGTH = 0x0D02, PACK_SKIP_ROWS = 0x0D03, PACK_SKIP_PIXELS = 0x0D04;
    /** How many differences are written out whole: past these the toll counts them. */
    private static final int TOLD = 5;

    private static final Field RENDERED = renderedField();

    private static Field renderedField() {
        try {
            Field rendered = ModelInstanceTextureCreator.class.getDeclaredField("rendered");
            rendered.setAccessible(true);
            return rendered;
        } catch (NoSuchFieldException failure) {
            throw new IllegalStateException("composite check: the game's ModelInstanceTextureCreator has no field rendered", failure);
        }
    }

    /** One skin's pixels as the card holds them. */
    private record Picture(int width, int height, int format, byte[] bytes) {}

    /** A skin as the wait made it, kept until the draw has made it again. */
    private record Kept(Texture made, Picture picture) {}

    /** Made in a wait and not yet come to by the draw: added on the render thread, and let go of on the logic thread as the game takes one back. */
    private static final Set<ModelInstanceTextureCreator> madeInTheWait = ConcurrentHashMap.newKeySet();

    // Render thread.
    private static boolean wasRendered;
    private static int checking;
    private static ModelSlotRenderData remaking;
    private static final IdentityHashMap<SmartTexture, Kept> kept = new IdentityHashMap<>();
    private static int formatTold = -1, differencesTold;
    /** What a skin is read back into, as large as the largest read so far. */
    private static ByteBuffer read = ByteBuffer.allocateDirect(0);

    private static final AtomicLong remade = new AtomicLong(), compared = new AtomicLong(), same = new AtomicLong(), differed = new AtomicLong(),
            remadeUnready = new AtomicLong(), otherSkins = new AtomicLong();

    /** Called as ModelInstanceTextureCreator.render begins. Render thread. */
    public static void making(ModelInstanceTextureCreator creator) {
        wasRendered = creator.isRendered();
    }

    /** Called on every way out of ModelInstanceTextureCreator.render. Render thread. */
    public static void made(ModelInstanceTextureCreator creator) {
        // Outside the draw's check of a model the game makes none: this one was made in the wait.
        if (checking == 0 && !wasRendered && creator.isRendered()) {
            madeInTheWait.add(creator);
        }
    }

    /** Called as ModelInstanceTextureCreator.postRender begins, on the logic thread, as the game takes it back into its pool. */
    public static void released(ModelInstanceTextureCreator creator) {
        madeInTheWait.remove(creator);
    }

    /** Called as ModelSlotRenderData.checkReady begins, as the frame's draw comes to a model. Render thread. */
    public static void checking(ModelSlotRenderData data) {
        checking++;
        ModelInstanceTextureCreator creator = data.textureCreator;
        if (checking > 1 || creator == null || !madeInTheWait.remove(creator)) {
            return;
        }
        remaking = data;
        remade.incrementAndGet();
        for (int i = 0; i < data.modelData.size(); i++) {
            ModelInstanceRenderData part = data.modelData.get(i);
            if (part.modelInstance.tex instanceof SmartTexture skin && skin.result != null && !kept.containsKey(skin)) {
                kept.put(skin, new Kept(skin.result, picture(skin.result)));
            }
        }
        try {
            RENDERED.setBoolean(creator, false);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("composite check: a composite could not be marked unmade", failure);
        }
    }

    /** Called on every way out of ModelSlotRenderData.checkReady. Render thread. */
    public static void checked(ModelSlotRenderData data) {
        checking--;
        if (data != remaking) {
            return;
        }
        remaking = null;
        try {
            if (!data.textureCreator.isRendered()) {
                // Left as the wait made it: the draw wears that until the game makes it again.
                remadeUnready.incrementAndGet();
                return;
            }
            for (Map.Entry<SmartTexture, Kept> each : kept.entrySet()) {
                Texture now = each.getKey().result;
                Kept before = each.getValue();
                if (now == before.made) {
                    otherSkins.incrementAndGet();
                    continue;
                }
                compare(before.picture, picture(now));
                TextureCombiner.instance.releaseTexture(before.made);
            }
        } finally {
            kept.clear();
        }
    }

    private static void compare(Picture wait, Picture draw) {
        compared.incrementAndGet();
        if (wait.width == draw.width && wait.height == draw.height && wait.format == draw.format && Arrays.equals(wait.bytes, draw.bytes)) {
            same.incrementAndGet();
            return;
        }
        differed.incrementAndGet();
        if (differencesTold == TOLD) {
            return;
        }
        differencesTold++;
        int length = Math.min(wait.bytes.length, draw.bytes.length), differing = 0, most = 0;
        for (int i = 0; i < length; i++) {
            int by = Math.abs((wait.bytes[i] & 0xFF) - (draw.bytes[i] & 0xFF));
            if (by != 0) {
                differing++;
                most = Math.max(most, by);
            }
        }
        Log.info(String.format(Locale.ROOT, "composite check: made in the wait %dx%d as 0x%X, made again in the draw %dx%d as 0x%X: %d bytes differ, the first at %d, by at most %d",
                wait.width, wait.height, wait.format, draw.width, draw.height, draw.format, differing, Arrays.mismatch(wait.bytes, draw.bytes), most));
    }

    /** The texture's pixels read back whole, with the GL state the read touches put back. Render thread. */
    private static Picture picture(Texture texture) {
        int bound = GL11.glGetInteger(TEXTURE_BINDING_2D), packBuffer = GL11.glGetInteger(PIXEL_PACK_BUFFER_BINDING);
        GL11.glPushClientAttrib(CLIENT_PIXEL_STORE_BIT);
        try {
            GL15.glBindBuffer(PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(PACK_ALIGNMENT, 4);
            GL11.glPixelStorei(PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(PACK_SKIP_PIXELS, 0);
            GL11.glBindTexture(TEXTURE_2D, texture.getID());
            int width = GL11.glGetTexLevelParameteri(TEXTURE_2D, 0, TEXTURE_WIDTH), height = GL11.glGetTexLevelParameteri(TEXTURE_2D, 0, TEXTURE_HEIGHT),
                    format = GL11.glGetTexLevelParameteri(TEXTURE_2D, 0, TEXTURE_INTERNAL_FORMAT);
            if (width <= 0 || height <= 0) {
                throw new IllegalStateException("composite check: the skin " + texture.getName() + " has no picture on the card");
            }
            if (format != formatTold) {
                formatTold = format;
                Log.info(String.format(Locale.ROOT, "composite check: skins are held as 0x%X and read back as RGBA bytes", format));
            }
            int size = width * height * 4;
            if (read.capacity() < size) {
                read = ByteBuffer.allocateDirect(size);
            }
            read.clear();
            GL11.glGetTexImage(TEXTURE_2D, 0, RGBA, UNSIGNED_BYTE, read);
            byte[] bytes = new byte[size];
            read.get(bytes);
            return new Picture(width, height, format, bytes);
        } finally {
            GL11.glBindTexture(TEXTURE_2D, bound);
            GL15.glBindBuffer(PIXEL_PACK_BUFFER, packBuffer);
            GL11.glPopClientAttrib();
        }
    }

    /** For the rig: what the check has come to since this was last asked. Logic thread. */
    static String tolls() {
        return String.format(Locale.ROOT, "made in the wait and again in the draw %d, skins compared %d, the same byte for byte %d, differed %d, made again unready %d, skins on the model it does not make %d",
                remade.getAndSet(0), compared.getAndSet(0), same.getAndSet(0), differed.getAndSet(0), remadeUnready.getAndSet(0), otherSkins.getAndSet(0));
    }
}
