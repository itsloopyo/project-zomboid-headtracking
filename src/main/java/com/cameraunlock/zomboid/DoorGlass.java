package com.cameraunlock.zomboid;

import java.lang.ref.WeakReference;
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
 * lights SMASHING times or more smashes the whole sash: every light and the bars between them,
 * which leaves one opening with shards round its edge.
 */
final class DoorGlass {
    private DoorGlass() {}

    /** The key a door's mod data keeps its broken lights under. */
    static final String BROKEN = "cameraunlockBrokenLights";
    /** The bit kept with a door's broken lights where a blast smashed its sash, with every light's bit. */
    static final int SMASHED = 1 << 30;
    /** How many of one blast's pellets through a door's lights smash its sash. */
    static final int SMASHING = 3;

    /** The door the last pellet went through a light of, held weakly to let the world go with the save, the world's age then, and how many of that blast's pellets have. */
    private static WeakReference<IsoObject> blasted = new WeakReference<>(null);
    private static double blastedAt;
    private static int pellets;

    /** The lights broken of each door loaded, for the builds to read off the logic thread. */
    private static final Map<IsoObject, Integer> broken = Collections.synchronizedMap(new WeakHashMap<>());

    /** The lights of a door that are broken, a bit a light. Any thread. */
    static int broken(IsoObject door) {
        Integer lights = broken.get(door);
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
        if (set(door, broken(door) | 1 << light) && DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "a shot broke light %d of the door at (%d, %d, %d)", light, door.getSquare().getX(), door.getSquare().getY(), door.getSquare().getZ()));
        }
    }

    /**
     * A pellet of a blast went through a light of a door: breaks it, and smashes the sash where
     * it is the SMASHING'th of the blast's through the door. A blast's pellets are all fired in
     * one tick of the world, which a world's age tells apart. Logic thread.
     */
    static void pellet(IsoObject door, int light) {
        double now = GameTime.getInstance().getWorldAgeHours();
        if (door != blasted.get() || now != blastedAt) {
            blasted = new WeakReference<>(door);
            blastedAt = now;
            pellets = 0;
        }
        if (++pellets < SMASHING) {
            breakLight(door, light);
        } else if (set(door, SMASHED | SMASHED - 1) && DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "a blast smashed the sash of the door at (%d, %d, %d)", door.getSquare().getX(), door.getSquare().getY(), door.getSquare().getZ()));
        }
    }

    /** Keeps a door's broken lights as `now`, with the sound and noise of breaking glass, and answers whether that broke any. */
    private static boolean set(IsoObject door, int now) {
        if (now == broken(door)) {
            return false;
        }
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
     * no glass or has not been measured. Where its sash is smashed, anywhere in it is its first light.
     */
    static int lightAt(IsoObject door, boolean north, float along, float up) {
        if (door.getSprite() == null || !door.getSprite().getProperties().has("doorTrans")) {
            return -1;
        }
        Doors.Shape shape = Doors.known(door.getSprite().getTextureForCurrentFrame(door.getDir()));
        return shape == null || shape.leaf.north != north ? -1 : lightIn(shape.leaf.panes, broken(door), along, up);
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
        return "wood, lights " + java.util.Arrays.toString(shape.leaf.panes);
    }

    /**
     * Which light (along, up) on a leaf is inside, five numbers a light as Windows.Shape.panes has
     * them, with `lights` broken: -1 for none. Where the sash is smashed, anywhere in it is light 0.
     */
    static int lightIn(float[] panes, int lights, float along, float up) {
        if ((lights & SMASHED) != 0 && panes.length > 0) {
            float[] sash = sash(panes);
            return along >= sash[0] && along <= sash[1] && up >= sash[2] && up <= sash[3] ? 0 : -1;
        }
        for (int pane = 0; pane < panes.length; pane += 5) {
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
        for (int light = 0; light * 5 < left.length && light < 30; light++) {
            if ((lights & 1 << light) != 0) {
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

    /** Whether the middle of a bar or bead from along start to end, foot to head, is inside a sash: it goes when the sash is smashed. */
    static boolean inSash(float[] sash, float start, float end, float foot, float head) {
        float along = (start + end) / 2f, up = (foot + head) / 2f;
        return along > sash[0] && along < sash[1] && up > sash[2] && up < sash[3];
    }
}
