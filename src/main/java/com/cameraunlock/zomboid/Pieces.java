package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoZombie;
import zombie.core.opengl.RenderThread;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;
import zombie.core.textures.Texture;

/**
 * A limb or a head cut off, flying from the zombie and lying where it lands.
 *
 * A piece is the zombie's own model drawn a second time with every bone but the piece's pressed
 * to a point inside it, and the piece's bones moved together along its flight. Its bones are
 * the ones the game posed the frame before the cut was pressed in: {@link Stumps} leaves a
 * new cut unpressed until the render thread has kept that frame's bones, and presses it after.
 * The piece is drawn in the frame the zombie stood in then, so it flies on whatever the zombie
 * does after. It keeps what it is drawn with: the models, which the game shares, and the
 * zombie's own textures, which the game would destroy with the zombie and is kept from
 * destroying until the piece goes (see {@link #keeps}).
 *
 * Once it lies still it is pictured and laid on the ground as a body is (see {@link LimbDecals}),
 * and nothing of it is kept or drawn after.
 *
 * Bones are made on the logic thread and kept and drawn on the render thread.
 */
final class Pieces {
    private Pieces() {}

    /**
     * Metres to one unit of the game's character model: the joint at the base of a standing
     * zombie's skull is 0.80 of a unit up (measured), and on a person 1.75 m tall about 1.55 m.
     */
    static final float METRES_PER_UNIT = 1.9f;
    /** How fast a piece leaves, in metres a second: away from the shot, and up; and how fast it spins, in turns a second, least and most. */
    static final float AWAY = 2.5f, UP = 2.5f, HEAD_UP = 3.5f, SPIN_LEAST = 1.2f, SPIN_MOST = 2.4f;
    static final float GRAVITY = 9.8f;
    /**
     * A piece's length from the joint it was cut at, as many times the bone from that joint to the
     * next (for a hand, the wrist to the knuckles; for a foot, the ankle to the ball of the foot); and its radius, in units.
     */
    static final float END_LENGTH_HAND = 2f, END_LENGTH_FOOT = 1.5f, LOWER_LENGTH = 1.7f, UPPER_LENGTH = 2.7f, HEAD_LENGTH = 0.14f, ARM_RADIUS = 0.03f, LEG_RADIUS = 0.045f, HEAD_RADIUS = 0.055f;
    /**
     * A burst head's shards: how many, how fast they leave, in metres a second, and how much of
     * the head each is along each of the model's axes, least and most. Pressed that flat a
     * shard is drawn as meat (CharacterDraw's stumped), not as a small head.
     */
    static final int SHARDS = 4;
    static final float SHARD_SPEED = 3.5f, SHARD_THIN = 0.012f, SHARD_MOST = 0.22f, SHARD_LENGTH = 0.05f, SHARD_RADIUS = 0.02f;
    /** How long a piece is drawn as a model at most, in seconds: one that never comes to rest goes then. */
    static final float LIES_FOR = 120f;
    /**
     * How long a piece laid on the ground is still drawn as a model, in seconds, while its
     * chunk is built again with the picture of it in.
     */
    static final float LAID_OVERLAP = 1f;
    /** How far past the ends and sides of its rod a piece's picture reaches, in units: a foot or a hand stands off it. */
    static final float PICTURE_PAST = 0.1f;
    /** How long a piece may wait to be kept, in seconds: one whose zombie is no longer posed with the cut never is. */
    static final float KEPT_BY = 1f;
    /** How many bones a part of the game's model has. */
    private static final int BONES = 60;

    static final int WAITING = 0, POSED = 1, KEPT = 2;
    /** How long a posed piece waits for the render thread to keep it, in seconds: a zombie not drawn has its cut pressed without one. */
    static final float KEPT_WITHIN = 0.5f;

    static final class Piece {
        final int part, cut;
        final float awayX, awayY, up;
        /** The length of the bone from the cut joint to the next, in units. */
        float bone;
        PieceMotion motion;
        long stepped;
        volatile boolean landed;
        volatile float landX, landY;
        volatile int state = WAITING;
        /** Where the piece's joint is and which way the piece runs from it, in the model, the frame it was kept. */
        float px, py, pz, ax, ay, az;
        /** Each part as it was drawn that frame, its bones then, and which of them are the piece's: published together with state KEPT. */
        List<ModelInstanceRenderData> parts = List.of();
        List<float[]> palettes = List.of();
        List<boolean[]> ofPiece = List.of();
        /** When the shot cut it, and the level it was cut on. */
        final long born = System.nanoTime();
        float z;
        /** The zombie as it was drawn that frame, to draw the piece by once the zombie is gone. */
        final ModelSlotRenderData person = new ModelSlotRenderData();
        /** Where and how the zombie stood that frame. */
        float x, y, standsUp, angle, scale;
        long kept, posed;
        volatile boolean splashed;
        /** A shard's share of the head along each of the model's axes, or null for a whole piece. */
        float[] squash;
        /** Half the side of the piece's picture on the ground, in units. */
        float pictureHalf;
        /** When it was laid on the ground as a picture, or 0: set on the render thread. */
        volatile long laid;

        Piece(int part, int cut, float awayX, float awayY, float up) {
            this.part = part;
            this.cut = cut;
            this.awayX = awayX;
            this.awayY = awayY;
            this.up = up;
        }
    }

    private static final Map<IsoGameCharacter, List<Piece>> pieces = new WeakHashMap<>();
    /** Pieces whose zombie the game has let go of or made again: kept here until they go. */
    private static final List<Piece> orphans = new ArrayList<>();
    /** The textures pieces are drawn with, which the game is kept from destroying. */
    private static final Set<Texture> held = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Texture> letGo = Collections.newSetFromMap(new IdentityHashMap<>());
    private static volatile boolean any;
    private static final Random random = new Random();
    private static final FloatBuffer moved = ByteBuffer.allocateDirect(BONES * 16 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();

    /** A part of a zombie cut off by a shot from (fromX, fromY): it flies away from there. Logic thread. */
    static void cut(IsoZombie zombie, int part, int cut, float fromX, float fromY) {
        float dx = zombie.getX() - fromX, dy = zombie.getY() - fromY, length = (float) Math.hypot(dx, dy);
        if (length < 1e-4f) {
            dx = 1f;
            dy = 0f;
            length = 1f;
        }
        float side = (random.nextFloat() - 0.5f) * 0.8f;
        float awayX = (dx - dy * side) / length * AWAY, awayY = (dy + dx * side) / length * AWAY;
        boolean head = part == Wounds.HEAD;
        Piece piece = new Piece(part, cut, awayX, awayY, (head ? HEAD_UP : UP) * (0.8f + 0.4f * random.nextFloat()));
        synchronized (pieces) {
            pieces.computeIfAbsent(zombie, z -> new ArrayList<>()).add(piece);
            any = true;
        }
    }

    /** A part burst by a shot from (fromX, fromY), from the cut on: shards of it fly every way, most away from the shot. Logic thread. */
    static void burst(IsoZombie zombie, int part, int cut, float fromX, float fromY) {
        float dx = zombie.getX() - fromX, dy = zombie.getY() - fromY, length = Math.max((float) Math.hypot(dx, dy), 1e-4f);
        for (int i = 0; i < SHARDS; i++) {
            float angle = (float) Math.atan2(dy, dx) + (random.nextFloat() - 0.5f) * 2.4f, speed = SHARD_SPEED * (0.6f + 0.8f * random.nextFloat());
            Piece shard = new Piece(part, cut, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed, speed * (0.3f + 0.7f * random.nextFloat()));
            // Two axes of a chunk's size and the third much thinner: pressed() reads a shard with
            // all three alike as a small whole part, a head's face and hair and all.
            shard.squash = new float[3];
            int thin = random.nextInt(3);
            for (int axis = 0; axis < 3; axis++) {
                shard.squash[axis] = axis == thin ? SHARD_THIN * (0.5f + random.nextFloat())
                        : SHARD_MOST * (0.6f + 0.4f * random.nextFloat());
            }
            synchronized (pieces) {
                pieces.computeIfAbsent(zombie, z -> new ArrayList<>()).add(shard);
                any = true;
            }
        }
    }

    /**
     * Whether a cut part's press is held back this frame: a new piece is kept unpressed until the
     * render thread has the bones. Takes where the joint is and which way the piece runs from
     * it in the model. Logic thread.
     */
    static boolean holdsBack(IsoGameCharacter zombie, int part, float jx, float jy, float jz, float ax, float ay, float az, float bone) {
        if (!any) {
            return false;
        }
        synchronized (pieces) {
            List<Piece> list = pieces.get(zombie);
            if (list == null) {
                return false;
            }
            boolean holds = false;
            for (Piece piece : list) {
                if (piece.part == part && piece.state == POSED && (System.nanoTime() - piece.posed) / 1e9f > KEPT_WITHIN) {
                    letGoUnkept(piece, "its zombie was not drawn");
                }
                if (piece.part == part && piece.state != KEPT) {
                    if (piece.state == WAITING) {
                        piece.posed = System.nanoTime();
                        piece.px = jx;
                        piece.py = jy;
                        piece.pz = jz;
                        piece.ax = ax;
                        piece.ay = ay;
                        piece.az = az;
                        piece.bone = bone;
                        piece.state = POSED;
                    }
                    holds = true;
                }
            }
            if (holds) {
                return true;
            }
        }
        return false;
    }

    /** Every piece kept and still to be drawn, whoever it was cut from. */
    static List<Piece> all() {
        if (!any) {
            return List.of();
        }
        List<Piece> all = new ArrayList<>();
        synchronized (pieces) {
            for (List<Piece> list : pieces.values()) {
                for (Piece piece : list) {
                    if (drawn(piece)) {
                        all.add(piece);
                    }
                }
            }
            all.addAll(orphans);
        }
        all.removeIf(piece -> !drawn(piece));
        return all;
    }

    /** A zombie's pieces, or none. */
    static List<Piece> of(IsoGameCharacter zombie) {
        if (!any || zombie == null) {
            return List.of();
        }
        synchronized (pieces) {
            List<Piece> list = pieces.get(zombie);
            return list == null ? List.of() : new ArrayList<>(list);
        }
    }

    /** Keeps the bones of a posed piece's zombie as drawn this frame. Render thread. */
    static void keep(Piece piece, ModelSlotRenderData data, List<ModelInstanceRenderData> parts, float x, float y, float standsUp, float angle, float scale) {
        List<ModelInstanceRenderData> owns = new ArrayList<>();
        List<float[]> palettes = new ArrayList<>();
        List<boolean[]> mines = new ArrayList<>();
        for (ModelInstanceRenderData part : parts) {
            if (part.model == null || !(part.model.tag instanceof SkinningData skeleton) || part.matrixPalette == null) {
                continue;
            }
            SkinningBone bone = skeleton.getBone(Stumps.boneOf(piece.part, piece.cut));
            if (bone == null || bone.index >= BONES) {
                continue;
            }
            FloatBuffer palette = part.matrixPalette;
            float[] copy = new float[Math.min(palette.remaining(), BONES * 16)];
            palette.get(palette.position(), copy);
            if (bone.index * 16 + 16 > copy.length) {
                continue;
            }
            if (flat(copy, bone.index)) {
                // A frame drawn after the cut was pressed: the cut published before the piece held it back. No piece is better than a flat one.
                letGoUnkept(piece, "its bones were already pressed");
                return;
            }
            boolean[] mine = new boolean[BONES];
            mark(bone, mine);
            ModelInstance instance = new ModelInstance();
            instance.model = part.model;
            instance.tex = part.modelInstance.tex;
            instance.tintR = part.modelInstance.tintR;
            instance.tintG = part.modelInstance.tintG;
            instance.tintB = part.modelInstance.tintB;
            instance.modelScript = part.modelInstance.modelScript;
            ModelInstanceRenderData own = new ModelInstanceRenderData();
            own.model = part.model;
            own.modelInstance = instance;
            own.hue = part.hue;
            own.ignoreLighting = part.ignoreLighting;
            owns.add(own);
            palettes.add(copy);
            mines.add(mine);
        }
        ModelSlotRenderData person = piece.person;
        person.alpha = 1f;
        person.animPlayer = data.animPlayer;
        person.finalScale = data.finalScale;
        person.x = x;
        person.y = y;
        person.z = data.z;
        person.animPlayerAngle = angle;
        piece.x = x;
        piece.y = y;
        piece.standsUp = standsUp;
        piece.angle = angle;
        piece.scale = scale;
        piece.kept = System.nanoTime();
        piece.stepped = piece.kept;
        boolean head = piece.part == Wounds.HEAD, leg = piece.part == Wounds.LEFT_LEG || piece.part == Wounds.RIGHT_LEG;
        // The head runs up from the neck; a limb runs on from its joint.
        float along = head ? -1f : 1f;
        boolean shard = piece.squash != null;
        float length = shard ? SHARD_LENGTH : head ? HEAD_LENGTH
                : piece.bone * (piece.cut == Wounds.UPPER ? UPPER_LENGTH : piece.cut == Wounds.LOWER ? LOWER_LENGTH : leg ? END_LENGTH_FOOT : END_LENGTH_HAND);
        float radius = shard ? SHARD_RADIUS : head ? HEAD_RADIUS : leg ? LEG_RADIUS : ARM_RADIUS;
        piece.motion = new PieceMotion(piece.px, piece.py, piece.pz, piece.ax * along, piece.ay * along, piece.az * along, length, radius, GRAVITY / METRES_PER_UNIT);
        piece.pictureHalf = length / 2f + radius + PICTURE_PAST;
        // Into the model's axes, as Characters places a model: x along (cos, sin), z along (sin, -cos), at angle + pi.
        float cos = (float) Math.cos(angle + Math.PI), sin = (float) Math.sin(angle + Math.PI);
        float vx = (piece.awayX * cos + piece.awayY * sin) / METRES_PER_UNIT, vz = (piece.awayX * sin - piece.awayY * cos) / METRES_PER_UNIT;
        float wx = random.nextFloat() - 0.5f, wy = random.nextFloat() - 0.5f, wz = random.nextFloat() - 0.5f, w = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
        float turns = (float) (2 * Math.PI) * (SPIN_LEAST + (SPIN_MOST - SPIN_LEAST) * random.nextFloat()) / Math.max(w, 1e-4f);
        piece.motion.throwIt(vx, piece.up / METRES_PER_UNIT, vz, wx * turns, wy * turns, wz * turns);
        piece.z = data.z;
        synchronized (pieces) {
            piece.parts = owns;
            piece.palettes = palettes;
            piece.ofPiece = mines;
            for (ModelInstanceRenderData own : owns) {
                if (own.modelInstance.tex != null) {
                    held.add(own.modelInstance.tex);
                }
            }
            piece.state = KEPT;
        }
        if (FirstPerson.TRACE) {
            Log.info(String.format(Locale.ROOT, "pieces: kept part %d cut %d from %d parts, joint (%.3f, %.3f, %.3f) runs (%.2f, %.2f, %.2f), scale %.3f",
                    piece.part, piece.cut, piece.parts.size(), piece.px, piece.py, piece.pz, piece.ax, piece.ay, piece.az, scale));
        }
    }

    /** Whether a bone's matrix in a palette is pressed flat: see CharacterDraw's pressed, the same measure. */
    private static boolean flat(float[] palette, int bone) {
        int at = bone * 16;
        float a0 = palette[at], a1 = palette[at + 1], a2 = palette[at + 2];
        float b0 = palette[at + 4], b1 = palette[at + 5], b2 = palette[at + 6];
        float c0 = palette[at + 8], c1 = palette[at + 9], c2 = palette[at + 10];
        float volume = Math.abs(a0 * (b1 * c2 - b2 * c1) - a1 * (b0 * c2 - b2 * c0) + a2 * (b0 * c1 - b1 * c0));
        float most = (float) Math.max(Math.sqrt(a0 * a0 + a1 * a1 + a2 * a2), Math.max(Math.sqrt(b0 * b0 + b1 * b1 + b2 * b2), Math.sqrt(c0 * c0 + c1 * c1 + c2 * c2)));
        return most < 1e-6f || volume / (most * most * most) < 0.3f;
    }

    /** Lets a piece go without drawing it: its cut is then pressed as any. */
    private static void letGoUnkept(Piece piece, String why) {
        piece.kept = System.nanoTime() - (long) (LIES_FOR * 1e9f);
        piece.state = KEPT;
        if (FirstPerson.TRACE) {
            Log.info("pieces: let part " + piece.part + " go unkept: " + why);
        }
    }

    private static void mark(SkinningBone bone, boolean[] mine) {
        if (bone.index < BONES) {
            mine[bone.index] = true;
        }
        if (bone.children != null) {
            for (SkinningBone child : bone.children) {
                mark(child, mine);
            }
        }
    }

    /** Whether a piece is kept and still to be drawn. */
    static boolean drawn(Piece piece) {
        long now = System.nanoTime();
        return piece.state == KEPT && (now - piece.kept) / 1e9f < LIES_FOR && (piece.laid == 0 || (now - piece.laid) / 1e9f < LAID_OVERLAP);
    }

    /** Whether a drawn piece has come to rest and is not yet laid on the ground. Render thread. */
    static boolean toLay(Piece piece) {
        return piece.laid == 0 && piece.motion != null && piece.motion.resting;
    }

    /**
     * Where a piece at rest lies: the middle of its picture on the map, x and y, and half the
     * picture's side, in tiles. Render thread.
     */
    static float[] lies(Piece piece) {
        float[] middle = piece.motion.middle;
        float cos = (float) Math.cos(piece.angle + Math.PI), sin = (float) Math.sin(piece.angle + Math.PI);
        return new float[] {piece.x + (middle[0] * cos + middle[2] * sin) * piece.scale, piece.y + (middle[0] * sin - middle[2] * cos) * piece.scale,
                piece.pictureHalf * piece.scale};
    }

    /** The piece is laid on the ground: it is drawn as a model a moment longer, and then goes. Render thread. */
    static void laid(Piece piece) {
        piece.laid = System.nanoTime();
    }

    /**
     * The bones of a piece's part as they are now in its flight, for {@code model}, or null
     * where the piece kept none for it. Render thread.
     */
    static FloatBuffer bones(Piece piece, int which) {
        float[] kept = piece.palettes.get(which);
        boolean[] mine = piece.ofPiece.get(which);
        long now = System.nanoTime();
        PieceMotion motion = piece.motion;
        if (which == 0) {
            motion.advance(Math.min((now - piece.stepped) / 1e9f, 0.1f));
            piece.stepped = now;
        }
        float[] now3 = new float[3];
        motion.pivot(now3);
        if (motion.touched && !piece.landed) {
            float cos = (float) Math.cos(piece.angle + Math.PI), sin = (float) Math.sin(piece.angle + Math.PI);
            piece.landX = piece.x + (now3[0] * cos + now3[2] * sin) * piece.scale;
            piece.landY = piece.y + (now3[0] * sin - now3[2] * cos) * piece.scale;
            piece.landed = true;
        }
        float[] r = motion.turn;
        float r00 = r[0], r01 = r[1], r02 = r[2], r10 = r[3], r11 = r[4], r12 = r[5], r20 = r[6], r21 = r[7], r22 = r[8];
        float mx = now3[0] - piece.px, my = now3[1] - piece.py, mz = now3[2] - piece.pz;
        float px = piece.px, py = piece.py, pz = piece.pz;
        float[] squash = piece.squash;
        moved.clear();
        int bones = kept.length / 16;
        for (int bone = 0; bone < bones; bone++) {
            int at = bone * 16;
            if (!mine[bone]) {
                // Pressed to the joint: whatever of the zombie the piece's corners are not, they are not drawn.
                for (int i = 0; i < 16; i++) {
                    moved.put(at + i, 0f);
                }
                moved.put(at + 3, px + mx);
                moved.put(at + 7, py + my);
                moved.put(at + 11, pz + mz);
                moved.put(at + 15, 1f);
                continue;
            }
            // Each row of the bone's matrix holds one axis's share of each corner, the fourth its place: turned, then the place moved.
            for (int column = 0; column < 4; column++) {
                float a0 = kept[at + column], a1 = kept[at + 4 + column], a2 = kept[at + 8 + column];
                if (column == 3) {
                    a0 -= px;
                    a1 -= py;
                    a2 -= pz;
                }
                if (squash != null) {
                    a0 *= squash[0];
                    a1 *= squash[1];
                    a2 *= squash[2];
                }
                float b0 = r00 * a0 + r01 * a1 + r02 * a2, b1 = r10 * a0 + r11 * a1 + r12 * a2, b2 = r20 * a0 + r21 * a1 + r22 * a2;
                if (column == 3) {
                    b0 += px + mx;
                    b1 += py + my;
                    b2 += pz + mz;
                }
                moved.put(at + column, b0);
                moved.put(at + 4 + column, b1);
                moved.put(at + 8 + column, b2);
                moved.put(at + 12 + column, kept[at + 12 + column]);
            }
        }
        moved.limit(kept.length);
        return moved;
    }

    /** Where a landed piece lies on the map, for its blood: x and y, or null while it flies. */
    static float[] landedAt(Piece piece) {
        return piece.landed ? new float[] {piece.landX, piece.landY} : null;
    }

    /**
     * Whether the game's destroying of a texture is put off: a piece is drawn with it. A texture
     * the game asked to destroy is destroyed when the last piece drawn with it goes.
     */
    static boolean keeps(Texture texture) {
        if (held.isEmpty()) {
            return false;
        }
        synchronized (pieces) {
            if (!held.contains(texture)) {
                return false;
            }
            letGo.add(texture);
            return true;
        }
    }

    /** A zombie the game makes again: its pieces lie on, apart from it. */
    static void reused(IsoGameCharacter zombie) {
        if (!any) {
            return;
        }
        synchronized (pieces) {
            List<Piece> list = pieces.remove(zombie);
            if (list != null) {
                orphans.addAll(list);
            }
        }
    }

    /** Called as the game leaves a save: every piece goes, and each texture the game asked to destroy meanwhile is destroyed. */
    static void forget() {
        List<Texture> destroying;
        synchronized (pieces) {
            destroying = new ArrayList<>(letGo);
            pieces.clear();
            orphans.clear();
            held.clear();
            letGo.clear();
            any = false;
        }
        for (Texture texture : destroying) {
            RenderThread.queueInvokeOnRenderContext(texture::destroy);
        }
    }

    /** Called once a game tick: blood where pieces land, and pieces long lain forgotten. Logic thread. */
    static void tick() {
        if (!any) {
            return;
        }
        List<Texture> destroying = new ArrayList<>();
        synchronized (pieces) {
            List<Piece> every = new ArrayList<>(orphans);
            pieces.values().forEach(every::addAll);
            for (Piece piece : every) {
                float[] at = piece.splashed ? null : landedAt(piece);
                if (at != null) {
                    piece.splashed = true;
                    Bleeding.splash(at[0], at[1], piece.z);
                }
                if (piece.state != KEPT && (System.nanoTime() - piece.born) / 1e9f > KEPT_BY) {
                    letGoUnkept(piece, "it was never posed and drawn");
                }
            }
            pieces.values().forEach(list -> list.removeIf(Pieces::gone));
            pieces.values().removeIf(List::isEmpty);
            orphans.removeIf(Pieces::gone);
            // Textures no piece is drawn with any more go as the game asked. Only a kept piece's parts are read: they are published whole.
            Set<Texture> still = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Piece piece : every) {
                if (piece.state == KEPT && !gone(piece)) {
                    piece.parts.forEach(part -> still.add(part.modelInstance.tex));
                }
            }
            held.retainAll(still);
            for (java.util.Iterator<Texture> it = letGo.iterator(); it.hasNext(); ) {
                Texture texture = it.next();
                if (!held.contains(texture)) {
                    it.remove();
                    destroying.add(texture);
                }
            }
            any = !pieces.isEmpty() || !orphans.isEmpty();
        }
        // The game destroys a texture on the render thread, which holds the graphics context.
        for (Texture texture : destroying) {
            RenderThread.queueInvokeOnRenderContext(texture::destroy);
        }
    }

    /** Whether a piece has lain its time, was let go without being kept, or is laid on the ground as a picture. */
    private static boolean gone(Piece piece) {
        long now = System.nanoTime(), laid = piece.laid;
        return piece.state == KEPT && ((now - piece.kept) / 1e9f >= LIES_FOR || laid != 0 && (now - laid) / 1e9f >= LAID_OVERLAP);
    }
}
