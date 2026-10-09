package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Locale;
import zombie.core.textures.Texture;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.areas.IsoBuilding;

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
    private static final float FLAT_ROOF_LAYERS = 4f;
    /** A flat roof read lower over its floor than this, in levels, lies on the floor. */
    private static final float FLAT_ROOF_ON_FLOOR = 0.06f;
    /** The cap along a ridge or a hip: the roof's own covering, which the picture draws a shade darker. */
    private static final float RIDGE = 53f;
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

    RoofBuilder(WorldScene world, WorldScene.Frame build) {
        this.world = world;
        this.build = build;
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
        build.frame.light(build.unseenLight.r, build.unseenLight.g, build.unseenLight.b);
        build.frame.open = 1f;
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
            if (world.cutAway((int) Math.floor((cap.x0 + cap.x1) / 2f), (int) Math.floor((cap.y0 + cap.y1) / 2f))) {
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
        if (standing == null) {
            return;
        }
        int ground = foundOn;
        long room = foundIn;
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        // The wall is out of doors and is lit as the open air is. The roof tile's own square
        // is the loft under it, which is dark.
        float red = build.frame.red, green = build.frame.green, blue = build.frame.blue, open = build.frame.open;
        build.frame.cornersInto(build.heldRed, build.heldGreen, build.heldBlue);
        build.frame.light(build.unseenLight.r, build.unseenLight.g, build.unseenLight.b);
        build.frame.open = 1f;
        addGableWalls(square, standing, ground, room, x, y, z, northWest, northEast, southWest, peak, peakAxis);
        build.frame.red = red;
        build.frame.green = green;
        build.frame.blue = blue;
        build.frame.open = open;
        build.frame.cornersFrom(build.heldRed, build.heldGreen, build.heldBlue);
    }

    private void addGableWalls(IsoGridSquare square, IsoBuilding standing, int ground, long room, int x, int y, int z, float northWest, float northEast, float southWest,
            float peak, int peakAxis) {
        for (int edge = 0; edge < 2; edge++) {
            boolean north = edge == 1;
            float second = north ? northEast : southWest;
            if (Math.max(northWest, second) < GABLE_LEAST || buildingUnder(north ? x : x - 1, north ? y - 1 : y, z) != null
                    || square != null && WorldScene.wallOn(square, north) != null) {
                continue;
            }
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
                    if (finish != null) {
                        build.building.signed = false;
                    }
                    continue;
                }
                build.gablesPainted++;
                int built = Own.textures() ? Masonry.of(finish) : 0;
                build.building.signed &= !Own.textures() || !Masonry.unread(finish);
                SpriteGeometry.Mesh painted = WorldScene.solid.get(WorldScene.PLAIN, built != 0 ? WallSides.over(built) : WallSides.key(colour[0], colour[1], colour[2], WallSides.SIDING));
                painted.open = build.frame.open + 32f * WorldScene.PAINT;
                float foot = z * ViewCamera.LEVEL_HEIGHT + (build.geometry.lift[0] + build.geometry.lift[1] + build.geometry.lift[2] + build.geometry.lift[3]) / 4f;
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
                continue;
            }
            build.gablesFaced++;
            build.held.texelsOf(build.frame);
            float material = build.frame.material;
            build.frame.takeTexels(facing.texture);
            build.frame.material = WorldScene.materialOf(facing.sprite);
            SpriteGeometry.Mesh mesh = build.wallBuilder.laid(WorldScene.solid.get(facing.texture.getID()), facing.texture, Masonry.suits(facing.sprite.getName()), x, y, z);
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
            build.frame.texelsOf(build.held);
            build.frame.material = material;
        }
    }

    /**
     * A tile under a ridge that runs along its middle: two half slopes from its edges up to
     * the ridge, and at a gable, where no roof lies beside it along the ridge, both carried out
     * over the wall as the slopes below them are.
     */
    private void addPeak(SpriteGeometry.Mesh sheet, RoofFit.Tile tile) {
        boolean alongX = tile.peakAxis == RoofFit.ALONG_X;
        float lift = (build.geometry.lift[0] + build.geometry.lift[1] + build.geometry.lift[2] + build.geometry.lift[3]) / 4f;
        float edge = tile.fitLow * ViewCamera.LEVEL_HEIGHT + lift, top = (tile.fitLow + tile.peak) * ViewCamera.LEVEL_HEIGHT + lift;
        float from = tile.carried || hasRoof(alongX ? tile.x : tile.x - 1, alongX ? tile.y - 1 : tile.y, tile.z) ? 0f : -RAKE;
        float to = tile.carried || hasRoof(alongX ? tile.x : tile.x + 1, alongX ? tile.y + 1 : tile.y, tile.z) ? 1f : 1f + RAKE;
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
            if (from < 0f) {
                addBarge(sheet, c[0], c[1], c[2], c[3], c[4], c[5]);
            }
            if (to > 1f) {
                addBarge(sheet, c[9], c[10], c[11], c[6], c[7], c[8]);
            }
        }
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
        float lift = (build.geometry.lift[0] + build.geometry.lift[1] + build.geometry.lift[2] + build.geometry.lift[3]) / 4f;
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
        // A square with two roof sprites has one of them fitted. The other lies as it reads.
        // The fitted one holds what its sprite read as, which the fit leaves as it was.
        RoofFit.Tile tile = laid != null && laid.texture == texture ? laid.tile : Roofs.measure(build.frame, texture, x, y, z);
        // Half a tile at a ridge is not where the map has it: the ridge is made, see RoofFit.peaks.
        if (tile.half != 0) {
            return;
        }
        roof = laid == null ? null : laid.roof;
        if (tile.shaped) {
            addGable(x, y, z, tile.corners[0] - z, tile.corners[1] - z, tile.corners[3] - z, 0f, 0);
        } else if (tile.cutCorner < 0) {
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
            int[] order = RoofFit.TRIANGLES[tile.crease];
            for (int triangle = 0; triangle < 2; triangle++) {
                if ((tile.own >> triangle & 1) == 0) {
                    continue;
                }
                for (int i = 0; i < 3; i++) {
                    int corner = order[triangle * 3 + i];
                    WorldScene.set(folded, i, RoofFit.cornerX(corner), RoofFit.cornerY(corner), tile.corners[corner] - z);
                }
                build.geometry.addTriangle(mesh, build.frame, x, y, z, folded);
            }
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
        if (placed) {
            underRoof(x, y, z, low, alongX ? high - low : 0f, alongX ? 0f : high - low);
        } else if (build.frame.width > Roofs.WHOLE_TILE_WIDE * build.frame.scale) {
            // Which way this tile slopes is a guess, and a sheet laid to a wrong guess
            // stands out of the roof. A level one at the tile's lowest height cannot: it
            // is under the tile whichever way it slopes, and still closes the roof.
            underRoof(x, y, z, Math.min(low, high), 0f, 0f);
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
        sheet.open = build.frame.open + 32f * WorldScene.SHINGLES;
        return sheet;
    }

    /**
     * At a gable the roof runs a little past the end wall too: where a sloping tile has no roof
     * beside it across its slope, a strip of roof carries its slope out over the wall.
     */
    private void addRake(SpriteGeometry.Mesh sheet, int x, int y, int z, float atOrigin, float alongX, float alongY) {
        if (Math.abs(alongX + alongY) < Roofs.FLAT) {
            return;
        }
        float lift = (build.geometry.lift[0] + build.geometry.lift[1] + build.geometry.lift[2] + build.geometry.lift[3]) / 4f;
        float low = (z + atOrigin) * ViewCamera.LEVEL_HEIGHT + lift, high = (z + atOrigin + alongX + alongY) * ViewCamera.LEVEL_HEIGHT + lift;
        float[] c = build.eave;
        for (int side = 0; side < 2; side++) {
            int besideX = x + (alongX != 0f ? 0 : side == 0 ? -1 : 1), besideY = y + (alongX != 0f ? side == 0 ? -1 : 1 : 0);
            if (hasRoof(besideX, besideY, z)) {
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
     * The board under the edge of a roof where it runs out over a gable, from one end of a
     * length of that edge to the other: it hangs from the roof and follows its slope, as the
     * board along an eave follows the eave.
     */
    private void addBarge(SpriteGeometry.Mesh sheet, float x0, float y0, float up0, float x1, float y1, float up1) {
        SpriteGeometry.Mesh plain = WorldScene.solid.get(WorldScene.PLAIN);
        // The roofing itself goes in this mesh when the roof has no colour of its own yet.
        float roofing = sheet.open;
        plain.open = build.frame.open;
        float[] c = barge;
        WorldScene.set(c, 0, x0, y0, up0);
        WorldScene.set(c, 1, x1, y1, up1);
        WorldScene.set(c, 2, x1, y1, up1 - BARGE);
        WorldScene.set(c, 3, x0, y0, up0 - BARGE);
        SpriteGeometry.addPlainQuad(plain, c, 0.55f * build.frame.red, 0.55f * build.frame.green, 0.53f * build.frame.blue);
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
    private void addEave(SpriteGeometry.Mesh sheet, int x, int y, int z, float atOrigin, float alongX, float alongY, boolean piped) {
        addEave(sheet, x, y, z, atOrigin, alongX, alongY, piped, false, false);
    }

    /**
     * Whether the low edge of a sloping tile is an eave: the slope has a fall, and no roof
     * goes on beyond it. A slope that comes down to the foot of its level goes on in the top
     * of the level below: the tile beyond is there, and this is the middle of the roof.
     */
    private boolean isEave(int x, int y, int z, float atOrigin, float alongX, float alongY) {
        float along = alongX != 0f ? alongX : alongY;
        if (Math.abs(along) < Roofs.FLAT) {
            return false;
        }
        boolean lowAtFar = along < 0f;
        int beyondX = x + (alongX != 0f ? (lowAtFar ? 1 : -1) : 0), beyondY = y + (alongY != 0f ? (lowAtFar ? 1 : -1) : 0);
        float low = lowAtFar ? atOrigin + along : atOrigin;
        return !hasRoof(beyondX, beyondY, z) && !(low < Roofs.FLAT && hasRoof(beyondX, beyondY, z - 1));
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
        float along = alongX != 0f ? alongX : alongY;
        boolean lowAtFar = along < 0f;
        float low = lowAtFar ? atOrigin + along : atOrigin;
        float out = lowAtFar ? EAVE : -EAVE;
        float edge = lowAtFar ? 1f : 0f;
        float drop = -Math.abs(along) * EAVE;
        float lift = (build.geometry.lift[0] + build.geometry.lift[1] + build.geometry.lift[2] + build.geometry.lift[3]) / 4f;
        float high = (z + low) * ViewCamera.LEVEL_HEIGHT + lift, far = (z + low + drop) * ViewCamera.LEVEL_HEIGHT + lift;
        boolean runsY = alongX != 0f;
        int startEnd = Eaves.end(meetsAtStart, hasRoof(runsY ? x : x - 1, runsY ? y - 1 : y, z));
        int endEnd = Eaves.end(meetsAtEnd, hasRoof(runsY ? x : x + 1, runsY ? y + 1 : y, z));
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
        plain.open = build.frame.open;
        float x1 = ex1, y1 = ey1, x2 = ex2, y2 = ey2;
        WorldScene.set(c, 0, x1, y1, far);
        WorldScene.set(c, 1, x2, y2, far);
        WorldScene.set(c, 2, x2, y2, far - FASCIA);
        WorldScene.set(c, 3, x1, y1, far - FASCIA);
        SpriteGeometry.addPlainQuad(plain, c, 0.55f * build.frame.red, 0.55f * build.frame.green, 0.53f * build.frame.blue);
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
            SpriteGeometry.addPlainQuad(plain, c, 0.55f * build.frame.red, 0.55f * build.frame.green, 0.53f * build.frame.blue);
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
        // Where the eave ends, at the corner of the building, the gutter empties into a pipe
        // down the wall to the ground.
        // Of the gutter's dark metal: pale, it reads as a post standing at the foot of the gable.
        if (piped && startEnd != Eaves.RUNS_ON) {
            int beyondX = x + (alongX != 0f ? (lowAtFar ? 1 : -1) : 0), beyondY = y + (alongY != 0f ? (lowAtFar ? 1 : -1) : 0);
            float foot = Eaves.pipeFoot(z, level -> under(beyondX, beyondY, level));
            // Against the wall at the building's corner, whatever the eave does past it.
            float px = runsY ? x + edge + ox * 0.5f : x, py = runsY ? y : y + edge + oy * 0.5f;
            float head = far - GUTTER;
            if (!Float.isNaN(foot) && head - foot - lift >= Eaves.SHORTEST_PIPE) {
                foot += lift;
                pipeFrom[0] = pipeTo[0] = px;
                pipeFrom[1] = pipeTo[1] = py;
                pipeFrom[2] = foot + Eaves.SHOE_HIGH;
                pipeTo[2] = head;
                SpriteGeometry.addStrut(plain, pipeFrom, pipeTo, PIPE, 0f, 0f, build.frame.red, build.frame.green, build.frame.blue);
                // The shoe: the pipe's foot turned out from the wall, to throw the water clear of it.
                pipeTo[0] = px + Math.signum(ox) * Eaves.SHOE_OUT;
                pipeTo[1] = py + Math.signum(oy) * Eaves.SHOE_OUT;
                pipeTo[2] = foot + PIPE;
                SpriteGeometry.addStrut(plain, pipeFrom, pipeTo, PIPE, 0f, 0f, build.frame.red, build.frame.green, build.frame.blue);
            }
        }
        sheet.open = roofing;
    }

    /** What is under an eave on the square past it, a level at a time going down: see Eaves.pipeFoot. */
    private int under(int x, int y, int level) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, level);
        if (square == null) {
            return Eaves.AIR;
        }
        if (square.getRoomID() != -1L || Roofs.roofing(square) || build.madeAt(x, y, level)) {
            return Eaves.ROOM;
        }
        return square.getFloor() != null ? Eaves.FLOOR : Eaves.AIR;
    }

    private boolean hasRoof(int x, int y, int z) {
        if (build.madeAt(x, y, z)) {
            return true;
        }
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        return square != null && Roofs.roofing(square);
    }
}
