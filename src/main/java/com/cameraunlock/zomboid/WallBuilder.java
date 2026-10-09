package com.cameraunlock.zomboid;

import java.util.Arrays;
import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The walls of a chunk being built: a wall stood on the edge of its square with its two skins, its courses, the caps that
 * close its top and its ends, and the reveals of its openings. One to a frame of the world's picture, and it writes into
 * that frame's meshes.
 */
final class WallBuilder {
    /** How far in from a wall's end its sprite is looked over for its first painted pixel, in unscaled pixels. */
    private static final float MOST_UNPAINTED = 5f;
    /** The height, in levels, a wall's sprite is looked over at: under the sill of a window. */
    private static final float SEAL_HEIGHT = 0.15f;
    /** A wall whose sprite is painted this high up, in levels, is a whole wall and not a low one. */
    private static final float WHOLE_WALL = 0.9f;
    /** How far up a wall's sprite an opening is looked for, in levels: above a sill and below a lintel. */
    private static final float OPENING_ROW = 0.5f;
    /** The narrowest gap in a wall's sprite that is an opening, as a share of the wall's length. */
    private static final float NARROWEST_OPENING = 0.2f;
    /** How far inside an opening's corners its sprite has to be clear for it to be a rectangle, in unscaled pixels. */
    private static final float OPENING_CORNER = 1.5f;
    /** No door is lower than this, in levels. */
    private static final float LOWEST_DOOR = 0.5f;
    /** A window pane with a lamp burning in the room behind it. */
    private static final float LIT_GLASS = 65f;
    /** How far a wall's colour may be from that of its building's walls, summed over red, green and blue, for it to be built as they are. */
    private static final float SAME_FINISH = 0.3f;
    /** How high a wall has to stand short of its level for its top to be closed: a full wall's top is under the floor or the roof over it. */
    private static final float CAPPED_BELOW = 0.97f;
    /** Where along a wall its colour is looked for, for the faces that close it. */
    private static final float[] CAP_COLOUR_AT = {0.5f, 0.12f, 0.88f};
    /** Wallpaper with a figure on it, which the picture prints sprig by sprig. */
    private static final float FIGURED_PAPER = 70f;
    /** For each of the two edges a wall can stand on: the frame origin the sprite was read with, then the opening. */
    private static final int OPENING_FLOATS = 7, OPENING_ORIGIN = 0, OPENING_START = 2, OPENING_END = 3, OPENING_FOOT = 4, OPENING_HEAD = 5, OPENING_TOP = 6;
    /** Where along a wall its height is read, and the lowest a wall's top is looked for, in levels. */
    private static final float[] TOP_READ_AT = {0.15f, 0.5f, 0.85f};
    private static final float LOWEST_TOP = 0.05f;

    private final WorldScene world;
    private final WorldScene.Frame build;

    /** The plain outside wall the far skin of the wall last asked about can show, or null: see farTint. */
    private WallFinish.Facing farFacing;
    /** Where in the chunk's lights the square beyond the wall last asked about is, or -1 when it is the same place as this side. */
    private int farLight;
    private final float[] laidTint = new float[4];
    /** The paper of the wall being added, or null for a wall that shows its sprite, and whether the sprite being added is an inside wall of a house. */
    private Papers.Paper paper;
    /** The colour farTint last gave a far skin, or null where it gave none. */
    private float[] farColour;
    /** How far the skin being added runs on past its wall's two ends, in tiles: see WallJoins. */
    private float skinStart, skinEnd;

    /** Whether the sprite being added to an edge is a sign, which may run on over the next squares: see EdgeRuns. */
    boolean sign;
    private final float[] run = new float[2], next = new float[2], upright = new float[4];
    private final SpriteGeometry.Frame runFrame = new SpriteGeometry.Frame();

    WallBuilder(WorldScene world, WorldScene.Frame build) {
        this.world = world;
        this.build = build;
    }

    /**
     * Whether the square step squares along the edge (da, db) holds a sign on the same edge
     * that one piece with the sprite now being added makes, painted where run says this one is.
     */
    private boolean runsOn(int x, int y, int z, float da, float db, int step) {
        IsoGridSquare square = build.cell.getGridSquare(x + step * (int) da, y + step * (int) db, z);
        if (square == null) {
            return false;
        }
        IsoFlagType attached = da != 0f ? IsoFlagType.attachedN : IsoFlagType.attachedW;
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite == null || !sprite.getProperties().has(attached) || sprite.getProperties().has(IsoFlagType.WallOverlay)
                    || !EdgeRuns.sign(sprite.getName(), sprite.getProperties().get("CustomName"))) {
                continue;
            }
            Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
            if (!WorldScene.usable(texture)) {
                continue;
            }
            runFrame.scale = build.frame.scale;
            WorldScene.frameOf(runFrame, object, texture);
            EdgeRuns.along(runFrame, da, db, next);
            if (step > 0 ? EdgeRuns.meets(run[0], run[1], next[0], next[1]) : EdgeRuns.meets(next[0], next[1], run[0], run[1])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a face standing on the square from (a, b) along (da, db), a level high: a wall, or
     * what hangs on one. A wall's sprite is trimmed to everything it shows, its cap and its corner post too, so the wall
     * itself can stop a few pixels inside the sprite's edge with nothing painted beyond. A face
     * that took those texels would be see-through there, a slit of sky between two walls. So
     * the sprite is walked in from each end of the face, below any window, and down from its
     * top, to the first pixel that is painted, and the face takes its texels from there. Only
     * a wall: a window or a curtain that stops short of the square's edge really does stop there.
     */
    void addEdge(SpriteGeometry.Mesh mesh, Texture texture, boolean wall, boolean door, int x, int y, int z,
            float layer, float da, float db) {
        build.building.plain = false;
        // A wall is as thick as a wall: two skins, one each side of the square's edge, both
        // with the sprite's outline. Through a window or a doorway the far skin is seen
        // behind the near one, which is the depth of the opening. What hangs on the wall
        // hangs on the skin inside the square.
        // A window that is a whole length of wall, a shop's front or the side of a sun room, is glass in a frame too.
        if (build.overLayer == 0 && Own.buildings()
                && (build.frame.material == WorldScene.GLASS && build.openingBuilder.addWindow(null, texture, x, y, z, da, db) || build.glazedWall && build.frame.material != WorldScene.GLASS && build.openingBuilder.addWindow(mesh, texture, x, y, z, da, db))) {
            return;
        }
        // What shares the edge's plane with the wall lies its layers into the square.
        float way = db != 0f ? SpriteGeometry.LAYERS_X : SpriteGeometry.LAYERS_Y;
        WorldScene.layers.lie(way, layer);
        standEdge(mesh, texture, wall, door, x, y, z, way, layer, da, db);
        WorldScene.layers.none();
    }

    private void standEdge(SpriteGeometry.Mesh mesh, Texture texture, boolean wall, boolean door, int x, int y, int z,
            float way, float layer, float da, float db) {
        float inX = db * WorldScene.WALL_HALF, inY = da * WorldScene.WALL_HALF;
        if (wall && build.seeThrough) {
            // A fence of wire or of rails is one sheet on the line between its squares. As two
            // skins a wall's thickness apart, the eye saw one lattice through the other and
            // the two beat against each other as it moved.
            build.geometry.addFace(mesh, build.frame, x, y, z, 0f, 0f, 0f, da, db, 0f, 0f, 0f, 1f);
            return;
        }
        if (!wall) {
            // A finger's width clear of the skin: lying in the skin's own plane, the two are
            // drawn through each other.
            build.geometry.shownA = inX + db * WorldScene.HUNG_CLEAR;
            build.geometry.shownB = inY + da * WorldScene.HUNG_CLEAR;
            float material = build.frame.material;
            if (material == WorldScene.GLASS) {
                faceIntoRoom(x, y, z, da != 0f);
            }
            if (door && build.doorLeaf && build.overLayer == 0) {
                // A leaf hangs in the middle of the wall's thickness: a built one where the wall is, the sprite's own with the edge's layers.
                WorldScene.layers.none();
                boolean built = Own.buildings() && build.openingBuilder.addBuiltDoor(texture, x, y, z, da, db);
                WorldScene.layers.lie(way, layer);
                if (built || build.openingBuilder.addDoor(mesh, texture, x, y, z, 0f, 0f, da, db)) {
                    build.geometry.shownA = build.geometry.shownB = 0f;
                    return;
                }
            }
            // And three layers more, which are thicker the further off: there the picture's depth is coarser.
            WorldScene.layers.lie(way, layer + 3f);
            // A sign is laid over all it paints up and down its wall, and along it where it runs on over the next squares: see EdgeRuns.
            float from = 0f, length = 1f, foot = 0f, up = door ? doorTop(texture, 0f, 0f, da, db) : 1f;
            if (sign && material != WorldScene.GLASS) {
                EdgeRuns.along(build.frame, da, db, run);
                if (EdgeRuns.overhangs(run[0], run[1]) && (runsOn(x, y, z, da, db, -1) || runsOn(x, y, z, da, db, 1))) {
                    from = Math.min(0f, run[0]);
                    length = Math.max(1f, run[1]) - from;
                }
                EdgeRuns.upright(build.frame, da, db, upright);
                foot = upright[WallHung.BOTTOM];
                up = upright[WallHung.TOP] - foot;
                IsoGridSquare below = foot < 0f && z > 0 && Own.furniture() ? build.cell.getGridSquare(x, y, z - 1) : null;
                if (below != null) {
                    build.geometry.shownUp = EdgeRuns.onAwning(foot, Awnings.meetsWall(below, da != 0f));
                }
            }
            // A door's top is a straight line, and its sprite has it as a staircase of pixels
            // on the slant. The face stops at the door's top, so the line is the face's own edge.
            build.geometry.addFace(mesh, build.frame, x, y, z, from * da, from * db, foot, length * da, length * db, 0f, 0f, 0f, up);
            if (build.casing && build.overLayer == 0) {
                // A door's frame goes round the doorway on both sides of the wall. The game
                // has it on the side its view saw, and from the other the doorway was bare.
                build.geometry.shownA = -build.geometry.shownA;
                build.geometry.shownB = -build.geometry.shownB;
                WorldScene.layers.lie(way, layer - 3f);
                build.geometry.addFace(mesh, build.frame, x, y, z, 0f, 0f, 0f, da, db, 0f, 0f, 0f, 1f);
            }
            build.geometry.shownA = build.geometry.shownB = build.geometry.shownUp = 0f;
            build.geometry.turned = false;
            build.frame.material = material;
            return;
        }
        float fromStart = unpainted(texture, 0f, 0f, da, db);
        float fromEnd = unpainted(texture, da, db, -da, -db);
        float fromTop = unpaintedTop(texture, 0f, 0f, da, db);
        boolean startIsLow = SpriteGeometry.frameU(build.frame, 0f, 0f) < SpriteGeometry.frameU(build.frame, da, db);
        float[] opening = openingOf(texture, 0f, 0f, da, db);
        int at = da != 0f ? 0 : OPENING_FLOATS;
        // Where the wall's own sprite is painted to its end, the end is the wall's: a corner
        // post's sprite is a sliver at one end of its square and nothing at the other.
        float pixels = Math.abs(SpriteGeometry.frameU(build.frame, da, db) - SpriteGeometry.frameU(build.frame, 0f, 0f));
        boolean startPainted = paintedOnWall(texture, 0f, 0f, da, db, (fromStart + 1.5f) / pixels, SEAL_HEIGHT, 0f);
        boolean endPainted = paintedOnWall(texture, 0f, 0f, da, db, 1f - (fromEnd + 1.5f) / pixels, SEAL_HEIGHT, 0f);
        int startJoin = 0, endJoin = 0;
        if (build.overLayer == 0) {
            build.joinLevel = z;
            int joins = WallJoins.of(build.standing, x, y, da != 0f);
            startJoin = startPainted ? joins & 7 : 0;
            endJoin = endPainted ? joins >> 3 : 0;
        }
        build.geometry.shownA = inX;
        build.geometry.shownB = inY;
        skinStart = (startJoin & WallJoins.NEAR) != 0 ? WorldScene.WALL_HALF : 0f;
        skinEnd = (endJoin & WallJoins.NEAR) != 0 ? WorldScene.WALL_HALF : 0f;
        // An inside wall is its paper's stripes, each a face of its own: see Papers.
        paper = build.overLayer == 0 && build.paperedWall && Own.textures() ? Papers.of(texture, build.frame, da != 0f) : null;
        if (paper == null && build.overLayer == 0 && build.paperedWall && Papers.unread(texture)) {
            build.building.signed = false;
        }
        SpriteGeometry.Mesh near = laid(mesh, texture, build.laidWall, x, y, z);
        if (paper != null) {
            int key = WallSides.key(paper.red, paper.green, paper.blue, WallSides.FLAT);
            WallSides.tint(key, build.paperTint);
            near = WorldScene.solid.get(texture.getID(), key);
        }
        // A wall that shows its own sprite shows it stood square, so its courses run on into the next length: see Flats.
        near = stoodSquare(near, texture, da != 0f);
        int nearFlat = build.frame.flat;
        addSkin(near, opening, at, x, y, z, 0f, 0f, da, db, fromStart, fromEnd, fromTop, startIsLow);
        build.frame.flat = 0;
        skinStart = (startJoin & WallJoins.FAR) != 0 ? WorldScene.WALL_HALF : 0f;
        skinEnd = (endJoin & WallJoins.FAR) != 0 ? WorldScene.WALL_HALF : 0f;
        build.geometry.shownA = -inX;
        build.geometry.shownB = -inY;
        // The sprite is painted for the side inside the square. Where the far side is another
        // place, the skin on it is that side's surface. Only the wall itself: ivy or a
        // stain over it is on the side it was painted on, and is no part of the wall's colour.
        float material = build.frame.material;
        int tint = build.overLayer == 0 ? farTint(texture, x, y, z, da != 0f) : 0;
        // Out of doors the far skin is the building's own outside wall, the sprite its other
        // faces show, where the pieces round this wall's opening can take it: a wall whose
        // opening is cut out of its sprite alone would be closed by a sprite with none, and
        // a low wall would be made a whole one.
        WallFinish.Facing faced = tint != 0 && farFacing != null && farFacing.texture != texture
                && (!build.wallOpens || opening[at + OPENING_START] >= 0f) && paintedOnWall(texture, 0f, 0f, da, db, 0.5f, WHOLE_WALL, 0f) ? farFacing : null;
        if (faced != null) {
            tint = 0;
            build.held.texelsOf(build.frame);
            build.frame.takeTexels(faced.texture);
            build.frame.material = WorldScene.materialOf(faced.sprite);
            fromStart = unpainted(faced.texture, 0f, 0f, da, db);
            fromEnd = unpainted(faced.texture, da, db, -da, -db);
            fromTop = unpaintedTop(faced.texture, 0f, 0f, da, db);
        }
        if (tint != 0) {
            build.frame.material = WorldScene.PAINT;
        }
        // The far skin is in the place beyond the wall, and is lit as that place is: the
        // outside of a house by the day, whatever the room behind it is lit by.
        float red = build.frame.red, green = build.frame.green, blue = build.frame.blue, open = build.frame.open;
        if (build.overLayer == 0 && farLight >= 0) {
            build.frame.cornersInto(build.heldRed, build.heldGreen, build.heldBlue);
            if (build.lightOpen[farLight] < 0) {
                build.frame.light(build.unseenLight.r, build.unseenLight.g, build.unseenLight.b);
                build.frame.open = 1f;
            } else {
                lightAlong(farLight, da != 0f);
            }
        }
        // The far skin is papered as the near one only where it shows the same sprite.
        paper = faced == null && tint == 0 ? paper : null;
        SpriteGeometry.Mesh far = near;
        if (faced != null) {
            far = stoodSquare(laid(WorldScene.solid.get(faced.texture.getID()), faced.texture, Masonry.suits(faced.sprite.getName()), x, y, z), faced.texture, da != 0f);
        } else if (tint != 0) {
            far = WorldScene.solid.get(texture.getID(), tint);
        } else {
            build.frame.flat = nearFlat;
        }
        addSkin(far, opening, at, x, y, z, 0f, 0f, da, db, fromStart, fromEnd, fromTop, startIsLow);
        build.frame.flat = 0;
        paper = null;
        // Out of doors, on a whole wall in one colour.
        if (tint != 0 && farColour != null && build.frame.open > 0.5f && opening[at + OPENING_TOP] >= WHOLE_WALL) {
            addCourses(opening, at, x, y, z, 0f, 0f, da, db, -Math.signum(inX), -Math.signum(inY));
        }
        if (faced != null) {
            build.frame.texelsOf(build.held);
        }
        if (build.overLayer == 0 && farLight >= 0) {
            build.frame.red = red;
            build.frame.green = green;
            build.frame.blue = blue;
            build.frame.open = open;
            build.frame.cornersFrom(build.heldRed, build.heldGreen, build.heldBlue);
        }
        build.frame.material = material;
        skinStart = skinEnd = 0f;
        build.geometry.shownA = build.geometry.shownB = 0f;
        build.geometry.sealLow = build.geometry.sealHigh = build.geometry.sealTop = 0f;
        if (build.overLayer == 0 && opening[at + OPENING_START] >= 0f) {
            addReveals(opening, at, x, y, z, 0f, 0f, da, db, inX, inY);
        }
        if (build.overLayer == 0) {
            addCaps(mesh, texture, opening[at + OPENING_TOP], (startJoin & WallJoins.OPEN) != 0, (endJoin & WallJoins.OPEN) != 0,
                    startPainted && endPainted, x, y, z, 0f, 0f, da, db, inX, inY);
        }
    }

    /**
     * Lights the frame as the square a wall's far side stands in is lit, shaded from one end of
     * the wall to the other as the squares along it are: each end is the mean of that square
     * and the next one along the wall on that side, where the two are both indoors or both
     * out. One light for the whole length made every square of a wall its own flat panel. By a
     * lamp, whose light the game gives a colour, a shop's front was a teal square and a pink
     * one in a grey wall.
     */
    private void lightAlong(int at, boolean north) {
        int step = north ? WorldScene.LIGHT_SPAN : 1;
        build.frame.light(build.lightRed[at], build.lightGreen[at], build.lightBlue[at]);
        build.frame.open = build.lightOpen[at];
        for (int end = 0; end < 2; end++) {
            int beside = at + (end == 0 ? -step : step);
            // The lights are read a square past the chunk and no further: a far side that is in that last row has nothing beyond one end.
            boolean alike = beside >= 0 && beside < build.lightOpen.length && build.lightOpen[beside] == build.lightOpen[at];
            float red = alike ? (build.lightRed[at] + build.lightRed[beside]) / 2f : build.lightRed[at];
            float green = alike ? (build.lightGreen[at] + build.lightGreen[beside]) / 2f : build.lightGreen[at];
            float blue = alike ? (build.lightBlue[at] + build.lightBlue[beside]) / 2f : build.lightBlue[at];
            // The square's corners go north-west, north-east, south-east, south-west: a wall on its
            // north edge ends at the first two, one on its west edge at the first and the last.
            int near = end == 0 ? 0 : north ? 1 : 3, far = end == 0 ? (north ? 3 : 1) : 2;
            build.frame.cornerRed[near] = build.frame.cornerRed[far] = red;
            build.frame.cornerGreen[near] = build.frame.cornerGreen[far] = green;
            build.frame.cornerBlue[near] = build.frame.cornerBlue[far] = blue;
        }
    }

    /**
     * The mesh a wall's skin goes in when it is to show the given sprite as the frame has
     * it: the mesh of the sprite stood square, with the frame told so, where the skin was
     * to go in the sprite's own mesh and is the wall itself. Any other mesh, one painted
     * over, laid as masonry or papered, is given back as it is.
     */
    private SpriteGeometry.Mesh stoodSquare(SpriteGeometry.Mesh mesh, Texture texture, boolean north) {
        build.frame.flat = 0;
        if (build.overLayer != 0 || mesh.tint != 0 || mesh.texture != texture.getID()) {
            return mesh;
        }
        int stood = build.flatOf(texture, north ? Flats.NORTH : Flats.WEST, 0f, 0f, 0f);
        if (stood == 0) {
            return mesh;
        }
        build.frame.flat = north ? SpriteGeometry.FLAT_NORTH : SpriteGeometry.FLAT_WEST;
        return WorldScene.solid.get(stood);
    }

    /**
     * The mesh a wall's skin goes in: the sprite's own, or for a wall of brick, lapped boards
     * or stone the one the picture lays as that over the sprite. See Masonry. What is drawn
     * over a wall, ivy or a stain, stays as painted. suits is whether the sprite is of a
     * sheet of outside walls, and x, y, z the square the wall is on.
     */
    SpriteGeometry.Mesh laid(SpriteGeometry.Mesh mesh, Texture texture, boolean suits, int x, int y, int z) {
        if (build.overLayer != 0 || !Own.textures()) {
            return mesh;
        }
        // Upright boards are made board by board in the sprite's own colour: see timber.
        if (build.frame.material == WorldScene.BOARDS) {
            float[] colour = SpriteColours.of(texture);
            if (colour == null) {
                build.building.signed &= !SpriteColours.unread(texture);
                return mesh;
            }
            return WorldScene.solid.get(texture.getID(), WallSides.laid(colour[0], colour[1], colour[2], 0f, 0f, 0f, WallSides.BOARDED));
        }
        if (!suits) {
            return mesh;
        }
        // A building's walls are many sprites, with a window in one, a doorway in the next and
        // a low one along the roof, each read on its own, and a few read wrongly: the piece
        // round a doorway has little wall in it to read. What the plain walls of the building
        // show is read from the most wall. So a wall of the colour of the building's finish is
        // built as that finish is, whatever it reads as itself, and one wall is one thing
        // from end to end. The wall under a roof's end, cut to the slope with a vent in it,
        // is built so whatever its colour.
        // A wall on a building's south or east side stands on the square outside it, so the
        // building is looked for under the square and then under the two the wall may face.
        Texture finish = null;
        for (int side = 0; side < 3 && finish == null; side++) {
            int underX = x - (side == 2 ? 1 : 0), underY = y - (side == 1 ? 1 : 0);
            for (int level = 0; level <= z && finish == null; level++) {
                IsoGridSquare under = build.cell.getGridSquare(underX, underY, level);
                IsoBuilding standing = under == null ? null : under.getBuilding();
                if (standing != null) {
                    finish = WallFinish.outside(build.cell, standing, level, under.getRoomID(), world.roundNow);
                }
            }
        }
        boolean unread = false;
        int how = 0;
        if (finish != null && finish != texture) {
            int built = Masonry.of(finish);
            unread = Masonry.unread(finish);
            int read = build.gableSheet ? 0 : Masonry.of(texture);
            unread |= !build.gableSheet && Masonry.unread(texture);
            if (built != 0 && !build.gableSheet) {
                float[] own = SpriteColours.of(texture);
                WallSides.tint(built, laidTint);
                unread |= SpriteColours.unread(texture);
                // The game paints a wall on a north edge lighter than the same wall on a west
                // edge, as its own light falls. A wall that reads as the finish does, or as
                // nothing, is the finish if it is the finish's colour made lighter or darker.
                boolean samePaint = own != null && (read == 0 || (read & 15) == (built & 15)) && WallSides.oneColourInTwoLights(own, laidTint);
                boolean sameColour = own != null && Math.abs(own[0] - laidTint[0]) + Math.abs(own[1] - laidTint[1]) + Math.abs(own[2] - laidTint[2]) <= SAME_FINISH;
                built = samePaint || sameColour ? built : 0;
            }
            how = built != 0 ? built : read;
        } else if (!build.gableSheet) {
            how = Masonry.of(texture);
            unread |= Masonry.unread(texture);
        }
        // A sprite is read by the next frame drawn. A build that went without any it asked
        // for is made again when next due, though nothing it read of the map has changed:
        // what was read may lay the wall one way now and the rest another once it is in.
        build.building.signed &= !unread;
        if (how == 0) {
            return mesh;
        }
        return WorldScene.solid.get(texture.getID(), how);
    }

    /**
     * Turns the window on the north or west edge of the square being added to face into its
     * room, which is how the picture tells the street side of a pane from the room side, and
     * marks it as lit from inside when a lamp burns in the room. A window between two
     * rooms, or between two places out of doors, faces into its own square.
     */
    private void faceIntoRoom(int x, int y, int z, boolean north) {
        IsoGridSquare beyond = build.cell.getGridSquare(north ? x : x - 1, north ? y - 1 : y, z);
        boolean ownIsRoom = build.here.getRoomID() != -1L || beyond == null || beyond.getRoomID() == -1L;
        // A face on a north edge looks north, out of its square, and one on a west edge east, into it.
        build.geometry.turned = north == ownIsRoom;
        if (WorldScene.lamplit(build.cell, ownIsRoom ? build.here : beyond, build.playerIndex, build.asTheGameShows || build.gamesView, build.lampsNear)) {
            build.frame.material = LIT_GLASS;
        }
    }

    /**
     * Closes a wall where the eye could see in between its two skins: across an end that
     * nothing meets, a doorway with no frame or the last panel of a fence; over the top of one
     * that stops short of its level, a fence, a garden wall, a parapet; and under the foot of
     * one that stands on an upper floor, which is seen from below where the floor juts out.
     * Each is the colour the wall's sprite has low down on it.
     */
    private void addCaps(SpriteGeometry.Mesh mesh, Texture texture, float top, boolean openStart, boolean openEnd, boolean whole,
            int x, int y, int z, float a, float b, float da, float db, float inX, float inY) {
        boolean overTop = whole && top > 0f && top < CAPPED_BELOW, underFoot = whole && z > 0;
        if (top <= 0f || !openStart && !openEnd && !overTop && !underFoot) {
            return;
        }
        float u = 0f, v = 0f;
        boolean found = false;
        for (float along : CAP_COLOUR_AT) {
            if (paintedOnWall(texture, a, b, da, db, along, SEAL_HEIGHT, 0f)) {
                u = SpriteGeometry.textureU(build.frame, a + da * along, b + db * along);
                v = SpriteGeometry.textureV(build.frame, a + da * along, b + db * along, SEAL_HEIGHT);
                found = true;
                break;
            }
        }
        if (!found) {
            return;
        }
        mesh.open = build.frame.open + 32f * build.frame.material;
        mesh.sway = 0f;
        float x0 = x + a, y0 = y + b, x1 = x0 + da, y1 = y0 + db;
        float foot0 = z * ViewCamera.LEVEL_HEIGHT + build.geometry.liftAt(a, b), foot1 = z * ViewCamera.LEVEL_HEIGHT + build.geometry.liftAt(a + da, b + db);
        float high = top * ViewCamera.LEVEL_HEIGHT;
        float[] c = build.reveal;
        for (int end = 0; end < 2; end++) {
            if (end == 0 ? !openStart : !openEnd) {
                continue;
            }
            float ex = end == 0 ? x0 : x1, ey = end == 0 ? y0 : y1, foot = end == 0 ? foot0 : foot1;
            WorldScene.set(c, 0, ex - inX, ey - inY, foot);
            WorldScene.set(c, 1, ex + inX, ey + inY, foot);
            WorldScene.set(c, 2, ex + inX, ey + inY, foot + high);
            WorldScene.set(c, 3, ex - inX, ey - inY, foot + high);
            SpriteGeometry.addQuad(mesh, c, u, v, build.frame.red, build.frame.green, build.frame.blue);
        }
        for (int side = 0; side < 2; side++) {
            if (side == 0 ? !overTop : !underFoot) {
                continue;
            }
            float up = side == 0 ? high : 0f;
            WorldScene.set(c, 0, x0 - inX, y0 - inY, foot0 + up);
            WorldScene.set(c, 1, x0 + inX, y0 + inY, foot0 + up);
            WorldScene.set(c, 2, x1 + inX, y1 + inY, foot1 + up);
            WorldScene.set(c, 3, x1 - inX, y1 - inY, foot1 + up);
            SpriteGeometry.addQuad(mesh, c, u, v, build.frame.red, build.frame.green, build.frame.blue);
        }
    }

    /**
     * One skin of a wall. A wall with a door or a window in it is the wall round the opening,
     * in pieces, and not one face with a hole in its picture: the edges of the opening are then
     * edges of faces, straight, where the sprite has them as staircases of pixels on the slant.
     */
    private void addSkin(SpriteGeometry.Mesh mesh, float[] opening, int at, int x, int y, int z, float a, float b, float da, float db,
            float fromStart, float fromEnd, float fromTop, boolean startIsLow) {
        float start = opening[at + OPENING_START], end = opening[at + OPENING_END];
        // A skin carried on past its wall's end to the outside of a corner takes its texels
        // from as far inside the sprite as it would have, the sprite having nothing out there.
        float perTile = SpriteGeometry.PIXELS_ACROSS_PER_TILE * build.frame.scale;
        float first = -skinStart, last = 1f + skinEnd;
        fromStart += skinStart * perTile;
        fromEnd += skinEnd * perTile;
        if (start < 0f) {
            addPiece(mesh, x, y, z, a, b, da, db, first, last, 0f, 1f, fromStart, fromEnd, fromTop, startIsLow);
            return;
        }
        float foot = opening[at + OPENING_FOOT], head = opening[at + OPENING_HEAD];
        addPiece(mesh, x, y, z, a, b, da, db, first, start, 0f, 1f, fromStart, 0f, fromTop, startIsLow);
        addPiece(mesh, x, y, z, a, b, da, db, end, last, 0f, 1f, 0f, fromEnd, fromTop, startIsLow);
        if (head < 1f) {
            addPiece(mesh, x, y, z, a, b, da, db, start, end, head, 1f, 0f, 0f, fromTop, startIsLow);
        }
        if (foot > 0f) {
            addPiece(mesh, x, y, z, a, b, da, db, start, end, 0f, foot, 0f, 0f, 0f, startIsLow);
        }
    }

    /** The part of a wall from one share of its length to another and from one height to another, in levels. */
    private void addPiece(SpriteGeometry.Mesh mesh, int x, int y, int z, float a, float b, float da, float db,
            float from, float to, float foot, float head, float sealFrom, float sealTo, float sealTop, boolean startIsLow) {
        build.geometry.sealTop = sealTop;
        if (paper == null) {
            build.geometry.sealLow = startIsLow ? sealFrom : sealTo;
            build.geometry.sealHigh = startIsLow ? sealTo : sealFrom;
            build.geometry.addFace(mesh, build.frame, x, y, z, a + da * from, b + db * from, foot, da * (to - from), db * (to - from), 0f, 0f, 0f, head - foot);
            return;
        }
        // The mesh is painted over in the paper's colour as a whole, and each stripe is lit by
        // as much more or less as its own colour is of that.
        float made = build.frame.material;
        build.frame.material = paper.figured ? FIGURED_PAPER : made;
        float red = build.frame.red, green = build.frame.green, blue = build.frame.blue;
        build.frame.cornersInto(build.paperRed, build.paperGreen, build.paperBlue);
        float begin = 0f;
        for (int stripe = 0; stripe < paper.ends.length; stripe++) {
            float end = paper.ends[stripe];
            float low = stripe == 0 ? from : Math.max(from, begin), high = stripe == paper.ends.length - 1 ? to : Math.min(to, end);
            begin = end;
            if (high <= low) {
                continue;
            }
            float r = paper.colours[stripe * 3] / Math.max(build.paperTint[0], 0.02f), g = paper.colours[stripe * 3 + 1] / Math.max(build.paperTint[1], 0.02f),
                    bl = paper.colours[stripe * 3 + 2] / Math.max(build.paperTint[2], 0.02f);
            build.floorBuilder.shade(red, green, blue, r, g, bl);
            float sealStart = low == from ? sealFrom : 0f, sealEnd = high == to ? sealTo : 0f;
            build.geometry.sealLow = startIsLow ? sealStart : sealEnd;
            build.geometry.sealHigh = startIsLow ? sealEnd : sealStart;
            build.geometry.addFace(mesh, build.frame, x, y, z, a + da * low, b + db * low, foot, da * (high - low), db * (high - low), 0f, 0f, 0f, head - foot);
        }
        build.frame.material = made;
        build.frame.red = red;
        build.frame.green = green;
        build.frame.blue = blue;
        build.frame.cornersFrom(build.paperRed, build.paperGreen, build.paperBlue);
    }

    /**
     * The sides of an opening through a wall, from one skin to the other: the jambs, the
     * underside of the lintel and the sill. Without them the eye sees in between the two skins.
     */
    private void addReveals(float[] opening, int at, int x, int y, int z, float a, float b, float da, float db, float inX, float inY) {
        SpriteGeometry.Mesh plain = WorldScene.solid.get(WorldScene.PLAIN);
        plain.open = build.frame.open + 32f * WorldScene.CONCRETE;
        float lift = (build.geometry.lift[0] + build.geometry.lift[1] + build.geometry.lift[2] + build.geometry.lift[3]) / 4f;
        float foot = (z + opening[at + OPENING_FOOT]) * ViewCamera.LEVEL_HEIGHT + lift, head = (z + opening[at + OPENING_HEAD]) * ViewCamera.LEVEL_HEIGHT + lift;
        float x0 = x + a + da * opening[at + OPENING_START], y0 = y + b + db * opening[at + OPENING_START];
        float x1 = x + a + da * opening[at + OPENING_END], y1 = y + b + db * opening[at + OPENING_END];
        float[] c = build.reveal;
        for (int side = 0; side < 2; side++) {
            float jx = side == 0 ? x0 : x1, jy = side == 0 ? y0 : y1;
            WorldScene.set(c, 0, jx - inX, jy - inY, foot);
            WorldScene.set(c, 1, jx + inX, jy + inY, foot);
            WorldScene.set(c, 2, jx + inX, jy + inY, head);
            WorldScene.set(c, 3, jx - inX, jy - inY, head);
            SpriteGeometry.addPlainQuad(plain, c, build.frame.red, build.frame.green, build.frame.blue);
        }
        for (int side = 0; side < 2; side++) {
            if (side == 0 ? opening[at + OPENING_HEAD] >= 1f : opening[at + OPENING_FOOT] <= 0f) {
                continue;
            }
            float up = side == 0 ? head : foot;
            WorldScene.set(c, 0, x0 - inX, y0 - inY, up);
            WorldScene.set(c, 1, x0 + inX, y0 + inY, up);
            WorldScene.set(c, 2, x1 + inX, y1 + inY, up);
            WorldScene.set(c, 3, x1 - inX, y1 - inY, up);
            SpriteGeometry.addPlainQuad(plain, c, build.frame.red, build.frame.green, build.frame.blue);
        }
    }

    /**
     * Where the door or window through a wall is, read off the wall's sprite: at at + OPENING_START
     * and OPENING_END the shares of the wall's length it starts and ends at, and at OPENING_FOOT
     * and OPENING_HEAD the heights of its sill and its lintel, in levels. A start below 0 is a
     * wall with no opening, or with one that is not a rectangle, an arched window, or with more
     * than one. Read once for each sprite on each edge.
     */
    private float[] openingOf(Texture texture, float a, float b, float da, float db) {
        int at = da != 0f ? 0 : OPENING_FLOATS;
        float[] known = WorldScene.openings.get(texture);
        if (known == null) {
            known = new float[2 * OPENING_FLOATS];
            Arrays.fill(known, Float.NaN);
            WorldScene.openings.put(texture, known);
        }
        if (known[at + OPENING_ORIGIN] == build.frame.originU && known[at + OPENING_ORIGIN + 1] == build.frame.originV) {
            return known;
        }
        known[at + OPENING_ORIGIN] = build.frame.originU;
        known[at + OPENING_ORIGIN + 1] = build.frame.originV;
        known[at + OPENING_START] = -1f;
        known[at + OPENING_TOP] = wallTop(texture, a, b, da, db);
        int pixels = Math.round(Math.abs(SpriteGeometry.frameU(build.frame, a + da, b + db) - SpriteGeometry.frameU(build.frame, a, b)));
        int first = -1, last = -1, gaps = 0, run = -1;
        for (int pixel = 0; pixel <= pixels; pixel++) {
            boolean clear = pixel < pixels && !paintedOnWall(texture, a, b, da, db, (pixel + 0.5f) / pixels, OPENING_ROW, 0f);
            if (clear && run < 0) {
                run = pixel;
            } else if (!clear && run >= 0) {
                // A gap that reaches the wall's end is the sprite stopping short there, not an opening.
                if (run > 0 && pixel < pixels && pixel - run >= NARROWEST_OPENING * pixels) {
                    gaps++;
                    first = run;
                    last = pixel;
                }
                run = -1;
            }
        }
        if (gaps != 1) {
            return known;
        }
        float start = (float) first / pixels, end = (float) last / pixels, middle = (start + end) / 2f;
        float perPixel = 1f / (SpriteGeometry.PIXELS_PER_LEVEL * build.frame.scale);
        float head = 1f, foot = 0f;
        for (float h = OPENING_ROW; h < 1f; h += perPixel) {
            if (paintedOnWall(texture, a, b, da, db, middle, h, 0f)) {
                head = h - perPixel / 2f;
                break;
            }
        }
        for (float h = OPENING_ROW; h > 0f; h -= perPixel) {
            if (paintedOnWall(texture, a, b, da, db, middle, h, 0f)) {
                foot = h + perPixel / 2f;
                break;
            }
        }
        // A rectangle is clear right into its corners. An arch is not.
        float inAlong = OPENING_CORNER * build.frame.scale / pixels, inUp = OPENING_CORNER * build.frame.scale * perPixel;
        for (int corner = 0; corner < 4; corner++) {
            float along = (corner & 1) == 0 ? start + inAlong : end - inAlong;
            float up = corner < 2 ? foot + inUp : head - inUp;
            if (paintedOnWall(texture, a, b, da, db, along, up, 0f)) {
                return known;
            }
        }
        known[at + OPENING_START] = start;
        known[at + OPENING_END] = end;
        known[at + OPENING_FOOT] = foot;
        known[at + OPENING_HEAD] = head;
        return known;
    }

    /**
     * How high a wall stands, in levels: the lowest its sprite's top is drawn at three places
     * along it, so that the top of a fence whose boards are cut to a curve is closed below
     * the lowest of them. 0 for a sprite with nothing painted at any of the three.
     */
    private float wallTop(Texture texture, float a, float b, float da, float db) {
        float perPixel = 1f / (SpriteGeometry.PIXELS_PER_LEVEL * build.frame.scale);
        float lowest = 0f;
        for (float along : TOP_READ_AT) {
            for (float h = 1f; h > LOWEST_TOP; h -= perPixel) {
                if (paintedOnWall(texture, a, b, da, db, along, h, 0f)) {
                    float top = Math.min(h + perPixel / 2f, 1f);
                    lowest = lowest == 0f ? top : Math.min(lowest, top);
                    break;
                }
            }
        }
        return lowest;
    }

    /** Whether the sprite is painted where it shows the point a share along the wall and up levels high, down frame pixels lower. */
    boolean paintedOnWall(Texture texture, float a, float b, float da, float db, float along, float up, float down) {
        float u = SpriteGeometry.frameU(build.frame, a + da * along, b + db * along);
        float v = SpriteGeometry.frameV(build.frame, a + da * along, b + db * along, up) + down;
        return u >= build.frame.left && u < build.frame.left + build.frame.width && v >= build.frame.top && v < build.frame.top + build.frame.height
                && texture.isMaskSet((int) u, (int) v);
    }

    /**
     * How many frame pixels down from the wall's top its sprite has nothing painted: the most
     * found a quarter, a half and three quarters of the way along it. 0 when it is painted
     * from the top, and where nothing is within MOST_UNPAINTED: a low wall stops where it stops.
     */
    private float unpaintedTop(Texture texture, float a, float b, float da, float db) {
        int most = Math.round(MOST_UNPAINTED * build.frame.scale);
        float deepest = 0f;
        for (int place = 1; place <= 3; place++) {
            for (int pixel = 0; pixel <= most; pixel++) {
                if (paintedOnWall(texture, a, b, da, db, place / 4f, 1f, pixel + 0.5f)) {
                    deepest = Math.max(deepest, pixel);
                    break;
                }
            }
        }
        return deepest;
    }

    /** How high the top of a door's sprite is drawn half way along it, in levels. A level for a sprite painted that high. */
    float doorTop(Texture texture, float a, float b, float da, float db) {
        float perPixel = 1f / (SpriteGeometry.PIXELS_PER_LEVEL * build.frame.scale);
        for (float h = 1f; h > LOWEST_DOOR; h -= perPixel) {
            if (paintedOnWall(texture, a, b, da, db, 0.5f, h, 0f)) {
                return Math.min(h + perPixel / 2f, 1f);
            }
        }
        return 1f;
    }

    /**
     * What the far skin of the wall on the north or west edge of the square being added is
     * painted over in, as WallSides packs it, and into farLight where the light of the place
     * beyond it is. 0 where the far skin shows the sprite as the near skin does: a wall within
     * one room, a fence or a wall in the open, a wall whose far side shows the same sprite, a
     * building with no wall that shows its finish, and a sprite the picture has not read yet.
     */
    private int farTint(Texture texture, int x, int y, int z, boolean north) {
        farColour = null;
        int beyondX = north ? x : x - 1, beyondY = north ? y - 1 : y;
        IsoGridSquare beyond = build.cell.getGridSquare(beyondX, beyondY, z);
        long own = build.here.getRoomID(), far = beyond == null ? -1L : beyond.getRoomID();
        farLight = -1;
        farFacing = null;
        if (own == far) {
            return 0;
        }
        // Above the ground a square of open air is often no square at all. On the ground it is map not loaded yet.
        if (beyond == null && build.cell.getGridSquare(beyondX, beyondY, 0) == null) {
            return 0;
        }
        int ownLight = (build.waterLocalX + 1) * WorldScene.LIGHT_SPAN + build.waterLocalY + 1;
        farLight = north ? ownLight - 1 : ownLight - WorldScene.LIGHT_SPAN;
        IsoBuilding building = far == -1L ? build.here.getBuilding() : beyond.getBuilding();
        if (building == null) {
            return 0;
        }
        Texture shows = far == -1L ? WallFinish.outside(build.cell, building, z, own, world.roundNow) : WallFinish.room(build.cell, building, z, far, world.roundNow);
        if (far == -1L) {
            farFacing = WallFinish.facing(build.cell, building, z, north, own, world.roundNow);
        }
        if (shows == null || shows == texture) {
            return 0;
        }
        float[] colour = SpriteColours.of(shows);
        // The picture reads a sprite's colour and how it is laid on the next frame drawn, and tells nobody when it has:
        // a build that went without either is made again when next due.
        build.building.signed &= !SpriteColours.unread(shows) && (far != -1L || !Masonry.unread(shows));
        if (colour == null) {
            return 0;
        }
        farColour = colour;
        // Out of doors the skin is built of what the building's outside walls are: brick or
        // stone where their sprite reads so, and boards lapped along it otherwise.
        int laid = far == -1L ? Masonry.of(shows) : 0;
        return laid != 0 ? WallSides.over(laid) : WallSides.key(colour[0], colour[1], colour[2], far == -1L ? WallSides.SIDING : WallSides.FLAT);
    }

    /**
     * The outside of a wall that is one flat colour has the two lines every storey of a
     * building has: a plinth along its foot and a course under its top, each a shade off the
     * wall and standing a finger proud of it. They are what says, from down the street, where
     * one floor ends and the next begins. The plinth stops either side of a doorway.
     */
    private void addCourses(float[] opening, int at, int x, int y, int z, float a, float b, float da, float db, float outX, float outY) {
        SpriteGeometry.Mesh plain = WorldScene.solid.get(WorldScene.PLAIN);
        plain.open = build.frame.open;
        plain.sway = 0f;
        float doorFrom = -1f, doorTo = -1f;
        if (opening[at + OPENING_START] >= 0f && opening[at + OPENING_FOOT] < Courses.DOORWAY_FOOT) {
            doorFrom = opening[at + OPENING_START];
            doorTo = opening[at + OPENING_END];
        }
        float base = z * ViewCamera.LEVEL_HEIGHT;
        float lift0 = build.geometry.liftAt(a, b), lift1 = build.geometry.liftAt(a + da, b + db);
        Courses.onEdge(plain, x, y, a, b, da, db, base + lift0, base + lift1, outX, outY, doorFrom, doorTo,
                farColour[0] * build.frame.red, farColour[1] * build.frame.green, farColour[2] * build.frame.blue);
    }

    /**
     * How many frame pixels along (da, db) from (a, b), at SEAL_HEIGHT, the sprite has nothing
     * painted. 0 when it is painted from the start, and when nothing is within MOST_UNPAINTED:
     * that is a sprite which is not a wall from edge to edge, and it is left as it is.
     */
    private float unpainted(Texture texture, float a, float b, float da, float db) {
        float pixels = Math.abs(SpriteGeometry.frameU(build.frame, a + da, b + db) - SpriteGeometry.frameU(build.frame, a, b));
        int most = Math.round(MOST_UNPAINTED * build.frame.scale);
        for (int pixel = 0; pixel <= most; pixel++) {
            if (paintedOnWall(texture, a, b, da, db, (pixel + 0.5f) / pixels, SEAL_HEIGHT, 0f)) {
                return pixel;
            }
        }
        return 0f;
    }
}
