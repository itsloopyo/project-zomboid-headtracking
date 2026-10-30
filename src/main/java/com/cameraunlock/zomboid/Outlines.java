package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import zombie.core.Color;
import zombie.core.textures.ColorInfo;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.WorldMarkers;
import zombie.iso.fboRenderChunk.FBORenderObjectOutline;
import zombie.iso.objects.IsoWorldInventoryObject;

/**
 * What the picture draws a line round: what the Interact key would act on, and what the game
 * has outlined, highlighted or ringed on the ground for the player, such as the container the
 * loot panel shows, in the colour the game gave it. The game draws its own into its own view,
 * under the picture.
 *
 * A build of a chunk notes which of its triangles and pieces each thing that may be outlined
 * drew (SpriteGeometry.MeshSet.own, Models.Instances.key), and the picture draws those again
 * into a mask that the developed picture draws the line round. A thing is known by where it
 * is and not by the object: a build that stands while its chunk is unloaded and loaded again
 * is of new objects in the same places.
 */
final class Outlines {
    /** No thing: what is drawn while no thing that may be outlined is being built. */
    static final long NONE = -1L;
    private static final int OBJECT = 0, ITEM = 1, BODY = 2, GHOST = 3;
    /** The most things on one square's list a key can tell apart. */
    private static final int INDICES = 1 << 12;
    /** The colour of the line round what the Interact key would act on. */
    static final float INTERACT_RED = 1f, INTERACT_GREEN = 0.93f, INTERACT_BLUE = 0.62f;
    /** The colours of the line round the piece a drag would put down (DragAim.Ghost), where it fits and where it does not, as the outline of its square. */
    static final float FITS_RED = 0.2f, FITS_GREEN = 1f, FITS_BLUE = 0.3f, CLASHES_RED = 1f, CLASHES_GREEN = 0.25f, CLASHES_BLUE = 0.2f;

    /**
     * What one picture outlines: each thing's key, its colour as red, green and blue by turns,
     * and whether it is a floor (see Pipeline.FLOOR_NEARER).
     */
    record Frame(long[] keys, float[] colours, boolean[] floors) {}

    /** The game's fields read by name, each looked up once. */
    private static final Field[] OUTLINED = new Field[1], MARKERS = new Field[1];
    /**
     * What the game has highlighted (IsoObject.setHighlighted) and not yet let go of. The game's
     * own list (FBORenderObjectHighlight) drops a thing it draws into a chunk's texture once it
     * is drawn there, which is most of what stands still, and keeps only the thing's flag.
     */
    private static final HashSet<IsoObject> HIGHLIGHTS = new HashSet<>();

    private Outlines() {}

    /**
     * The key of a thing that may be outlined, or NONE. Every object on a square, floors
     * included, since the game's Lua may outline or highlight any of them (the tutorial its
     * sink and the floor by a window); what lies on the ground; and the bodies that hold
     * things. Taken as an Object so that a caller handing it a body or an item has its class
     * looked up only when this runs: see TestMap, whose map has neither.
     */
    static long key(Object thing) {
        IsoObject object = (IsoObject) thing;
        if (object instanceof IsoWorldInventoryObject item) {
            IsoGridSquare square = item.getSquare();
            return square == null ? NONE : key(square, ITEM, square.getWorldObjects().indexOf(item));
        }
        if (object instanceof IsoMovingObject body) {
            IsoGridSquare square = body.getCurrentSquare();
            return square == null || body.getContainer() == null ? NONE : key(square, BODY, square.getStaticMovingObjects().indexOf(body));
        }
        IsoGridSquare square = object.getSquare();
        return square == null ? NONE : key(square, OBJECT, square.getObjects().indexOf(object));
    }

    /** The key of the piece a drag would put down whose first part stands on the square: see DragAim.Ghost. */
    static long ghost(IsoGridSquare square) {
        return key(square, GHOST, 0);
    }

    /** The x of the square a thing with this key stands on, as far as a key holds it. */
    static int squareX(long key) {
        return (int) (key >>> 42 & 0x3FFFFF);
    }

    /** The y of the square a thing with this key stands on, as far as a key holds it. */
    static int squareY(long key) {
        return (int) (key >>> 20 & 0x3FFFFF);
    }

    private static long key(IsoGridSquare square, int kind, int index) {
        if (index < 0 || index >= INDICES) {
            return NONE;
        }
        return ((long) square.getX() & 0x3FFFFF) << 42 | ((long) square.getY() & 0x3FFFFF) << 20 | ((long) square.getZ() + 32 & 0x3F) << 14
                | (long) kind << 12 | index;
    }

    /**
     * What a player's picture outlines this frame, or null for nothing. Logic thread, where the
     * game's Lua outlines, highlights and marks things and lets go of them.
     *
     * The game draws each of these into its own view, which is under the picture: what it
     * highlights (a tint over the thing, such as the tutorial's sink and the floor by its
     * window), what it outlines (the container the loot panel shows), and the rings it lays
     * on the ground (the tutorial's places to walk to), whose floors are outlined here as far
     * as the ring reaches. A thing both highlighted and outlined takes the outline's colour,
     * which is the one drawn after.
     *
     * @param interact the key of what the Interact key would act on, or NONE
     */
    static Frame gather(int playerIndex, long interact) {
        Gathering gathering = GATHERING;
        gathering.count = 0;
        synchronized (HIGHLIGHTS) {
            for (Iterator<IsoObject> each = HIGHLIGHTS.iterator(); each.hasNext(); ) {
                IsoObject object = each.next();
                if (!object.isHighlighted() || object.getSquare() == null) {
                    each.remove();
                    continue;
                }
                long key = object.isHighlighted(playerIndex) ? key(object) : NONE;
                if (key != NONE) {
                    ColorInfo colour = object.getHighlightColor(playerIndex);
                    gathering.add(key, colour.r, colour.g, colour.b, isFloor(object));
                }
            }
        }
        IsoCell cell = IsoWorld.instance.currentCell;
        for (WorldMarkers.GridSquareMarker marker : Outlines.<List<WorldMarkers.GridSquareMarker>>read(MARKERS, Links.GROUND_MARKERS, WorldMarkers.instance)) {
            if (!marker.isActive() || marker.isRemoved()) {
                continue;
            }
            int x = (int) marker.getOriginalX(), y = (int) marker.getOriginalY(), z = (int) marker.getOriginalZ();
            int reach = (int) Math.floor(marker.getSize());
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    IsoGridSquare square = dx * dx + dy * dy > marker.getSize() * marker.getSize() ? null : cell.getGridSquare(x + dx, y + dy, z);
                    IsoObject floor = square == null ? null : square.getFloor();
                    long key = floor == null ? NONE : key(floor);
                    if (key != NONE) {
                        gathering.add(key, marker.getR(), marker.getG(), marker.getB(), true);
                    }
                }
            }
        }
        for (IsoObject object : Outlines.<HashSet<IsoObject>>read(OUTLINED, Links.OUTLINED, FBORenderObjectOutline.getInstance())) {
            long key = object.isOutlineHighlight(playerIndex) ? key(object) : NONE;
            if (key != NONE) {
                int abgr = object.getOutlineHighlightCol(playerIndex);
                gathering.add(key, Color.getRedChannelFromABGR(abgr), Color.getGreenChannelFromABGR(abgr), Color.getBlueChannelFromABGR(abgr),
                        isFloor(object));
            }
        }
        // Last, so that its line is the one drawn where it is also outlined by the game.
        if (interact != NONE) {
            gathering.add(interact, INTERACT_RED, INTERACT_GREEN, INTERACT_BLUE, false);
        }
        return gathering.count == 0 ? null : new Frame(Arrays.copyOf(gathering.keys, gathering.count), Arrays.copyOf(gathering.colours, gathering.count * 3),
                Arrays.copyOf(gathering.floors, gathering.count));
    }

    static void highlighted(IsoObject object) {
        synchronized (HIGHLIGHTS) {
            HIGHLIGHTS.add(object);
        }
    }

    static void unhighlighted(IsoObject object) {
        synchronized (HIGHLIGHTS) {
            HIGHLIGHTS.remove(object);
        }
    }

    private static boolean isFloor(IsoObject object) {
        IsoGridSquare square = object.getSquare();
        return square != null && square.getFloor() == object;
    }

    /** What one picture outlines as it is gathered: one, filled anew by each gather, on the logic thread. */
    private static final Gathering GATHERING = new Gathering();

    private static final class Gathering {
        long[] keys = new long[8];
        float[] colours = new float[24];
        boolean[] floors = new boolean[8];
        int count;

        void add(long key, float red, float green, float blue, boolean floor) {
            if (count == keys.length) {
                keys = Arrays.copyOf(keys, count * 2);
                colours = Arrays.copyOf(colours, count * 6);
                floors = Arrays.copyOf(floors, count * 2);
            }
            keys[count] = key;
            floors[count] = floor;
            colours[count * 3] = red;
            colours[count * 3 + 1] = green;
            colours[count * 3 + 2] = blue;
            count++;
        }
    }

    /**
     * What a picture outlines, gathered, with the piece a drag would put down (DragAim.Ghost) first,
     * green where it fits and red where it does not; outlined as it is where the drag shows none.
     */
    static Frame withGhost(Frame gathered, DragAim.Ghost ghost) {
        IsoGridSquare square = ghost.square();
        if (square == null) {
            return gathered;
        }
        int others = gathered == null ? 0 : gathered.keys.length;
        long[] keys = new long[others + 1];
        float[] colours = new float[keys.length * 3];
        boolean[] floors = new boolean[keys.length];
        keys[0] = ghost(square);
        colours[0] = ghost.fits() ? FITS_RED : CLASHES_RED;
        colours[1] = ghost.fits() ? FITS_GREEN : CLASHES_GREEN;
        colours[2] = ghost.fits() ? FITS_BLUE : CLASHES_BLUE;
        if (gathered != null) {
            System.arraycopy(gathered.keys, 0, keys, 1, others);
            System.arraycopy(gathered.colours, 0, colours, 3, others * 3);
            System.arraycopy(gathered.floors, 0, floors, 1, others);
        }
        return new Frame(keys, colours, floors);
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(Field[] cache, Links.Field link, Object owner) {
        try {
            if (cache[0] == null) {
                cache[0] = owner.getClass().getDeclaredField(link.name());
                cache[0].setAccessible(true);
            }
            return (T) cache[0].get(owner);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("the things the game draws round or over could not be read: " + link.name(), failure);
        }
    }
}
