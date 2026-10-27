package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.SpriteRenderer;
import zombie.core.opengl.RenderThread;
import zombie.core.skinnedmodel.DeadBodyAtlas;
import zombie.core.skinnedmodel.advancedanimation.AnimatedModel;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.core.textures.SmartTexture;
import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoDeadBody;

/**
 * Corpses drawn as models lying where they fell, in a view whose graphics mode says so
 * (Quality.corpseModels), in place of the game's flat picture of each that WorldScene lays on
 * the ground otherwise.
 *
 * The game poses a model of a body once, to make its picture (DeadBodyAtlas), from the bones the
 * body died in. As that picture is made the posed parts are kept here, their bones and their
 * skins, and CharacterDraw draws them as it draws a zombie. They are never posed again: a corpse
 * lies as it fell and costs no animation. The skins are the game's, which it destroys when it
 * next poses that model for another body; it is kept from destroying one while a kept corpse
 * is drawn with it (keeps).
 *
 * A body is known by the game's entry for its picture, which bodies that look alike share. A
 * draw that finds nothing kept of a body says so (missed), and the logic thread asks the game to
 * pose it again (askAgain): one posed before corpses were drawn as models, or let go since. The
 * render thread keeps, draws and lets go.
 */
final class Corpses {
    private Corpses() {}

    /** A corpse not drawn for this long is let go, and its skins with it: one that comes back into sight is posed again. */
    private static final long UNSEEN_NANOS = 30_000_000_000L;
    /** How often the render thread looks for corpses to let go. */
    private static final long SWEPT_EVERY_NANOS = 1_000_000_000L;
    /** How long a body asked to be posed again waits before it is asked again, and how many are asked in one round of builds. */
    private static final long ASKED_AGAIN_NANOS = 1_000_000_000L;
    private static final int ASKED_A_ROUND = 4;

    /**
     * A corpse as a build of its chunk found it: the body, the game's entry for its picture,
     * where it lies and the height it lies at (WorldScene.restUnder), the square that lights it,
     * and its key for the outline round it (Outlines.key). Made on the logic thread, read on the
     * render thread.
     */
    record Lying(IsoDeadBody body, Object entry, float x, float y, float up, float z, IsoGridSquare square, long outline) {}

    /** A corpse's parts as the game posed them, each with its own bones, and the way the model was turned. */
    static final class Kept {
        final List<ModelInstanceRenderData> parts;
        final List<Texture> skins;
        final float angle;
        /** What CharacterDraw reads of a person, standing for the corpse. */
        final ModelSlotRenderData person = new ModelSlotRenderData();
        /** When it was last asked for by a draw: render thread. */
        long seen;

        Kept(List<ModelInstanceRenderData> parts, List<Texture> skins, float angle) {
            this.parts = parts;
            this.skins = skins;
            this.angle = angle;
        }
    }

    private static final Map<Object, Kept> kept = new ConcurrentHashMap<>();
    /** The entries whose posing is kept when the game next poses one, each with when the game was asked to. */
    private static final Map<Object, Long> wanted = new ConcurrentHashMap<>();
    /** The bodies a draw found nothing kept of, by entry, to be posed again. */
    private static final Map<Object, IsoDeadBody> missing = new ConcurrentHashMap<>();
    /** How many kept corpses are drawn with each skin, and the skins the game asked to destroy meanwhile. */
    private static final Map<Texture, Integer> held = new IdentityHashMap<>();
    private static final Map<Texture, Boolean> letGo = new IdentityHashMap<>();
    private static long sweptAt;
    private static boolean saidKept;
    private static Field pictureEntry, states, stateParts;

    /** The game's entry for a body's picture, or null while it has none. Logic thread. */
    static Object entryOf(IsoDeadBody body) {
        DeadBodyAtlas.BodyTexture picture = body.getAtlasTexture();
        if (picture == null) {
            return null;
        }
        try {
            if (pictureEntry == null) {
                Field field = DeadBodyAtlas.BodyTexture.class.getDeclaredField(Links.BODY_PICTURE_ENTRY.name());
                field.setAccessible(true);
                pictureEntry = field;
            }
            return pictureEntry.get(picture);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("the entry of a body's picture could not be read", failure);
        }
    }

    /** A draw found nothing kept of a body: it is to be posed again. Render thread. */
    static void missed(Lying body) {
        missing.putIfAbsent(body.entry(), body.body());
    }

    /**
     * Asks the game to pose again the bodies draws found nothing kept of, ASKED_A_ROUND at most,
     * and none asked within ASKED_AGAIN_NANOS, which is most likely being posed for the asking. A
     * body whose picture is another by now is left to the build that finds it. Logic thread,
     * while a frame is handed to the render thread, where the game asks for its own pictures.
     */
    static void askAgain() {
        if (missing.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        int asked = 0;
        for (Iterator<Map.Entry<Object, IsoDeadBody>> it = missing.entrySet().iterator(); it.hasNext() && asked < ASKED_A_ROUND; ) {
            Map.Entry<Object, IsoDeadBody> each = it.next();
            it.remove();
            Object entry = each.getKey();
            IsoDeadBody body = each.getValue();
            Long before = wanted.get(entry);
            if (kept.containsKey(entry) || before != null && now - before < ASKED_AGAIN_NANOS || entryOf(body) != entry) {
                continue;
            }
            wanted.put(entry, now);
            DeadBodyAtlas.instance.invalidateBodyTexture(body.getAtlasTexture(), body);
            asked++;
        }
        if (asked > 0) {
            DeadBodyAtlas.instance.render();
        }
    }

    /**
     * Called as the game has made the picture of a body from a model it posed for it: keeps the
     * posed parts, when the body's entry is wanted. Render thread.
     */
    static void baked(AnimatedModel model, Object entry, float angle) {
        if (!wanted.containsKey(entry)) {
            return;
        }
        List<ModelInstanceRenderData> parts = new ArrayList<>();
        List<Texture> skins = new ArrayList<>();
        for (Object each : posedParts(model)) {
            AnimatedModel.AnimatedModelInstanceRenderData posed = (AnimatedModel.AnimatedModelInstanceRenderData) each;
            ModelInstance source = posed.modelInstance;
            Model shape = source.model;
            if (shape == null || !shape.isStatic && posed.matrixPalette == null) {
                continue;
            }
            ModelInstance instance = new ModelInstance();
            instance.model = shape;
            instance.tex = source.tex;
            instance.tintR = source.tintR;
            instance.tintG = source.tintG;
            instance.tintB = source.tintB;
            instance.hue = source.hue;
            instance.modelScript = source.modelScript;
            ModelInstanceRenderData own = new ModelInstanceRenderData();
            own.model = shape;
            own.modelInstance = instance;
            own.tex = source.tex;
            own.hue = source.hue;
            own.ignoreLighting = posed.ignoreLighting;
            if (shape.isStatic) {
                own.xfrm.set(posed.xfrm.get(new float[16]));
            } else {
                FloatBuffer bones = posed.matrixPalette.duplicate();
                bones.position(0);
                FloatBuffer copy = ByteBuffer.allocateDirect(bones.remaining() * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
                copy.put(bones).flip();
                own.matrixPalette = copy;
            }
            parts.add(own);
            if (source.tex instanceof SmartTexture skin) {
                skins.add(skin);
            }
        }
        Kept body = new Kept(parts, skins, angle);
        ModelSlotRenderData person = body.person;
        person.alpha = 1f;
        person.ambientR = 1f;
        person.ambientG = 1f;
        person.ambientB = 1f;
        person.animPlayer = model.getAnimationPlayer();
        person.finalScale = model.getScale();
        person.animPlayerAngle = angle;
        body.seen = System.nanoTime();
        synchronized (held) {
            for (Texture skin : skins) {
                held.merge(skin, 1, Integer::sum);
            }
        }
        if (!saidKept) {
            saidKept = true;
            Log.info("corpses: the first body kept to be drawn as a model, " + parts.size() + " parts and " + skins.size() + " skins of it");
        }
        Kept before = kept.put(entry, body);
        if (before != null) {
            release(before);
        }
    }

    /** The parts of a model posed apart from any character, as the frame being drawn holds them. */
    private static List<?> posedParts(AnimatedModel model) {
        try {
            if (states == null) {
                Field field = AnimatedModel.class.getDeclaredField(Links.POSED_STATES.name());
                field.setAccessible(true);
                Field parts = Class.forName(Links.POSED_PARTS.owner().replace('/', '.')).getDeclaredField(Links.POSED_PARTS.name());
                parts.setAccessible(true);
                states = field;
                stateParts = parts;
            }
            Object state = ((Object[]) states.get(model))[SpriteRenderer.instance.getRenderStateIndex()];
            return (List<?>) stateParts.get(state);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("the parts of a posed body could not be read", failure);
        }
    }

    /** What is kept of a body's entry, noted as drawn now, or null while nothing is. Render thread. */
    static Kept of(Object entry, long now) {
        Kept body = kept.get(entry);
        if (body != null) {
            body.seen = now;
        }
        return body;
    }

    /** Whether any corpse is kept to be drawn. */
    static boolean any() {
        return !kept.isEmpty();
    }

    /** Lets go of the corpses not drawn for UNSEEN_NANOS: one that comes back into sight is missed and posed again. Render thread, once a picture. */
    static void sweep(long now) {
        if (now - sweptAt < SWEPT_EVERY_NANOS) {
            return;
        }
        sweptAt = now;
        for (Iterator<Map.Entry<Object, Kept>> it = kept.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Object, Kept> each = it.next();
            if (now - each.getValue().seen > UNSEEN_NANOS) {
                it.remove();
                wanted.remove(each.getKey());
                release(each.getValue());
            }
        }
    }

    /** A kept corpse is no longer drawn: each skin the game asked to destroy that no other is drawn with is destroyed. Render thread. */
    private static void release(Kept body) {
        List<Texture> destroying = new ArrayList<>();
        synchronized (held) {
            for (Texture skin : body.skins) {
                if (held.merge(skin, -1, Integer::sum) == 0) {
                    held.remove(skin);
                    if (letGo.remove(skin) != null) {
                        destroying.add(skin);
                    }
                }
            }
        }
        for (Texture skin : destroying) {
            skin.destroy();
        }
    }

    /** Whether the game's destroying of a texture is put off: a kept corpse is drawn with it. */
    static boolean keeps(Texture texture) {
        synchronized (held) {
            if (!held.containsKey(texture)) {
                return false;
            }
            letGo.put(texture, Boolean.TRUE);
            return true;
        }
    }

    /** Called as the game leaves a save: every corpse goes, and each skin the game asked to destroy meanwhile is destroyed. */
    static void forget() {
        List<Texture> destroying;
        synchronized (held) {
            destroying = new ArrayList<>(letGo.keySet());
            kept.clear();
            wanted.clear();
            missing.clear();
            held.clear();
            letGo.clear();
        }
        for (Texture skin : destroying) {
            RenderThread.queueInvokeOnRenderContext(skin::destroy);
        }
    }
}
