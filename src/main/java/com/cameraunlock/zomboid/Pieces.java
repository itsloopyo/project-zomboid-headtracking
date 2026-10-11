package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoZombie;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;

/**
 * A limb or a head cut off, flying from the zombie and lying where it lands.
 *
 * A piece is the zombie's own model drawn a second time with every bone but the piece's pressed
 * to a point inside it, and the piece's bones moved together along its flight. Its bones are
 * the ones the game posed the frame before the cut was pressed in: {@link Stumps} leaves a
 * new cut unpressed until the render thread has kept that frame's bones, and presses it after.
 * The piece is drawn in the frame the zombie stood in then, so it flies on whatever the zombie
 * does after. It is drawn with the zombie's model and textures, so only while the zombie is.
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
    /** A piece's length from the joint it was cut at, as many times the bone from that joint to the next; and its radius, in units. */
    static final float LOWER_LENGTH = 1.7f, UPPER_LENGTH = 2.7f, HEAD_LENGTH = 0.14f, ARM_RADIUS = 0.03f, LEG_RADIUS = 0.045f, HEAD_RADIUS = 0.055f;
    /** How long a piece lies once it has landed, in seconds. */
    static final float LIES_FOR = 120f;
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
        /** Each part's bones that frame, and which of them are the piece's. */
        final List<Model> models = new ArrayList<>();
        final List<float[]> palettes = new ArrayList<>();
        final List<boolean[]> ofPiece = new ArrayList<>();
        /** Where and how the zombie stood that frame. */
        float x, y, standsUp, angle, scale;
        long kept, posed;
        volatile boolean splashed;

        Piece(int part, int cut, float awayX, float awayY, float up) {
            this.part = part;
            this.cut = cut;
            this.awayX = awayX;
            this.awayY = awayY;
            this.up = up;
        }
    }

    private static final Map<IsoGameCharacter, List<Piece>> pieces = new WeakHashMap<>();
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
            for (Piece piece : list) {
                if (piece.part == part && piece.state == POSED && (System.nanoTime() - piece.posed) / 1e9f > KEPT_WITHIN) {
                    // Never drawn: nothing is kept, and the piece is let go.
                    piece.kept = System.nanoTime() - (long) (LIES_FOR * 1e9f);
                    piece.state = KEPT;
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
                    return true;
                }
            }
        }
        return false;
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
    static void keep(Piece piece, List<ModelInstanceRenderData> parts, float x, float y, float standsUp, float angle, float scale) {
        for (ModelInstanceRenderData part : parts) {
            if (part.model == null || !(part.model.tag instanceof SkinningData skeleton) || part.matrixPalette == null) {
                continue;
            }
            FloatBuffer palette = part.matrixPalette;
            float[] copy = new float[Math.min(palette.remaining(), BONES * 16)];
            palette.get(palette.position(), copy);
            boolean[] mine = new boolean[BONES];
            SkinningBone bone = skeleton.getBone(Stumps.boneOf(piece.part, piece.cut));
            if (bone == null) {
                continue;
            }
            mark(bone, mine);
            piece.models.add(part.model);
            piece.palettes.add(copy);
            piece.ofPiece.add(mine);
        }
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
        float length = head ? HEAD_LENGTH : piece.bone * (piece.cut == Wounds.UPPER ? UPPER_LENGTH : LOWER_LENGTH);
        piece.motion = new PieceMotion(piece.px, piece.py, piece.pz, piece.ax * along, piece.ay * along, piece.az * along, length,
                head ? HEAD_RADIUS : leg ? LEG_RADIUS : ARM_RADIUS, GRAVITY / METRES_PER_UNIT);
        // Into the model's axes, as Characters places a model: x along (cos, sin), z along (sin, -cos), at angle + pi.
        float cos = (float) Math.cos(angle + Math.PI), sin = (float) Math.sin(angle + Math.PI);
        float vx = (piece.awayX * cos + piece.awayY * sin) / METRES_PER_UNIT, vz = (piece.awayX * sin - piece.awayY * cos) / METRES_PER_UNIT;
        float wx = random.nextFloat() - 0.5f, wy = random.nextFloat() - 0.5f, wz = random.nextFloat() - 0.5f, w = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
        float turns = (float) (2 * Math.PI) * (SPIN_LEAST + (SPIN_MOST - SPIN_LEAST) * random.nextFloat()) / Math.max(w, 1e-4f);
        piece.motion.throwIt(vx, piece.up / METRES_PER_UNIT, vz, wx * turns, wy * turns, wz * turns);
        piece.state = KEPT;
        if (FirstPerson.TRACE) {
            Log.info(String.format(Locale.ROOT, "pieces: kept part %d cut %d from %d parts, joint (%.3f, %.3f, %.3f) runs (%.2f, %.2f, %.2f), scale %.3f",
                    piece.part, piece.cut, piece.models.size(), piece.px, piece.py, piece.pz, piece.ax, piece.ay, piece.az, scale));
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
        return piece.state == KEPT && (System.nanoTime() - piece.kept) / 1e9f < LIES_FOR;
    }

    /**
     * The bones of a piece's part as they are now in its flight, for {@code model}, or null
     * where the piece kept none for it. Render thread.
     */
    static FloatBuffer bones(Piece piece, Model model) {
        int which = piece.models.indexOf(model);
        if (which < 0) {
            return null;
        }
        float[] kept = piece.palettes.get(which);
        boolean[] mine = piece.ofPiece.get(which);
        long now = System.nanoTime();
        PieceMotion motion = piece.motion;
        motion.advance(Math.min((now - piece.stepped) / 1e9f, 0.1f));
        piece.stepped = now;
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

    /** Called once a game tick: blood where pieces land, and pieces long lain forgotten. Logic thread. */
    static void tick() {
        if (!any) {
            return;
        }
        synchronized (pieces) {
            for (var entry : pieces.entrySet()) {
                for (Piece piece : entry.getValue()) {
                    float[] at = piece.splashed ? null : landedAt(piece);
                    if (at != null && entry.getKey() instanceof IsoZombie zombie) {
                        piece.splashed = true;
                        Bleeding.splash(at[0], at[1], zombie.getZ());
                    }
                }
                entry.getValue().removeIf(piece -> piece.state == KEPT && (System.nanoTime() - piece.kept) / 1e9f >= LIES_FOR);
            }
            pieces.values().removeIf(List::isEmpty);
            any = !pieces.isEmpty();
        }
    }
}
