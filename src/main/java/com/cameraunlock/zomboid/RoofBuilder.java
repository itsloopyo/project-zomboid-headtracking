package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import zombie.core.properties.PropertyContainer;
import zombie.core.textures.Texture;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.SpriteDetails.IsoFlagType;

/**
 * The roofs of a chunk being built: the slopes, the backs under them, the caps, the eaves and their gutters, the rakes
 * and the gable walls. One to a frame of the world's picture, and it writes into that frame's meshes.
 */
final class RoofBuilder {
    /** How far a roof runs out past its wall, and how deep the board along its end is, in tiles. */
    private static final float EAVE = 0.22f, FASCIA = 0.12f, RAKE = 0.14f, GUTTER = 0.07f, PIPE = 0.035f;
    /**
     * The cap along a ridge or down a hip, in tiles: how far it reaches either side of the line,
     * half the width of its level crown, how far the crown stands over the line and how far its
     * edges stand off the roof.
     */
    private static final float CAP_HALF = 0.11f, CAP_CROWN = 0.035f, CAP_RISE = 0.03f, CAP_CLEAR = 0.008f;
    /** How many layers of a floor a flat roof's covering lies above the floor it is on: more than a floor has of its own. */
    static final float FLAT_ROOF_LAYERS = 4f;
    /** A flat roof read lower over its floor than this, in levels, lies on the floor. */
    private static final float FLAT_ROOF_ON_FLOOR = 0.06f;
    /** The cap along a ridge or a hip: the roof's own covering, which the picture draws a shade darker. */
    static final float RIDGE = 53f;
    /** A roof's edge lower than this over the floor of its level, in levels, has no wall made under it. */
    private static final float GABLE_LEAST = 0.05f;
    /** The west, east, north and south edge of a square, each as its two corners. */
    private static final int[][] EDGES = {{0, 3}, {1, 2}, {0, 1}, {3, 2}};
    /** How deep the board under a gable's edge is, in tiles: deep enough to cover where the wall's own pieces stop short of the roof. */
    private static final float BARGE = 0.2f;

    private final WorldScene world;
    private final WorldScene.Frame build;

    private float steepest;
    private final float[] folded = new float[9];
    private final float[] heldLift = new float[4];
    /** The roof the tile being added is part of, or null. */
    private Roofs.Roof roof;
    private float roofU, roofV;
    private final float[] gable = new float[4];
    /** The level buildingUnder last found a building on, and the room it found there. */
    private int foundOn;
    private long foundIn;
    /** Which of a folded tile's west, east, north and south edges is an eave. */
    private final boolean[] foldEave = new boolean[4];
    private final float[] cutWall = new float[12];
    private final float[] barge = new float[12];
    private final float[] pipeFrom = new float[3], pipeTo = new float[3];
    /**
     * What eaveRow found for each square it was asked about in this frame's build, by
     * RoofFit.key, and each level's squares as Hoods reads them. Each piece of a row and the
     * corner past it asks for its own square and the four beside it, every eave asks for the
     * square past it, and a square with no roof on it is read from the map at every asking.
     */
    private final HashMap<Long, Boolean> eaveRows = new HashMap<>();
    private final HashMap<Integer, Hoods.Squares> hoodLevels = new HashMap<>();
    /** What coveredIn made of each sprite in this frame's build. */
    private final IdentityHashMap<Texture, Roofs.Roof> coverings = new IdentityHashMap<>();

    RoofBuilder(WorldScene world, WorldScene.Frame build) {
        this.world = world;
        this.build = build;
    }

    /** For a frame's build of the map as it is now: what was found in the last is let go. */
    void newBuild() {
        eaveRows.clear();
        hoodLevels.clear();
        coverings.clear();
    }

    private Hoods.Squares hoods(int z) {
        Hoods.Squares level = hoodLevels.get(z);
        if (level == null) {
            level = Hoods.on(build.cell, z);
            hoodLevels.put(z, level);
        }
        return level;
    }

    /**
     * Lays the tiles of the slopes the map leaves out that lie in a chunk, and the caps of the
     * ridges and hips in it: see RoofFit.backs and RoofFit.caps. They are of the roof's own
     * colour, and lit as the open air is: no square of the map holds them, so none has a
     * light for them.
     */
    void addBacks(IsoChunk chunk, int chunkX, int chunkY) {
        ArrayList<Roofs.Laid> backs = Roofs.madeIn(build.cell, chunk.wx, chunk.wy, world.roundNow, build.frame.scale);
        ArrayList<Roofs.Capped> caps = Roofs.cappedIn(build.cell, chunk.wx, chunk.wy, world.roundNow, build.frame.scale);
        if (backs == null && caps == null) {
            return;
        }
        build.building.plain = false;
        float[] ground = world.terrain.corners(build.cell, chunk.wx, chunk.wy);
        build.frame.light(1f, 1f, 1f);
        build.frame.open = WorldScene.OPEN_UNLIT;
        build.frame.kind = 0f;
        // A gable under one of these is a plain wall with nothing over it: see laid.
        build.overLayer = 0;
        build.gableSheet = false;
        float[] lift = build.geometry.lift;
        for (int i = 0; backs != null && i < backs.size(); i++) {
            RoofFit.Tile tile = backs.get(i).tile;
            if (world.cutAway(tile.x, tile.y)) {
                continue;
            }
            liftOf(ground, tile.x - chunkX, tile.y - chunkY);
            roof = backs.get(i).roof;
            SpriteGeometry.Mesh sheet = roofSheet();
            if (tile.carried) {
                // A slope carried into a valley is its plane and nothing else: it has no edge of its own on this square.
                if (tile.peak > 0f) {
                    addPeak(sheet, tile);
                } else {
                    float[] over = tile.corners;
                    SpriteGeometry.addOver(sheet, tile.x, tile.y, over[0] * ViewCamera.LEVEL_HEIGHT + lift[0], over[1] * ViewCamera.LEVEL_HEIGHT + lift[1],
                            over[2] * ViewCamera.LEVEL_HEIGHT + lift[2], over[3] * ViewCamera.LEVEL_HEIGHT + lift[3], roofU, roofV, build.frame.red, build.frame.green, build.frame.blue);
                }
                continue;
            }
            addGable(tile.x, tile.y, tile.z, tile.corners[0] - tile.z, tile.corners[1] - tile.z, tile.corners[3] - tile.z, tile.peak, tile.peakAxis);
            // A made crown on the floor of its level has its gable a level down, as the map's own has (addRoof): left out, the
            // north end of a gable seven squares wide stood open from the loft's floor to the ridge under it (7408, 6533).
            if (RoofFit.levelOnItsFloor(tile) && tile.z > 0) {
                addCrownGable(tile.x, tile.y, tile.z);
            }
            if (tile.shaped) {
                addFolded(sheet, tile, 3, false);
                addFoldEaves(sheet, tile);
                continue;
            }
            if (tile.peak > 0f) {
                addPeak(sheet, tile);
                continue;
            }
            float[] at = tile.corners;
            SpriteGeometry.addOver(sheet, tile.x, tile.y, at[0] * ViewCamera.LEVEL_HEIGHT + lift[0], at[1] * ViewCamera.LEVEL_HEIGHT + lift[1],
                    at[2] * ViewCamera.LEVEL_HEIGHT + lift[2], at[3] * ViewCamera.LEVEL_HEIGHT + lift[3], roofU, roofV, build.frame.red, build.frame.green, build.frame.blue);
            addEave(sheet, tile.x, tile.y, tile.z, at[0] - tile.z, at[1] - at[0], at[3] - at[0], true);
            addRake(sheet, tile.x, tile.y, tile.z, at[0] - tile.z, at[1] - at[0], at[3] - at[0]);
        }
        for (int i = 0; caps != null && i < caps.size(); i++) {
            RoofFit.Cap cap = caps.get(i).cap;
            if (turf(caps.get(i).roof.finish) || world.cutAway((int) Math.floor((cap.x0 + cap.x1) / 2f), (int) Math.floor((cap.y0 + cap.y1) / 2f))) {
                continue;
            }
            liftOf(ground, Math.clamp((int) Math.floor((cap.x0 + cap.x1) / 2f) - chunkX, 0, WorldScene.CHUNK_TILES - 1),
                    Math.clamp((int) Math.floor((cap.y0 + cap.y1) / 2f) - chunkY, 0, WorldScene.CHUNK_TILES - 1));
            roof = caps.get(i).roof;
            addCap(cap);
        }
    }

    /** The building that stands under a square of a roof on level z, on the lowest level it is found on, or null for a roof over open ground. */
    private IsoBuilding buildingUnder(int x, int y, int z) {
        for (int level = 0; level <= z; level++) {
            IsoGridSquare under = build.cell.getGridSquare(x, y, level);
            IsoBuilding standing = under == null ? null : under.getBuilding();
            if (standing != null) {
                foundOn = level;
                foundIn = under.getRoomID();
                return standing;
            }
        }
        return null;
    }

    /**
     * The wall under the end of a roof. The game's view sees a house from the south-east, and
     * its maps have the walls that close a roof's ends, the gables, only where that view
     * sees them: at the east and south ends. A roof that ends at the west or the north has
     * nothing under its edge, and from that side one looks in under the slopes. So under the
     * west and the north edge of a roof tile, where the building under it goes no further and
     * the map has no wall, the wall is made: from the floor of the tile's level up to the roof, in the
     * sprite the building's east or south face shows. The heights are the roof's over that
     * floor, in levels, at the tile's north-west, north-east and south-west corners. peak is how
     * far a ridge along the middle of the tile stands over them, and peakAxis the axis the
     * roof climbs to it along: see RoofFit.peaks.
     */
    private void addGable(int x, int y, int z, float northWest, float northEast, float southWest, float peak, int peakAxis) {
        // A roof that runs out past its building's wall has no wall under its own edge: the
        // gable is under the last tile that has the building beneath it.
        IsoBuilding standing = buildingUnder(x, y, z);
        boolean porch = standing == null;
        if (porch) {
            // Or a roof carried on past its building over a porch: from the porch the end was
            // open up under both slopes, and is closed above the porch's beam, the floor of z. A
            // roof run a square past its wall over the ground is the eave of the gable under it.
            standing = z > 0 && builtFloorUnder(x, y, z) ? buildingAlongTheRoof(x, y, z) : null;
            if (standing == null) {
                return;
            }
        }
        int ground = foundOn;
        long room = foundIn;
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        outdoors();
        addGableWalls(square, standing, ground, room, x, y, z, northWest, northEast, southWest, peak, peakAxis, porch);
        indoors();
    }

    /** How far a roof is followed south or east from a square with no building under it to the building it comes off, in squares. */
    private static final int PORCH_DEEP = 3;

    /** Whether the square under a roof tile on (x, y, z) is floored, as a porch's deck is, and not the ground's own blend. */
    private boolean builtFloorUnder(int x, int y, int z) {
        IsoGridSquare under = build.cell.getGridSquare(x, y, z - 1);
        IsoObject floor = under == null ? null : under.getFloor();
        return floor != null && floor.getSprite() != null && floor.getSprite().getName().startsWith("floors_");
    }

    /**
     * The building a roof over open ground on (x, y, z) comes off: the first one under it to the
     * south or the east, the roof going on over every square between, or failing that to the
     * north or the west, for a porch roof run on down from the house's own slope past its wall
     * (13837..13843, 4677..4678). Null for a roof standing on its own.
     */
    private IsoBuilding buildingAlongTheRoof(int x, int y, int z) {
        for (int way = 0; way < 4; way++) {
            for (int step = 1; step <= PORCH_DEEP; step++) {
                int across = way < 2 ? step : -step;
                int alongX = way % 2 == 0 ? x : x + across, alongY = way % 2 == 0 ? y + across : y;
                if (!roofGoesOn(alongX, alongY, z)) {
                    break;
                }
                IsoBuilding standing = buildingUnder(alongX, alongY, z);
                if (standing != null) {
                    return standing;
                }
            }
        }
        return null;
    }

    /** The light a gable is made in, and the light it is set back to after. */
    private float indoorRed, indoorGreen, indoorBlue, indoorOpen;

    /** A gable is out of doors and is lit as the open air is. The roof tile's own square is the loft under it, which is dark. */
    private void outdoors() {
        indoorRed = build.frame.red;
        indoorGreen = build.frame.green;
        indoorBlue = build.frame.blue;
        indoorOpen = build.frame.open;
        build.frame.cornersInto(build.heldRed, build.heldGreen, build.heldBlue, build.heldUnlit);
        build.frame.light(1f, 1f, 1f);
        build.frame.open = WorldScene.OPEN_UNLIT;
    }

    private void indoors() {
        build.frame.red = indoorRed;
        build.frame.green = indoorGreen;
        build.frame.blue = indoorBlue;
        build.frame.open = indoorOpen;
        build.frame.cornersFrom(build.heldRed, build.heldGreen, build.heldBlue, build.heldUnlit);
    }

    private final float[] beyond = new float[4];

    /**
     * The gable a trailer's map paints as boards it marks roof, on the square past the roof's
     * end, along the square's west or north edge (location_trailer_01_48..51, 40..43): siding
     * cut to the roof under a fascia. The picture hangs a board of its own from the roof's edge
     * there and the map's are left out (underARake), and under that board the end was open
     * from the top of the wall up to the roof.
     */
    void addGableBeyond(int x, int y, int z) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        for (int edge = 0; edge < 2; edge++) {
            boolean north = edge == 1;
            int behindX = north ? x : x - 1, behindY = north ? y - 1 : y;
            if (!slopesAlong(behindX, behindY, z, north ? RoofFit.ALONG_X : RoofFit.ALONG_Y) || square != null && WorldScene.wallOn(square, north) != null
                    || buildingUnder(x, y, z) != null || !cornersAt(behindX, behindY, z, beyond)) {
                continue;
            }
            // The roof's heights along its own east or south edge, which is this square's west or north one.
            float near = north ? beyond[3] : beyond[1], far = beyond[2];
            IsoBuilding standing = buildingUnder(behindX, behindY, z);
            if (standing == null || Math.max(near, far) < GABLE_LEAST) {
                continue;
            }
            outdoors();
            addGableWall(standing, foundOn, foundIn, x, y, z, north, near, far, 0f, 0);
            indoors();
        }
    }

    /**
     * Whether the building goes on past the edge of a roof tile on (x, y, z), on the square
     * beyond: it stands there, and where rooms are under the tile on the storey below, rooms
     * are there too. A lower wing beyond has none on that storey, only a roof of its own, and
     * the end of this roof stands over it in the open: taking the wing's ground floor for the
     * house going on left that gable out. A row the map leaves out of a tall hall's upper room
     * is the hall's (TallHalls): read as no room, every row of it under the roof was a gable,
     * brick walls standing in the church's nave over the altar and on its columns (12757,
     * 1671..1683, levels 2..4).
     */
    private boolean buildingGoesOn(int x, int y, int beyondX, int beyondY, int z) {
        if (buildingUnder(beyondX, beyondY, z) == null) {
            return false;
        }
        if (z == 0 || TallHalls.roomOf(build.cell, x, y, z - 1) == RoomSight.OUTSIDE) {
            return true;
        }
        return TallHalls.roomOf(build.cell, beyondX, beyondY, z - 1) != RoomSight.OUTSIDE;
    }

    /**
     * Whether the edge of a roof tile on (x, y, z) stands over the building's outer wall on the
     * storey below, the tile out over the open and the building beyond. A porch roof the house's
     * slope runs on down to is higher over that wall than the wall's top, and the gap between
     * them opened into the roof (13837..13843, 4677..4678): the wall goes on up to the roof.
     */
    private boolean overOuterWall(int x, int y, int beyondX, int beyondY, int z, boolean north) {
        if (z == 0) {
            return false;
        }
        IsoGridSquare below = build.cell.getGridSquare(x, y, z - 1);
        return below != null && WorldScene.wallOn(below, north) != null && TallHalls.roomOf(build.cell, x, y, z - 1) == RoomSight.OUTSIDE
                && TallHalls.roomOf(build.cell, beyondX, beyondY, z - 1) != RoomSight.OUTSIDE;
    }

    /** How far a roof's sheet laid as a floor is followed past the building's end to the slope made over it, in squares. */
    private static final int SHEET_RUN = 6;

    /**
     * Whether a roof's sheet laid as a floor on the square is the foot of a slope the map leaves
     * out (Roofs.hiddenSheet): a slope was made over it, or it is that sheet run on past the
     * building's end over open ground, the squares between all of the same sheet. Drawn, it was a
     * ledge of shingle at the eave under the made slope, and past a barn's gable a board standing
     * out three squares into the air (3952..3954, 11459).
     */
    boolean underAMadeSlope(int x, int y, int z) {
        // Read before its roof is whole, the answer is no: the build is no plain one, to be made again.
        build.building.plain = false;
        Roofs.readRoofOver(build.cell, x, y, z, world.roundNow, build.frame.scale);
        if (build.madeAt(x, y, z)) {
            return true;
        }
        IsoGridSquare ground = build.cell.getGridSquare(x, y, 0);
        if (ground != null && ground.getBuilding() != null) {
            return false;
        }
        for (int side = 0; side < 4; side++) {
            int dx = side == 0 ? -1 : side == 1 ? 1 : 0, dy = side == 2 ? -1 : side == 3 ? 1 : 0;
            for (int step = 1; step <= SHEET_RUN; step++) {
                IsoGridSquare beside = build.cell.getGridSquare(x + step * dx, y + step * dy, z);
                if (beside == null || beside.getFloor() == null || !Roofs.hiddenSheet(beside.getFloor())) {
                    break;
                }
                if (build.madeAt(x + step * dx, y + step * dy, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The gable under a roof's level crown that the map lays as a floor on level z, over the
     * loft on the level below: the top of a roof cut flat a whole level up. The slopes either
     * side of the crown have their gables (addGable), and between them, under the crown's west
     * or north edge, the end of the house was open from the loft's floor up to the crown, and
     * the crown's underside showed through it.
     */
    void addCrownGable(int x, int y, int z) {
        addGable(x, y, z - 1, 1f, 1f, 1f, 0f, 0);
    }

    private void addGableWalls(IsoGridSquare square, IsoBuilding standing, int ground, long room, int x, int y, int z, float northWest, float northEast, float southWest,
            float peak, int peakAxis, boolean porch) {
        for (int edge = 0; edge < 2; edge++) {
            boolean north = edge == 1;
            float second = north ? northEast : southWest;
            int beyondX = north ? x : x - 1, beyondY = north ? y - 1 : y;
            // Over a porch no building goes on to say where the roof's end is, so its own end does.
            if (Math.max(northWest, second) < GABLE_LEAST
                    || !overOuterWall(x, y, beyondX, beyondY, z, north) && (buildingGoesOn(x, y, beyondX, beyondY, z) || porch && roofGoesOn(beyondX, beyondY, z))
                    || square != null && WorldScene.wallOn(square, north) != null) {
                continue;
            }
            addGableWall(standing, ground, room, x, y, z, north, northWest, second, peak, peakAxis);
            // A slope's top rows the map lays a level up, over a loft whose end has no wall: the
            // gable goes on down to where the building's wall stands (the barn at 7648, 9352..9353,
            // whose roof's underside showed through its end a level below the ridge).
            for (int level = z - 1; level > ground && !porch; level--) {
                IsoGridSquare below = build.cell.getGridSquare(x, y, level);
                if (below == null || WorldScene.wallOn(below, north) != null || roofGoesOn(x, y, level) || buildingGoesOn(x, y, beyondX, beyondY, level)) {
                    break;
                }
                addGableWall(standing, ground, room, x, y, level, north, 1f, 1f, 0f, 0);
            }
        }
    }

    /**
     * One of those walls, along the north edge of (x, y) or the west one, the roof northWest
     * over the floor of level z at (x, y) and second at the edge's other end.
     */
    private void addGableWall(IsoBuilding standing, int ground, long room, int x, int y, int z, boolean north, float northWest, float second, float peak, int peakAxis) {
        // The building's lowest level, and no other: on the roof's own level its walls are
        // the gables of its other ends, cut to the roof's slope, and a gable's sprite laid
        // on this wall would be mostly air.
        WallFinish.Facing facing = WallFinish.facing(build.cell, standing, ground, north, room, world.roundNow);
        Texture finish = WallFinish.outside(build.cell, standing, ground, room, world.roundNow);
        float alongA = north ? 1f : 0f, alongB = north ? 0f : 1f;
        if (facing == null) {
            // That face of the building has no plain wall to take the sprite of: the gable
            // is built of what the building's outside walls are, as the far skin of a
            // wall is.
            float[] colour = finish == null ? null : SpriteColours.of(finish);
            if (colour == null) {
                build.gablesBare++;
                if (finish != null && SpriteColours.unread(finish)) {
                    build.building.signed = false;
                }
                return;
            }
            build.gablesPainted++;
            int built = Own.textures(build.playerIndex) ? Masonry.of(finish) : 0;
            build.building.signed &= !Own.textures(build.playerIndex) || !Masonry.unread(finish);
            SpriteGeometry.Mesh painted = WorldScene.solid.get(WorldScene.PLAIN, built != 0 ? WallSides.over(built) : WallSides.key(colour[0], colour[1], colour[2], WallSides.SIDING));
            painted.open = build.frame.open + 32f * WorldScene.PAINT;
            float foot = z * ViewCamera.LEVEL_HEIGHT + meanLift();
            float farX = x + alongA, farY = y + alongB;
            float[] c = build.eave;
            WorldScene.set(c, 0, x, y, foot);
            WorldScene.set(c, 1, farX, farY, foot);
            WorldScene.set(c, 2, farX, farY, foot + second * ViewCamera.LEVEL_HEIGHT);
            WorldScene.set(c, 3, x, y, foot + northWest * ViewCamera.LEVEL_HEIGHT);
            SpriteGeometry.addPlainQuad(painted, c, build.frame.red, build.frame.green, build.frame.blue);
            if (peak > 0f && (peakAxis == RoofFit.ALONG_X) == north) {
                WorldScene.set(c, 0, x, y, foot + northWest * ViewCamera.LEVEL_HEIGHT);
                WorldScene.set(c, 1, farX, farY, foot + second * ViewCamera.LEVEL_HEIGHT);
                WorldScene.set(c, 2, x + alongA / 2f, y + alongB / 2f, foot + ((northWest + second) / 2f + peak) * ViewCamera.LEVEL_HEIGHT);
                WorldScene.set(c, 3, x, y, foot + northWest * ViewCamera.LEVEL_HEIGHT);
                SpriteGeometry.addPlainQuad(painted, c, build.frame.red, build.frame.green, build.frame.blue);
            }
            return;
        }
        build.gablesFaced++;
        build.held.texelsOf(build.frame);
        float material = build.frame.material;
        build.frame.takeTexels(facing.texture);
        build.frame.material = WorldScene.materialOf(facing.sprite);
        SpriteGeometry.Mesh mesh = build.wallBuilder.laid(WorldScene.solid.get(facing.texture.getID()), facing.texture, Masonry.suits(facing.sprite.getName()), false, WallSides.madeOf(facing.sprite), x, y, z, north);
        if (second >= GABLE_LEAST) {
            WorldScene.set(folded, 0, 0f, 0f, 0f);
            WorldScene.set(folded, 1, alongA, alongB, 0f);
            WorldScene.set(folded, 2, alongA, alongB, second);
            build.geometry.addTriangle(mesh, build.frame, x, y, z, folded);
        }
        if (northWest >= GABLE_LEAST) {
            WorldScene.set(folded, 0, 0f, 0f, 0f);
            WorldScene.set(folded, 1, alongA, alongB, second);
            WorldScene.set(folded, 2, 0f, 0f, northWest);
            build.geometry.addTriangle(mesh, build.frame, x, y, z, folded);
        }
        if (peak > 0f && (peakAxis == RoofFit.ALONG_X) == north) {
            WorldScene.set(folded, 0, 0f, 0f, northWest);
            WorldScene.set(folded, 1, alongA, alongB, second);
            WorldScene.set(folded, 2, alongA / 2f, alongB / 2f, (northWest + second) / 2f + peak);
            build.geometry.addTriangle(mesh, build.frame, x, y, z, folded);
        }
        if (LogWalls.is(facing.sprite.getName()) && Own.buildings()) {
            build.wallBuilder.addGableLogs(facing.texture, x, y, z * ViewCamera.LEVEL_HEIGHT + meanLift(), north, northWest, second,
                    (peakAxis == RoofFit.ALONG_X) == north ? peak : 0f);
        }
        build.frame.texelsOf(build.held);
        build.frame.material = material;
    }

    /**
     * A tile under a ridge that runs along its middle: two half slopes from its edges up to
     * the ridge, and at a gable, where no roof lies beside it along the ridge, both carried out
     * over the wall as the slopes below them are.
     */
    private void addPeak(SpriteGeometry.Mesh sheet, RoofFit.Tile tile) {
        boolean alongX = tile.peakAxis == RoofFit.ALONG_X;
        float lift = meanLift();
        float edge = tile.fitLow * ViewCamera.LEVEL_HEIGHT + lift, top = (tile.fitLow + tile.peak) * ViewCamera.LEVEL_HEIGHT + lift;
        // Into the middle of a ridge across this one's end, or out over a gable as a rake.
        boolean rakeFrom = tile.peakBefore == 0f && !tile.carried && !roofGoesOn(alongX ? tile.x : tile.x - 1, alongX ? tile.y - 1 : tile.y, tile.z);
        boolean rakeTo = tile.peakAfter == 0f && !tile.carried && !roofGoesOn(alongX ? tile.x : tile.x + 1, alongX ? tile.y + 1 : tile.y, tile.z);
        float from = rakeFrom ? -RAKE : -tile.peakBefore, to = rakeTo ? 1f + RAKE : 1f + tile.peakAfter;
        float[] c = build.eave;
        for (int side = 0; side < 2; side++) {
            float low = side, high = 0.5f;
            if (alongX) {
                WorldScene.set(c, 0, tile.x + low, tile.y + from, edge);
                WorldScene.set(c, 1, tile.x + high, tile.y + from, top);
                WorldScene.set(c, 2, tile.x + high, tile.y + to, top);
                WorldScene.set(c, 3, tile.x + low, tile.y + to, edge);
            } else {
                WorldScene.set(c, 0, tile.x + from, tile.y + low, edge);
                WorldScene.set(c, 1, tile.x + from, tile.y + high, top);
                WorldScene.set(c, 2, tile.x + to, tile.y + high, top);
                WorldScene.set(c, 3, tile.x + to, tile.y + low, edge);
            }
            SpriteGeometry.addQuad(sheet, c, roofU, roofV, build.frame.red, build.frame.green, build.frame.blue);
            // At a gable the half slope's edge has its board, as a whole slope's has.
            if (rakeFrom) {
                addBarge(sheet, c[0], c[1], c[2], c[3], c[4], c[5]);
            }
            if (rakeTo) {
                addBarge(sheet, c[9], c[10], c[11], c[6], c[7], c[8]);
            }
        }
    }

    /** The mean of the ground's lift at the four corners of the square being added. */
    private float meanLift() {
        float[] lift = build.geometry.lift;
        return (lift[0] + lift[1] + lift[2] + lift[3]) / 4f;
    }

    /** Sets the ground's lift at the corners of a square of the chunk whose ground is given. */
    private void liftOf(float[] ground, int localX, int localY) {
        int corner = localX * Terrain.CORNERS + localY;
        float[] lift = build.geometry.lift;
        lift[0] = ground[corner];
        lift[1] = ground[corner + Terrain.CORNERS];
        lift[2] = ground[corner + Terrain.CORNERS + 1];
        lift[3] = ground[corner + 1];
    }

    /**
     * The mod's own sheet over the triangles of a folded roof tile that which names, bit 0 the
     * first and bit 1 the second: in the tile's own surface, or just under it for a triangle
     * the tile's sprite is drawn on.
     */
    private void addFolded(SpriteGeometry.Mesh sheet, RoofFit.Tile tile, int which, boolean under) {
        int[] order = RoofFit.TRIANGLES[tile.crease];
        for (int triangle = 0; triangle < 2; triangle++) {
            if ((which >> triangle & 1) == 0) {
                continue;
            }
            for (int i = 0; i < 3; i++) {
                int corner = order[triangle * 3 + i];
                float high = tile.corners[corner] - tile.z;
                WorldScene.set(folded, i, tile.x + RoofFit.cornerX(corner), tile.y + RoofFit.cornerY(corner),
                        (under ? WorldScene.under(tile.z, high) : (tile.z + high) * ViewCamera.LEVEL_HEIGHT) + build.geometry.lift[corner]);
            }
            SpriteGeometry.addTriangle(sheet, folded, roofU, roofV, build.frame.red, build.frame.green, build.frame.blue);
        }
    }

    /**
     * The eaves of a folded tile. Each of its triangles is a slope of its own, and where one
     * comes down to a level edge of the square with no roof beyond, that edge is an eave.
     */
    private void addFoldEaves(SpriteGeometry.Mesh sheet, RoofFit.Tile tile) {
        int[] order = RoofFit.TRIANGLES[tile.crease];
        float[] at = tile.corners;
        for (int pass = 0; pass < 2; pass++) {
            for (int edge = 0; edge < 4; edge++) {
                int first = EDGES[edge][0], second = EDGES[edge][1];
                if (pass == 0) {
                    foldEave[edge] = false;
                }
                if (Math.abs(at[first] - at[second]) > RoofFit.SAME) {
                    continue;
                }
                // The triangle against this edge is the one whose corner off the fold is one of the edge's own.
                int off = order[2] == first || order[2] == second ? order[2] : order[5];
                int high = order[0] == first || order[0] == second ? order[1] : order[0];
                float rise = at[high] - at[first];
                if (off != first && off != second || rise < Roofs.FLAT) {
                    continue;
                }
                boolean alongX = edge < 2, lowAtFar = edge == 1 || edge == 3;
                float atOrigin = at[first] - tile.z + (lowAtFar ? rise : 0f), along = lowAtFar ? -rise : rise;
                if (pass == 0) {
                    foldEave[edge] = isEave(tile.x, tile.y, tile.z, atOrigin, alongX ? along : 0f, alongX ? 0f : along);
                    continue;
                }
                // Two eaves of one tile meet at the tile's outer corner, each cut on the slant
                // to the other. One pipe to a corner: the eave along x gives it.
                addEave(sheet, tile.x, tile.y, tile.z, atOrigin, alongX ? along : 0f, alongX ? 0f : along, !alongX,
                        foldEave[alongX ? 2 : 0], foldEave[alongX ? 3 : 1]);
            }
        }
    }

    /**
     * The cap along a ridge or down a hip: a low strip with a level crown and two sides that
     * come down onto the roof, of the roof's own covering.
     */
    private void addCap(RoofFit.Cap cap) {
        SpriteGeometry.Mesh sheet = roofSheet();
        sheet.open = build.frame.open + 32f * RIDGE;
        float dx = cap.x1 - cap.x0, dy = cap.y1 - cap.y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float acrossX = -dy / length, acrossY = dx / length;
        float lift = meanLift();
        float from = cap.high0 * ViewCamera.LEVEL_HEIGHT + lift, to = cap.high1 * ViewCamera.LEVEL_HEIGHT + lift;
        float edge = CAP_CLEAR - cap.fall * ViewCamera.LEVEL_HEIGHT * CAP_HALF;
        float[] c = build.eave;
        for (int strip = 0; strip < 3; strip++) {
            float near = strip == 0 ? -CAP_HALF : strip == 1 ? -CAP_CROWN : CAP_CROWN, far = strip == 0 ? -CAP_CROWN : strip == 1 ? CAP_CROWN : CAP_HALF;
            float nearUp = strip == 0 ? edge : CAP_RISE, farUp = strip == 2 ? edge : CAP_RISE;
            WorldScene.set(c, 0, cap.x0 + acrossX * near, cap.y0 + acrossY * near, from + nearUp);
            WorldScene.set(c, 1, cap.x1 + acrossX * near, cap.y1 + acrossY * near, to + nearUp);
            WorldScene.set(c, 2, cap.x1 + acrossX * far, cap.y1 + acrossY * far, to + farUp);
            WorldScene.set(c, 3, cap.x0 + acrossX * far, cap.y0 + acrossY * far, from + farUp);
            SpriteGeometry.addQuad(sheet, c, roofU, roofV, build.frame.red, build.frame.green, build.frame.blue);
        }
    }

    /**
     * A roof tile, laid where its whole roof puts it: see Roofs. The sprite is drawn as its
     * own outline reads, so that all of it shows, and stood on the plane the roof's fit gave
     * the tile, so that it meets the tiles beside it.
     */
    void addRoof(SpriteGeometry.Mesh mesh, Texture texture, int x, int y, int z) {
        build.building.plain = false;
        Roofs.Laid laid = Roofs.at(build.cell, x, y, z, world.roundNow, build.frame.scale);
        // A square with two roof sprites has one of them fitted, which holds what its sprite read
        // as. The other lies as it reads, but on a valley's fold, where it is a piece of the same
        // picture of where two slopes meet: see RoofFit.laidOn.
        boolean itsOwn = laid != null && laid.texture == texture;
        RoofFit.Tile on = RoofFit.laidOn(laid == null ? null : laid.tile, itsOwn);
        RoofFit.Tile tile = itsOwn ? on : Roofs.measure(build.frame, texture, x, y, z);
        // Half a tile at a ridge is not where the map has it: the ridge is made, see RoofFit.peaks.
        if (tile.half != 0) {
            return;
        }
        if (on != null && !itsOwn) {
            onFold(mesh, on, on.own, x, y, z);
            return;
        }
        roof = laid == null ? null : laid.roof;
        // A trim from the sheets of a roof's accents is a board on the roof's edge, no tile of it
        // (Roofs.take): it is laid as its sprite, with no wall under it and no sheet closing a roof.
        boolean trim = Roofs.accent(build.roofName);
        if (!trim && tile.shaped) {
            addGable(x, y, z, tile.corners[0] - z, tile.corners[1] - z, tile.corners[3] - z, 0f, 0);
        } else if (!trim && tile.cutCorner < 0) {
            RoofFit.cornersOf(tile.fitAxis, tile.fitLow - z, tile.fitHigh - z, gable);
            addGable(x, y, z, gable[0], gable[1], gable[3], 0f, 0);
        }
        float northWest = tile.low - z, southEast = tile.high - z;
        if (Math.abs(northWest - southEast) > steepest) {
            steepest = Math.abs(northWest - southEast);
            build.steepestSaid = String.format(Locale.ROOT, "%s at (%d, %d, %d) nw %.2f se %.2f frame %.0fx%.0f at (%.0f, %.0f) origin (%.0f, %.0f) scale %.0f",
                    build.roofName, x, y, z, northWest, southEast, build.frame.width, build.frame.height, build.frame.left, build.frame.top, build.frame.originU, build.frame.originV, build.frame.scale);
        }
        if (tile.shaped) {
            // Two slopes on one square: each half of the sprite goes on its own, and where
            // the map's slope is only one of the two the other half is the mod's own sheet.
            onFold(mesh, tile, tile.own, x, y, z);
            build.roofsFolded++;
            SpriteGeometry.Mesh sheet = roofSheet();
            addFolded(sheet, tile, tile.own, true);
            addFolded(sheet, tile, ~tile.own & 3, false);
            addFoldEaves(sheet, tile);
            return;
        }
        // A tile that reads level and that the fit found to be the foot of a slope lies on the slope.
        if (tile.axis == RoofFit.LEVEL && tile.fitAxis == RoofFit.LEVEL) {
            build.roofsFlat++;
            float level = (northWest + southEast) / 2f;
            // A flat roof that lies on its level has the floor there under it already, and lies
            // clear of it: in the floor's own plane the two are drawn through each other.
            WorldScene.layers.lie(SpriteGeometry.LAYERS_UP, level < FLAT_ROOF_ON_FLOOR ? FLAT_ROOF_LAYERS : 0f);
            int made = build.geometry.addFace(roofLaidFlat(mesh, texture, level, 0f, 0f), build.frame, x, y, z, 0f, 0f, level, 1f, 0f, 0f, 0f, 1f, 0f);
            build.frame.flat = 0;
            WorldScene.layers.none();
            if (made > 0 && level > 2f * WorldScene.UNDER_ROOF) {
                underRoof(x, y, z, level, 0f, 0f);
            }
            if (RoofFit.levelOnItsFloor(tile)) {
                addCornerEaves(tile);
                // A crown cut flat on the floor of its level, as one laid as a floor is (addCrownGable): the barn's at 3955..3966, 11462.
                if (!trim && z > 0) {
                    addCrownGable(x, y, z);
                }
            }
            return;
        }
        boolean readAlongX = tile.axis == RoofFit.ALONG_X, alongX = tile.fitAxis == RoofFit.ALONG_X;
        float low = tile.fitLow - z, high = tile.fitHigh - z;
        float[] lift = build.geometry.lift;
        for (int corner = 0; corner < 4; corner++) {
            boolean farA = corner == 1 || corner == 2, farB = corner >= 2;
            float read = (readAlongX ? farA : farB) ? southEast : northWest;
            float fitted = (alongX ? farA : farB) ? high : low;
            heldLift[corner] = lift[corner];
            lift[corner] += (fitted - read) * ViewCamera.LEVEL_HEIGHT;
        }
        SpriteGeometry.Mesh slope = roofLaidFlat(mesh, texture, northWest, readAlongX ? southEast - northWest : 0f, readAlongX ? 0f : southEast - northWest);
        int added = readAlongX ? build.geometry.addFace(slope, build.frame, x, y, z, 0f, 0f, northWest, 1f, 0f, southEast - northWest, 0f, 1f, 0f)
                : build.geometry.addFace(slope, build.frame, x, y, z, 0f, 0f, northWest, 1f, 0f, 0f, 0f, 1f, southEast - northWest);
        build.frame.flat = 0;
        System.arraycopy(heldLift, 0, lift, 0, 4);
        boolean placed = tile.fitted || tile.sure;
        if (alongX) {
            build.roofsWestEast++;
        } else if (placed) {
            build.roofsNorthSouth++;
        } else {
            build.roofsUnclear++;
        }
        if (added == 0) {
            return;
        }
        if (tile.cutCorner >= 0) {
            underCut(tile, x, y, z);
            return;
        }
        if (trim) {
            return;
        }
        if (placed) {
            underRoof(x, y, z, low, alongX ? high - low : 0f, alongX ? 0f : high - low);
        } else if (build.frame.width > Roofs.WHOLE_TILE_WIDE * build.frame.scale) {
            // Which way this tile slopes is a guess, and a sheet laid to a wrong guess
            // stands out of the roof. A level one at the tile's lowest height cannot: it
            // is under the tile whichever way it slopes, and still closes the roof.
            underRoof(x, y, z, Math.min(low, high), 0f, 0f);
        }
    }

    /** A sprite of the roofs' sheets that is no tile of a roof: laid as one where its roof took it as a flat top (Roofs.flatTop). */
    void addFlatTop(SpriteGeometry.Mesh mesh, Texture texture, int x, int y, int z) {
        // Its own sprite says first whether it can be one: the sheet under every slope cannot, and asked for, a square
        // that holds no tile has its whole roof read again on every build of its chunk.
        if (!Roofs.flatTop(Roofs.measure(build.frame, texture, x, y, z), Roofs.overARoom(build.cell, x, y, z))) {
            return;
        }
        Roofs.Laid laid = Roofs.at(build.cell, x, y, z, world.roundNow, build.frame.scale);
        if (laid != null && laid.texture == texture) {
            addRoof(mesh, texture, x, y, z);
        }
    }

    /** The sprite on the triangles of a folded tile that which names, bit 0 the first of RoofFit.TRIANGLES and bit 1 the second. */
    private void onFold(SpriteGeometry.Mesh mesh, RoofFit.Tile tile, int which, int x, int y, int z) {
        int[] order = RoofFit.TRIANGLES[tile.crease];
        for (int triangle = 0; triangle < 2; triangle++) {
            if ((which >> triangle & 1) == 0) {
                continue;
            }
            for (int i = 0; i < 3; i++) {
                int corner = order[triangle * 3 + i];
                WorldScene.set(folded, i, RoofFit.cornerX(corner), RoofFit.cornerY(corner), tile.corners[corner] - z);
            }
            build.geometry.addTriangle(mesh, build.frame, x, y, z, folded);
        }
    }

    /**
     * The mesh a whole tile of roof goes in on the plane its sprite reads as, given as Flats
     * holds one: the mesh of the sprite laid flat on that plane, with the frame told so,
     * so the tile's courses meet the next tile's. The sprite's own mesh for a sprite that
     * does not paint the whole of the plane.
     */
    private SpriteGeometry.Mesh roofLaidFlat(SpriteGeometry.Mesh mesh, Texture texture, float high, float climbA, float climbB) {
        int flat = build.flatOf(texture, Flats.ROOF, high, climbA, climbB);
        if (flat == 0) {
            return mesh;
        }
        build.frame.flat = SpriteGeometry.FLAT_FLOOR;
        return WorldScene.solid.get(flat);
    }

    /**
     * The game drew each roof tile from one side, and a slope that faces away from that side
     * is a sliver of sprite, or none: seen from the other side the roof has holes with the
     * rooms showing through. Under every tile of roof goes a plain sheet of the same slope, in
     * the grey of shingle, which is what shows wherever the sprite has nothing.
     */
    private void underRoof(int x, int y, int z, float atOrigin, float alongX, float alongY) {
        SpriteGeometry.Mesh sheet = roofSheet();
        float[] lift = build.geometry.lift;
        SpriteGeometry.addOver(sheet, x, y, WorldScene.under(z, atOrigin) + lift[0], WorldScene.under(z, atOrigin + alongX) + lift[1],
                WorldScene.under(z, atOrigin + alongX + alongY) + lift[2], WorldScene.under(z, atOrigin + alongY) + lift[3],
                roofU, roofV, build.frame.red, build.frame.green, build.frame.blue);
        addEave(sheet, x, y, z, atOrigin, alongX, alongY, true);
        addRake(sheet, x, y, z, atOrigin, alongX, alongY);
    }

    /**
     * What goes with a tile the map cuts to a triangle, where a hip would cross its square and
     * the map has the slope on one side of it only (Roofs.cut): the plain sheet under the half
     * of the square the tile covers, the eave along its low edge where it has the whole of
     * one, and a wall under the cut, from the roof down to the floor it stands over, in the
     * roof's own covering. Given what a whole tile has, the sheet and the strip past a gable
     * stood out beyond the cut, and the end of the roof was open under it.
     */
    private void underCut(RoofFit.Tile tile, int x, int y, int z) {
        SpriteGeometry.Mesh sheet = roofSheet();
        boolean alongX = tile.fitAxis == RoofFit.ALONG_X;
        float low = tile.fitLow - z, high = tile.fitHigh - z;
        RoofFit.cornersOf(tile.fitAxis, low, high, gable);
        float[] lift = build.geometry.lift;
        int at = 0;
        for (int corner = 0; corner < 4; corner++) {
            if (corner != tile.cutCorner) {
                WorldScene.set(folded, at++, x + RoofFit.cornerX(corner), y + RoofFit.cornerY(corner), WorldScene.under(z, gable[corner]) + lift[corner]);
            }
        }
        SpriteGeometry.addTriangle(sheet, folded, roofU, roofV, build.frame.red, build.frame.green, build.frame.blue);
        // The north-west corner is on the edge nearer the map's origin whichever way the tile slopes, and the south-east on the far one.
        if (low < high == (tile.cutCorner == 2)) {
            addEave(sheet, x, y, z, low, alongX ? high - low : 0f, alongX ? 0f : high - low, true);
        }
        // Down to the first floor under the cut, and never into a room.
        int foot = z;
        while (foot > 0) {
            IsoGridSquare here = build.cell.getGridSquare(x, y, foot), below = build.cell.getGridSquare(x, y, foot - 1);
            if (here != null && here.getFloor() != null || below != null && below.getRoomID() != -1L) {
                break;
            }
            foot--;
        }
        WorldScene.set(cutWall, 0, x, y + 1f, foot * ViewCamera.LEVEL_HEIGHT + lift[3]);
        WorldScene.set(cutWall, 1, x + 1f, y, foot * ViewCamera.LEVEL_HEIGHT + lift[1]);
        WorldScene.set(cutWall, 2, x + 1f, y, (z + gable[1]) * ViewCamera.LEVEL_HEIGHT + lift[1]);
        WorldScene.set(cutWall, 3, x, y + 1f, (z + gable[3]) * ViewCamera.LEVEL_HEIGHT + lift[3]);
        SpriteGeometry.addQuad(sheet, cutWall, roofU, roofV, build.frame.red, build.frame.green, build.frame.blue);
    }

    /**
     * The mesh the mod's own roofing goes in, and into roofU and roofV the texel all of it
     * takes. It is the colour most of the roof's own sprite is, painted flat over a painted
     * place on that sprite, so a slope the sprite shows nothing of is the colour of the
     * slopes it does show. Until the picture has read that colour, and for a roof with no
     * sprite to read, it is plain shingle grey.
     */
    private SpriteGeometry.Mesh roofSheet() {
        float[] colour = roof == null || roof.finish == null ? null : SpriteColours.of(roof.finish);
        build.building.signed &= roof == null || roof.finish == null || !SpriteColours.unread(roof.finish);
        SpriteGeometry.Mesh sheet;
        if (colour == null) {
            sheet = WorldScene.solid.get(WorldScene.PLAIN);
            roofU = roofV = 0f;
        } else {
            // The mean of the tile's sprite, the dark between its shingles with them: the usual
            // colour is the shingle's own face, and a sheet of that beside the tiles is paler and bluer than they are.
            sheet = WorldScene.solid.get(roof.finish.getID(), WallSides.key(colour[WallSides.MEAN], colour[WallSides.MEAN + 1], colour[WallSides.MEAN + 2], WallSides.FLAT));
            roofU = roof.u;
            roofV = roof.v;
        }
        // Of the stuff the roof's own tiles are: shingle on the roofs' sheets, the bare sheet metal
        // of a trailer's roof on its own sheet, which as shingle was a crumpled foil along its eaves.
        sheet.open = build.frame.open + 32f * (colour == null ? WorldScene.SHINGLES : WorldScene.materialOf(roof.finish.getName(), false));
        return sheet;
    }

    /**
     * At a gable the roof runs a little past the end wall too: where a sloping tile has no roof
     * beside it across its slope, a strip of roof carries its slope out over the wall.
     */
    private void addRake(SpriteGeometry.Mesh sheet, int x, int y, int z, float atOrigin, float alongX, float alongY) {
        if (Math.abs(alongX + alongY) < Roofs.FLAT || turf(x, y, z)) {
            return;
        }
        float lift = meanLift();
        float low = (z + atOrigin) * ViewCamera.LEVEL_HEIGHT + lift, high = (z + atOrigin + alongX + alongY) * ViewCamera.LEVEL_HEIGHT + lift;
        float[] c = build.eave;
        for (int side = 0; side < 2; side++) {
            int besideX = x + (alongX != 0f ? 0 : side == 0 ? -1 : 1), besideY = y + (alongX != 0f ? side == 0 ? -1 : 1 : 0);
            if (roofGoesOn(besideX, besideY, z) || roomUnder(besideX, besideY, z)) {
                continue;
            }
            float edge = side, out = side == 0 ? -RAKE : RAKE;
            if (alongX != 0f) {
                WorldScene.set(c, 0, x, y + edge, low);
                WorldScene.set(c, 1, x + 1f, y + edge, high);
                WorldScene.set(c, 2, x + 1f, y + edge + out, high);
                WorldScene.set(c, 3, x, y + edge + out, low);
            } else {
                WorldScene.set(c, 0, x + edge, y, low);
                WorldScene.set(c, 1, x + edge, y + 1f, high);
                WorldScene.set(c, 2, x + edge + out, y + 1f, high);
                WorldScene.set(c, 3, x + edge + out, y, low);
            }
            SpriteGeometry.addQuad(sheet, c, roofU, roofV, build.frame.red, build.frame.green, build.frame.blue);
            addBarge(sheet, c[9], c[10], c[11], c[6], c[7], c[8]);
        }
    }

    /**
     * The paint of the boards along an eave and under a gable's edge, a part of its own: its colour made
     * part of their light was read out of doors as a lamp's, and they were warm lines along every roof at dusk.
     */
    private static final int BOARD = WallSides.laid(0.55f, 0.55f, 0.53f, 0f, 0f, 0f, WallSides.FINE);

    /**
     * The board under the edge of a roof where it runs out over a gable, from one end of a
     * length of that edge to the other: it hangs from the roof and follows its slope, as the
     * board along an eave follows the eave.
     */
    private void addBarge(SpriteGeometry.Mesh sheet, float x0, float y0, float up0, float x1, float y1, float up1) {
        SpriteGeometry.Mesh plain = WorldScene.solid.get(WorldScene.PLAIN, BOARD);
        // The roofing itself goes in this mesh when the roof has no colour of its own yet.
        float roofing = sheet.open;
        plain.open = build.frame.open;
        float[] c = barge;
        WorldScene.set(c, 0, x0, y0, up0);
        WorldScene.set(c, 1, x1, y1, up1);
        WorldScene.set(c, 2, x1, y1, up1 - BARGE);
        WorldScene.set(c, 3, x0, y0, up0 - BARGE);
        SpriteGeometry.addPlainQuad(plain, c, build.frame.red, build.frame.green, build.frame.blue);
        sheet.open = roofing;
    }

    /**
     * Whether a trim from the sheet of a roof's accents is the board the map paints under the
     * edge of a gable: the roof beside it, to the west or the north, slopes along the edge it
     * is on. Each square's length of that board is painted for the game's view, and stood on
     * its wall the lengths are a row of blocks. The picture hangs a board of its own from the
     * roof's edge, and the map's is left out. A trim along an eave is level, and stays.
     */
    boolean underARake(int x, int y, int z) {
        return slopesAlong(x - 1, y, z, RoofFit.ALONG_Y) || slopesAlong(x, y - 1, z, RoofFit.ALONG_X);
    }

    /**
     * The heights over the floor of level z, in levels, at the corners of the roof tile the map has on (x, y, z), as
     * RoofFit.standOn takes them. False for a square with no roof tile, or only half of one.
     */
    boolean cornersAt(int x, int y, int z, float[] corners) {
        build.building.plain = false;
        Roofs.Laid map = Roofs.at(build.cell, x, y, z, world.roundNow, build.frame.scale);
        if (map == null || map.tile.half != 0) {
            return false;
        }
        if (map.tile.shaped) {
            for (int corner = 0; corner < 4; corner++) {
                corners[corner] = map.tile.corners[corner] - z;
            }
        } else {
            RoofFit.cornersOf(map.tile.fitAxis, map.tile.fitLow - z, map.tile.fitHigh - z, corners);
        }
        return true;
    }

    /**
     * Whether a trim from the sheet of a roof's accents is the gutter and fascia on an edge
     * where this picture makes an eave of its own: one hung on the square's west edge (west)
     * or north edge (north) where the map's slope beyond that edge, on the trim's level or the
     * level above, falls to the edge and has its eave made there (isEave). A corner piece, hung
     * on no edge, goes with the eave that ends at its north-west corner. The trim is a strip
     * painted for the game's view, and stood on the wall under the made eave it is a second,
     * jagged eave.
     */
    boolean trimUnderAMadeEave(int x, int y, int z, boolean west, boolean north, boolean corner) {
        for (int up = 0; up <= 1; up++) {
            if (west && eaveMadeBy(x - 1, y, z, up, RoofFit.ALONG_X) || north && eaveMadeBy(x, y - 1, z, up, RoofFit.ALONG_Y)
                    || corner && (eaveMadeBy(x - 1, y - 1, z, up, RoofFit.ALONG_X) || eaveMadeBy(x - 1, y - 1, z, up, RoofFit.ALONG_Y))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a trim from the sheet of a roof's accents is a corner piece, hung on no edge. The
     * eave's end caps the game marks attachedSE (roofs_accents_01_43, 47 and 53) are painted at
     * the square's north-west corner all the same, so that flag does not keep one out.
     */
    static boolean cornerPiece(PropertyContainer properties) {
        return !properties.has(IsoFlagType.attachedW) && !properties.has(IsoFlagType.attachedN) && !properties.has(IsoFlagType.attachedE)
                && !properties.has(IsoFlagType.attachedS) && !properties.has(IsoFlagType.attachedNW);
    }

    /**
     * Whether the map's slope on the square, up levels over z, falls along the axis to its far
     * edge and has its eave made there: on z its foot may stand over the floor, as a lean-to's
     * does; on the level above it comes down to that level's floor, the top of z's wall.
     */
    private boolean eaveMadeBy(int x, int y, int z, int up, int axis) {
        build.building.plain = false;
        Roofs.Laid map = Roofs.at(build.cell, x, y, z + up, world.roundNow, build.frame.scale);
        if (map == null || map.tile.shaped || map.tile.fitting || map.tile.half != 0) {
            return false;
        }
        RoofFit.Tile tile = map.tile;
        boolean falls = up == 0 ? tile.fitAxis == axis && (tile.fitted || tile.sure) && tile.fitHigh < tile.fitLow - Roofs.FLAT : RoofFit.fallsToItsFloor(tile, axis);
        float along = tile.fitHigh - tile.fitLow;
        return falls && isEave(x, y, z + up, tile.fitLow - (z + up), axis == RoofFit.ALONG_X ? along : 0f, axis == RoofFit.ALONG_X ? 0f : along);
    }

    private boolean slopesAlong(int x, int y, int z, int axis) {
        build.building.plain = false;
        Roofs.Laid map = Roofs.at(build.cell, x, y, z, world.roundNow, build.frame.scale), made = Roofs.madeAt(x, y, z, world.roundNow);
        return map != null && !map.tile.shaped && !map.tile.fitting && map.tile.fitAxis == axis
                || made != null && !made.tile.shaped && (made.tile.fitAxis == axis || made.tile.peak > 0f && made.tile.peakAxis == axis);
    }

    /**
     * A roof does not stop at the wall. Where a sloping tile is the lowest of its slope, with
     * no roof beyond its low edge, the slope runs on past the wall and a little down: the
     * eave, with a board closing its end. piped is whether the gutter may empty into a pipe here.
     */
    void addEave(SpriteGeometry.Mesh sheet, int x, int y, int z, float atOrigin, float alongX, float alongY, boolean piped) {
        addEave(sheet, x, y, z, atOrigin, alongX, alongY, piped, false, false);
    }

    /**
     * Whether the low edge of a sloping tile is an eave: the slope has a fall, and no roof
     * goes on beyond it. A slope that comes down to the foot of its level goes on in the top
     * of the level below: the tile beyond is there, and this is the middle of the roof.
     */
    private boolean isEave(int x, int y, int z, float atOrigin, float alongX, float alongY) {
        float along = alongX != 0f ? alongX : alongY;
        if (Math.abs(along) < Roofs.FLAT || turf(x, y, z)) {
            return false;
        }
        boolean lowAtFar = along < 0f;
        int beyondX = x + (alongX != 0f ? (lowAtFar ? 1 : -1) : 0), beyondY = y + (alongY != 0f ? (lowAtFar ? 1 : -1) : 0);
        float low = lowAtFar ? atOrigin + along : atOrigin;
        return !roofGoesOn(beyondX, beyondY, z) && !(low < Roofs.FLAT && reachesUpTo(beyondX, beyondY, z) && !eaveRow(beyondX, beyondY, z - 1)
                && !eavesMeetInTheCorner(beyondX, beyondY, z - 1)) && !roomUnder(beyondX, beyondY, z);
    }

    /** Whether the roof on the square is turf, the map's tile there or a slope made where the map leaves one out: see turf(Texture). */
    private boolean turf(int x, int y, int z) {
        Roofs.Laid map = Roofs.at(build.cell, x, y, z, world.roundNow, build.frame.scale);
        if (map != null) {
            return turf(map.texture);
        }
        Roofs.Laid made = Roofs.madeAt(x, y, z, world.roundNow);
        return made != null && turf(made.roof.finish);
    }

    /**
     * Whether the roof sprite is the turf over a bunker (industry_bunker_01): a grassed bank down to the ground, with no
     * cap on its hips, and no board, gutter or pipe along its edges.
     */
    private static boolean turf(Texture texture) {
        return texture != null && texture.getName() != null && texture.getName().startsWith("industry_bunker_01_");
    }

    /**
     * Whether the square is in a room on the level under z. An eave or a rake hangs over the
     * open air: a row the map leaves out of a roof over a room, or fills with a fitting, is
     * still roof, and an eave hung there hung its fascia and gutter across the room's ceiling,
     * a rake its barge board down through it.
     */
    private boolean roomUnder(int x, int y, int z) {
        IsoGridSquare under = build.cell.getGridSquare(x, y, z - 1);
        return under != null && under.getRoomID() != -1L;
    }

    /**
     * Whether a roof on the square, a level under z, comes up to the floor of level z, within
     * RoofFit.JOIN: the slope of an eave at that floor going on down. A lower roof that stops
     * short of it, a lean-to against the wall of the storey above, leaves the eave an eave.
     */
    private boolean reachesUpTo(int x, int y, int z) {
        Roofs.Laid map = Roofs.at(build.cell, x, y, z - 1, world.roundNow, build.frame.scale), made = Roofs.madeAt(x, y, z - 1, world.roundNow);
        return map != null && !map.tile.fitting && topOf(map.tile) >= z - RoofFit.JOIN || made != null && topOf(made.tile) >= z - RoofFit.JOIN;
    }

    /** The height of a tile's highest point, in levels from the ground. */
    private static float topOf(RoofFit.Tile tile) {
        if (tile.shaped) {
            return Math.max(Math.max(tile.corners[0], tile.corners[1]), Math.max(tile.corners[2], tile.corners[3]));
        }
        return Math.max(tile.fitLow, tile.fitHigh) + tile.peak;
    }

    /**
     * Whether the square holds the row of eave the map hangs at the top of a level from the
     * square's west or north wall, under the lowest tile of a slope that falls to that wall
     * from the square beyond it, a level up: the eave addEave makes of that tile. Hung on the
     * wall, the row's pieces were a row of dark slabs under the eave with the wall showing in a
     * triangle between each two. The row goes on under the corner of a hipped roof, whose tile
     * reads level at the eave's height, as far as the next square along holds the row under a
     * slope.
     */
    boolean eaveRow(int x, int y, int z) {
        long key = RoofFit.key(x, y, z);
        Boolean known = eaveRows.get(key);
        if (known == null) {
            known = findEaveRow(x, y, z);
            eaveRows.put(key, known);
        }
        return known;
    }

    private boolean findEaveRow(int x, int y, int z) {
        int wall = hoods(z).eave(x, y);
        if (wall == Hoods.NONE) {
            return false;
        }
        boolean west = wall == Outdoors.WEST;
        RoofFit.Tile over = over(x, y, z, west);
        int axis = west ? RoofFit.ALONG_X : RoofFit.ALONG_Y;
        if (over != null && (RoofFit.fallsToItsFloor(over, axis) || RoofFit.foldFallsToItsFloor(over, axis))) {
            return true;
        }
        // The row hangs under the east edge of a tile beyond a west wall, under the south edge beyond a north one.
        return over != null && RoofFit.levelOnItsFloor(over) && (cornerEaves(over) & 1 << (west ? EAST : SOUTH)) != 0;
    }

    /** Edges of a square as EDGES numbers them, and the way out of the square across each. */
    private static final int WEST = 0, EAST = 1, NORTH = 2, SOUTH = 3;
    private static final int[] OUT_X = {-1, 1, 0, 0}, OUT_Y = {0, 0, -1, 1};
    /** For each edge, the edge across the square's start of its run (its north or west end) and across its end. */
    private static final int[] AT_START = {NORTH, NORTH, WEST, WEST}, AT_END = {SOUTH, SOUTH, EAST, EAST};
    /** The climb over a tile of the slope beside each eave cornerEaves found, and the height of its foot over the floor, in levels. */
    private final float[] cornerRise = new float[4], cornerFoot = new float[4];

    /**
     * The edges of a roof tile level at the floor of its level (RoofFit.levelOnItsFloor) that
     * are eaves of a hipped roof's corner, as bits by EDGES' numbers, 0 for a tile that is no
     * such corner: the map draws the corner of a hipped roof at its eave as a level tile. An
     * edge is its eave where no roof lies past it, the slope on the square beside it along the
     * edge comes down to its floor on the edge's line, the roof past the other end of the
     * eave's run is open too, and the roof rises from the tile into the building across from
     * the edge. A flat roof's tile beside a slope has more roof past the run's end, or level
     * roof across from the edge, and is none.
     */
    private int cornerEaves(RoofFit.Tile tile) {
        int x = tile.x, y = tile.y, z = tile.z, eaves = 0;
        for (int edge = 0; edge < 4; edge++) {
            if (roofGoesOn(x + OUT_X[edge], y + OUT_Y[edge], z)) {
                continue;
            }
            RoofFit.Tile across = tileAt(x - OUT_X[edge], y - OUT_Y[edge], z);
            if (across == null || Math.max(across.fitLow, across.fitHigh) < z + Roofs.FLAT && across.peak <= 0f && !across.shaped) {
                continue;
            }
            int runX = OUT_Y[edge] != 0 ? 1 : 0, runY = 1 - runX;
            for (int step = -1; step <= 1; step += 2) {
                RoofFit.Tile beside = tileAt(x + step * runX, y + step * runY, z);
                if (beside != null && fallsOver(beside, edge) && !roofGoesOn(x - step * runX, y - step * runY, z)) {
                    eaves |= 1 << edge;
                    cornerRise[edge] = Math.abs(beside.fitHigh - beside.fitLow);
                    cornerFoot[edge] = Math.min(beside.fitLow, beside.fitHigh) - beside.z;
                }
            }
        }
        return eaves;
    }

    /** Whether a tile is a slope that falls across the given edge of its square, out of it, to the floor of its level there. */
    private static boolean fallsOver(RoofFit.Tile tile, int edge) {
        int axis = edge == WEST || edge == EAST ? RoofFit.ALONG_X : RoofFit.ALONG_Y;
        return edge == EAST || edge == SOUTH ? RoofFit.fallsToItsFloor(tile, axis) : RoofFit.risesFromItsFloor(tile, axis);
    }

    private RoofFit.Tile tileAt(int x, int y, int z) {
        Roofs.Laid laid = Roofs.at(build.cell, x, y, z, world.roundNow, build.frame.scale);
        return laid == null ? null : laid.tile;
    }

    /**
     * The eaves along the outer edges of the corner of a hipped roof that the map draws as a
     * level tile: see cornerEaves. Each runs on from the eave of the slope beside it at that
     * slope's pitch and height, to meet the other at the corner, or closed across its end where the other
     * edge is none. With none, the slope's eave stopped a square short of the corner and its
     * end stood open, and the map's own eave pieces under the corner, which the eave row is,
     * are left out.
     */
    private void addCornerEaves(RoofFit.Tile tile) {
        int eaves = cornerEaves(tile);
        if (eaves == 0) {
            return;
        }
        SpriteGeometry.Mesh sheet = roofSheet();
        for (int edge = 0; edge < 4; edge++) {
            if ((eaves & 1 << edge) == 0) {
                continue;
            }
            // At the slope's own eave's height, so the two run on into each other.
            float rise = cornerRise[edge], floor = cornerFoot[edge];
            boolean alongX = edge == WEST || edge == EAST, toFar = edge == EAST || edge == SOUTH;
            float along = toFar ? -rise : rise, atOrigin = toFar ? floor + rise : floor;
            addEave(sheet, tile.x, tile.y, tile.z, atOrigin, alongX ? along : 0f, alongX ? 0f : along, true,
                    (eaves & 1 << AT_START[edge]) != 0, (eaves & 1 << AT_END[edge]) != 0);
        }
    }

    /** The roof's tile a level up on the square beyond the wall a row of eave hangs from, or null. */
    private RoofFit.Tile over(int x, int y, int z, boolean west) {
        Roofs.Laid laid = Roofs.at(build.cell, west ? x - 1 : x, west ? y : y - 1, z + 1, world.roundNow, build.frame.scale);
        return laid == null ? null : laid.tile;
    }

    /** Whether the square holds the piece the map ends a row of eave with, a corner past the row's end, where eaveRow holds that row. */
    boolean eaveRowEnd(int x, int y, int z) {
        return Hoods.endOf(hoods(z), x, y, (nx, ny) -> eaveRow(nx, ny, z));
    }

    /**
     * Whether the square holds the map's piece for the corner where two eaves meet
     * (Hoods.innerCornerOf), walled on its north and its west, with a slope a level up beyond
     * each wall that comes down to it: those slopes make the eaves, which meet over the square,
     * and the piece is left out. Laid as a roof's tile from the level below, it was a dark slope
     * from the eaves to the ground across the corner, and as a roof reaching the slopes' feet it
     * kept them from making their eaves (isEave).
     */
    boolean eavesMeetInTheCorner(int x, int y, int z) {
        Hoods.Squares level = hoods(z);
        if (!Hoods.holdsInnerCorner(build.cell.getGridSquare(x, y, z)) || !level.walled(x, y, Outdoors.NORTH) || !level.walled(x, y, Outdoors.WEST)) {
            return false;
        }
        for (int wall = 0; wall < 2; wall++) {
            boolean west = wall == 0;
            RoofFit.Tile over = over(x, y, z, west);
            int axis = west ? RoofFit.ALONG_X : RoofFit.ALONG_Y;
            if (over == null || !RoofFit.fallsToItsFloor(over, axis) && !RoofFit.foldFallsToItsFloor(over, axis)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Makes the row of eave the map hangs at the top of a level from the square's west or north
     * wall where no slope comes down to that wall to make it (eaveRow): across the foot of a
     * gable, along a wall under the storey above or under a flat roof. It is an eave of its own,
     * a roof's pitch carried out from the top of the wall, run on along the wall as far as the
     * row goes and closed at its ends. Hung on the wall as the map paints it, each square's
     * length of the row was a slab slanting across the wall. Along an open side, a stage's
     * front under its flat roof, there is no wall and the row hangs from the roof's edge: laid
     * as the map's tiles it was a row of thin sheets with a pale triangle between each two
     * (4947..4952, 7947). False, and nothing made, for a square with no such row.
     */
    boolean addEaveRow(Texture texture, int x, int y, int z) {
        int wall = ownEaveRow(x, y, z);
        if (wall == Hoods.NONE) {
            return false;
        }
        Hoods.Squares level = hoods(z);
        boolean west = wall == Outdoors.WEST;
        // The square inside the wall a level up, as the roof it would fall from lies over the wall.
        int insideX = west ? x - 1 : x, insideY = west ? y : y - 1;
        Roofs.Laid inside = Roofs.at(build.cell, insideX, insideY, z + 1, world.roundNow, build.frame.scale);
        roof = inside != null && inside.roof.finish != null ? inside.roof : coveredIn(texture);
        SpriteGeometry.Mesh sheet = roofSheet();
        int stepX = west ? 0 : 1, stepY = west ? 1 : 0;
        int startEnd = level.eave(x - stepX, y - stepY) == wall ? Eaves.RUNS_ON : Eaves.GABLE;
        // The row along the roof's other edge from the same tile, round the corner past the end: the two are mitred to each other.
        int cornerX = x + stepX - stepY, cornerY = y + stepY - stepX;
        boolean meets = level.eave(cornerX, cornerY) == (west ? Outdoors.NORTH : Outdoors.WEST) && ownEaveRow(cornerX, cornerY, z) != Hoods.NONE;
        int endEnd = level.eave(x + stepX, y + stepY) == wall ? Eaves.RUNS_ON : meets ? Eaves.MEETS : Eaves.GABLE;
        hangEave(sheet, insideX, insideY, z + 1, RoofFit.PITCH, west ? -RoofFit.PITCH : 0f, west ? 0f : -RoofFit.PITCH, true, startEnd, endEnd);
        return true;
    }

    /** Whether the square holds the piece the map ends a row of eave with, past the end of a row addEaveRow makes. */
    boolean eaveRowOfItsOwnEnd(int x, int y, int z) {
        return Hoods.endOf(hoods(z), x, y, (nx, ny) -> ownEaveRow(nx, ny, z) != Hoods.NONE && !eaveRow(nx, ny, z));
    }

    /**
     * The wall a row of eave the map hangs on the square hangs from, where addEaveRow makes it,
     * or Hoods.NONE: a wall stands there, or roofing the row edges lies a level up inside it.
     */
    private int ownEaveRow(int x, int y, int z) {
        Hoods.Squares level = hoods(z);
        int wall = level.eave(x, y);
        if (wall == Hoods.NONE || level.walled(x, y, wall)) {
            return wall;
        }
        boolean west = wall == Outdoors.WEST;
        // A flat roof is a floor of the roofs' sheets (roofs_01_54), no tile of a roof: what tells is the game's roofing on the square.
        IsoGridSquare inside = build.cell.getGridSquare(west ? x - 1 : x, west ? y : y - 1, z + 1);
        return inside != null && Roofs.covered(build.cell, inside) ? wall : Hoods.NONE;
    }

    /** A roof of no tiles that is covered in what a sprite shows, one to a sprite. */
    private Roofs.Roof coveredIn(Texture texture) {
        return coverings.computeIfAbsent(texture, Roofs::coveredIn);
    }

    /**
     * As addEave, for an eave that meets another at one or both ends of its run, the end at
     * the square's north or west and the one at its south or east: see Eaves.
     */
    private void addEave(SpriteGeometry.Mesh sheet, int x, int y, int z, float atOrigin, float alongX, float alongY, boolean piped,
            boolean meetsAtStart, boolean meetsAtEnd) {
        if (!isEave(x, y, z, atOrigin, alongX, alongY)) {
            return;
        }
        boolean runsY = alongX != 0f;
        int startEnd = Eaves.end(meetsAtStart, roofGoesOn(runsY ? x : x - 1, runsY ? y - 1 : y, z));
        int endEnd = Eaves.end(meetsAtEnd, roofGoesOn(runsY ? x : x + 1, runsY ? y + 1 : y, z));
        hangEave(sheet, x, y, z, atOrigin, alongX, alongY, piped, startEnd, endEnd);
    }

    /** The eave past the low edge of a sloping tile, as addEave, with how each end of its run ends given: see Eaves. */
    private void hangEave(SpriteGeometry.Mesh sheet, int x, int y, int z, float atOrigin, float alongX, float alongY, boolean piped,
            int startEnd, int endEnd) {
        float along = alongX != 0f ? alongX : alongY;
        boolean lowAtFar = along < 0f;
        float low = lowAtFar ? atOrigin + along : atOrigin;
        float out = lowAtFar ? EAVE : -EAVE;
        float edge = lowAtFar ? 1f : 0f;
        float drop = -Math.abs(along) * EAVE;
        float lift = meanLift();
        float high = (z + low) * ViewCamera.LEVEL_HEIGHT + lift, far = (z + low + drop) * ViewCamera.LEVEL_HEIGHT + lift;
        boolean runsY = alongX != 0f;
        // The run's two ends, at the wall and at the eave's edge: where it starts and stops along the run.
        float wallFrom = -Eaves.atWall(startEnd, RAKE), edgeFrom = -Eaves.atEdge(startEnd, EAVE, RAKE);
        float wallTo = 1f + Eaves.atWall(endEnd, RAKE), edgeTo = 1f + Eaves.atEdge(endEnd, EAVE, RAKE);
        float[] c = build.eave;
        if (runsY) {
            WorldScene.set(c, 0, x + edge, y + wallFrom, high);
            WorldScene.set(c, 1, x + edge + out, y + edgeFrom, far);
            WorldScene.set(c, 2, x + edge + out, y + edgeTo, far);
            WorldScene.set(c, 3, x + edge, y + wallTo, high);
        } else {
            WorldScene.set(c, 0, x + wallFrom, y + edge, high);
            WorldScene.set(c, 1, x + edgeFrom, y + edge + out, far);
            WorldScene.set(c, 2, x + edgeTo, y + edge + out, far);
            WorldScene.set(c, 3, x + wallTo, y + edge, high);
        }
        SpriteGeometry.addQuad(sheet, c, roofU, roofV, build.frame.red, build.frame.green, build.frame.blue);
        // The soffit: the boarded underside of the eave, level from the fascia's foot back to
        // the wall, which is what is seen of a roof from the ground beside the house.
        SpriteGeometry.Mesh plain = WorldScene.solid.get(WorldScene.PLAIN);
        // The roofing itself goes in this mesh when the roof has no colour of its own yet.
        float roofing = sheet.open;
        plain.open = build.frame.open + 32f * WorldScene.CONCRETE;
        float under = far - FASCIA;
        float wx1 = c[0], wy1 = c[1], wx2 = c[9], wy2 = c[10], ex1 = c[3], ey1 = c[4], ex2 = c[6], ey2 = c[7];
        WorldScene.set(c, 0, wx1, wy1, under);
        WorldScene.set(c, 1, ex1, ey1, under);
        WorldScene.set(c, 2, ex2, ey2, under);
        WorldScene.set(c, 3, wx2, wy2, under);
        SpriteGeometry.addPlainQuad(plain, c, build.frame.red, build.frame.green, build.frame.blue);
        // The board along the eave's end.
        SpriteGeometry.Mesh board = WorldScene.solid.get(WorldScene.PLAIN, BOARD);
        board.open = build.frame.open;
        float x1 = ex1, y1 = ey1, x2 = ex2, y2 = ey2;
        WorldScene.set(c, 0, x1, y1, far);
        WorldScene.set(c, 1, x2, y2, far);
        WorldScene.set(c, 2, x2, y2, far - FASCIA);
        WorldScene.set(c, 3, x1, y1, far - FASCIA);
        SpriteGeometry.addPlainQuad(board, c, build.frame.red, build.frame.green, build.frame.blue);
        // Where the roof stops at a gable the eave is a hollow wedge seen end on, and is
        // closed with a board of its own.
        for (int end = 0; end < 2; end++) {
            if ((end == 0 ? startEnd : endEnd) != Eaves.GABLE) {
                continue;
            }
            float wx = end == 0 ? wx1 : wx2, wy = end == 0 ? wy1 : wy2, ex = end == 0 ? ex1 : ex2, ey = end == 0 ? ey1 : ey2;
            WorldScene.set(c, 0, wx, wy, high);
            WorldScene.set(c, 1, ex, ey, far);
            WorldScene.set(c, 2, ex, ey, under);
            WorldScene.set(c, 3, wx, wy, under);
            SpriteGeometry.addPlainQuad(board, c, build.frame.red, build.frame.green, build.frame.blue);
        }
        // The gutter along the eave: a trough standing out from the board, its outer face and
        // its underside.
        float ox = alongX != 0f ? Math.signum(out) * GUTTER : 0f, oy = alongX != 0f ? 0f : Math.signum(out) * GUTTER;
        plain.open = build.frame.open + 32f * WorldScene.QUAY;
        WorldScene.set(c, 0, x1 + ox, y1 + oy, far + 0.01f);
        WorldScene.set(c, 1, x2 + ox, y2 + oy, far + 0.01f);
        WorldScene.set(c, 2, x2 + ox, y2 + oy, far - GUTTER);
        WorldScene.set(c, 3, x1 + ox, y1 + oy, far - GUTTER);
        SpriteGeometry.addPlainQuad(plain, c, build.frame.red, build.frame.green, build.frame.blue);
        WorldScene.set(c, 0, x1, y1, far - GUTTER);
        WorldScene.set(c, 1, x2, y2, far - GUTTER);
        WorldScene.set(c, 2, x2 + ox, y2 + oy, far - GUTTER);
        WorldScene.set(c, 3, x1 + ox, y1 + oy, far - GUTTER);
        SpriteGeometry.addPlainQuad(plain, c, build.frame.red, build.frame.green, build.frame.blue);
        // Where the roof stops at a gable the gutter's end is capped: left open, its face and floor stood past the
        // corner as a black L seen from below (13744, 3318).
        for (int end = 0; end < 2; end++) {
            if ((end == 0 ? startEnd : endEnd) != Eaves.GABLE) {
                continue;
            }
            float ex = end == 0 ? x1 : x2, ey = end == 0 ? y1 : y2;
            WorldScene.set(c, 0, ex, ey, far + 0.01f);
            WorldScene.set(c, 1, ex + ox, ey + oy, far + 0.01f);
            WorldScene.set(c, 2, ex + ox, ey + oy, far - GUTTER);
            WorldScene.set(c, 3, ex, ey, far - GUTTER);
            SpriteGeometry.addPlainQuad(plain, c, build.frame.red, build.frame.green, build.frame.blue);
        }
        // Where the eave ends, at the corner of the building, the gutter empties into a pipe
        // down the wall to the ground.
        // Of the gutter's dark metal: pale, it reads as a post standing at the foot of the gable.
        if (piped && startEnd != Eaves.RUNS_ON) {
            int beyondX = x + (alongX != 0f ? (lowAtFar ? 1 : -1) : 0), beyondY = y + (alongY != 0f ? (lowAtFar ? 1 : -1) : 0);
            int wallX = runsY ? x + (int) edge : x, wallY = runsY ? y : y + (int) edge;
            float foot = Eaves.pipeFoot(z, level -> under(beyondX, beyondY, level), level -> walledAt(wallX, wallY, level, !runsY));
            // Against the wall at the building's corner, whatever the eave does past it.
            float px = runsY ? x + edge + ox * 0.5f : x, py = runsY ? y : y + edge + oy * 0.5f;
            float head = far - GUTTER;
            if (!Float.isNaN(foot) && head - foot - lift >= Eaves.SHORTEST_PIPE) {
                foot += lift;
                pipeFrom[0] = pipeTo[0] = px;
                pipeFrom[1] = pipeTo[1] = py;
                pipeFrom[2] = foot + Eaves.SHOE_HIGH;
                pipeTo[2] = head;
                build.geometry.addStrut(plain, pipeFrom, pipeTo, PIPE, 0f, 0f, build.frame.red, build.frame.green, build.frame.blue);
                // The shoe: the pipe's foot turned out from the wall, to throw the water clear of it.
                pipeTo[0] = px + Math.signum(ox) * Eaves.SHOE_OUT;
                pipeTo[1] = py + Math.signum(oy) * Eaves.SHOE_OUT;
                pipeTo[2] = foot + PIPE;
                build.geometry.addStrut(plain, pipeFrom, pipeTo, PIPE, 0f, 0f, build.frame.red, build.frame.green, build.frame.blue);
            }
        }
        sheet.open = roofing;
    }

    /**
     * Whether a wall stands on the edge of the square (x, y, level) under an eave that runs
     * along x (north) or along y, or on the square before it along the run: the pipe at the
     * start of the run, on the corner between the two, is against either.
     */
    private boolean walledAt(int x, int y, int level, boolean north) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, level), before = build.cell.getGridSquare(north ? x - 1 : x, north ? y : y - 1, level);
        return square != null && WorldScene.wallOn(square, north) != null || before != null && WorldScene.wallOn(before, north) != null;
    }

    /** What is under an eave on the square past it, a level at a time going down: see Eaves.pipeFoot. */
    private int under(int x, int y, int level) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, level);
        if (square == null) {
            return Eaves.AIR;
        }
        if (square.getRoomID() != -1L || Roofs.covered(build.cell, square) && !eaveRow(x, y, level) || build.madeAt(x, y, level)) {
            return Eaves.ROOM;
        }
        return square.getFloor() != null ? Eaves.FLOOR : Eaves.AIR;
    }

    /**
     * Whether the roof goes on over the square beside the end of a slope, an eave or a hip's
     * corner: a tile of roof lies there, made or the map's. A trim the map paints on the
     * square past a gable or an eave's end, a board or a corner piece narrower than a tile,
     * is no roof going on, so the rake, the eave's end board and the corner's eave are made.
     */
    private boolean roofGoesOn(int x, int y, int z) {
        if (build.madeAt(x, y, z)) {
            return true;
        }
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        if (square == null || !Roofs.roofing(build.cell, square)) {
            return false;
        }
        Roofs.Laid map = Roofs.at(build.cell, x, y, z, world.roundNow, build.frame.scale);
        return map != null && !map.tile.fitting;
    }
}
