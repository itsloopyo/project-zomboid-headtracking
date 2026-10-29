package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.core.textures.Texture;
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
    /**
     * A ceiling square is four triangles round its middle, CENTRE, which takes the mean of its
     * corners. Split in two, each square's light ran in straight lines along the one diagonal
     * every square shares, and where the light changes from square to square, as it does
     * between the lamps of a hall, the ceiling showed those diagonals as streaks.
     */
    private static final int CENTRE = 4;
    private static final int[] CEILING_CORNERS = {CENTRE, 0, 1, CENTRE, 1, 2, CENTRE, 2, 3, CENTRE, 3, 0};
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
     * floors high, one under a tent's canopy, whose canvas is its ceiling, and one under a glass
     * roof or a skylight, which lets the sky in where a slope of tiles is rafters. Left open too
     * where the square's own floor is a roof's sheet (Roofs.hiddenSheet): the foot of a roof the
     * map puts in a room upstairs, out under the sky, where plaster at the top of the level hung
     * in the air over the roof (2062..2070, 7741, level 1). A square beside
     * one of that room's with no floor is the hall too (TallHalls), or each row left out of it
     * hangs a strip of plaster across the hall at the upper floor. Such a row is given the
     * hall's ceiling at the top of its own level, by the same rules.
     */
    void addRoomCeiling(int x, int y, int z, int localX, int localY, boolean openAbove) {
        IsoGridSquare above = build.cell.getGridSquare(x, y, z + 1);
        boolean floored = above != null && above.getFloor() != null;
        IsoObject floor = build.cell.getGridSquare(x, y, z).getFloor();
        if (!floored && (openAbove || z > 0 && floor != null && Roofs.hiddenSheet(floor) || above != null && (above.getRoomID() != -1L || Tents.canvasOn(above) || glazed(above)) || build.madeAt(x, y, z) || TallHalls.besideHallOver(build.cell, x, y, z + 1))
                || world.cutAway(x, y)) {
            return;
        }
        build.cornerLights(localX, localY);
        SpriteGeometry.Mesh plain = build.solid.get(WorldScene.PLAIN);
        plain.open = 32f * WorldScene.CONCRETE;
        plain.sway = 0f;
        plain.face(0f, 0f, -1f);
        float up = (z + 1) * ViewCamera.LEVEL_HEIGHT;
        build.layers.sheet(-1f);
        float[] lift = build.geometry.lift, red = build.frame.cornerRed, green = build.frame.cornerGreen, blue = build.frame.cornerBlue;
        for (int corner : CEILING_CORNERS) {
            // The weights at a corner are 1 and 0, and at the middle each a quarter, a power of two: the blend gives the bits of the corner's light and of the mean.
            if (corner == CENTRE) {
                plain.litAt(build.frame, 0.5f, 0.5f, 1f, 1f, 1f);
                plain.kindFixed();
                plain.add(x + 0.5f, y + 0.5f, up + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f, 0f, 0f,
                        (red[0] + red[1] + red[2] + red[3]) / 4f, (green[0] + green[1] + green[2] + green[3]) / 4f, (blue[0] + blue[1] + blue[2] + blue[3]) / 4f);
                continue;
            }
            float a = corner == 1 || corner == 2 ? 1f : 0f, b = corner >= 2 ? 1f : 0f;
            plain.litAt(build.frame, a, b, 1f, 1f, 1f);
            plain.kindFixed();
            plain.add(x + a, y + b, up + lift[corner], 0f, 0f, red[corner], green[corner], blue[corner]);
        }
        plain.litAsFace();
        build.layers.none();
    }

    /**
     * The sides of the upper part of a tall hall where the map stands no wall: an edge of a
     * square of the hall (TallHalls.roomOf) to a square in the open air, a level high, plaster
     * as its ceiling is. A hall that narrows as it rises, a nave over its aisles, leaves its
     * upper storey open at the step, and the sky showed between the aisles' ceiling and the
     * nave's. Not over stairs, where an upper floor is open to the stairwell and may stand open
     * to a deck beside it. Not on a square a roof lies on either: the roof is that square's top,
     * and its gables (RoofBuilder) are its sides. Plaster a level high stood in the gables' plane
     * behind them and in the open air over the roof between them (a row of gables over a shop's
     * front, 11928..11944, 6907, level 1).
     */
    void addHallSides(int x, int y, int z, int localX, int localY) {
        IsoGridSquare under = build.cell.getGridSquare(x, y, z - 1);
        IsoGridSquare here = build.cell.getGridSquare(x, y, z);
        if (under == null || overStairs(under) || world.cutAway(x, y) || here != null && Roofs.covered(build.cell, here) || build.madeAt(x, y, z)) {
            return;
        }
        boolean lit = false;
        for (int side = 0; side < 4; side++) {
            int beyondX = side == WEST ? x - 1 : side == EAST ? x + 1 : x, beyondY = side == NORTH ? y - 1 : side == SOUTH ? y + 1 : y;
            if (TallHalls.roomOf(build.cell, beyondX, beyondY, z) != RoomSight.OUTSIDE || walled(x, y, z, side)) {
                continue;
            }
            if (!lit) {
                build.cornerLights(localX, localY);
                lit = true;
            }
            // From P to Q the hall is on the left, so the face looks into it.
            int p = SIDE_FROM[side], q = SIDE_TO[side];
            float[] lift = build.geometry.lift, red = build.frame.cornerRed, green = build.frame.cornerGreen, blue = build.frame.cornerBlue;
            float floor = z * ViewCamera.LEVEL_HEIGHT;
            float[] c = build.reveal;
            c[0] = x + CORNER_X[p]; c[1] = y + CORNER_Y[p]; c[2] = floor + lift[p];
            c[3] = x + CORNER_X[q]; c[4] = y + CORNER_Y[q]; c[5] = floor + lift[q];
            c[6] = c[3]; c[7] = c[4]; c[8] = c[5] + ViewCamera.LEVEL_HEIGHT;
            c[9] = c[0]; c[10] = c[1]; c[11] = c[2] + ViewCamera.LEVEL_HEIGHT;
            SpriteGeometry.Mesh plain = build.solid.get(WorldScene.PLAIN);
            plain.open = 32f * WorldScene.CONCRETE;
            plain.sway = 0f;
            // At the middle of the side, the weights of its two corners are each a half, a power of two: the blend gives the bits of their mean.
            plain.litAt(build.frame, (CORNER_X[p] + CORNER_X[q]) / 2f, (CORNER_Y[p] + CORNER_Y[q]) / 2f, 1f, 1f, 1f);
            plain.kindFixed();
            SpriteGeometry.addPlainQuad(plain, c, (red[p] + red[q]) / 2f, (green[p] + green[q]) / 2f, (blue[p] + blue[q]) / 2f);
            plain.litAsFace();
        }
    }

    private static final int NORTH = 0, SOUTH = 1, WEST = 2, EAST = 3;
    /** A square's corners (x, y), (x + 1, y), (x + 1, y + 1), (x, y + 1), and each side's ends in the order that faces into the square. */
    private static final float[] CORNER_X = {0f, 1f, 1f, 0f}, CORNER_Y = {0f, 0f, 1f, 1f};
    private static final int[] SIDE_FROM = {1, 3, 0, 2}, SIDE_TO = {0, 2, 3, 1};

    /** Whether a wall, door or window stands on the side of a square: one on its north or west edge is its own, one on its south or east edge is the next square's. */
    private boolean walled(int x, int y, int z, int side) {
        boolean north = side == NORTH || side == SOUTH;
        IsoGridSquare owner = build.cell.getGridSquare(side == EAST ? x + 1 : x, side == SOUTH ? y + 1 : y, z);
        if (owner == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = owner.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite == null) {
                continue;
            }
            PropertyContainer flags = sprite.getProperties();
            if (flags.has(IsoFlagType.WallNW) || (north ? flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.DoorWallN) || flags.has(IsoFlagType.WindowN)
                    : flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.DoorWallW) || flags.has(IsoFlagType.WindowW))) {
                return true;
            }
        }
        return false;
    }

    private static boolean overStairs(IsoGridSquare square) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && WorldScene.isStair(sprite.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a roof laid on the square is glass (SeeThrough). Not while a sprite of it is still
     * to be read: the build is made again once it has been.
     */
    private boolean glazed(IsoGridSquare square) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite == null || sprite.getName() == null || !sprite.getName().startsWith("roofs")) {
                continue;
            }
            Texture texture = WorldScene.textureOf(objects.get(i));
            if (texture == null) {
                continue;
            }
            Boolean glass = SeeThrough.of(texture);
            if (glass == null) {
                build.building.signed = false;
            } else if (glass) {
                return true;
            }
        }
        return false;
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
        // Marks are read before the squares, so a change marked while they are read leaves the count above the kept one, and the depth is worked out again.
        int marks = marksRead(x, y, z);
        Depth known = WorldScene.canopies.get(key);
        // A band or a square of canopy taken down or put up is marked on its chunk. Without the chunk-mark patches in, nothing is
        // marked, and the depth is worked out again as often as WallFinish reads a building's finishes again.
        if (known != null && known.marks() == marks && (ChunkMarks.live || world.roundNow - known.round() < WallFinish.KEPT)) {
            return known.drop();
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
        // Kept only once every chunk the search reaches is loaded: before that the answer may be short of the truth, and what a
        // chunk is filled with as it loads is not marked. The build is made again once more of the map is loaded.
        if (filled(x, y)) {
            WorldScene.canopies.replace(key, new Depth(drop, marks, world.roundNow));
        } else {
            build.wantsMap = true;
        }
        return drop;
    }

    /** Whether every chunk fasciaOf reads from a square, south and east, is loaded and filled: one square of each is looked at, on the ground, which a loaded chunk has whole. */
    private boolean filled(int x, int y) {
        int wx = Math.floorDiv(x, WorldScene.CHUNK_TILES), wy = Math.floorDiv(y, WorldScene.CHUNK_TILES);
        for (int east = wx; east <= Math.floorDiv(x + CANOPY_SPAN, WorldScene.CHUNK_TILES); east++) {
            if (!filled(build.cell.getGridSquare(east * WorldScene.CHUNK_TILES, y, 0))) {
                return false;
            }
        }
        for (int south = wy + 1; south <= Math.floorDiv(y + CANOPY_SPAN, WorldScene.CHUNK_TILES); south++) {
            if (!filled(build.cell.getGridSquare(x, south * WorldScene.CHUNK_TILES, 0))) {
                return false;
            }
        }
        return true;
    }

    private static boolean filled(IsoGridSquare square) {
        return square != null && square.getChunk().loaded;
    }

    /** How deep canopyDrop found a canopy, the marks on the chunks it read when it did (marksRead), and the round of builds it did in. */
    record Depth(float drop, int marks, int round) {}

    /** The marks for what may be built on the chunks fasciaOf reads from a square, south and east, on the square's level and the one under it. */
    private static int marksRead(int x, int y, int z) {
        int wx = Math.floorDiv(x, WorldScene.CHUNK_TILES), wy = Math.floorDiv(y, WorldScene.CHUNK_TILES);
        return ChunkMarks.structuresIn(wx, wy, wx, Math.floorDiv(y + CANOPY_SPAN, WorldScene.CHUNK_TILES), z - 1, z)
                + ChunkMarks.structuresIn(wx, wy, Math.floorDiv(x + CANOPY_SPAN, WorldScene.CHUNK_TILES), wy, z - 1, z);
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
