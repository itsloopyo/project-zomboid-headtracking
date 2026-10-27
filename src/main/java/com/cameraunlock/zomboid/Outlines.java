package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import zombie.core.Color;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.fboRenderChunk.FBORenderObjectOutline;
import zombie.iso.objects.IsoCurtain;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoLightSwitch;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;
import zombie.iso.objects.IsoWorldInventoryObject;

/**
 * What the picture draws a line round: what the Interact key would act on, and what the game
 * has outlined for the player, such as the container the loot panel shows, in the colour the
 * game gave it. The game draws its own outline into its own view, under the picture.
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

    /** What one picture outlines: each thing's key, and its colour as red, green and blue by turns. */
    record Frame(long[] keys, float[] colours) {}

    private static Field outlined;

    private Outlines() {}

    /**
     * The key of a thing that may be outlined, or NONE. The things the Interact key acts on,
     * every container, what lies on the ground and the bodies: the game outlines nothing else
     * that a player in first person can be shown. Taken as an Object so that a caller handing
     * it a body or an item has its class looked up only when this runs: see TestMap, whose map
     * has neither.
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
        if (object.getContainer() == null && !(object instanceof IsoDoor) && !(object instanceof IsoWindow) && !(object instanceof IsoCurtain)
                && !(object instanceof IsoLightSwitch) && !(object instanceof IsoThumpable thumpable && thumpable.isDoor()) && !CentreUse.clickedThoughEmpty(object)) {
            return NONE;
        }
        IsoGridSquare square = object.getSquare();
        return square == null ? NONE : key(square, OBJECT, square.getObjects().indexOf(object));
    }

    /** The key of the piece a drag would put down whose first part stands on the square: see DragAim.Ghost. */
    static long ghost(IsoGridSquare square) {
        return key(square, GHOST, 0);
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
     * game's Lua outlines and lets go of things.
     *
     * @param interact the key of what the Interact key would act on, or NONE
     */
    static Frame gather(int playerIndex, long interact) {
        ArrayList<IsoObject> game = null;
        for (IsoObject object : outlinedByTheGame()) {
            if (object.isOutlineHighlight(playerIndex) && key(object) != NONE) {
                if (game == null) {
                    game = new ArrayList<>();
                }
                game.add(object);
            }
        }
        int count = (interact != NONE ? 1 : 0) + (game == null ? 0 : game.size());
        if (count == 0) {
            return null;
        }
        long[] keys = new long[count];
        float[] colours = new float[count * 3];
        int at = 0;
        if (game != null) {
            for (IsoObject object : game) {
                int abgr = object.getOutlineHighlightCol(playerIndex);
                keys[at] = key(object);
                colours[at * 3] = Color.getRedChannelFromABGR(abgr);
                colours[at * 3 + 1] = Color.getGreenChannelFromABGR(abgr);
                colours[at * 3 + 2] = Color.getBlueChannelFromABGR(abgr);
                at++;
            }
        }
        // Last, so that its line is the one drawn where it is also outlined by the game.
        if (interact != NONE) {
            keys[at] = interact;
            colours[at * 3] = INTERACT_RED;
            colours[at * 3 + 1] = INTERACT_GREEN;
            colours[at * 3 + 2] = INTERACT_BLUE;
        }
        return new Frame(keys, colours);
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
        keys[0] = ghost(square);
        colours[0] = ghost.fits() ? FITS_RED : CLASHES_RED;
        colours[1] = ghost.fits() ? FITS_GREEN : CLASHES_GREEN;
        colours[2] = ghost.fits() ? FITS_BLUE : CLASHES_BLUE;
        if (gathered != null) {
            System.arraycopy(gathered.keys, 0, keys, 1, others);
            System.arraycopy(gathered.colours, 0, colours, 3, others * 3);
        }
        return new Frame(keys, colours);
    }

    @SuppressWarnings("unchecked")
    private static HashSet<IsoObject> outlinedByTheGame() {
        try {
            if (outlined == null) {
                outlined = FBORenderObjectOutline.class.getDeclaredField(Links.OUTLINED.name());
                outlined.setAccessible(true);
            }
            return (HashSet<IsoObject>) outlined.get(FBORenderObjectOutline.getInstance());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("the things the game outlines could not be read", failure);
        }
    }
}
