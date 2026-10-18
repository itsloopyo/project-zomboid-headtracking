package com.cameraunlock.zomboid;

import java.util.Arrays;
import zombie.core.properties.PropertyContainer;
import zombie.core.textures.ColorInfo;
import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;

/**
 * The floors of a chunk being built: a floor's own sprite, its tiles where the mod lays them, and the ground of its
 * neighbours spread over its edges. One to a frame of the world's picture, and it writes into that frame's meshes.
 */
final class FloorBuilder {
    /** Half the width of the joint between two tiles, in tiles of the map, and how much darker than the sprite paints it the joint is drawn. */
    private static final float JOINT_HALF = 0.006f, JOINT_DARK = 0.8f;
    /** A carpet no more uneven than this is one colour all over. A carpet with a figure woven in it is more, and keeps its sprite. */
    private static final float CARPET_UNEVEN = 0.006f;
    /** How far past the canopy's edge its lining runs, to meet the band hung just outside the edge. */
    private static final float CANOPY_LIP = 0.1f;

    private final WorldScene.Frame build;

    private final int[] spreading = new int[8];
    private final float[] tileTint = new float[4];
    private final float[] canopyRed = new float[4], canopyGreen = new float[4], canopyBlue = new float[4];

    FloorBuilder(WorldScene.Frame build) {
        this.build = build;
    }

    /**
     * The game lays its ground a square at a time, so where grass meets earth the line between
     * them is a staircase of square corners. Ground does not end like that. Here grass, earth
     * and sand spread over the edge of whatever lies next to them: the neighbour's ground is
     * laid over this square too, all of it at the corners the neighbour touches and none at the
     * others, and the picture breaks the fade between into an uneven edge.
     *
     * Of two kinds of open ground the one whose texture has the higher name spreads over the
     * other, which is as good a rule as any and is the same from both sides. Ground spreads
     * over a road or a path and out over the edge of water, never the other way, and nothing
     * spreads indoors.
     */
    void spreadGround(int x, int y, int localX, int localY) {
        int own = (localX + 1) * WorldScene.LIGHT_SPAN + localY + 1;
        build.spreadLayers = 0;
        // A street ends at its kerb, and the verge stands behind it: nothing grows out over the tarmac.
        if (build.groundKind[own] == Terrain.UNKNOWN || build.groundKind[own] == Terrain.STREET || build.lightOpen[own] != 1) {
            return;
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
                for (int corner = 0; corner < 4; corner++) {
                    int i = localX + (corner == 1 || corner == 2 ? 1 : 0), j = localY + (corner >= 2 ? 1 : 0);
                    boolean touches = false;
                    for (int ci = 0; ci <= 1; ci++) {
                        for (int cj = 0; cj <= 1; cj++) {
                            int touching = (i + ci) * WorldScene.LIGHT_SPAN + j + cj;
                            touches |= touching != own && spreads(touching, own, mine) && build.groundRank[touching] == name;
                        }
                    }
                    build.cover[corner] = touches ? 1f : 0f;
                }
                build.frameOf(build.groundObject[at], build.groundTexture[at]);
                build.geometry.cover = build.cover;
                // Grass does not spread down a kerb onto the street.
                build.geometry.kerbKeep = SpriteGeometry.LAND;
                build.spreadFaces++;
                WorldScene.layers.lie(SpriteGeometry.LAYERS_UP, build.floorLayer + spread);
                build.geometry.addFace(WorldScene.laid.get(build.groundTexture[at].getID()), build.frame, x, y, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
                WorldScene.layers.none();
                build.geometry.kerbKeep = SpriteGeometry.BOTH;
                build.geometry.cover = null;
            }
        }
        build.spreadLayers = spread;
    }

    private boolean spreads(int at, int own, int mine) {
        if (build.groundKind[at] != Terrain.NATURAL || build.lightOpen[at] != 1 || build.groundTexture[at] == null) {
            return false;
        }
        int name = build.groundRank[at];
        return name != mine && (build.groundKind[own] != Terrain.NATURAL || name > mine);
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
            addBoards(WorldScene.solid.get(flat, WallSides.key(boards.red(), boards.green(), boards.blue(), WallSides.FLAT)), boards, x, y, z);
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
            if (addTiles(texture, sprite.carpet, x, y, z)) {
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
     * A square of tiled floor, laid: a bed the colour of the joints, and on it each tile as a
     * face of its own in its own colour, with a straight edge. How many tiles and which colours
     * is measured off the sprite: see Tiles. False, adding nothing, for a sprite not measured
     * yet or a floor that is no grid of plain tiles.
     */
    private boolean addTiles(Texture texture, boolean carpet, int x, int y, int z) {
        Tiles.Shape tiles = Tiles.of(texture, build.frame);
        if (tiles == null) {
            if (Tiles.unread(texture)) {
                build.building.signed = false;
            }
            return false;
        }
        // A fitted carpet is one colour, with a nap the picture has a picture of: the few dots of
        // lighter and darker the game scatters on it are blots when magnified.
        if (carpet ? tiles.uneven > CARPET_UNEVEN : tiles.count == 0) {
            return false;
        }
        int count = carpet ? 1 : tiles.count;
        int key = WallSides.key(tiles.red, tiles.green, tiles.blue, WallSides.FLAT);
        WallSides.tint(key, tileTint);
        SpriteGeometry.Mesh on = WorldScene.solid.get(texture.getID(), key);
        float made = build.frame.material, red = build.frame.red, green = build.frame.green, blue = build.frame.blue;
        build.frame.cornersInto(build.paperRed, build.paperGreen, build.paperBlue);
        build.frame.material = carpet ? WorldScene.CARPET : WorldScene.TILE;
        float joint = count > 1 ? JOINT_HALF : 0f, each = 1f / count;
        if (count > 1) {
            shade(red, green, blue, JOINT_DARK * tiles.jointRed / Math.max(tileTint[0], 0.02f), JOINT_DARK * tiles.jointGreen / Math.max(tileTint[1], 0.02f),
                    JOINT_DARK * tiles.jointBlue / Math.max(tileTint[2], 0.02f));
            layFloor(true);
            build.geometry.addFace(on, build.frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        }
        layFloor(true);
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < count; j++) {
                int at = (i * count + j) * 3;
                float r = carpet ? tiles.red : tiles.colours[at], g = carpet ? tiles.green : tiles.colours[at + 1], b = carpet ? tiles.blue : tiles.colours[at + 2];
                shade(red, green, blue, r / Math.max(tileTint[0], 0.02f), g / Math.max(tileTint[1], 0.02f), b / Math.max(tileTint[2], 0.02f));
                build.geometry.addFace(on, build.frame, x, y, z, i * each + joint, j * each + joint, 0f, each - 2f * joint, 0f, 0f, 0f, each - 2f * joint, 0f);
            }
        }
        WorldScene.layers.none();
        build.frame.material = made;
        build.frame.red = red;
        build.frame.green = green;
        build.frame.blue = blue;
        build.frame.cornersFrom(build.paperRed, build.paperGreen, build.paperBlue);
        return true;
    }

    /**
     * A square of boarded floor in the picture's own wood: one face in the mean colour of the
     * sprite, which the picture lays with boards as many to a tile as the sprite has and
     * running the way they run. The sprite's own joints are not drawn under them.
     */
    private void addBoards(SpriteGeometry.Mesh on, Flats.Boards boards, int x, int y, int z) {
        WallSides.tint(on.tint, tileTint);
        float made = build.frame.material, red = build.frame.red, green = build.frame.green, blue = build.frame.blue;
        build.frame.cornersInto(build.paperRed, build.paperGreen, build.paperBlue);
        build.frame.material = WorldScene.boardsOf(boards.count(), boards.alongY());
        shade(red, green, blue, boards.red() / Math.max(tileTint[0], 0.02f), boards.green() / Math.max(tileTint[1], 0.02f), boards.blue() / Math.max(tileTint[2], 0.02f));
        layFloor(true);
        build.geometry.addFace(on, build.frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        WorldScene.layers.none();
        build.frame.material = made;
        build.frame.red = red;
        build.frame.green = green;
        build.frame.blue = blue;
        build.frame.cornersFrom(build.paperRed, build.paperGreen, build.paperBlue);
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
        ColorInfo light = ground != null && ground.isSeen(build.playerIndex) ? build.lightOf(ground) : build.unseenLight;
        boolean lit = build.mainsOn || build.here.haveElectricity();
        float red = lit ? Soffits.under(light.r, Soffits.GLOW_RED) : light.r, green = lit ? Soffits.under(light.g, Soffits.GLOW_GREEN) : light.g;
        float blue = lit ? Soffits.under(light.b, Soffits.GLOW_BLUE) : light.b;
        Arrays.fill(canopyRed, red);
        Arrays.fill(canopyGreen, green);
        Arrays.fill(canopyBlue, blue);
        float up = z * ViewCamera.LEVEL_HEIGHT - drop;
        SpriteGeometry.Mesh plain = WorldScene.solid.get(WorldScene.PLAIN);
        Soffits.add(plain, x, y, up, canopyRed, canopyGreen, canopyBlue, 32f * WorldScene.CONCRETE, 32f * WorldScene.LAMP, Soffits.fitted(x, y), lit);
        plain.open = 32f * WorldScene.CONCRETE;
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
            build.bandFrame.light(build.unseenLight.r, build.unseenLight.g, build.unseenLight.b);
            build.bandFrame.open = 1f;
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
        // A room's ceiling is its own: CeilingBuilder.addRoomCeiling.
        if (below != null && below.getRoomID() != -1L) {
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
        build.frame.cornersInto(build.heldRed, build.heldGreen, build.heldBlue);
        byte lit = below == null || below.isSeen(build.playerIndex) || below.getRoomID() == -1L || build.asTheGameShows || build.gamesView ? WorldScene.OWN_LIGHT
                : WorldScene.lampBurns(build.cell, below, build.lampsNear) ? WorldScene.LAMP_LIT : WorldScene.ROOM_LIT;
        ColorInfo light = below != null && below.isSeen(build.playerIndex) ? build.lightOf(below) : lit == WorldScene.OWN_LIGHT ? build.unseenLight : WorldScene.SHADE_ONLY;
        if (below != null && below.isSeen(build.playerIndex)) {
            light = build.daylit(below, light);
        }
        build.frame.light(light.r, light.g, light.b);
        build.frame.open = WorldScene.LIT_STEP * lit;
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
        build.frame.cornersFrom(build.heldRed, build.heldGreen, build.heldBlue);
    }
}
