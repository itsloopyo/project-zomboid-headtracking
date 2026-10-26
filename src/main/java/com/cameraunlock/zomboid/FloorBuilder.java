package com.cameraunlock.zomboid;

import java.util.Arrays;
import zombie.core.properties.PropertyContainer;
import zombie.core.textures.ColorInfo;
import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;

/**
 * The floors of a chunk being built: a floor's own sprite, its tiles where the mod lays them, and the ground of its
 * neighbours spread over its edges. One to a frame of the world's picture, and it writes into that frame's meshes.
 */
final class FloorBuilder {
    /** Half the width of the joint between two tiles of a floor, in tiles of the map: the world program lays them so (FLOOR_JOINT). */
    static final float JOINT_HALF = 0.006f;
    /** How much darker than the sprite paints it the joint is drawn. */
    private static final float JOINT_DARK = 0.8f;
    /** A carpet no more uneven than this is one colour all over. A carpet with a figure woven in it is more, and keeps its sprite. */
    private static final float CARPET_UNEVEN = 0.006f;
    /** How far past the canopy's edge its lining runs, to meet the band hung just outside the edge. */
    private static final float CANOPY_LIP = 0.1f;

    private final WorldScene.Frame build;

    private final int[] spreading = new int[8];
    private final float[] coverGrid = new float[SpriteGeometry.GRID_COVER];
    /** How much of a square's own ground the ground spreading over it covers where the square's own goes on through. */
    private static final float OWN_KEPT = 0f;
    /**
     * How much of a path of loose ground the ground beside it covers at their shared edge:
     * enough for a ragged fringe a little way in and no more. As much as over other ground, it
     * ran half the square in.
     */
    private static final float MADE_EDGE = 0.6f;
    private final int[] around = new int[25];
    /**
     * Of a grid of three by three, the middles of the north, west, east and south edges and the step from
     * each to its two corners.
     */
    private static final int[] EDGE_MIDDLE = {1, 3, 5, 7}, EDGE_STEP = {1, 3, 3, 1};
    private final float[] tileTint = new float[4];
    private final float[] canopyRed = new float[4], canopyGreen = new float[4], canopyBlue = new float[4];

    FloorBuilder(WorldScene.Frame build) {
        this.build = build;
    }

    /** Whether spreadGround may lay the next squares' ground over this one. */
    boolean spreadsOver(int localX, int localY) {
        int own = (localX + 1) * WorldScene.LIGHT_SPAN + localY + 1;
        // A street ends at its kerb, and the verge stands behind it: nothing grows out over the tarmac.
        if (build.groundKind[own] == Terrain.UNKNOWN || build.groundKind[own] == Terrain.STREET || build.lightOpen[own] != 1) {
            return false;
        }
        // Nor over paving, which ends at a straight edge as the game paints it: frayed a quarter of a square in from
        // either side, a pavement one square wide read as eaten away.
        IsoObject floor = build.groundObject[own];
        if (build.groundKind[own] == Terrain.MADE && floor != null && floor.getSprite() != null) {
            PropertyContainer properties = floor.getSprite().getProperties();
            return Ground.loose(properties.get("FloorMaterial"), properties.get("FootstepMaterial"));
        }
        return true;
    }

    /**
     * The game lays its ground a square at a time, so where grass meets earth the line between
     * them is a staircase of square corners. Ground does not end like that. Here grass, earth
     * and sand spread over the edge of the ground and the paths of loose ground next to them:
     * the neighbour's ground is laid over this square too, all of it at the corners the
     * neighbour touches and none at the others, and the picture breaks the fade between into an
     * uneven edge.
     *
     * Of two kinds of open ground the one whose texture has the higher name spreads over the
     * other, which is as good a rule as any and is the same from both sides. Ground spreads
     * a fringe over a path of loose ground and out over the edge of water, never the other way.
     * It stops at paving and at a street, and nothing spreads indoors. A lone square of ground,
     * a tree's pit in a pavement, spreads over nothing but other open ground.
     *
     * Returns how many of its neighbours' grounds it laid over the square.
     */
    int spreadGround(int x, int y, int localX, int localY) {
        int own = (localX + 1) * WorldScene.LIGHT_SPAN + localY + 1;
        if (!spreadsOver(localX, localY)) {
            return 0;
        }
        int mine = build.groundRank[own];
        int spread = 0;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                int at = own + di * WorldScene.LIGHT_SPAN + dj;
                if (!spreads(at, own, mine)) {
                    continue;
                }
                int name = build.groundRank[at];
                boolean done = false;
                for (int k = 0; k < spread; k++) {
                    done |= spreading[k] == name;
                }
                if (done) {
                    continue;
                }
                spreading[spread++] = name;
                around(x, y, x - localX, y - localY, own, mine, name);
                boolean grid = coverFrom(around, build.cover, coverGrid);
                if (build.groundKind[own] == Terrain.MADE) {
                    for (int i = 0; i < coverGrid.length; i++) {
                        coverGrid[i] *= MADE_EDGE;
                    }
                    coverGrid[4] = 0f;
                    grid = true;
                }
                build.frameOf(build.groundObject[at], build.groundTexture[at]);
                // Of the neighbour's ground's stuff, as its own floor is: not of whatever was added last on this square.
                IsoSprite ground = build.groundObject[at].getSprite();
                PropertyContainer properties = ground.getProperties();
                build.frame.material = Ground.floor(properties.get("FloorMaterial"), properties.get("FootstepMaterial"), ground.getName());
                // Laid from the same flat picture as the neighbour's own floor, or the two meet in a seam
                // where the one picture of the ground runs into the other.
                int flat = build.flatOf(build.groundTexture[at], Flats.FLOOR, 0f, 0f, 0f);
                build.frame.flat = flat != 0 ? SpriteGeometry.FLAT_FLOOR : 0;
                build.geometry.cover = grid ? coverGrid : build.cover;
                // Grass does not spread down a kerb onto the street.
                build.geometry.kerbKeep = SpriteGeometry.LAND;
                build.spreadFaces++;
                WorldScene.layers.lie(SpriteGeometry.LAYERS_UP, build.floorLayer + spread - 1);
                SpriteGeometry.Mesh laidOn = WorldScene.laid.get(flat != 0 ? flat : build.groundTexture[at].getID());
                if (grid) {
                    for (int quarter = 0; quarter < 4; quarter++) {
                        build.geometry.addFace(laidOn, build.frame, x, y, 0, (quarter & 1) * 0.5f, (quarter >> 1) * 0.5f, 0f, 0.5f, 0f, 0f, 0f, 0.5f, 0f);
                    }
                } else {
                    build.geometry.addFace(laidOn, build.frame, x, y, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
                }
                build.frame.flat = 0;
                WorldScene.layers.none();
                build.geometry.kerbKeep = SpriteGeometry.BOTH;
                build.geometry.cover = null;
            }
        }
        return spread;
    }

    /** What the squares round one are to ground spreading over it, in coverFrom's around: */
    static final int OTHER = 0, OWN = 1, SPREADING = 2;

    /**
     * The squares round own, x across and y down from (-2, -2), as coverFrom reads them. The ring
     * two out is beyond the frame's grids and is read from the world, by the same tests.
     */
    private void around(int x, int y, int chunkX, int chunkY, int own, int mine, int name) {
        for (int i = 0; i < 25; i++) {
            int dx = i % 5 - 2, dy = i / 5 - 2;
            if (dx == 0 && dy == 0) {
                around[i] = OWN;
            } else if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
                int at = own + dx * WorldScene.LIGHT_SPAN + dy;
                around[i] = spreads(at, own, mine) && build.groundRank[at] == name ? SPREADING
                        : build.groundKind[at] == Terrain.NATURAL && build.lightOpen[at] == 1 && build.groundRank[at] == mine ? OWN : OTHER;
            } else {
                IsoGridSquare square = build.squareAt(chunkX, chunkY, x + dx, y + dy, 0);
                IsoObject floor = square == null ? null : square.getFloor();
                boolean natural = square != null && square.isOutside() && Terrain.kind(square, floor) == Terrain.NATURAL;
                int rank = natural ? build.rank(floor.getSprite()) : 0;
                // name is of a square that spreads over own: never mine, and above it where own is natural ground.
                around[i] = natural && rank == name && WorldScene.textureOf(floor) != null
                        && (build.groundKind[own] == Terrain.NATURAL || build.naturalBeside(chunkX, chunkY, x + dx, y + dy)) ? SPREADING
                        : natural && rank == mine ? OWN : OTHER;
            }
        }
    }

    /**
     * How much of a ground spreading over a square covers it, from what the squares round it
     * are (around, five by five of OTHER, OWN and SPREADING, row by row, the square itself in the middle):
     * corners at its four corners in SpriteGeometry.lift's order, and grid as a SpriteGeometry.GRID_COVER.
     * Returns whether the face has to be laid by grid; where it does not, grid is what corners
     * would give anyway.
     *
     * A corner the spreading ground touches is covered. Between two covered corners the edge is
     * covered too where the square across it is that ground, and where it is not, the square's own
     * ground runs on through the edge into the next square, as a path one square wide does between
     * grass on both sides. The middle of a square its own ground runs through, or that the
     * spreading ground lies on both sides of, keeps its own ground. And a corner where the
     * square's own ground meets itself across the diagonal, between two squares of the spreading
     * ground, is the square's own: a path that runs on the slant goes on through it. So is a corner
     * where the spreading ground has one of the four squares that meet there and the square's own
     * ground the other three, and the own ground does not go on beyond the square across from it:
     * there a path stepping on the slant turns, with grass on its far side too (often another of
     * the game's grasses, which is not the spreading ground), and covered it was cut at every
     * step. Where the own ground goes on beyond, it is the corner of a lawn or of a lone square of
     * grass, and is covered, rounding it. It is told from the four squares and the three
     * beyond, which each of the three squares reads alike, so they say the same of it. An edge by
     * such a corner, the square's own ground across it, is the square's own as well, the path going
     * on through it. Laid by the corners alone, each of these squares was the spreading ground all
     * over, and a path between grass was grass.
     */
    static boolean coverFrom(int[] around, float[] corners, float[] grid) {
        boolean byGrid = false;
        int turnsAt = 0;
        for (int corner = 0; corner < 4; corner++) {
            int cx = corner == 1 || corner == 2 ? 1 : 0, cy = corner >= 2 ? 1 : 0;
            int spreading = 0, own = 0, spreadX = 0, spreadY = 0;
            for (int ci = cx; ci <= cx + 1; ci++) {
                for (int cj = cy; cj <= cy + 1; cj++) {
                    int what = around[(cj + 1) * 5 + ci + 1];
                    spreading += what == SPREADING ? 1 : 0;
                    own += what == OWN ? 1 : 0;
                    if (what == SPREADING) {
                        spreadX = ci + 1;
                        spreadY = cj + 1;
                    }
                }
            }
            int dx = cx * 2, dy = cy * 2;
            boolean through = around[(dy + 1) * 5 + dx + 1] == OWN && around[10 + dx + 1] == SPREADING && around[(dy + 1) * 5 + 2] == SPREADING;
            boolean turns = false;
            if (spreading == 1 && own == 3) {
                // The square across the four from the spreading ground's, and the three one step further on.
                int acrossX = 2 * cx + 3 - spreadX, acrossY = 2 * cy + 3 - spreadY;
                int stepX = acrossX - spreadX, stepY = acrossY - spreadY;
                turns = around[acrossY * 5 + acrossX + stepX] != OWN || around[(acrossY + stepY) * 5 + acrossX] != OWN
                        || around[(acrossY + stepY) * 5 + acrossX + stepX] != OWN;
            }
            corners[corner] = spreading > 0 && !through && !turns ? 1f : 0f;
            grid[cy * 6 + cx * 2] = corners[corner];
            turnsAt |= turns ? 1 << cy * 6 + cx * 2 : 0;
            byGrid |= through;
        }
        int kept = 0;
        for (int edge = 0; edge < 4; edge++) {
            int at = EDGE_MIDDLE[edge];
            float p = grid[at - EDGE_STEP[edge]], q = grid[at + EDGE_STEP[edge]];
            int across = around[(at / 3 + 1) * 5 + at % 3 + 1];
            if (across == SPREADING) {
                grid[at] = 1f;
            } else if (p == 1f && q == 1f || across == OWN && (turnsAt >> at - EDGE_STEP[edge] & 1 | turnsAt >> at + EDGE_STEP[edge] & 1) == 1) {
                grid[at] = OWN_KEPT;
                kept++;
                byGrid = true;
            } else {
                grid[at] = (p + q) / 2f;
            }
        }
        if (grid[1] == 1f && grid[7] == 1f || grid[3] == 1f && grid[5] == 1f || kept >= 2) {
            grid[4] = OWN_KEPT;
            byGrid = true;
        } else {
            grid[4] = (corners[0] + corners[1] + corners[2] + corners[3]) / 4f;
        }
        return byGrid;
    }

    private boolean spreads(int at, int own, int mine) {
        if (build.groundKind[at] != Terrain.NATURAL || build.lightOpen[at] != 1 || build.groundTexture[at] == null) {
            return false;
        }
        int name = build.groundRank[at];
        return name != mine && (build.groundKind[own] != Terrain.NATURAL ? !build.groundLone[at] : name > mine);
    }

    /** A floor, or what the game lays on one, flat on the ground of its square. */
    void addFloor(SpriteGeometry.Mesh mesh, WorldScene.Kind sprite, PropertyContainer properties, Texture texture, boolean isFloor,
            int x, int y, int z) {
        // The game's own edging of one ground over another, a square at a time. spreadGround
        // does that here.
        if (!isFloor && z == 0 && sprite.natural) {
            return;
        }
        build.frame.kind = properties.has(IsoFlagType.water) ? SpriteGeometry.WATER : 0f;
        // The floor of a square is its sprite laid flat, a picture that goes on into the
        // squares beside it: see Flats. Not water, which the picture moves, nor what is
        // painted over a floor.
        boolean floor = isFloor && build.overLayer == 0 && build.floorLayer == 0 && build.frame.kind == 0f;
        int flat = floor ? build.flatOf(texture, Flats.FLOOR, 0f, 0f, 0f) : 0;
        build.frame.flat = flat != 0 ? SpriteGeometry.FLAT_FLOOR : 0;
        if (flat != 0) {
            mesh = WorldScene.solid.get(flat);
        }
        // A floor shows one pattern of boards, the sprite's: the picture's own wood is laid on it only as boards of the sprite's size and lie.
        // Where the picture lays none of its own textures a wooden floor stays wood, which is how its board joints and its gloss are drawn.
        boolean ownWood = build.frame.material == WorldScene.WOOD && Own.textures(build.playerIndex);
        Flats.Boards boards = ownWood && flat != 0 ? Flats.boards(flat) : null;
        if (ownWood && isFloor && boards == null) {
            build.frame.material = WorldScene.TILE;
        }
        if (build.floorLayer == 0 && z > build.lowestLevel) {
            // Under a floor over open air the sprite is its own underside: not the picture's wood.
            float made = build.frame.material;
            build.frame.material = ownWood ? WorldScene.TILE : made;
            addCeiling(mesh, x, y, z);
            build.frame.material = made;
        }
        // A line painted on the road is laid as paint, not as its sprite: see RoadPaint.
        boolean painted = (build.overLayer > 0 ? build.paintOver : sprite.paint && !isFloor) && build.frame.kind == 0f && Own.paint(build.playerIndex);
        RoadPaint.Shape paint = painted ? RoadPaint.of(texture, build.frame) : null;
        if (painted && paint == null && RoadPaint.unread(texture)) {
            build.building.signed = false;
        }
        SpriteGeometry.Mesh on = paint != null ? WorldScene.laid.get(paint.picture) : build.floorLayer == 0 ? mesh : WorldScene.laid.get(texture.getID());
        // Straw is the square's floor in a barn, and laid over the grass outside one.
        if (sprite.straw && build.frame.kind == 0f && build.geometry.kerb == null && Own.textures(build.playerIndex)) {
            build.frame.flat = 0;
            if (addStraw(texture, isFloor, x, y, z)) {
                return;
            }
            build.frame.flat = flat != 0 ? SpriteGeometry.FLAT_FLOOR : 0;
        }
        // A rug the game lays over the floor is its square drawn again: see Rugs.
        int rug = !isFloor && build.floorLayer > 0 && Own.textures(build.playerIndex) ? build.rugOf(texture, sprite.rug, sprite.pile, x, y, z) : 0;
        float laidAs = build.frame.material;
        if (rug != 0) {
            on = WorldScene.laid.get(rug);
            build.frame.flat = SpriteGeometry.FLAT_RUG;
            build.frame.material = Rugs.material(sprite.rug, build.frame.material);
        }
        if (build.frame.kind != 0f && z == 0 && build.floorLayer == 0) {
            // Water knows where its bank is: each corner says whether land touches it.
            for (int corner = 0; corner < 4; corner++) {
                int i = build.waterLocalX + (corner == 1 || corner == 2 ? 1 : 0), j = build.waterLocalY + (corner >= 2 ? 1 : 0);
                boolean land = false;
                for (int ci = 0; ci <= 1; ci++) {
                    for (int cj = 0; cj <= 1; cj++) {
                        // A square the map has not loaded is not land: counted as land,
                        // the last water the map holds has a bank along its far side,
                        // pale shallows and a line of foam where the water goes on.
                        byte kind = build.groundKind[(i + ci) * WorldScene.LIGHT_SPAN + j + cj];
                        land |= kind != Terrain.WATER && kind != Terrain.UNKNOWN;
                    }
                }
                build.cover[corner] = land ? 0f : 1f;
            }
            build.geometry.cover = build.cover;
        }
        // A floor a kerb crosses ends at the kerb, and the ground of the square beside goes on beyond
        // it. What the game paints over the floor, an edging or a line, ends there with it.
        if (z == 0 && build.geometry.kerb != null && (!isFloor || build.beyondKerb >= 0)) {
            build.geometry.kerbKeep = build.geometry.kerbStreet && !build.vergeOver ? SpriteGeometry.STREET : SpriteGeometry.LAND;
        }
        if (boards != null && build.geometry.kerb == null && Own.textures(build.playerIndex)) {
            addBoards(flat, boards, x, y, z);
            build.frame.flat = 0;
            build.geometry.cover = null;
            return;
        }
        if (boards != null) {
            build.frame.material = WorldScene.TILE;
        }
        if (build.floorLayer == 0 && isFloor && build.frame.kind == 0f && build.geometry.kerb == null && (sprite.carpet || sprite.tiled) && Own.textures(build.playerIndex)) {
            // Tiles are faces of their own on the sprite's own mesh, painted over.
            build.frame.flat = 0;
            if (addTiles(texture, sprite.carpet, properties.has(IsoFlagType.exterior), x, y, z)) {
                return;
            }
            build.frame.flat = flat != 0 ? SpriteGeometry.FLAT_FLOOR : 0;
        }
        layFloor(isFloor && build.frame.kind == 0f);
        if (paint != null) {
            RoadPaint.lay(build.geometry, on, build.frame, paint, x, y, z);
        } else {
            build.geometry.addFace(on, build.frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        }
        WorldScene.layers.none();
        build.geometry.kerbKeep = SpriteGeometry.BOTH;
        build.frame.kind = 0f;
        build.frame.flat = 0;
        if (rug != 0) {
            build.frame.material = laidAs;
        }
        build.geometry.cover = null;
    }

    /**
     * The next layer of a square's floor. The first of a floor, where floor is true, is a sheet that keeps the
     * sun off whatever is under it; what is painted over it lies on it as layers.
     */
    private void layFloor(boolean floor) {
        if (floor && build.floorLayer == 0) {
            WorldScene.layers.sheet(build.floorLayer++);
        } else {
            WorldScene.layers.lie(SpriteGeometry.LAYERS_UP, build.floorLayer++);
        }
    }

    /**
     * A square of tiled floor, laid: each tile a face of its own in its own colour, edge to edge
     * with the next, with the joints laid on it by the picture (WallSides.TILED). Laid as a bed
     * the colour of the joints with the tiles a joint apart on it, a joint was narrower than a
     * pixel a few paces off, and joints flickered in and out as the eye moved. How many tiles
     * and which colours is measured off the sprite: see Tiles. False, adding nothing, for a
     * sprite not measured yet or a floor that is no grid of plain tiles and not speckled.
     */
    private boolean addTiles(Texture texture, boolean carpet, boolean outside, int x, int y, int z) {
        Tiles.Shape tiles = Tiles.of(texture, build.frame);
        if (tiles == null) {
            if (Tiles.unread(texture)) {
                build.building.signed = false;
            }
            return false;
        }
        // A fitted carpet is one colour, with a nap the picture has a picture of: the few dots of
        // lighter and darker the game scatters on it are blots when magnified. So is terrazzo, with its chips.
        boolean terrazzo = !carpet && tiles.speckled;
        if (carpet ? tiles.uneven > CARPET_UNEVEN : !terrazzo && tiles.count == 0 && tiles.diagonal == 0 && tiles.blocks == 0 && tiles.pavers == null && tiles.hexes == null) {
            return false;
        }
        int count = carpet ? 1 : tiles.count;
        float made = build.frame.material, red = build.frame.red, green = build.frame.green, blue = build.frame.blue;
        build.frame.cornersInto(build.paperRed, build.paperGreen, build.paperBlue, build.paperUnlit);
        // Slabs and pavers laid outside, a drive or a path, are paving: as tiles they took the grain of tarmac.
        build.frame.material = carpet ? WorldScene.FLOOR_CARPET : outside && (tiles.rimmed || tiles.pavers != null || tiles.hexes != null) ? Ground.PAVING : WorldScene.TILE;
        float each = 1f / count;
        layFloor(true);
        if (terrazzo) {
            // Terrazzo is one face in the mean colour of the sprite, whose chips, blots when magnified, the picture lays.
            build.frame.material = WorldScene.TERRAZZO;
            build.geometry.addFace(painted(texture.getID(), red, green, blue, tiles.red, tiles.green, tiles.blue), build.frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        } else if (!carpet && tiles.blocks > 0) {
            // Parquet is one face in the mean colour of the sprite, which the picture lays as blocks of slats in its own wood.
            build.frame.material = WorldScene.parquetOf(tiles.blocks, tiles.slats, tiles.evenAlongY);
            build.geometry.addFace(painted(texture.getID(), red, green, blue, tiles.red, tiles.green, tiles.blue), build.frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        } else if (!carpet && tiles.pavers != null) {
            addPavers(tiles, red, green, blue, x, y, z);
        } else if (!carpet && tiles.hexes != null) {
            addHexes(tiles, red, green, blue, x, y, z);
        } else if (!carpet && tiles.diagonal > 0) {
            addDiagonal(tiles, red, green, blue, x, y, z);
        } else if (count == 1 && !tiles.rimmed) {
            float r = carpet ? tiles.red : tiles.colours[0], g = carpet ? tiles.green : tiles.colours[1], b = carpet ? tiles.blue : tiles.colours[2];
            build.geometry.addFace(painted(texture.getID(), red, green, blue, r, g, b), build.frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        } else {
            SpriteGeometry geometry = build.geometry;
            geometry.alongA = 1f;
            geometry.alongB = 0f;
            geometry.acrossA = 0f;
            geometry.acrossB = 1f;
            geometry.jointPhase = 0f;
            geometry.jointStep = each;
            geometry.footH = geometry.footV = geometry.perLevel = 0f;
            shade(red, green, blue, 1f, 1f, 1f);
            for (int i = 0; i < count; i++) {
                for (int j = 0; j < count; j++) {
                    int at = (i * count + j) * 3;
                    SpriteGeometry.Mesh on = WorldScene.solid.get(WorldScene.PLAIN, WallSides.laid(tiles.colours[at], tiles.colours[at + 1], tiles.colours[at + 2],
                            JOINT_DARK * tiles.jointRed, JOINT_DARK * tiles.jointGreen, JOINT_DARK * tiles.jointBlue, WallSides.TILED));
                    geometry.addFace(on, build.frame, x, y, z, i * each, j * each, 0f, each, 0f, 0f, 0f, each, 0f);
                }
            }
            geometry.jointStep = Float.NaN;
            geometry.acrossB = 0f;
        }
        WorldScene.layers.none();
        build.frame.material = made;
        build.frame.red = red;
        build.frame.green = green;
        build.frame.blue = blue;
        build.frame.cornersFrom(build.paperRed, build.paperGreen, build.paperBlue, build.paperUnlit);
        return true;
    }

    /**
     * A square of straw on a barn floor: one face on the sprite's own mesh in its mean colour, which the picture
     * beds with stalks of straw (WorldScene.STRAW). The stalks the game paints are blots a few dots long, the same
     * on every square. False, adding nothing, for a sprite not read yet.
     */
    private boolean addStraw(Texture texture, boolean isFloor, int x, int y, int z) {
        float[] colour = SpriteColours.of(texture);
        if (colour == null) {
            if (SpriteColours.unread(texture)) {
                build.building.signed = false;
            }
            return false;
        }
        float made = build.frame.material, red = build.frame.red, green = build.frame.green, blue = build.frame.blue;
        build.frame.cornersInto(build.paperRed, build.paperGreen, build.paperBlue, build.paperUnlit);
        build.frame.material = WorldScene.STRAW;
        layFloor(isFloor);
        build.geometry.addFace(painted(texture.getID(), red, green, blue, colour[WallSides.MEAN], colour[WallSides.MEAN + 1], colour[WallSides.MEAN + 2]),
                build.frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        WorldScene.layers.none();
        build.frame.material = made;
        build.frame.red = red;
        build.frame.green = green;
        build.frame.blue = blue;
        build.frame.cornersFrom(build.paperRed, build.paperGreen, build.paperBlue, build.paperUnlit);
        return true;
    }

    /**
     * A square of tiles laid on the diagonal (Tiles.Shape.diagonal), with the joints laid on it
     * by the picture: its texture coordinates count joints along a + b and along a - b. One
     * face in one colour, or for a chequer the diamonds of each colour cut to the square.
     */
    private void addDiagonal(Tiles.Shape tiles, float red, float green, float blue, int x, int y, int z) {
        SpriteGeometry geometry = build.geometry;
        geometry.alongA = geometry.alongB = geometry.acrossA = tiles.diagonal;
        geometry.acrossB = -tiles.diagonal;
        geometry.jointPhase = tiles.phase;
        geometry.jointStep = 1f;
        geometry.footV = -tiles.phaseAcross;
        geometry.footH = geometry.perLevel = 0f;
        shade(red, green, blue, 1f, 1f, 1f);
        for (int odd = 0; odd < (tiles.checkered ? 2 : 1); odd++) {
            SpriteGeometry.Mesh on = WorldScene.solid.get(WorldScene.PLAIN, diagonalKey(tiles, odd));
            if (!tiles.checkered) {
                geometry.addFace(on, build.frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
                break;
            }
            float[] triangles = odd == 0 ? tiles.evenTriangles : tiles.oddTriangles;
            for (int at = 0; at < triangles.length; at += 6) {
                for (int corner = 0; corner < 3; corner++) {
                    given[corner * 4] = triangles[at + corner * 2];
                    given[corner * 4 + 1] = triangles[at + corner * 2 + 1];
                }
                geometry.addGiven(on, build.frame, x, y, z, given, 0);
            }
        }
        geometry.jointStep = Float.NaN;
        geometry.alongA = geometry.alongB = geometry.acrossA = geometry.acrossB = geometry.jointPhase = geometry.footV = 0f;
    }

    /**
     * A square of pavers (Tiles.Shape.pavers): each paver a face of its own in its own colour,
     * its texture coordinates running from 0 to 1 across it each way, so that the picture lays
     * joints round its edges and none across it. Of a paver that runs over the square's edge
     * into the next, the part on this square: the next square lays the rest.
     */
    private void addPavers(Tiles.Shape tiles, float red, float green, float blue, int x, int y, int z) {
        SpriteGeometry geometry = build.geometry;
        geometry.alongB = geometry.acrossA = 0f;
        geometry.jointStep = 1f;
        geometry.footH = geometry.perLevel = 0f;
        shade(red, green, blue, 1f, 1f, 1f);
        float cellA = 1f / tiles.cellsA, cellB = 1f / tiles.cellsB;
        float[] pavers = tiles.pavers;
        for (int at = 0; at < pavers.length; at += 7) {
            float wide = pavers[at + 2] * cellA, deep = pavers[at + 3] * cellB;
            float startA = tiles.firstA + pavers[at] * cellA, startB = tiles.firstB + pavers[at + 1] * cellB;
            startA -= (float) Math.floor(startA);
            startB -= (float) Math.floor(startB);
            SpriteGeometry.Mesh on = WorldScene.solid.get(WorldScene.PLAIN, WallSides.laid(pavers[at + 4], pavers[at + 5], pavers[at + 6],
                    JOINT_DARK * tiles.jointRed, JOINT_DARK * tiles.jointGreen, JOINT_DARK * tiles.jointBlue, WallSides.TILED));
            geometry.alongA = 1f / wide;
            geometry.acrossB = 1f / deep;
            for (int backA = 0; backA <= 1; backA++) {
                for (int backB = 0; backB <= 1; backB++) {
                    float fromA = startA - backA, fromB = startB - backB;
                    float lowA = Math.max(fromA, 0f), highA = Math.min(fromA + wide, 1f), lowB = Math.max(fromB, 0f), highB = Math.min(fromB + deep, 1f);
                    if (highA <= lowA || highB <= lowB) {
                        continue;
                    }
                    geometry.jointPhase = fromA / wide;
                    geometry.footV = -fromB / deep;
                    geometry.addFace(on, build.frame, x, y, z, lowA, lowB, 0f, highA - lowA, 0f, 0f, 0f, highB - lowB, 0f);
                }
            }
        }
        geometry.jointStep = Float.NaN;
        geometry.alongA = geometry.acrossB = geometry.jointPhase = geometry.footV = 0f;
    }

    /**
     * A square of hexagonal pavers (Tiles.Shape.hexes): each paver in its own colour, laid as a
     * triangle from its middle to each of its six edges. A triangle's texture coordinates run
     * from 0 on its edge to a half at the middle, so that the picture lays a joint along the
     * edge and none inside. Of a paver that runs over the square's edge into the next, the part
     * on this square: the next square lays the rest.
     */
    private void addHexes(Tiles.Shape tiles, float red, float green, float blue, int x, int y, int z) {
        SpriteGeometry geometry = build.geometry;
        geometry.alongA = geometry.alongB = 0f;
        geometry.jointPhase = -0.5f;
        geometry.jointStep = 1f;
        geometry.footH = geometry.perLevel = 0f;
        shade(red, green, blue, 1f, 1f, 1f);
        float[] corners = tiles.hexCorners, hexes = tiles.hexes;
        float reach = 0f;
        for (float corner : corners) {
            reach = Math.max(reach, Math.abs(corner));
        }
        for (int at = 0; at < hexes.length; at += 5) {
            SpriteGeometry.Mesh on = WorldScene.solid.get(WorldScene.PLAIN, WallSides.laid(hexes[at + 2], hexes[at + 3], hexes[at + 4],
                    JOINT_DARK * tiles.jointRed, JOINT_DARK * tiles.jointGreen, JOINT_DARK * tiles.jointBlue, WallSides.TILED));
            for (int backA = -1; backA <= 1; backA++) {
                for (int backB = -1; backB <= 1; backB++) {
                    float midA = hexes[at] + backA, midB = hexes[at + 1] + backB;
                    if (midA + reach <= 0f || midA - reach >= 1f || midB + reach <= 0f || midB - reach >= 1f) {
                        continue;
                    }
                    for (int k = 0; k < 6; k++) {
                        float fromA = midA + corners[k * 2], fromB = midB + corners[k * 2 + 1];
                        float toA = midA + corners[(k * 2 + 2) % 12], toB = midB + corners[(k * 2 + 3) % 12];
                        // Square to the edge, towards the middle, as far as takes the coordinate from 0 on the edge to a half there.
                        float squareA = fromB - toB, squareB = toA - fromA;
                        float scale = 0.5f / (squareA * (midA - fromA) + squareB * (midB - fromB));
                        geometry.acrossA = squareA * scale;
                        geometry.acrossB = squareB * scale;
                        geometry.footV = -(squareA * fromA + squareB * fromB) * scale;
                        hexPiece[0] = midA;
                        hexPiece[1] = midB;
                        hexPiece[2] = fromA;
                        hexPiece[3] = fromB;
                        hexPiece[4] = toA;
                        hexPiece[5] = toB;
                        int count = clipToSquare(3);
                        for (int corner = 1; corner < count - 1; corner++) {
                            given[0] = hexPiece[0];
                            given[1] = hexPiece[1];
                            given[4] = hexPiece[corner * 2];
                            given[5] = hexPiece[corner * 2 + 1];
                            given[8] = hexPiece[corner * 2 + 2];
                            given[9] = hexPiece[corner * 2 + 3];
                            geometry.addGiven(on, build.frame, x, y, z, given, 0);
                        }
                    }
                }
            }
        }
        geometry.jointStep = Float.NaN;
        geometry.acrossA = geometry.acrossB = geometry.jointPhase = geometry.footV = 0f;
    }

    /** A triangle of a hexagonal paver as (a, b) corner after corner, and as much of it as lies on the square, which has at most seven. */
    private final float[] hexPiece = new float[14], hexCut = new float[14];

    /**
     * Cuts the polygon of count corners in hexPiece to the square, from a and b of 0 to 1, in
     * place, and returns how many corners are left: none where it lies off the square.
     */
    private int clipToSquare(int count) {
        for (int edge = 0; edge < 4 && count > 0; edge++) {
            int axis = edge % 2;
            float sign = edge < 2 ? 1f : -1f, offset = edge < 2 ? 0f : 1f;
            int kept = 0;
            for (int corner = 0; corner < count; corner++) {
                int next = (corner + 1) % count;
                float here = sign * (hexPiece[corner * 2 + axis] - offset), there = sign * (hexPiece[next * 2 + axis] - offset);
                if (here >= 0f) {
                    hexCut[kept * 2] = hexPiece[corner * 2];
                    hexCut[kept * 2 + 1] = hexPiece[corner * 2 + 1];
                    kept++;
                }
                if (here >= 0f != there >= 0f) {
                    float t = here / (here - there);
                    hexCut[kept * 2] = hexPiece[corner * 2] + t * (hexPiece[next * 2] - hexPiece[corner * 2]);
                    hexCut[kept * 2 + 1] = hexPiece[corner * 2 + 1] + t * (hexPiece[next * 2 + 1] - hexPiece[corner * 2 + 1]);
                    kept++;
                }
            }
            System.arraycopy(hexCut, 0, hexPiece, 0, kept * 2);
            count = kept;
        }
        return count;
    }

    /**
     * What the picture lays the even diamonds of tiles on the diagonal in, or for 1 the odd: the
     * diamonds' colour and their joints' as the sprite has them, lighter or darker. Two colours
     * that simply meet have no joint between them: it is each diamond's own colour.
     */
    static int diagonalKey(Tiles.Shape tiles, int odd) {
        float[] colours = tiles.colours;
        int at = odd * 3;
        return tiles.grouted
                ? WallSides.laid(colours[at], colours[at + 1], colours[at + 2], tiles.jointRed, tiles.jointGreen, tiles.jointBlue, WallSides.TILED)
                : WallSides.laid(colours[at], colours[at + 1], colours[at + 2], colours[at], colours[at + 1], colours[at + 2], WallSides.TILED);
    }

    /** A triangle for SpriteGeometry.addGiven: a, b and a texel for each corner, the texel unused under joints. */
    private final float[] given = new float[12];

    /**
     * A square of boarded floor in the picture's own wood: one face in the mean colour of the
     * sprite, which the picture lays with boards as many to a tile as the sprite has and
     * running the way they run. The sprite's own joints are not drawn under them.
     */
    private void addBoards(int flat, Flats.Boards boards, int x, int y, int z) {
        float made = build.frame.material, red = build.frame.red, green = build.frame.green, blue = build.frame.blue;
        build.frame.cornersInto(build.paperRed, build.paperGreen, build.paperBlue, build.paperUnlit);
        build.frame.material = WorldScene.boardsOf(boards.count(), boards.alongY());
        SpriteGeometry.Mesh on = painted(flat, red, green, blue, boards.red(), boards.green(), boards.blue());
        layFloor(true);
        build.geometry.addFace(on, build.frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        WorldScene.layers.none();
        build.frame.material = made;
        build.frame.red = red;
        build.frame.green = green;
        build.frame.blue = blue;
        build.frame.cornersFrom(build.paperRed, build.paperGreen, build.paperBlue, build.paperUnlit);
    }

    /**
     * The mesh of the texture with the given id painted in a colour, the frame lit as it was (red, green
     * and blue, and the corners held in paperRed and its fellows) times as much lighter or darker as the
     * colour is than the paint. The paint is the colour kept as finely as a tile's (WallSides.FINE),
     * and only a grey is left in the light: out of doors a light with more of one part than the open
     * ground's has is read as a lamp's, and kept lit at night when the daylight is held back. Kept to
     * sixteen steps a part, as a wall's paint is, a dark brown deck came out yellow.
     */
    private SpriteGeometry.Mesh painted(int texture, float red, float green, float blue, float r, float g, float b) {
        int key = WallSides.laid(r, g, b, 0f, 0f, 0f, WallSides.FINE);
        WallSides.tint(key, tileTint);
        float lighter = (r + g + b) / Math.max(tileTint[0] + tileTint[1] + tileTint[2], 0.06f);
        shade(red, green, blue, lighter, lighter, lighter);
        return WorldScene.solid.get(texture, key);
    }

    /** Lights the frame as it was lit, held in paperRed and its fellows, times a shade for each of red, green and blue. */
    void shade(float red, float green, float blue, float r, float g, float b) {
        build.frame.red = red * r;
        build.frame.green = green * g;
        build.frame.blue = blue * b;
        for (int corner = 0; corner < 4; corner++) {
            build.frame.cornerRed[corner] = build.paperRed[corner] * r;
            build.frame.cornerGreen[corner] = build.paperGreen[corner] * g;
            build.frame.cornerBlue[corner] = build.paperBlue[corner] * b;
        }
    }

    /**
     * Lines one square of a canopy and closes its edges: see Soffits. Its underside is as far
     * under the floor as the band round it is deep, lit by the ground beneath. Along its south
     * and east edges the map hangs the band. Its north and west edges, which the game's view
     * never saw, get the band from the far end of the same row or column of the canopy.
     */
    private void addCanopy(int x, int y, int z, float drop) {
        build.building.plain = false;
        IsoGridSquare ground = null;
        for (int under = z - 1; under >= 0 && ground == null; under--) {
            ground = build.cell.getGridSquare(x, y, under);
        }
        boolean shown = ground != null && ground.isSeen(build.playerIndex);
        ColorInfo light = shown ? build.lightOf(ground) : WorldScene.SHADE_ONLY;
        boolean lit = build.mainsOn || build.here.haveElectricity();
        // Under a lit canopy what the game has given no light is lit by the canopy alone.
        float red = lit ? Soffits.under(shown ? light.r : 0f, Soffits.GLOW_RED) : light.r, green = lit ? Soffits.under(shown ? light.g : 0f, Soffits.GLOW_GREEN) : light.g;
        float blue = lit ? Soffits.under(shown ? light.b : 0f, Soffits.GLOW_BLUE) : light.b;
        float unlit = shown || lit ? 0f : WorldScene.LIT_STEP * WorldScene.OPEN_LIT;
        Arrays.fill(canopyRed, red);
        Arrays.fill(canopyGreen, green);
        Arrays.fill(canopyBlue, blue);
        float up = z * ViewCamera.LEVEL_HEIGHT - drop;
        SpriteGeometry.Mesh plain = WorldScene.solid.get(WorldScene.PLAIN);
        Soffits.add(plain, x, y, up, canopyRed, canopyGreen, canopyBlue, unlit + 32f * WorldScene.CONCRETE, unlit + 32f * WorldScene.LAMP, Soffits.fitted(x, y), lit);
        plain.open = unlit + 32f * WorldScene.CONCRETE;
        float[] c = build.eave;
        for (int side = 0; side < 4; side++) {
            int stepX = side == 0 ? -1 : side == 1 ? 1 : 0, stepY = side == 2 ? -1 : side == 3 ? 1 : 0;
            if (WorldScene.isCanopy(build.cell.getGridSquare(x + stepX, y + stepY, z), build.cell.getGridSquare(x + stepX, y + stepY, z - 1))) {
                continue;
            }
            // The lining runs a little past the edge, under the foot of the band.
            float x0 = stepX < 0 ? x - CANOPY_LIP : stepX > 0 ? x + 1f : x, x1 = stepX < 0 ? x : stepX > 0 ? x + 1f + CANOPY_LIP : x + 1f;
            float y0 = stepY < 0 ? y - CANOPY_LIP : stepY > 0 ? y + 1f : y, y1 = stepY < 0 ? y : stepY > 0 ? y + 1f + CANOPY_LIP : y + 1f;
            WorldScene.set(c, 0, x0, y0, up);
            WorldScene.set(c, 1, x1, y0, up);
            WorldScene.set(c, 2, x1, y1, up);
            WorldScene.set(c, 3, x0, y1, up);
            SpriteGeometry.addPlainQuad(plain, c, red, green, blue);
            if (stepX > 0 || stepY > 0) {
                continue;
            }
            // The north or the west edge: the band of the edge across the canopy, stood here.
            boolean north = stepY < 0;
            IsoObject band = build.ceilingBuilder.fasciaOf(x, y, z, north);
            Texture texture = band == null ? null : WorldScene.textureOf(band);
            if (texture == null) {
                // No band to borrow: a plain one, the lining's colour, as deep as the canopy.
                float top = z * ViewCamera.LEVEL_HEIGHT;
                WorldScene.set(c, 0, x, y, up);
                WorldScene.set(c, 1, north ? x + 1f : x, north ? y : y + 1f, up);
                WorldScene.set(c, 2, north ? x + 1f : x, north ? y : y + 1f, top);
                WorldScene.set(c, 3, x, y, top);
                SpriteGeometry.addPlainQuad(plain, c, red, green, blue);
                continue;
            }
            build.bandFrame.scale = build.frame.scale;
            WorldScene.frameOf(build.bandFrame, band, texture);
            build.bandFrame.light(1f, 1f, 1f);
            build.bandFrame.open = WorldScene.OPEN_UNLIT;
            build.bandFrame.material = WorldScene.materialOf(band.getSprite());
            float raised = build.geometry.raised;
            build.geometry.raised = 0f;
            if (north) {
                build.geometry.shownB = y - CANOPY_LIP - build.bandY;
                build.geometry.addFace(WorldScene.solid.get(texture.getID()), build.bandFrame, x, build.bandY, z - 1, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
                build.geometry.shownB = 0f;
            } else {
                build.geometry.shownA = x - CANOPY_LIP - build.bandX;
                build.geometry.addFace(WorldScene.solid.get(texture.getID()), build.bandFrame, build.bandX, y, z - 1, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
                build.geometry.shownA = 0f;
            }
            build.geometry.raised = raised;
        }
    }

    /**
     * A floor that juts out over a pavement or a porch is the underside of a canopy, and one
     * over open ground shows its sprite underneath. Seen from below it is lit by the place
     * beneath, not by whatever falls on the floor above, so its underside is a face of its own
     * just under the floor, carrying the light of the square beneath: left to the floor's own
     * back, a canopy's underside is lit by the room upstairs. Over open air with no square in
     * it, it has the light of the open ground.
     */
    private void addCeiling(SpriteGeometry.Mesh mesh, int x, int y, int z) {
        IsoGridSquare below = build.cell.getGridSquare(x, y, z - 1);
        // A room's ceiling is its own, and so is a tall hall's over a row left out of its upper room: CeilingBuilder.addRoomCeiling.
        if (below != null && (below.getRoomID() != -1L || TallHalls.under(build.cell, x, y, z - 1) != null)) {
            return;
        }
        // Under water with nothing below it there is nothing to see the ceiling from, and
        // the picture drawn from under the water would show it in place of the water it leaves out.
        if (build.frame.kind != 0f && below == null) {
            return;
        }
        if (WorldScene.isCanopy(build.here, below)) {
            float drop = build.ceilingBuilder.canopyDrop(x, y, z);
            if (drop > 0f) {
                addCanopy(x, y, z, drop);
                return;
            }
        }
        float red = build.frame.red, green = build.frame.green, blue = build.frame.blue, open = build.frame.open, kind = build.frame.kind;
        build.frame.cornersInto(build.heldRed, build.heldGreen, build.heldBlue, build.heldUnlit);
        byte lit = below != null && below.isSeen(build.playerIndex) ? WorldScene.OWN_LIGHT
                : below == null || below.getRoomID() == -1L || build.asTheGameShows || build.gamesView ? WorldScene.OPEN_LIT
                : WorldScene.lampBurns(build.cell, below, build.lampsNear) ? WorldScene.LAMP_LIT : WorldScene.ROOM_LIT;
        ColorInfo light = lit == WorldScene.OWN_LIGHT ? build.daylit(below, build.lightOf(below)) : WorldScene.SHADE_ONLY;
        build.frame.light(light.r, light.g, light.b);
        build.frame.open = WorldScene.LIT_STEP * lit;
        if (lit == WorldScene.ROOM_LIT || lit == WorldScene.LAMP_LIT) {
            Arrays.fill(build.frame.cornerDay, build.dayShare(below));
        }
        // The ceiling under a floor of water is a ceiling, not more water.
        build.frame.kind = 0f;
        WorldScene.layers.sheet(-1f);
        build.geometry.addFace(mesh, build.frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        WorldScene.layers.none();
        build.frame.kind = kind;
        build.frame.red = red;
        build.frame.green = green;
        build.frame.blue = blue;
        build.frame.open = open;
        build.frame.cornersFrom(build.heldRed, build.heldGreen, build.heldBlue, build.heldUnlit);
    }
}
