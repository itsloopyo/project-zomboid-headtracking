package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import zombie.characters.IsoGameCharacter;
import zombie.core.opengl.RenderThread;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoDeadBody;

/**
 * A character that died, kept as a view last drew it, so it can go on being drawn from the
 * moment the game stops drawing it until its corpse is drawn as a model where it fell. The game
 * takes the character out of the world before the corpse has a picture and a posed model to
 * draw, and that place was empty for a moment in between. A figure is held HELD_NANOS at most
 * after the game stops drawing it, so one whose corpse never comes is not left standing.
 *
 * Each view (Characters) holds its own; the skins they are drawn with are the game's, which it
 * destroys once the character is let go, and are kept from destruction here while a figure is
 * held with them (keeps). Render thread, but for keeps.
 */
final class Dying {
    private Dying() {}

    /** Off unless the game is started with -Dcameraunlock.zomboid.dyingFigures=true: nothing is held or logged. */
    static final boolean ON = Boolean.getBoolean("cameraunlock.zomboid.dyingFigures");

    /** How long a figure the game no longer draws is drawn on at most, waiting for its corpse. */
    static final long HELD_NANOS = 6_000_000_000L;
    /** How near the place a figure was last drawn a corpse is taken for its own, in tiles. */
    static final float CORPSE_WITHIN = 1f;

    static final class Held {
        final IsoGameCharacter character;
        /** The character's id and when it was first held, for the log. */
        final int id;
        final long heldAt = System.nanoTime();
        final List<ModelInstanceRenderData> parts = new ArrayList<>();
        final ModelSlotRenderData person = new ModelSlotRenderData();
        /** The corpses lying as models near it as it was first held: none of them is its own. */
        final List<IsoDeadBody> lyingBefore = new ArrayList<>();
        float x, y, z, up, angle;
        /** Set under the lock of all, for the logic thread to read there. */
        IsoGridSquare square;
        /** When a draw first found the game no longer drawing it, or 0 while it does. */
        long goneAt;
        /** Set once the game leaves the save: the skins it is drawn with may be gone. */
        volatile boolean forgotten;

        Held(IsoGameCharacter character) {
            this.character = character;
            this.id = character.getID();
        }

        /** Whether a corpse drawn at (x, y) on level z may be this figure's own. */
        boolean owns(IsoDeadBody body, float bodyX, float bodyY, float bodyZ) {
            float toX = bodyX - x, toY = bodyY - y;
            return (int) Math.floor(bodyZ) == (int) Math.floor(z) && toX * toX + toY * toY <= CORPSE_WITHIN * CORPSE_WITHIN && !lyingBefore.contains(body);
        }
    }

    /** Every figure held by any view, for keeps. */
    private static final List<Held> all = new ArrayList<>();
    private static final float[] xfrm = new float[16];
    /** The skins the game asked to destroy while a held figure was drawn with them. */
    private static final Map<Texture, Boolean> letGo = new IdentityHashMap<>();

    /** What the log says of holds, each kind said at most once in SAID_EVERY_NANOS, with how many went unsaid since. */
    static final int STARTED = 0, RELEASED = 1, MISSED = 2;
    private static final long SAID_EVERY_NANOS = 250_000_000L;
    private static final long[] saidAt = new long[3];
    private static final int[] unsaid = new int[3];

    static void say(int kind, String line) {
        long now = System.nanoTime();
        if (now - saidAt[kind] < SAID_EVERY_NANOS) {
            unsaid[kind]++;
            return;
        }
        saidAt[kind] = now;
        Log.info(unsaid[kind] == 0 ? line : line + " (" + unsaid[kind] + " more like it not said)");
        unsaid[kind] = 0;
    }

    /** A new figure held for a character that died, drawn where it stands at (x, y, z). */
    static Held hold(IsoGameCharacter character, float x, float y, float z) {
        Held held = new Held(character);
        synchronized (all) {
            all.add(held);
        }
        say(STARTED, String.format(Locale.ROOT, "dying figure held: character %d at %.2f, %.2f, %.2f", held.id, x, y, z));
        return held;
    }

    /**
     * Keeps the figure as the game draws it this frame: its parts, each with a copy of its bones,
     * where it stands and the way it faces, and the square it stands on as the frame gathered its
     * character's light (FigureLights), whose light it is drawn by from then on.
     */
    static void keep(Held held, ModelSlotRenderData data, List<ModelInstanceRenderData> parts, float up, IsoGridSquare square) {
        synchronized (all) {
            held.square = square;
            int at = 0;
            for (int i = 0; i < parts.size(); i++) {
                ModelInstanceRenderData part = parts.get(i);
                if (part.model == null || part.modelInstance == null || (part.model.isStatic ? part.xfrm == null : part.matrixPalette == null)) {
                    continue;
                }
                ModelInstanceRenderData own;
                if (at < held.parts.size()) {
                    own = held.parts.get(at);
                } else {
                    own = new ModelInstanceRenderData();
                    own.modelInstance = new ModelInstance();
                    held.parts.add(own);
                }
                at++;
                ModelInstance source = part.modelInstance, instance = own.modelInstance;
                instance.model = part.model;
                instance.tex = source.tex;
                instance.tintR = source.tintR;
                instance.tintG = source.tintG;
                instance.tintB = source.tintB;
                instance.hue = source.hue;
                instance.modelScript = source.modelScript;
                own.model = part.model;
                own.tex = part.tex;
                own.hue = part.hue;
                own.ignoreLighting = part.ignoreLighting;
                if (part.model.isStatic) {
                    own.xfrm.set(part.xfrm.get(xfrm));
                    own.matrixPalette = null;
                } else {
                    FloatBuffer bones = part.matrixPalette.duplicate();
                    bones.position(0);
                    FloatBuffer copy = own.matrixPalette;
                    if (copy == null || copy.capacity() < bones.remaining()) {
                        copy = ByteBuffer.allocateDirect(bones.remaining() * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
                    }
                    copy.clear();
                    copy.put(bones).flip();
                    own.matrixPalette = copy;
                }
            }
            while (held.parts.size() > at) {
                held.parts.remove(held.parts.size() - 1);
            }
        }
        ModelSlotRenderData person = held.person;
        person.alpha = data.alpha;
        person.ambientR = 1f;
        person.ambientG = 1f;
        person.ambientB = 1f;
        person.animPlayer = data.animPlayer;
        person.finalScale = data.finalScale;
        person.animPlayerAngle = data.animPlayerAngle;
        person.x = data.x;
        person.y = data.y;
        person.z = data.z;
        for (int i = 0; i < person.effectLights.length; i++) {
            ModelInstance.EffectLight from = data.effectLights[i], to = person.effectLights[i];
            to.x = from.x;
            to.y = from.y;
            to.z = from.z;
            to.r = from.r;
            to.g = from.g;
            to.b = from.b;
            to.radius = from.radius;
        }
        held.x = data.x;
        held.y = data.y;
        held.z = data.z;
        held.up = up;
        held.angle = data.animPlayerAngle;
        settle();
    }

    /**
     * A held figure is drawn no more, for the reason given: each skin the game asked to destroy
     * that no other is drawn with is destroyed.
     */
    static void release(Held held, String reason, long now) {
        synchronized (all) {
            all.remove(held);
        }
        settle();
        say(RELEASED, String.format(Locale.ROOT, "dying figure released (%s): character %d, held %d ms, %s", reason, held.id, (now - held.heldAt) / 1_000_000L,
                held.goneAt == 0L ? "the game still drew it" : "drawn by the mod for " + (now - held.goneAt) / 1_000_000L + " ms after the game stopped drawing it"));
    }

    /** Logic thread: the square each figure held by any view stands on, into into, or null for one on none: see FigureLights. */
    static void squares(List<IsoGridSquare> into) {
        synchronized (all) {
            for (int i = 0; i < all.size(); i++) {
                into.add(all.get(i).square);
            }
        }
    }

    /** Whether the game's destroying of a texture is put off: a held figure is drawn with it. */
    static boolean keeps(Texture texture) {
        synchronized (all) {
            if (!drawnWith(texture)) {
                return false;
            }
            letGo.put(texture, Boolean.TRUE);
            return true;
        }
    }

    private static boolean drawnWith(Texture texture) {
        for (int i = 0; i < all.size(); i++) {
            List<ModelInstanceRenderData> parts = all.get(i).parts;
            for (int part = 0; part < parts.size(); part++) {
                if (parts.get(part).modelInstance.tex == texture) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Destroys the skins the game asked to destroy that no held figure is drawn with any more. Render thread. */
    private static void settle() {
        List<Texture> destroying = null;
        synchronized (all) {
            if (letGo.isEmpty()) {
                return;
            }
            for (var it = letGo.keySet().iterator(); it.hasNext(); ) {
                Texture texture = it.next();
                if (!drawnWith(texture)) {
                    it.remove();
                    if (destroying == null) {
                        destroying = new ArrayList<>();
                    }
                    destroying.add(texture);
                }
            }
        }
        if (destroying != null) {
            for (Texture texture : destroying) {
                texture.destroy();
            }
        }
    }

    /** Called as the game leaves a save: every figure goes, and each skin the game asked to destroy meanwhile is destroyed. */
    static void forget() {
        List<Texture> destroying;
        int figures;
        synchronized (all) {
            destroying = new ArrayList<>(letGo.keySet());
            figures = all.size();
            for (int i = 0; i < all.size(); i++) {
                all.get(i).forgotten = true;
            }
            all.clear();
            letGo.clear();
        }
        if (figures > 0) {
            say(RELEASED, "dying figure released (save left): " + figures + " figures");
        }
        for (Texture texture : destroying) {
            RenderThread.queueInvokeOnRenderContext(texture::destroy);
        }
    }
}
