package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import se.krka.kahlua.vm.KahluaTable;
import zombie.GameTime;
import zombie.SoundManager;
import zombie.WorldSoundManager;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.util.list.PZArrayList;

/**
 * The lights of glass in a door that shots have broken. The game keeps no glass in a door, so
 * the mod keeps which lights are broken, one bit a light in the order Doors measures them off
 * the shut sprite, in the door's mod data, where it is saved with the door. The first person
 * picture draws a broken light as the shards left in its bead (OpeningBuilder.addBuiltDoor).
 *
 * A round breaks the light it goes through. A shotgun's blast whose pellets go through a door's
 * lights SMASHING times or more smashes the sash: every light breaks, and the bars and beads are
 * blown out round the middle of the pellets, each splinter of them as far again as the next or
 * not, so wood is left in ragged ends nearer the stiles and rails (remnants).
 */
final class DoorGlass {
    private DoorGlass() {}

    /** The key a door's mod data keeps its broken lights under. */
    private static final String BROKEN = "cameraunlockBrokenLights";
    /** How many lights of a leaf have a bit of their own for their glass broken. */
    private static final int MOST_LIGHTS = 30;
    /**
     * The bit kept where a blast smashed a door's sash. Every light of it is broken, and the bits
     * under it hold where in the sash the blast's middle was: AT_BITS for the share of its width
     * from its start, and AT_BITS over them for the share of its height from its foot.
     */
    static final int SMASHED = 1 << MOST_LIGHTS;
    private static final int AT_BITS = 15, AT_MOST = (1 << AT_BITS) - 1;
    /** How many of one blast's pellets through a door's lights smash its sash. */
    private static final int SMASHING = 3;
    /** How far from a blast's middle the wood of a sash is blown out, as a share of the sash's narrower side, and how much further or less each splinter goes, as a share of that. */
    static final float BLOWN = 0.42f, RAGGED = 0.4f;
    /** How many splinters a bar or a bead is split into across its width. */
    static final int SPLINTERS = 4;

    /**
     * Each door a pellet of the last blast went through a light of, held weakly to let the world go
     * with the save: how many of that blast's pellets have, and the sums of where along and up. The
     * world's age the blast was fired at tells it from the next. Logic thread.
     */
    private static final Map<IsoObject, float[]> blasted = new WeakHashMap<>();
    private static double blastedAt;

    /** The lights broken of each door loaded, for the builds to read off the logic thread. */
    private static final Map<IsoObject, Integer> broken = Collections.synchronizedMap(new WeakHashMap<>());
    /** Whether a light has been kept broken this session: in most none is, and the builds and the sums ask of every door they meet. */
    private static volatile boolean any;

    /** The lights of a door that are broken, a bit a light. Any thread. */
    static int broken(IsoObject door) {
        Integer lights = any ? broken.get(door) : null;
        return lights == null ? 0 : lights;
    }

    /** Called as the game has loaded a chunk's squares, on its logic thread: the broken lights of its doors, as saved. */
    static void loaded(IsoChunk chunk) {
        for (int z = chunk.getMinLevel(); z <= chunk.getMaxLevel(); z++) {
            for (int i = 0; i < WorldScene.CHUNK_TILES * WorldScene.CHUNK_TILES; i++) {
                IsoGridSquare square = chunk.getGridSquare(i % WorldScene.CHUNK_TILES, i / WorldScene.CHUNK_TILES, z);
                if (square == null) {
                    continue;
                }
                PZArrayList<IsoObject> objects = square.getObjects();
                for (int k = 0; k < objects.size(); k++) {
                    IsoObject object = objects.get(k);
                    if (isDoor(object) && object.hasModData() && object.getModData().rawget(BROKEN) instanceof Double lights) {
                        any = true;
                        broken.put(object, lights.intValue());
                    }
                }
            }
        }
    }

    /**
     * Breaks a light of a door, as the game breaks a window (IsoWindow.smashWindow): its sound,
     * and the noise zombies hear. Nothing where it is broken already. Logic thread.
     */
    static void breakLight(IsoObject door, int light) {
        if ((broken(door) & SMASHED) == 0 && set(door, broken(door) | 1 << light) && DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "a shot broke light %d of the door at (%d, %d, %d)", light, door.getSquare().getX(), door.getSquare().getY(), door.getSquare().getZ()));
        }
    }

    /**
     * A pellet of a blast went through a light of a door: breaks it, and smashes the sash where
     * it is the SMASHING'th of the blast's through the door. A blast's pellets are all fired in
     * one tick of the world, which a world's age tells apart. Logic thread.
     */
    static void pellet(IsoObject door, int light, float along, float up) {
        if ((broken(door) & SMASHED) != 0) {
            return;
        }
        double now = GameTime.getInstance().getWorldAgeHours();
        if (now != blastedAt) {
            blasted.clear();
            blastedAt = now;
        }
        // A blast's pellets each go through every door on their own line before the next is fired, so two doors' counts run at once.
        float[] through = blasted.computeIfAbsent(door, first -> new float[3]);
        through[0]++;
        through[1] += along;
        through[2] += up;
        if (through[0] < SMASHING) {
            breakLight(door, light);
            return;
        }
        float[] sash = sash(leafOf(door).panes);
        float middleAlong = through[1] / through[0], middleUp = through[2] / through[0];
        if (set(door, smashedAt(sash, middleAlong, middleUp)) && DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "a blast smashed the sash of the door at (%d, %d, %d) round %.3f along and %.3f levels up", door.getSquare().getX(),
                    door.getSquare().getY(), door.getSquare().getZ(), middleAlong, middleUp));
        }
    }

    /** What a door's broken lights are kept as where a blast smashed the sash given round (along, up). */
    static int smashedAt(float[] sash, float along, float up) {
        int across = Math.round(Math.clamp((along - sash[0]) / (sash[1] - sash[0]), 0f, 1f) * AT_MOST);
        int high = Math.round(Math.clamp((up - sash[2]) / (sash[3] - sash[2]), 0f, 1f) * AT_MOST);
        return SMASHED | high << AT_BITS | across;
    }

    /** Where the blast that smashed a sash had its middle, along and up in levels, and how far round it the wood went, in tiles. */
    static float[] blast(float[] sash, int lights) {
        float across = (lights & AT_MOST) / (float) AT_MOST, high = (lights >> AT_BITS & AT_MOST) / (float) AT_MOST;
        return new float[] {sash[0] + across * (sash[1] - sash[0]), sash[2] + high * (sash[3] - sash[2]),
                BLOWN * Math.min(sash[1] - sash[0], (sash[3] - sash[2]) * ViewCamera.LEVEL_HEIGHT)};
    }

    /**
     * What a blast leaves of a bar or a bead from along `from` to `to` and from `foot` to `head` in
     * levels: split into SPLINTERS along its length, each blown out as far round the blast's
     * middle as its own share of RAGGED says, four numbers a piece left as the bar is given.
     */
    static float[] remnants(float from, float to, float foot, float head, float[] blast, int seed) {
        float level = ViewCamera.LEVEL_HEIGHT;
        boolean upright = (head - foot) * level > to - from;
        float s0 = upright ? foot * level : from, s1 = upright ? head * level : to;
        float w0 = upright ? from : foot * level, w1 = upright ? to : head * level;
        float middle = upright ? blast[1] * level : blast[0], beside = upright ? blast[0] : blast[1] * level;
        float[] left = new float[SPLINTERS * 8];
        int count = 0;
        for (int splinter = 0; splinter < SPLINTERS; splinter++) {
            float a = w0 + (w1 - w0) * splinter / SPLINTERS, b = w0 + (w1 - w0) * (splinter + 1) / SPLINTERS;
            float reach = blast[2] * (1f + RAGGED * (2f * OpeningBuilder.chance(seed + splinter * 7919) - 1f));
            float off = (a + b) / 2f - beside;
            float half = Math.abs(off) >= reach ? 0f : (float) Math.sqrt(reach * reach - off * off);
            float[] ends = half == 0f ? new float[] {s0, s1} : new float[] {s0, Math.min(s1, middle - half), Math.max(s0, middle + half), s1};
            for (int piece = 0; piece < ends.length; piece += 2) {
                if (ends[piece + 1] - ends[piece] < 1e-4f) {
                    continue;
                }
                left[count++] = upright ? a : ends[piece];
                left[count++] = upright ? b : ends[piece + 1];
                left[count++] = upright ? ends[piece] / level : a / level;
                left[count++] = upright ? ends[piece + 1] / level : b / level;
            }
        }
        return Arrays.copyOf(left, count);
    }

    /** Whether a point along and up in levels is within the furthest a blast may have blown a sash's wood out, and the glass with it: see remnants. */
    static boolean blownOut(float[] blast, float along, float up) {
        float da = along - blast[0], du = (up - blast[1]) * ViewCamera.LEVEL_HEIGHT, reach = blast[2] * (1f + RAGGED);
        return da * da + du * du < reach * reach;
    }

    /** Where in the world a door stands, for the seed its splinters are drawn from, as the picture draws them. */
    static int seed(int x, int y, int z) {
        return x * 73856093 ^ y * 19349663 ^ z * 83492791;
    }

    /** Where a smashed sash's bar breaks from: OpeningBuilder draws it from the same seed the shots go through it by. */
    static int barSeed(int seed, int bar) {
        return seed ^ bar * 2654435;
    }

    /** The leaf of a shut door as the picture measured it (Doors), or null. */
    private static Windows.Shape leafOf(IsoObject door) {
        Doors.Shape shape = Doors.known(door.getSprite().getTextureForCurrentFrame(door.getDir()));
        return shape == null ? null : shape.leaf;
    }

    /** Keeps a door's broken lights as `now`, with the sound and noise of breaking glass, and answers whether that broke any. */
    private static boolean set(IsoObject door, int now) {
        if (now == broken(door)) {
            return false;
        }
        any = true;
        broken.put(door, now);
        KahluaTable data = door.getModData();
        data.rawset(BROKEN, (double) now);
        IsoGridSquare square = door.getSquare();
        SoundManager.instance.PlayWorldSound("SmashWindow", square, 0.2f, 70f, 1f, true);
        WorldSoundManager.instance.addSound(null, square.getX(), square.getY(), square.getZ(), 10, 20, true, 4f, 15f);
        ChunkMarks.changed(door);
        return true;
    }

    /**
     * The light of a shut door's leaf a shot crosses at `along` the edge and `up` levels over the
     * floor, as the picture measured the leaf (Doors): -1 where it crosses wood, or the door has
     * no glass or has not been measured. Where its sash is smashed, anywhere in it but what is
     * left of its bars is its first light.
     */
    static int lightAt(IsoObject door, boolean north, float along, float up) {
        if (door.getSprite() == null || !door.getSprite().getProperties().has("doorTrans")) {
            return -1;
        }
        Windows.Shape leaf = leafOf(door);
        if (leaf == null || leaf.north != north) {
            return -1;
        }
        IsoGridSquare square = door.getSquare();
        int lights = broken(door);
        // The aim asks this every frame it crosses a glazed door: only a smashed sash's bars are read.
        float[] bars = (lights & SMASHED) != 0 ? OpeningBuilder.barsToRails(leaf) : leaf.bars;
        return lightIn(leaf.panes, bars, lights, seed(square.getX(), square.getY(), square.getZ()), along, up);
    }

    /** What the picture measured of a door's leaf, for a log line: why a shot found no light in it. */
    static String describe(IsoObject door, boolean north) {
        if (door.getSprite() == null || !door.getSprite().getProperties().has("doorTrans")) {
            return "no glass in its sprite";
        }
        Doors.Shape shape = Doors.known(door.getSprite().getTextureForCurrentFrame(door.getDir()));
        if (shape == null) {
            return "its leaf not measured";
        }
        if (shape.leaf.north != north) {
            return "its leaf measured on the other edge";
        }
        return "wood, lights " + Arrays.toString(shape.leaf.panes);
    }

    /**
     * Which light (along, up) on a leaf is inside, five numbers a light as Windows.Shape.panes has
     * them, with `lights` broken: -1 for none. Where the sash is smashed, anywhere in it but in what
     * the blast left of its bars, four numbers a bar, the splinters drawn from `seed`, is light 0.
     */
    static int lightIn(float[] panes, float[] bars, int lights, int seed, float along, float up) {
        if ((lights & SMASHED) != 0 && panes.length > 0) {
            float[] sash = sash(panes);
            if (along < sash[0] || along > sash[1] || up < sash[2] || up > sash[3]) {
                return -1;
            }
            float[] blast = blast(sash, lights);
            for (int bar = 0; bar < bars.length; bar += 4) {
                if (!inSash(sash, bars[bar], bars[bar + 1], bars[bar + 2], bars[bar + 3])) {
                    continue;
                }
                float[] left = remnants(bars[bar], bars[bar + 1], bars[bar + 2], bars[bar + 3], blast, barSeed(seed, bar));
                for (int piece = 0; piece < left.length; piece += 4) {
                    if (along >= left[piece] && along <= left[piece + 1] && up >= left[piece + 2] && up <= left[piece + 3]) {
                        return -1;
                    }
                }
            }
            return 0;
        }
        // A light past the ones with a bit of their own cannot be broken, and withBroken draws it whole: it is no light to a shot.
        for (int pane = 0; pane < Math.min(panes.length, MOST_LIGHTS * 5); pane += 5) {
            if (along >= panes[pane] && along <= panes[pane + 1] && up >= panes[pane + 2] && up <= panes[pane + 3]) {
                return pane / 5;
            }
        }
        return -1;
    }

    /** Whether a thing is a door the builds draw as a leaf. */
    static boolean isDoor(IsoObject object) {
        return object instanceof IsoDoor || object instanceof IsoThumpable thumpable && thumpable.isDoor();
    }

    /** The first door shut on a square's north or west edge, or null. */
    static IsoObject shutDoorOn(IsoGridSquare square, boolean north) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (shutOn(objects.get(i), north)) {
                return objects.get(i);
            }
        }
        return null;
    }

    /** Whether a thing is a door shut on its square's north edge, or its west one. */
    static boolean shutOn(IsoObject object, boolean north) {
        return object instanceof IsoDoor door ? door.getNorth() == north && !door.IsOpen()
                : object instanceof IsoThumpable thumpable && thumpable.isDoor() && thumpable.getNorth() == north && !thumpable.IsOpen();
    }

    /** Whether a door is barricaded on either side. */
    static boolean barricaded(IsoObject door) {
        return door instanceof IsoDoor hinged ? hinged.isBarricaded() : ((IsoThumpable) door).isBarricaded();
    }

    /** The panes of a leaf, five numbers a light as Windows.Shape.panes has them, with those of the lights broken left with no glass. */
    static float[] withBroken(float[] panes, int lights) {
        if (lights == 0) {
            return panes;
        }
        float[] left = panes.clone();
        for (int light = 0; light * 5 < left.length && light < MOST_LIGHTS; light++) {
            if ((lights & SMASHED) != 0 || (lights & 1 << light) != 0) {
                left[light * 5 + 4] = 0f;
            }
        }
        return left;
    }

    /** The box round a leaf's lights (five numbers a light, as Windows.Shape.panes has them): along from, along to, foot and head. */
    static float[] sash(float[] panes) {
        float[] sash = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int pane = 0; pane < panes.length; pane += 5) {
            sash[0] = Math.min(sash[0], panes[pane]);
            sash[1] = Math.max(sash[1], panes[pane + 1]);
            sash[2] = Math.min(sash[2], panes[pane + 2]);
            sash[3] = Math.max(sash[3], panes[pane + 3]);
        }
        return sash;
    }

    /** Whether the middle of a bar or bead from along start to end, foot to head, is inside a sash: a blast that smashes the sash splinters it. */
    static boolean inSash(float[] sash, float start, float end, float foot, float head) {
        float along = (start + end) / 2f, up = (foot + head) / 2f;
        return along > sash[0] && along < sash[1] && up > sash[2] && up < sash[3];
    }
}
