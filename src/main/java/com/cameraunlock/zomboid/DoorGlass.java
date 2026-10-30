package com.cameraunlock.zomboid;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import se.krka.kahlua.vm.KahluaTable;
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
 */
final class DoorGlass {
    private DoorGlass() {}

    /** The key a door's mod data keeps its broken lights under. */
    static final String BROKEN = "cameraunlockBrokenLights";

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
        int was = broken(door), now = was | 1 << light;
        if (now == was) {
            return;
        }
        broken.put(door, now);
        KahluaTable data = door.getModData();
        data.rawset(BROKEN, (double) now);
        IsoGridSquare square = door.getSquare();
        SoundManager.instance.PlayWorldSound("SmashWindow", square, 0.2f, 70f, 1f, true);
        WorldSoundManager.instance.addSound(null, square.getX(), square.getY(), square.getZ(), 10, 20, true, 4f, 15f);
        ChunkMarks.changed(door);
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "a shot broke light %d of the door at (%d, %d, %d)", light, square.getX(), square.getY(), square.getZ()));
        }
    }

    /**
     * The light of a shut door's leaf a shot crosses at `along` the edge and `up` levels over the
     * floor, as the picture measured the leaf (Doors): -1 where it crosses wood, or the door has
     * no glass or has not been measured.
     */
    static int lightAt(IsoObject door, boolean north, float along, float up) {
        if (door.getSprite() == null || !door.getSprite().getProperties().has("doorTrans")) {
            return -1;
        }
        Doors.Shape shape = Doors.known(door.getSprite().getTextureForCurrentFrame(door.getDir()));
        return shape == null || shape.leaf.north != north ? -1 : lightIn(shape.leaf.panes, along, up);
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

    /** Which light (along, up) on a leaf is inside, five numbers a light as Windows.Shape.panes has them: -1 for none. */
    static int lightIn(float[] panes, float along, float up) {
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
        for (int light = 0; light * 5 < left.length && light < Integer.SIZE; light++) {
            if ((lights & 1 << light) != 0) {
                left[light * 5 + 4] = 0f;
            }
        }
        return left;
    }

    /** Whether every light of a leaf is broken. */
    static boolean allBroken(float[] panes, int lights) {
        int count = panes.length / 5;
        return count > 0 && count <= Integer.SIZE - 1 && lights == (1 << count) - 1;
    }
}
