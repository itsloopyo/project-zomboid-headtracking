package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * Which of the lamps round the eye throw shadows, and when their shadow maps are drawn again:
 * street lamps and lamps hung on houses (Lamps), lamps in rooms (RoomLamps) and rooms' ceiling
 * lights (CeilingLights). Each shadowed lamp has a slot of six faces in one depth texture, laid
 * out and looked up as a fire's are (FireShadows.face, FireShadows.GLSL's cubeFaceAt).
 *
 * The world and its furniture are drawn into a slot when its lamp is new to it, when a build in
 * its reach has been made again or has come or gone, and after STALE_NANOS whatever, one stale
 * slot a frame, so the slots come due in turn. That texture is kept. The world's program reads
 * a second one, the first's copy with the people round the lamps nearest the eye drawn over it
 * every frame (choosePeople): a slot is copied again only when its picture of the world was drawn
 * again, when people are drawn into it, or when someone was drawn into it before.
 *
 * Only the light the picture adds of its own for a lamp is darkened in its shadow: Lamps' and
 * RoomLamps' light round the lamp, which the game's light of the square already stops at walls.
 * A ceiling light has nothing of the picture's, so what it is darkened in is the game's own
 * light of a square of its room: by CEILING_DARKENS of its share of it at night, the rest being
 * what the room's walls throw back.
 *
 * Render thread only.
 */
final class LampShadows {
    /** The most slots, and the faces of one. */
    static final int MOST = 6, FACES = 6;
    /** The kinds of lamp, by the list the scene gathered them into. */
    static final int OUTDOOR = 0, ROOM = 1, CEILING = 2;
    /** The most lamps offered a slot in one frame: every lamp of the three lists. */
    static final int CANDIDATES = Lamps.LIGHTS + RoomLamps.LIGHTS + CeilingLights.MOST;
    /** How many slots are drawn again a frame at most: new ones first, then the oldest gone stale. */
    static final int DRAWN_A_FRAME = 2;
    /** How long a slot whose builds are the same is kept before it is drawn again anyway, for the plants the wind moves, in nanoseconds. */
    static final long STALE_NANOS = 1_000_000_000L;
    /** How many times the weight of a lamp that holds a slot another must have to take it. */
    static final float HELD = 1.5f;
    /**
     * The near plane of a face, in tiles: what is nearer the lamp than this along a face is not
     * drawn into it. A lamp's light is put in its shade, its lantern or the head of its post,
     * which would otherwise hide it from everything.
     */
    static final float NEAR = 0.3f;
    /** The farthest a slot's faces see, in tiles: a street lamp lists a reach of 60, and its light is a tenth of its pool's eight tiles out. */
    static final float FARTHEST = 12f;
    /** How far past a lamp's reach its faces see. */
    static final float PAST_REACH = 0.5f;
    /** How much of the game's light of a square a ceiling light makes is taken where it is hidden, at night. */
    static final float CEILING_DARKENS = 0.6f;
    /** The game's light of a room by its switch, (229, 216, 209) of 255, linear. */
    private static final float[] CEILING_LIGHT = {0.789f, 0.694f, 0.645f};
    /** A lamp nearer than this to where a slot's maps were drawn from, in tiles, is the same lamp. */
    private static final float SAME = 0.05f;
    /** Less weight than this is no lamp: one switched off, or out by day. */
    private static final float LEAST_WEIGHT = 1e-4f;
    /**
     * How far from a lamp the people drawn into its maps stand at most, in tiles: past it a lamp's
     * light is too thin for a person's shadow to show. Every person drawn is the game's own draw
     * of them once more, each frame.
     */
    static final float PEOPLE_WITHIN = 6f;

    /** Whether lamps throw shadows in a view: the row is on and the view is not the game's own look. */
    static boolean on(int view) {
        return Part.LAMP_SHADOWS.on() && !Quality.plain(view);
    }

    /** How many slots there are, and how many texels a face is across, for the view's mode: sharper shadows, more and finer. */
    static int slots(int view) {
        return Quality.sharpShadows(view) ? MOST : 3;
    }

    static int faceSize(int view) {
        return Quality.sharpShadows(view) ? 384 : 256;
    }

    /** How many slots a frame draws the people into at most, the nearest the eye's. */
    static int peopleSlots(int view) {
        return Quality.sharpShadows(view) ? 3 : 2;
    }

    /**
     * How many people may stand in a lamp's reach for them to be drawn into its maps: High and
     * Medium. Past it, a crowd under the lamp casts no shadow from it, and the lamp's maps keep
     * the world's shadows alone: every person drawn is the game's own draw of them once more.
     */
    static int crowdMost(int view) {
        return Quality.sharpShadows(view) ? 12 : 6;
    }

    /** The lamps offered this frame: where each is and how far its faces would see, its kind and place in its list, its weight, and a ceiling light's rectangle. */
    final float[] offered = new float[CANDIDATES * 4], offeredRect = new float[CANDIDATES * 4], offeredWeight = new float[CANDIDATES];
    final int[] offeredKind = new int[CANDIDATES], offeredIndex = new int[CANDIDATES];
    int offers;

    /** Where each slot's maps were drawn from (x, y, up) and how far they see, and the kind of its lamp; drawnIn 0 is never. */
    final float[] from = new float[MOST * 4];
    private final int[] kindOf = new int[MOST];
    final int[] drawnIn = new int[MOST];
    private final long[] drawnNanos = new long[MOST], drawnOf = new long[MOST];
    /** The face size and the number of slots the slots were drawn for: drawn at another, none of them is drawn. */
    int face, slotCount;
    /** The slots to draw this frame, toDraw of them. */
    final int[] draw = new int[MOST];
    int toDraw;
    /** For each lamp of Lamps' and RoomLamps' lists, its slot, or -1 for one that throws no shadow. */
    final float[] lampSlot = new float[Lamps.LIGHTS], roomLampSlot = new float[RoomLamps.LIGHTS];
    /** For each slot, the rectangle of floor of a ceiling light with its maps drawn, all 0 for any other: the shader's uLampRect. */
    final float[] rect = new float[MOST * 4];
    /**
     * By slot: whether anyone stands within peopleReach of its lamp this frame, and no more than
     * crowdMost, which the frame says; whether they are drawn into its copy this frame (choosePeople); and whether the copy
     * the world reads holds someone drawn into it.
     */
    final boolean[] peopleNear = new boolean[MOST], peopled = new boolean[MOST];
    private final boolean[] liveHoldsPeople = new boolean[MOST];
    /** How many slots the people are drawn into at most this frame, and how many people a slot's lamp may have in reach: peopleSlots and crowdMost for the view taken. */
    int peopleMost, crowdMost;

    private final boolean[] kept = new boolean[MOST], taken = new boolean[CANDIDATES];
    private final float[] held = new float[CANDIDATES];
    private final int[] strongest = new int[MOST], slotOf = new int[CANDIDATES];
    private final float[] seen = new float[16], past = new float[5];
    private int frame;

    /**
     * Offers the lamps of the view's sky, those whose light can reach what the picture shows, and
     * gives the strongest slots. drawn is the frame's builds, nanos its time.
     */
    void take(SkyState state, ViewCamera.Pose camera, float tanHalf, float aspect, ArrayList<WorldScene.ChunkMeshes> drawn, long nanos, int view) {
        offers = 0;
        boolean on = on(view);
        if (on) {
            ViewCamera.view(camera, seen);
            float wide = tanHalf * aspect;
            // A lamp out of doors lights the picture at night and while the game still holds its daylight at dusk (Lamps.GLSL).
            float outdoors = Math.max(state.night, 1f - state.daylightKept);
            for (int i = 0; i < state.lampCount; i++) {
                int at = i * Lamps.RECORD;
                offer(camera, wide, tanHalf, OUTDOOR, i, state.lamps, at, Math.abs(state.lamps[at + 7]), state.lamps[at + 3] * outdoors, null, 0);
            }
            for (int i = 0; i < state.roomLampCount; i++) {
                int at = i * RoomLamps.FLOATS;
                // A set's light leaves its glass, and changes with what the glass shows: only lamps.
                if (state.roomLamps[at + RoomLamps.WAY + 3] == 0f) {
                    offer(camera, wide, tanHalf, ROOM, i, state.roomLamps, at, state.roomLamps[at + RoomLamps.HUE + 3], state.roomLamps[at + 3] * state.night, null, 0);
                }
            }
            for (int i = 0; i < state.ceilingCount; i++) {
                int at = i * CeilingLights.FLOATS;
                float[] ceiling = state.ceilings;
                float reach = (float) Math.hypot(ceiling[at + CeilingLights.RECT + 2] - ceiling[at + CeilingLights.RECT], ceiling[at + CeilingLights.RECT + 3] - ceiling[at + CeilingLights.RECT + 1]) / 2f
                        + ViewCamera.LEVEL_HEIGHT;
                offer(camera, wide, tanHalf, CEILING, i, ceiling, at + CeilingLights.AT, reach, ceiling[at + CeilingLights.STRENGTH] * state.night, ceiling, at + CeilingLights.RECT);
            }
        }
        assign(drawn, ++frame, nanos, on ? slots(view) : 0, faceSize(view));
        peopleMost = peopleSlots(view);
        crowdMost = crowdMost(view);
        if (DevBuild.ON) {
            say();
        }
    }

    /** Which lamps the log last said throw shadows, as a hash of their kinds and places: dev only. */
    private int said;

    /** Says in the log which lamps throw shadows, when that changes. */
    private void say() {
        int hash = Flora.mix(slotCount, face, 0);
        for (int slot = 0; slot < slotCount; slot++) {
            if (ready(slot)) {
                hash += Flora.mix(Float.floatToIntBits(from[slot * 4]) + kindOf[slot], Float.floatToIntBits(from[slot * 4 + 1]), Float.floatToIntBits(from[slot * 4 + 2]));
            }
        }
        if (hash == said) {
            return;
        }
        said = hash;
        StringBuilder line = new StringBuilder("first person lamp shadows, ").append(slotCount).append(" slots of ").append(face).append(" texels a face:");
        for (int slot = 0; slot < slotCount; slot++) {
            if (ready(slot)) {
                line.append(String.format(Locale.ROOT, " %s (%.2f, %.2f, %.2f) seeing %.1f", KINDS[kindOf[slot]], from[slot * 4], from[slot * 4 + 1], from[slot * 4 + 2], from[slot * 4 + 3]));
            }
        }
        Log.info(line.toString());
    }

    private static final String[] KINDS = {"lamp out of doors", "lamp in a room", "ceiling light"};

    /** How far from a slot's lamp the people drawn into its maps stand at most. */
    float peopleReach(int slot) {
        return Math.min(from[slot * 4 + 3], PEOPLE_WITHIN);
    }

    /** Of the slots with people near (peopleNear), has the up to peopleMost whose lamps are nearest the eye at (x, y, up) drawn with them. */
    void choosePeople(float x, float y, float up) {
        Arrays.fill(peopled, false);
        for (int k = 0; k < peopleMost; k++) {
            int nearest = -1;
            float nearestFar = Float.MAX_VALUE;
            for (int slot = 0; slot < slotCount; slot++) {
                float dx = from[slot * 4] - x, dy = from[slot * 4 + 1] - y, du = from[slot * 4 + 2] - up;
                float far = dx * dx + dy * dy + du * du;
                if (peopleNear[slot] && !peopled[slot] && far < nearestFar) {
                    nearest = slot;
                    nearestFar = far;
                }
            }
            if (nearest < 0) {
                return;
            }
            peopled[nearest] = true;
        }
    }

    private void offer(ViewCamera.Pose camera, float wide, float high, int kind, int index, float[] place, int at, float reach, float strength, float[] plan, int planAt) {
        float x = place[at], y = place[at + 1], up = place[at + 2];
        float far = Math.min(reach, FARTHEST) + PAST_REACH;
        float dx = x - camera.eyeX, dy = y - camera.eyeY, du = up - camera.eyeUp;
        float weight = weight(strength, dx * dx + dy * dy + du * du);
        if (weight >= LEAST_WEIGHT && lightsSeen(seen, wide, high, x, y, up, far, past)) {
            add(kind, index, x, y, up, far, weight, plan, planAt);
        }
    }

    /** Offers a lamp of a kind, index in its list, at (x, y, up), whose faces would see far tiles, of a weight; plan at planAt is a ceiling light's rectangle, or null. */
    void add(int kind, int index, float x, float y, float up, float far, float weight, float[] plan, int planAt) {
        int k = offers++;
        offered[k * 4] = x;
        offered[k * 4 + 1] = y;
        offered[k * 4 + 2] = up;
        offered[k * 4 + 3] = far;
        offeredWeight[k] = weight;
        offeredKind[k] = kind;
        offeredIndex[k] = index;
        for (int c = 0; c < 4; c++) {
            offeredRect[k * 4 + c] = plan == null ? 0f : plan[planAt + c];
        }
    }

    /** How much a lamp counts for when slots are given: its light, less with the square of its distance from the eye. */
    static float weight(float strength, float distanceSquared) {
        return strength / (1f + distanceSquared);
    }

    /** Whether a lamp at (x, y, up) whose maps see reach tiles can light what a picture of the view shows: its ball is not wholly past one of the picture's edges. */
    static boolean lightsSeen(float[] view, float wide, float high, float x, float y, float up, float reach, float[] past) {
        PictureEdges.outsideOf(view, wide, high, x, y, up, past);
        for (float beyond : past) {
            if (beyond > reach) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gives the strongest of the lamps offered (offered, offeredWeight) slots, keeping each lamp
     * in the slot it had, and says which slots are to be drawn this frame, the frame's builds
     * drawn and its time nanos. frame counts up from 1.
     */
    void assign(ArrayList<WorldScene.ChunkMeshes> drawn, int frame, long nanos, int slots, int faceSize) {
        if (slots != slotCount || faceSize != face) {
            Arrays.fill(drawnIn, 0);
            slotCount = slots;
            face = faceSize;
        }
        Arrays.fill(slotOf, -1);
        Arrays.fill(kept, false);
        for (int lamp = 0; lamp < offers; lamp++) {
            held[lamp] = offeredWeight[lamp];
            for (int slot = 0; slot < slots; slot++) {
                if (drawnIn[slot] != 0 && same(slot, lamp)) {
                    held[lamp] = offeredWeight[lamp] * HELD;
                }
            }
        }
        int strong = strongest(slots);
        // First the lamps that still have their slot.
        for (int k = 0; k < strong; k++) {
            int lamp = strongest[k];
            for (int slot = 0; slot < slots; slot++) {
                if (!kept[slot] && drawnIn[slot] != 0 && same(slot, lamp)) {
                    kept[slot] = true;
                    slotOf[lamp] = slot;
                    break;
                }
            }
        }
        // Then new ones, into slots no kept lamp holds.
        for (int k = 0; k < strong; k++) {
            int lamp = strongest[k];
            if (slotOf[lamp] >= 0) {
                continue;
            }
            for (int slot = 0; slot < slots; slot++) {
                if (!kept[slot]) {
                    kept[slot] = true;
                    slotOf[lamp] = slot;
                    System.arraycopy(offered, lamp * 4, from, slot * 4, 4);
                    kindOf[slot] = offeredKind[lamp];
                    drawnIn[slot] = 0;
                    break;
                }
            }
        }
        // Slots not yet drawn are drawn now, up to DRAWN_A_FRAME; then the oldest of those whose builds changed or that are
        // stale, one a frame so they come due in turn. A lamp whose slot is not drawn yet throws no shadow until it is.
        toDraw = 0;
        for (int slot = 0; slot < slots && toDraw < DRAWN_A_FRAME; slot++) {
            if (kept[slot] && drawnIn[slot] == 0) {
                draw[toDraw++] = slot;
            }
        }
        if (toDraw < DRAWN_A_FRAME) {
            int oldest = -1;
            for (int slot = 0; slot < slots; slot++) {
                if (kept[slot] && drawnIn[slot] != 0 && !listed(slot) && (oldest < 0 || drawnIn[slot] < drawnIn[oldest])
                        && (nanos - drawnNanos[slot] >= STALE_NANOS || builds(slot, drawn) != drawnOf[slot])) {
                    oldest = slot;
                }
            }
            if (oldest >= 0) {
                draw[toDraw++] = oldest;
            }
        }
        for (int k = 0; k < toDraw; k++) {
            drawnIn[draw[k]] = frame;
            drawnNanos[draw[k]] = nanos;
            drawnOf[draw[k]] = builds(draw[k], drawn);
        }
        publish();
    }

    /** Tells lampSlot, roomLampSlot and rect which lamps have their maps drawn and where. */
    private void publish() {
        Arrays.fill(lampSlot, -1f);
        Arrays.fill(roomLampSlot, -1f);
        Arrays.fill(rect, 0f);
        for (int lamp = 0; lamp < offers; lamp++) {
            int slot = slotOf[lamp];
            if (slot < 0 || drawnIn[slot] == 0) {
                continue;
            }
            switch (offeredKind[lamp]) {
                case OUTDOOR -> lampSlot[offeredIndex[lamp]] = slot;
                case ROOM -> roomLampSlot[offeredIndex[lamp]] = slot;
                default -> System.arraycopy(offeredRect, lamp * 4, rect, slot * 4, 4);
            }
        }
    }

    private long builds(int slot, ArrayList<WorldScene.ChunkMeshes> drawn) {
        return FireShadows.builds(from[slot * 4], from[slot * 4 + 1], from[slot * 4 + 2], from[slot * 4 + 3], drawn);
    }

    /** Whether a slot holds the maps of a lamp that throws a shadow this frame. */
    boolean ready(int slot) {
        return slot < slotCount && kept[slot] && drawnIn[slot] != 0;
    }

    /** Whether any slot does, or is to be drawn: with none, the frame draws and reads no lamp's map. */
    boolean any() {
        for (int slot = 0; slot < slotCount; slot++) {
            if (ready(slot)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the copy the world reads of a ready slot has to be made again this frame: its world
     * was drawn again, someone is drawn into it this frame (peopled), or someone was drawn into
     * the copy before and has to go.
     */
    boolean copyDue(int slot) {
        return ready(slot) && (listed(slot) || peopled[slot] || liveHoldsPeople[slot]);
    }

    /** Said of a slot whose copy was made again this frame, with its people drawn into it when peopled. */
    void copied(int slot) {
        liveHoldsPeople[slot] = peopled[slot];
    }

    /**
     * The depth textures the slots are drawn into have been made again, holding none of them:
     * each slot this frame does not draw is drawn again as one not drawn yet.
     */
    void atlasMade() {
        for (int slot = 0; slot < MOST; slot++) {
            liveHoldsPeople[slot] = false;
            if (!listed(slot)) {
                drawnIn[slot] = 0;
            }
        }
        publish();
    }

    private static final String[] FROM = new String[MOST], RECT = new String[MOST];

    static {
        for (int slot = 0; slot < MOST; slot++) {
            FROM[slot] = "uLampFrom[" + slot + "]";
            RECT[slot] = "uLampRect[" + slot + "]";
        }
    }

    /** Gives the world's program, whose source has GLSL in it, the slots' maps on unit, which the picture has bound. */
    void lightWorld(Program program, int unit) {
        program.set("uLampShadow", unit);
        for (int slot = 0; slot < MOST; slot++) {
            program.set(FROM[slot], from[slot * 4], from[slot * 4 + 1], from[slot * 4 + 2], from[slot * 4 + 3]);
            program.set(RECT[slot], rect[slot * 4], rect[slot * 4 + 1], rect[slot * 4 + 2], rect[slot * 4 + 3]);
        }
        program.set("uLampFace", face, Math.max(slotCount, 1));
    }

    boolean listed(int slot) {
        for (int k = 0; k < toDraw; k++) {
            if (draw[k] == slot) {
                return true;
            }
        }
        return false;
    }

    private boolean same(int slot, int lamp) {
        float dx = from[slot * 4] - offered[lamp * 4], dy = from[slot * 4 + 1] - offered[lamp * 4 + 1], du = from[slot * 4 + 2] - offered[lamp * 4 + 2];
        return kindOf[slot] == offeredKind[lamp] && dx * dx + dy * dy + du * du < SAME * SAME;
    }

    /** Puts into strongest the up to `slots` lamps offered of the most held weight, strongest first, and says how many. */
    private int strongest(int slots) {
        int count = Math.min(offers, slots);
        Arrays.fill(taken, false);
        for (int k = 0; k < count; k++) {
            int best = -1;
            for (int lamp = 0; lamp < offers; lamp++) {
                if (!taken[lamp] && (best < 0 || held[lamp] > held[best])) {
                    best = lamp;
                }
            }
            taken[best] = true;
            strongest[k] = best;
        }
        return count;
    }

    /**
     * For the world's program, after FireShadows.GLSL, whose cubeFaceAt it reads its maps by:
     * how much of a lamp's light reaches a point, and what a ceiling light leaves of the game's
     * light of a square of its room where it is hidden.
     */
    static final String GLSL = String.format(Locale.ROOT, """
            uniform sampler2DShadow uLampShadow;
            // Where each slot's maps were drawn from, and how far they see.
            uniform vec4 uLampFrom[%1$d];
            // For a slot of a room's ceiling light, the rectangle of floor it lights, its least x and y
            // then its most; all 0 for any other slot.
            uniform vec4 uLampRect[%1$d];
            // Texels a face is across, and how many slots the texture holds.
            uniform vec2 uLampFace;

            // How much of the light of the lamp whose maps are in slot reaches p facing n. Past what
            // the maps see, all of it.
            float lampShadow(float slot, vec3 p, vec3 n) {
                vec4 from = uLampFrom[int(slot)];
                vec3 at = cubeFaceAt(p + n * 0.04 - from.xyz, slot, uLampFace.y, uLampFace.x, %2$f, from.w);
                return shadow2D(uLampShadow, vec3(at.xy, min(at.z, 1.0))).r;
            }

            // What a face at p, with relief n, turned to the eye as face, keeps of given, the game's
            // light of its square, where a ceiling light of its room is hidden from it: at night the
            // light's share of it goes, all but what the room's walls throw back.
            vec3 ceilingShaded(vec3 p, vec3 n, vec3 face, vec3 given) {
                vec3 q = p + face * 0.05;
                for (int s = 0; s < %1$d; s++) {
                    vec4 rect = uLampRect[s];
                    if (rect.z <= rect.x || q.x < rect.x || q.y < rect.y || q.x >= rect.z || q.y >= rect.w) {
                        continue;
                    }
                    float floorUp = uLampFrom[s].z - %3$f;
                    if (q.z < floorUp - 0.05 || q.z >= floorUp + %4$f) {
                        continue;
                    }
                    float hidden = 1.0 - lampShadow(float(s), p, n);
                    given -= min(given, vec3(%6$f, %7$f, %8$f)) * (hidden * %5$f * uDark);
                }
                return given;
            }
            """, MOST, NEAR, (1f - CeilingLights.UNDER) * ViewCamera.LEVEL_HEIGHT, ViewCamera.LEVEL_HEIGHT, CEILING_DARKENS,
            CEILING_LIGHT[0], CEILING_LIGHT[1], CEILING_LIGHT[2]);
}
