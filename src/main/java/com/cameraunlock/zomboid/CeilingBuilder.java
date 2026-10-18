package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The ceilings of a chunk being built: the ceiling of a room, the lining under a floor that is a roof over open air,
 * and the band hung round the edge of such a canopy. One to a frame of the world's picture, and it writes into that frame's meshes.
 */
final class CeilingBuilder {
    private static final int[] CEILING_CORNERS = {0, 1, 2, 0, 2, 3};
    /** How far a canopy is looked across for its edge, in squares, and the least and most deep one is made, in tiles. */
    private static final int CANOPY_SPAN = 24;
    private static final float CANOPY_SHALLOWEST = 0.25f, CANOPY_DEEPEST = 1.2f;

    private final WorldScene world;
    private final WorldScene.Frame build;

    CeilingBuilder(WorldScene world, WorldScene.Frame build) {
        this.world = world;
        this.build = build;
    }

    /**
     * A room has a plaster ceiling at the top of its level, lit by the room. Under a floor too:
     * the boards or carpet of the room upstairs are not what its ceiling is made of. Over the
     * rest of a room the map has only what the game's view from above shows, the roof's near
     * slopes or nothing at all, and from inside the room that is rafters and sky. Left open
     * where nothing is laid over the square: one the stairs go up through or a roof comes down
     * through (openAbove), one with a room over it, which is how the map marks a hall two
     * floors high, and one under a tent's canopy, whose canvas is its ceiling.
     */
    void addRoomCeiling(int x, int y, int z, int localX, int localY, boolean openAbove) {
        IsoGridSquare above = build.cell.getGridSquare(x, y, z + 1);
        boolean floored = above != null && above.getFloor() != null;
        if (!floored && (openAbove || above != null && (above.getRoomID() != -1L || Tents.canvasOn(above)) || build.madeAt(x, y, z))
                || world.cutAway(x, y)) {
            return;
        }
        build.cornerLights(localX, localY);
        SpriteGeometry.Mesh plain = WorldScene.solid.get(WorldScene.PLAIN);
        plain.open = 32f * WorldScene.CONCRETE;
        plain.sway = 0f;
        plain.face(0f, 0f, -1f);
        float up = (z + 1) * ViewCamera.LEVEL_HEIGHT;
        WorldScene.layers.sheet(-1f);
        for (int corner : CEILING_CORNERS) {
            plain.unlit = build.frame.unlitAt(corner == 0 ? 1f : 0f, corner == 1 ? 1f : 0f, corner == 2 ? 1f : 0f, corner == 3 ? 1f : 0f);
            plain.add(x + (corner == 1 || corner == 2 ? 1f : 0f), y + (corner >= 2 ? 1f : 0f), up + build.geometry.lift[corner], 0f, 0f,
                    build.frame.cornerRed[corner], build.frame.cornerGreen[corner], build.frame.cornerBlue[corner]);
        }
        plain.unlit = SpriteGeometry.AS_FACE;
        WorldScene.layers.none();
    }

    /**
     * The band hung along the south edge of the canopy a square is part of, or along its east
     * edge: the sprite the map hangs, a level down, on the first square past the canopy that
     * way. Null where the canopy has none there. Leaves the band's square in bandX and bandY.
     */
    IsoObject fasciaOf(int x, int y, int z, boolean south) {
        for (int step = 1; step <= CANOPY_SPAN; step++) {
            int atX = south ? x : x + step, atY = south ? y + step : y;
            if (WorldScene.isCanopy(build.cell.getGridSquare(atX, atY, z), build.cell.getGridSquare(atX, atY, z - 1))) {
                continue;
            }
            IsoGridSquare under = build.cell.getGridSquare(atX, atY, z - 1);
            if (under == null) {
                return null;
            }
            PZArrayList<IsoObject> objects = under.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoSprite sprite = objects.get(i).getSprite();
                if (sprite == null) {
                    continue;
                }
                PropertyContainer properties = sprite.getProperties();
                if (properties.has(south ? IsoFlagType.attachedN : IsoFlagType.attachedW) && !WorldScene.isWall(properties, sprite.getType()) && WorldScene.textureOf(objects.get(i)) != null) {
                    build.bandX = atX;
                    build.bandY = atY;
                    return objects.get(i);
                }
            }
            return null;
        }
        return null;
    }

    /**
     * How deep the canopy a square is part of is: as deep as the band the map hangs along its
     * south or its east edge is high. 0 for a floor with no such band, which is no canopy
     * but a balcony or a bridge, and keeps the underside it has.
     */
    float canopyDrop(int x, int y, int z) {
        long key = ((long) x << 36) ^ ((long) y << 8) ^ z;
        Float known = WorldScene.canopies.get(key);
        if (known != null) {
            return known;
        }
        float drop = 0f;
        for (int way = 0; way < 2 && drop == 0f; way++) {
            IsoObject band = fasciaOf(x, y, z, way == 0);
            if (band == null) {
                continue;
            }
            build.bandFrame.scale = build.frame.scale;
            WorldScene.frameOf(build.bandFrame, band, WorldScene.textureOf(band));
            if (WallHung.painted(build.bandFrame, way == 0 ? WallHung.NORTH : WallHung.WEST, build.hungAt)) {
                drop = Math.clamp((1f - build.hungAt[WallHung.BOTTOM]) * ViewCamera.LEVEL_HEIGHT, CANOPY_SHALLOWEST, CANOPY_DEEPEST);
            }
        }
        // Kept only once the map is loaded as far as the search reaches: before that the answer may be short of the truth.
        if (build.cell.getGridSquare(x + CANOPY_SPAN, y, 0) != null && build.cell.getGridSquare(x, y + CANOPY_SPAN, 0) != null) {
            WorldScene.canopies.put(key, drop);
        }
        return drop;
    }

    /** Whether a square out of doors has a canopy over it, one or two levels up, whose lights are on. */
    boolean underLitCanopy(int chunkX, int chunkY, int x, int y, int z) {
        for (int up = z + 1; up <= z + 2; up++) {
            IsoGridSquare over = build.squareAt(chunkX, chunkY, x, y, up);
            if (over == null || over.getFloor() == null) {
                continue;
            }
            build.canopied = true;
            return WorldScene.isCanopy(over, build.squareAt(chunkX, chunkY, x, y, up - 1)) && (build.mainsOn || over.haveElectricity()) && canopyDrop(x, y, up) > 0f;
        }
        return false;
    }
}
