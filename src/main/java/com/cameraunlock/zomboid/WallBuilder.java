package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.properties.PropertyContainer;
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
    static final float LIT_GLASS = 65f;
    /** The glass of a window's sprite with a room on both sides, which has no street side: see OpeningBuilder.paneMaterial. */
    static final float INNER_GLASS = 94f;
    /** How far a wall's colour may be from that of its building's walls, summed over red, green and blue, for it to be built as they are. */
    private static final float SAME_FINISH = 0.3f;
    /** How high a wall has to stand short of its level for its top to be closed: a full wall's top is under the floor or the roof over it. */
    private static final float CAPPED_BELOW = 0.97f;
    /** Where along a wall its colour is looked for, for the faces that close it. */
    private static final float[] CAP_COLOUR_AT = {0.5f, 0.12f, 0.88f};
    /** Wallpaper with a figure on it, which the picture prints sprig by sprig. */
    static final float FIGURED_PAPER = 70f;
    /** Half the width of a joint between two tiles of a wall, in tiles: the world program lays them so (TILE_JOINT). */
    static final float TILE_JOINT_HALF = 0.004f;
    /** How many squares along its line a wall with an opening looks for a whole wall of its sheet to read its tiles from. */
    private static final int TILES_REACH = 4;
    /** The walls with an opening the log has said keep their sprite, by texture name, on the north edge and on the west. */
    private static final Set<String> KEPT_NORTH = ConcurrentHashMap.newKeySet(), KEPT_WEST = ConcurrentHashMap.newKeySet();
    /** For each of the two edges a wall can stand on: the frame origin the sprite was read with, then the opening. */
    private static final int OPENING_FLOATS = 9, OPENING_ORIGIN = 0, OPENING_START = 2, OPENING_END = 3, OPENING_FOOT = 4, OPENING_HEAD = 5, OPENING_TOP = 6,
            DOORWAY_START = 7, DOORWAY_END = 8;
    /** Where along a wall its height is read, and the lowest a wall's top is looked for, in levels. */
    private static final float[] TOP_READ_AT = {0.15f, 0.5f, 0.85f};
    private static final float LOWEST_TOP = 0.05f;
    /**
     * Where an open garage door's band is read from down to its foot, the lowest the foot is looked for, where along the
     * band and how high up a piece at an end of the opening paints its jamb, and where up the head the leaf takes its
     * colour, in levels or shares of the edge; and how far under the leaf's underside its stiffeners hang, in tiles.
     */
    private static final float RAISED_SCAN_FROM = 0.97f, LOWEST_RAISED_DOOR = 0.5f, RAISED_JAMB_AT = 0.1f, RAISED_JAMB_UP = 0.3f, RAISED_DYE_UP = 0.93f,
            RAISED_STIFFENERS = 0.022f;
    /** Where garage_leaf_track's track is across the model and where its hanger is along it, and the top of the track over the model's foot, in tiles. */
    private static final float RAISED_TRACK_SIDE = 0.445f, RAISED_HANGER_IN = -2.13f, RAISED_TRACK_TOP = 0.1f;

    private final WorldScene world;
    private final WorldScene.Frame build;

    /** The plain outside wall the far skin of the wall last asked about can show, or null: see farTint. */
    private WallFinish.Facing farFacing;
    /** Where in the chunk's lights the square beyond the wall last asked about is, or -1 when it is the same place as this side. */
    private int farLight;
    private final float[] laidTint = new float[4];
    /** The paper of the wall being added, or null for a wall that shows its sprite, and whether the sprite being added is an inside wall of a house. */
    private Papers.Paper paper;
    /**
     * The tiles of the wall being added, or null; the picture its faces are painted over in their
     * colours, its sprite stood square where it has been, and how the frame takes texels from it;
     * and that picture as it is, which a course with a figure on it shows.
     */
    private WallTiles.Wall tiles;
    private int tileBase, tileFlat;
    private SpriteGeometry.Mesh tileStood;
    private final SpriteGeometry.Frame tileFrame = new SpriteGeometry.Frame();
    /** The colour farTint last gave a far skin, or null where it gave none. */
    private float[] farColour;
    /** The regions either side of the edge being added, the one inside its square first: see addEdge. */
    private long edgeNear, edgeFar;
    /** How far the skin being added runs on past its wall's two ends, in tiles: see WallJoins. */
    private float skinStart, skinEnd;

    /** Whether the sprite being added to an edge is a sign, which may run on over the next squares: see EdgeRuns. */
    boolean sign;
    private final float[] run = new float[2], next = new float[2], upright = new float[4], walked = new float[2], walkedUpright = new float[4];
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
        return carriesOn(x + step * (int) da, y + step * (int) db, z, da, db, step, run);
    }

    /**
     * Whether the square (x, y) holds a sign on the edge (da, db) that carries on from a piece
     * painted from[0] to from[1] along the square before it, that way (1 along the edge, -1
     * back): one piece with it. Leaves next and runFrame with that piece.
     */
    private boolean carriesOn(int x, int y, int z, float da, float db, int way, float[] from) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
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
            if (way > 0 ? EdgeRuns.meets(from[0], from[1], next[0], next[1]) : EdgeRuns.meets(next[0], next[1], from[0], from[1])) {
                return true;
            }
        }
        return false;
    }

    /**
     * How far up a sign is hung to rest on the awning under it (EdgeRuns.onAwning), the same for
     * every piece of a sign that runs on over the next squares: the most any of its pieces
     * needs, so the board stays one straight board clear of the cloth wherever the cloth is under
     * it. Each piece lifted by its own square's awning stepped the board where an awning under
     * part of it ends, and laid the lettering twice at two heights where two pieces overhang
     * each other there. Taken only where every piece has an awning under it, the letters over
     * the awning were behind its cloth again. foot is the piece's own foot, run where it paints.
     */
    private float runLift(int x, int y, int z, float da, float db, float foot) {
        float lift = liftOver(x, y, z, da, foot);
        for (int way = -1; way <= 1; way += 2) {
            System.arraycopy(run, 0, walked, 0, 2);
            for (int step = way; carriesOn(x + step * (int) da, y + step * (int) db, z, da, db, way, walked); step += way) {
                EdgeRuns.upright(runFrame, da, db, walkedUpright);
                lift = Math.max(lift, liftOver(x + step * (int) da, y + step * (int) db, z, da, walkedUpright[WallHung.BOTTOM]));
                System.arraycopy(next, 0, walked, 0, 2);
            }
        }
        return lift;
    }

    /** How far up a piece of a sign whose foot is that far below its floor rests on the awning hung on its wall a level down, 0 for none. */
    private float liftOver(int x, int y, int z, float da, float foot) {
        IsoGridSquare below = build.cell.getGridSquare(x, y, z - 1);
        return below == null ? 0f : EdgeRuns.onAwning(foot, Awnings.meetsWall(below, da != 0f));
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
        boolean onKerbTop = build.standOnKerbTop(z, da != 0f);
        // What stands on an edge is seen from the place either side of it, and is drawn while the
        // eye sees into either: a room's, or the open air's. What is built as a room's contents stays its room's.
        long given = WorldScene.solid.room;
        boolean own = given != RoomSight.OUTSIDE && given != RoomSight.ANYWHERE;
        edgeNear = own ? given : roomAt(x, y, z);
        edgeFar = own ? given : da != 0f ? roomAt(x, y - 1, z) : roomAt(x - 1, y, z);
        long either = RoomSight.pair(edgeNear, edgeFar);
        build.contentsOf(either);
        mesh = inRoom(mesh, either);
        try {
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
        } finally {
            build.contentsOf(given);
            if (onKerbTop) {
                build.leaveKerbTop();
            }
        }
    }

    /**
     * The door of an open garage, which the game paints only as the band of it showing under the head of the opening: the
     * sections lie under the ceiling on the garage's side of the wall, as far in as the door is high, its tracks along the
     * pieces at the ends of the opening, where the sprite paints the jamb. Its colour is the head's. A garage with no room
     * on either side, a carport, has nothing to lie in.
     */
    void addRaisedGarageDoor(Texture texture, int x, int y, int z, float da, float db) {
        boolean north = da != 0f;
        IsoGridSquare beyond = build.cell.getGridSquare(north ? x : x - 1, north ? y - 1 : y, z);
        boolean own = OpeningBuilder.indoors(build.here), far = OpeningBuilder.indoors(beyond);
        if (!own && !far) {
            return;
        }
        float perPixel = 1f / (SpriteGeometry.PIXELS_PER_LEVEL * build.frame.scale), under = RAISED_SCAN_FROM;
        if (!paintedOnWall(texture, 0f, 0f, da, db, 0.5f, under, 0f)) {
            return;
        }
        while (under > LOWEST_RAISED_DOOR && paintedOnWall(texture, 0f, 0f, da, db, 0.5f, under - perPixel, 0f)) {
            under -= perPixel;
        }
        if (under <= LOWEST_RAISED_DOOR) {
            return;
        }
        int turns = raisedTurns(da, db, own);
        String name = raisedLeaf(turns, da, db, paintedOnWall(texture, 0f, 0f, da, db, RAISED_JAMB_AT, RAISED_JAMB_UP, 0f),
                paintedOnWall(texture, 0f, 0f, da, db, 1f - RAISED_JAMB_AT, RAISED_JAMB_UP, 0f));
        float a = 0.5f * da, b = 0.5f * db;
        float[] lift = build.geometry.lift;
        float foot = z * ViewCamera.LEVEL_HEIGHT + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f + under * ViewCamera.LEVEL_HEIGHT - RAISED_STIFFENERS;
        build.building.plain = false;
        // Across the edge the leaf is in the place beyond, and is lit and seen as that room, as the wall's far skin is.
        int farAt = (build.waterLocalX + 1) * WorldScene.LIGHT_SPAN + build.waterLocalY + 1 - (north ? 1 : WorldScene.LIGHT_SPAN);
        float red = build.frame.red, green = build.frame.green, blue = build.frame.blue, open = build.frame.open;
        if (!own) {
            build.frame.cornersInto(build.heldRed, build.heldGreen, build.heldBlue, build.heldUnlit);
            if (build.lightOpen[farAt] < 0) {
                build.frame.light(1f, 1f, 1f);
                build.frame.open = WorldScene.OPEN_UNLIT;
            } else {
                lightAlong(farAt, north);
            }
        }
        long given = WorldScene.solid.room;
        build.contentsOf((own ? build.here : beyond).getRoomID());
        build.placed.add(Models.get(name), build.frame, x, y, x + a, y + b, foot, turns, 1f, 1f, texture.getID(), 1 << Models.PAINT,
                SpriteGeometry.textureU(build.frame, a, b), SpriteGeometry.textureV(build.frame, a, b, RAISED_DYE_UP), 0, 0f, 0f);
        if (!name.equals("garage_leaf")) {
            float[] at = new float[2];
            Models.turn(turns, name.equals("garage_leaf_track") ? RAISED_TRACK_SIDE : -RAISED_TRACK_SIDE, RAISED_HANGER_IN, at);
            float hung = foot + RAISED_TRACK_TOP, ceiling = (z + 1) * ViewCamera.LEVEL_HEIGHT + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f;
            build.placed.add(Models.get("garage_hanger"), build.frame, x, y, x + a + at[0], y + b + at[1], hung, turns, 1f, ceiling - hung, 0, 0, 0f, 0f, 0, 0f, 0f);
        }
        build.contentsOf(given);
        if (!own) {
            build.frame.red = red;
            build.frame.green = green;
            build.frame.blue = blue;
            build.frame.open = open;
            build.frame.cornersFrom(build.heldRed, build.heldGreen, build.heldBlue, build.heldUnlit);
        }
    }

    /**
     * The quarter turns that run a garage leaf's depth (the model's -y) into the garage: towards the square whose edge the
     * piece stands on when that square is the room, else across the edge, along (db, da).
     */
    static int raisedTurns(float da, float db, boolean own) {
        float intoA = (own ? 1f : -1f) * db, intoB = (own ? 1f : -1f) * da;
        float[] way = new float[2];
        for (int turns = 0; turns < 4; turns++) {
            Models.turn(turns, 0f, -1f, way);
            if (Math.abs(way[0] - intoA) < 0.5f && Math.abs(way[1] - intoB) < 0.5f) {
                return turns;
            }
        }
        throw new IllegalStateException("no quarter turn runs a garage leaf along " + intoA + ", " + intoB);
    }

    /**
     * The leaf a piece of an open garage door takes: with its track on the side where the piece paints the jamb when it is
     * at one end of the opening, else the plain leaf of a piece in the middle (or of a door one piece wide).
     */
    static String raisedLeaf(int turns, float da, float db, boolean jambAtStart, boolean jambAtEnd) {
        if (jambAtStart == jambAtEnd) {
            return "garage_leaf";
        }
        float[] side = new float[2];
        Models.turn(turns, 1f, 0f, side);
        float towards = (side[0] * da + side[1] * db) * (jambAtEnd ? 1f : -1f);
        return towards > 0f ? "garage_leaf_track" : "garage_leaf_track_left";
    }

    /**
     * Adds a stain the game paints over the wall along the square's edge from (0, 0) along
     * (da, db), a level high, on the wall's skin inside the square, which is the side the
     * game's view shows it on, the given number of layers clear of the skin.
     */
    void addStain(SpriteGeometry.Mesh mesh, int x, int y, int z, float layer, float da, float db) {
        mesh = inRoom(mesh, mesh.room != RoomSight.OUTSIDE && mesh.room != RoomSight.ANYWHERE ? mesh.room : roomAt(x, y, z));
        boolean onKerbTop = build.standOnKerbTop(z, da != 0f);
        try {
            float way = db != 0f ? SpriteGeometry.LAYERS_X : SpriteGeometry.LAYERS_Y;
            WorldScene.layers.lie(way, layer);
            build.geometry.shownA = db * WorldScene.WALL_HALF;
            build.geometry.shownB = da * WorldScene.WALL_HALF;
            build.geometry.addFace(mesh, build.frame, x, y, z, 0f, 0f, 0f, da, db, 0f, 0f, 0f, 1f);
            build.geometry.shownA = build.geometry.shownB = 0f;
            WorldScene.layers.none();
        } finally {
            if (onKerbTop) {
                build.leaveKerbTop();
            }
        }
    }

    private void standEdge(SpriteGeometry.Mesh mesh, Texture texture, boolean wall, boolean door, int x, int y, int z,
            float way, float layer, float da, float db) {
        float inX = db * WorldScene.WALL_HALF, inY = da * WorldScene.WALL_HALF;
        if ((wall || build.linkSheet) && build.seeThrough) {
            // A fence of wire or of rails is one sheet on the line between its squares. As two
            // skins a wall's thickness apart, the eye saw one lattice through the other and
            // the two beat against each other as it moved.
            // On an edge that a kerb runs along it stands on the kerb's top (Frame.standOnKerbTop).
            if (build.chainLink != null) {
                addChainLink(x, y, z, da, db);
            } else {
                build.geometry.addFace(mesh, build.frame, x, y, z, 0f, 0f, 0f, da, db, 0f, 0f, 0f, 1f);
            }
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
            if (!door && !sign && material != WorldScene.GLASS && addTiledOverlay(mesh, texture, build.tiledSheet, x, y, z, da, db)) {
                build.geometry.shownA = build.geometry.shownB = build.geometry.shownUp = 0f;
                build.geometry.turned = false;
                build.frame.material = material;
                return;
            }
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
                if (z > 0 && Own.furniture()) {
                    build.geometry.shownUp = runLift(x, y, z, da, db, foot);
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
        if (build.overLayer == 0 && build.doorWall) {
            build.openingBuilder.doorwayIn(x, y, z, da != 0f, opening[at + DOORWAY_START], opening[at + DOORWAY_END]);
        }
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
        // Each skin is seen from the place it faces, and is drawn only while the eye sees into that place.
        long nearRoom = edgeNear, farRoom = edgeFar;
        build.contentsOf(nearRoom);
        skinStart = (startJoin & WallJoins.NEAR) != 0 ? WorldScene.WALL_HALF : 0f;
        skinEnd = (endJoin & WallJoins.NEAR) != 0 ? WorldScene.WALL_HALF : 0f;
        // An inside wall is its paper's stripes, each a face of its own: see Papers.
        boolean papered = build.overLayer == 0 && build.paperedWall && Own.textures(build.playerIndex);
        paper = papered ? Papers.of(texture, build.frame, da != 0f) : null;
        // Only a paper that was asked for is ever read, so only then is the build made again once it is.
        if (paper == null && papered && Papers.unread(texture)) {
            build.building.signed = false;
        }
        // A tiled wall is its tiles, its joints and the strips between its bands, each a face of its own: see WallTiles.
        tiles = paper == null && build.overLayer == 0 && build.tiledSheet != null && Own.textures(build.playerIndex)
                ? tilesOf(texture, x, y, z, da, db, opening[at + OPENING_START] >= 0f || build.wallOpens) : null;
        SpriteGeometry.Mesh near = laid(mesh, texture, build.laidWall, x, y, z, da != 0f);
        if (paper != null) {
            int key = WallSides.key(paper.red, paper.green, paper.blue, WallSides.FLAT);
            WallSides.tint(key, build.paperTint);
            near = WorldScene.solid.get(texture.getID(), key);
        }
        if (tiles != null) {
            // Stood square the sprite's far end, which it stops a few dots short of, is filled in: on the sprite itself the last tile had a slit cut in it there.
            int stood = build.flatOf(texture, da != 0f ? Flats.NORTH : Flats.WEST, 0f, 0f, 0f);
            tileBase = stood != 0 ? stood : texture.getID();
            tileFlat = stood == 0 ? 0 : da != 0f ? SpriteGeometry.FLAT_NORTH : SpriteGeometry.FLAT_WEST;
            tileStood = WorldScene.solid.get(tileBase);
            near = tileStood;
        }
        // A wall that shows its own sprite shows it stood square, so its courses run on into the next length: see Flats.
        near = inRoom(stoodSquare(near, texture, da != 0f), nearRoom);
        int nearFlat = build.frame.flat;
        // Tiles the map lays over the wall, hung on it, are tiles on its skin inside the square, and the rest of what is laid over a wall is as painted.
        if (build.overLayer == 0 || !addTiledOverlay(mesh, texture, build.overSheet, x, y, z, da, db)) {
            addSkin(near, opening, at, x, y, z, 0f, 0f, da, db, fromStart, fromEnd, fromTop, startIsLow);
        }
        build.frame.flat = 0;
        build.contentsOf(farRoom);
        skinStart = (startJoin & WallJoins.FAR) != 0 ? WorldScene.WALL_HALF : 0f;
        skinEnd = (endJoin & WallJoins.FAR) != 0 ? WorldScene.WALL_HALF : 0f;
        build.geometry.shownA = -inX;
        build.geometry.shownB = -inY;
        // The sprite is painted for the side inside the square. Where the far side is another
        // place, the skin on it is that side's surface. Only the wall itself: ivy or a
        // stain over it is on the side it was painted on, and is no part of the wall's colour.
        float material = build.frame.material;
        int tint = build.overLayer == 0 ? farTint(texture, x, y, z, da != 0f) : 0;
        // A garage door is the same door on its far side, lit as that side is, not the building's outside wall.
        if (build.garageDoor) {
            tint = 0;
        }
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
            build.frame.cornersInto(build.heldRed, build.heldGreen, build.heldBlue, build.heldUnlit);
            if (build.lightOpen[farLight] < 0) {
                build.frame.light(1f, 1f, 1f);
                build.frame.open = WorldScene.OPEN_UNLIT;
            } else {
                lightAlong(farLight, da != 0f);
            }
        }
        // The far skin is papered as the near one only where it shows the same sprite.
        paper = faced == null && tint == 0 ? paper : null;
        tiles = faced == null && tint == 0 ? tiles : null;
        SpriteGeometry.Mesh far = near;
        if (faced != null) {
            far = stoodSquare(laid(WorldScene.solid.get(faced.texture.getID()), faced.texture, Masonry.suits(faced.sprite.getName()), x, y, z, da != 0f), faced.texture, da != 0f);
        } else if (tint != 0) {
            far = WorldScene.solid.get(texture.getID(), tint);
        } else {
            build.frame.flat = nearFlat;
        }
        addSkin(inRoom(far, farRoom), opening, at, x, y, z, 0f, 0f, da, db, fromStart, fromEnd, fromTop, startIsLow);
        build.frame.flat = 0;
        paper = null;
        tiles = null;
        tileStood = null;
        // Out of doors, on a whole wall in one colour.
        if (tint != 0 && farColour != null && WorldScene.outdoors(build.frame.open) && opening[at + OPENING_TOP] >= WHOLE_WALL) {
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
            build.frame.cornersFrom(build.heldRed, build.heldGreen, build.heldBlue, build.heldUnlit);
        }
        build.contentsOf(RoomSight.pair(nearRoom, farRoom));
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
     * Adds a square's length of chain link fencing on the edge from (0, 0) along (da, db): the
     * fabric up to the rail, the rail along its top, the post at the north-west corner where
     * the sprite has one and no lone post stands there, and a post at an end no other chain
     * link meets. Each follows the land at the ends of the edge as the sheet did.
     */
    private void addChainLink(int x, int y, int z, float da, float db) {
        ChainLink.Run run = build.chainLink;
        boolean north = da != 0f;
        SpriteGeometry.Mesh steel = WorldScene.solid.get(WorldScene.PLAIN, WallSides.key(ChainLink.RED, ChainLink.GREEN, ChainLink.BLUE, WallSides.FLAT));
        float material = build.frame.material;
        build.frame.material = ChainLink.FABRIC;
        build.geometry.addFace(steel, build.frame, x, y, z, 0f, 0f, 0f, da, db, 0f, 0f, 0f, run.rail());
        build.frame.material = material;
        steel.open = build.frame.open;
        steel.sway = 0f;
        float level = ViewCamera.LEVEL_HEIGHT, base = (z + build.geometry.raised) * level;
        float startUp = base + build.geometry.liftAt(0f, 0f), endUp = base + build.geometry.liftAt(da, db);
        // A rail runs on a rail's thickness past an end where the fence turns a corner or ends,
        // so two that meet at a corner leave no notch on its outside. Where the next length
        // runs straight on, the two rails butt, as one tube would run on.
        float slope = endUp - startUp;
        float before = linkOn(x - (int) da, y - (int) db, z, north, x, y, north) ? 0f : ChainLink.RAIL_RADIUS;
        float after = linkOn(x + (int) da, y + (int) db, z, north, x, y, north) ? 0f : ChainLink.RAIL_RADIUS;
        WorldScene.set(linkFrom, 0, x - da * before, y - db * before, startUp + run.rail() * level - slope * before);
        WorldScene.set(linkTo, 0, x + da * (1f + after), y + db * (1f + after), endUp + run.rail() * level + slope * after);
        SpriteGeometry.addTube(steel, linkFrom, linkTo, ChainLink.RAIL_RADIUS, ChainLink.RAIL_SIDES, build.frame.red, build.frame.green, build.frame.blue);
        if (build.linkPostDue ? !lonePostOn(x, y, z) : !run.hasPost() && !postMeets(x, y, z, x, y, north)) {
            addLinkPost(steel, x, y, startUp, run.post() * level);
        }
        build.linkPostDue = false;
        if (!postMeets(x + (int) da, y + (int) db, z, x, y, north)) {
            addLinkPost(steel, x + da, y + db, endUp, run.post() * level);
        }
    }

    private void addLinkPost(SpriteGeometry.Mesh steel, float x, float y, float foot, float high) {
        SpriteGeometry.addRound(steel, x, y, foot, foot + high, ChainLink.POST_RADIUS, ChainLink.POST_RADIUS, ChainLink.POST_SIDES,
                0f, 0f, build.frame.red, build.frame.green, build.frame.blue);
    }

    /**
     * Whether the corner (px, py) of the map has a post of chain link other than the length on
     * the edge of square (x, y) being added would stand there: a whole length on the north or
     * the west edge of the square there or of the one before it along that edge, which stands
     * its own post at an end no other meets, a gate of chain link, whose sprite paints its
     * post, or a lone post. A torn length's sprite paints a post where it has one, and that
     * is not always at the corner, so the whole length's post stands there.
     */
    private boolean postMeets(int px, int py, int z, int x, int y, boolean north) {
        return lonePostOn(px, py, z) || wholeOn(px, py, z, true, x, y, north) || wholeOn(px - 1, py, z, true, x, y, north)
                || wholeOn(px, py, z, false, x, y, north) || wholeOn(px, py - 1, z, false, x, y, north);
    }

    /** As linkOn, for a whole length or a gate alone. */
    private boolean wholeOn(int sx, int sy, int z, boolean onNorth, int x, int y, boolean north) {
        return linkOn(sx, sy, z, onNorth, x, y, north, false);
    }

    private boolean linkOn(int sx, int sy, int z, boolean onNorth, int x, int y, boolean north) {
        return linkOn(sx, sy, z, onNorth, x, y, north, true);
    }

    /**
     * Whether square (sx, sy) has chain link on its north or west edge other than the length
     * being added: a whole length, a gate hung with wire, and where torn, a torn or burnt one.
     */
    private boolean linkOn(int sx, int sy, int z, boolean onNorth, int x, int y, boolean north, boolean torn) {
        if (sx == x && sy == y && onNorth == north) {
            return false;
        }
        PZArrayList<IsoObject> objects = objectsOn(sx, sy, z);
        for (int i = 0; objects != null && i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite == null || sprite.getName() == null) {
                continue;
            }
            String name = sprite.getName();
            PropertyContainer properties = sprite.getProperties();
            ChainLink.Run run = ChainLink.of(name);
            boolean onEdge = onNorth ? Edges.north(properties, sprite.getType()) : Edges.west(properties, sprite.getType());
            if (run != null && (onNorth ? run.north() : run.west()) || torn && ChainLink.broken(name, properties.get("Material"), onEdge)
                    || onEdge && ChainLink.gate(name, properties.get("Material2"))) {
                return true;
            }
        }
        return false;
    }

    /** Whether square (sx, sy) holds a lone post of chain link, which stands on its north-west corner: see Outdoors. */
    private boolean lonePostOn(int sx, int sy, int z) {
        PZArrayList<IsoObject> objects = objectsOn(sx, sy, z);
        for (int i = 0; objects != null && i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && ChainLink.lonePost(sprite.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * What stands on a square, or null where there is no square. Above the ground the map has
     * no square where there is only air, so a missing one is one not loaded only where the
     * ground under it is missing too: the build is then made again once the map has loaded it.
     */
    private PZArrayList<IsoObject> objectsOn(int sx, int sy, int z) {
        IsoGridSquare square = build.cell.getGridSquare(sx, sy, z);
        if (square == null) {
            if (z == 0 || build.cell.getGridSquare(sx, sy, 0) == null) {
                build.wantsMap = true;
            }
            return null;
        }
        return square.getObjects();
    }

    private final float[] linkFrom = new float[3], linkTo = new float[3];

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
        build.frame.open = build.openOf(at);
        // As WorldScene.Frame.cornerLights: a square the game has given no light counts as 1, and the corner keeps how much of its light such squares are.
        byte kind = build.unlitKindRound(at);
        build.frame.unlitKind = kind;
        boolean ownGiven = build.lightUnseen[at] == WorldScene.OWN_LIGHT;
        float ownGrey = (build.lightRed[at] + build.lightGreen[at] + build.lightBlue[at]) / 3f;
        for (int end = 0; end < 2; end++) {
            int beside = at + (end == 0 ? -step : step);
            // The lights are read a square past the chunk and no further: a far side that is in that last row has nothing beyond one end.
            boolean alike = beside >= 0 && beside < build.lightOpen.length && build.lightOpen[beside] == build.lightOpen[at]
                    && (build.lightUnseen[beside] == WorldScene.OWN_LIGHT || build.lightUnseen[beside] == kind);
            float red = alike ? (build.lightRed[at] + build.lightRed[beside]) / 2f : build.lightRed[at];
            float green = alike ? (build.lightGreen[at] + build.lightGreen[beside]) / 2f : build.lightGreen[at];
            float blue = alike ? (build.lightBlue[at] + build.lightBlue[beside]) / 2f : build.lightBlue[at];
            boolean besideGiven = !alike || build.lightUnseen[beside] == WorldScene.OWN_LIGHT;
            float given = (ownGiven ? ownGrey : 0f)
                    + (alike && besideGiven ? (build.lightRed[beside] + build.lightGreen[beside] + build.lightBlue[beside]) / 3f : 0f);
            int unlitCount = (ownGiven ? 0 : 1) + (alike && !besideGiven ? 1 : 0);
            float unlit = WorldScene.Frame.unlitShare(given, unlitCount);
            float day = unlitCount == 0 ? 0f : ((ownGiven ? 0f : build.lightDay[at]) + (alike && !besideGiven ? build.lightDay[beside] : 0f)) / unlitCount;
            // The square's corners go north-west, north-east, south-east, south-west: a wall on its
            // north edge ends at the first two, one on its west edge at the first and the last.
            int near = end == 0 ? 0 : north ? 1 : 3, far = end == 0 ? (north ? 3 : 1) : 2;
            build.frame.cornerRed[near] = build.frame.cornerRed[far] = red;
            build.frame.cornerGreen[near] = build.frame.cornerGreen[far] = green;
            build.frame.cornerBlue[near] = build.frame.cornerBlue[far] = blue;
            build.frame.cornerUnlit[near] = build.frame.cornerUnlit[far] = unlit;
            build.frame.cornerDay[near] = build.frame.cornerDay[far] = day;
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
     * sheet of outside walls, x, y, z the square the wall is on, and north whether it is on the
     * square's north edge.
     */
    SpriteGeometry.Mesh laid(SpriteGeometry.Mesh mesh, Texture texture, boolean suits, int x, int y, int z, boolean north) {
        if (build.overLayer != 0 || !Own.textures(build.playerIndex)) {
            return mesh;
        }
        // Upright boards are made board by board in the sprite's own colour: see timber. A wooden
        // wall on a building's own sheet (location_, WorldScene.materialOf) can be boards lapped
        // along it instead, a trailer's, and its sprite says which.
        if (build.frame.material == WorldScene.BOARDS) {
            if (texture.getName() != null && texture.getName().startsWith("location_")) {
                int read = Masonry.of(texture);
                build.building.signed &= !Masonry.unread(texture);
                if ((read & 15) == WallSides.LAPPED) {
                    return WorldScene.solid.get(texture.getID(), read);
                }
            }
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
        // A roof's end is of the wall it stands on: the nearest room under it, on either side of
        // its edge, not the ground floor, which may be a wing of another finish (a stone garage
        // under a sided gable).
        for (int level = z - 1; level >= 0 && build.gableSheet && finish == null; level--) {
            for (int side = 0; side < 2 && finish == null; side++) {
                IsoGridSquare under = build.cell.getGridSquare(x - (side == 1 && !north ? 1 : 0), y - (side == 1 && north ? 1 : 0), level);
                IsoBuilding standing = under == null ? null : under.getBuilding();
                if (standing != null && under.getRoomID() != -1L) {
                    finish = WallFinish.outside(build.cell, standing, level, under.getRoomID(), world.roundNow);
                }
            }
        }
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
     * places out of doors faces into its own square, and one between two rooms is glass with
     * no street side.
     */
    private void faceIntoRoom(int x, int y, int z, boolean north) {
        IsoGridSquare beyond = build.cell.getGridSquare(north ? x : x - 1, north ? y - 1 : y, z);
        boolean ownIsRoom = OpeningBuilder.indoors(build.here) || !OpeningBuilder.indoors(beyond);
        // A face on a north edge looks north, out of its square, and one on a west edge east, into it.
        build.geometry.turned = north == ownIsRoom;
        if (ownIsRoom && OpeningBuilder.indoors(beyond)) {
            build.frame.material = INNER_GLASS;
        } else if (WorldScene.lamplit(build.cell, ownIsRoom ? build.here : beyond, build.playerIndex, build.asTheGameShows || build.gamesView, build.lampsNear)) {
            build.frame.material = LIT_GLASS;
        }
    }

    /** The room a square is in, or RoomSight.OUTSIDE for the open air or no square. */
    private long roomAt(int x, int y, int z) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        return square == null ? RoomSight.OUTSIDE : square.getRoomID();
    }

    /** The mesh of the same set, texture and tint as the one given, for the contents of a room. */
    private static SpriteGeometry.Mesh inRoom(SpriteGeometry.Mesh mesh, long room) {
        if (mesh.room == room || mesh.set == null) {
            return mesh;
        }
        long was = mesh.set.room;
        mesh.set.room = room;
        SpriteGeometry.Mesh in = mesh.set.get(mesh.texture, mesh.tint);
        mesh.set.room = was;
        in.carryAs(mesh);
        return in;
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
        if (tiles != null) {
            addTiled(x, y, z, a, b, da, db, from, to, foot, head, sealFrom, sealTo, startIsLow);
            return;
        }
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
        build.frame.cornersInto(build.paperRed, build.paperGreen, build.paperBlue, build.paperUnlit);
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
        build.frame.cornersFrom(build.paperRed, build.paperGreen, build.paperBlue, build.paperUnlit);
    }

    /**
     * The part of a tiled wall from one share of its length to another and from one height to
     * another: each band of it, paint or a strip of trim in one face, tiles as one face for each
     * run of evenly laid courses with the joints laid on it by the picture (WallSides.TILED,
     * WallTiles.stretches). A course with a figure on it shows the sprite's dots tile by tile,
     * with its joints between them.
     */
    private void addTiled(int x, int y, int z, float a, float b, float da, float db,
            float from, float to, float foot, float head, float sealFrom, float sealTo, boolean startIsLow) {
        float made = build.frame.material;
        build.frame.flat = tileFlat;
        float halfUp = TILE_JOINT_HALF / ViewCamera.LEVEL_HEIGHT;
        for (WallTiles.Band band : tiles.bands) {
            float low = Math.max(foot, band.foot), high = Math.min(head, band.head);
            if (high <= low) {
                continue;
            }
            if (!band.tiled) {
                build.frame.material = made;
                SpriteGeometry.Mesh on = band.figured[0] ? tileStood : painted(band.red, band.green, band.blue);
                tiledFace(on, x, y, z, a, b, da, db, from, to, low, high, from, to, sealFrom, sealTo, startIsLow);
                continue;
            }
            SpriteGeometry.Mesh laid = WorldScene.solid.get(WorldScene.PLAIN,
                    WallSides.laid(band.red, band.green, band.blue, band.jointRed, band.jointGreen, band.jointBlue, WallSides.TILED));
            SpriteGeometry.Mesh joints = painted(band.jointRed, band.jointGreen, band.jointBlue);
            for (WallTiles.Stretch stretch : WallTiles.stretches(band, halfUp)) {
                float stretchLow = Math.max(low, stretch.low), stretchHigh = Math.min(high, stretch.high);
                if (stretchHigh <= stretchLow) {
                    continue;
                }
                if (!stretch.figured) {
                    build.frame.material = WorldScene.TILE;
                    SpriteGeometry geometry = build.geometry;
                    geometry.alongA = da;
                    geometry.alongB = db;
                    geometry.jointPhase = band.phase;
                    geometry.jointStep = band.step;
                    geometry.footH = stretch.low;
                    geometry.footV = stretch.footV;
                    geometry.perLevel = stretch.perLevel;
                    tiledFace(laid, x, y, z, a, b, da, db, from, to, stretchLow, stretchHigh, from, to, sealFrom, sealTo, startIsLow);
                    geometry.jointStep = Float.NaN;
                    continue;
                }
                // A course with a figure: each tile the sprite's dots, and the joints between them, and the course line under it
                // where the course under it has a figure too.
                build.frame.material = WorldScene.TILE;
                for (int tile = 0; tile <= band.joints.length; tile++) {
                    float start = tile == 0 ? from : Math.max(from, band.joints[tile - 1] + TILE_JOINT_HALF);
                    float end = tile == band.joints.length ? to : Math.min(to, band.joints[tile] - TILE_JOINT_HALF);
                    tiledFace(tileStood, x, y, z, a, b, da, db, start, end, stretchLow, stretchHigh, from, to, sealFrom, sealTo, startIsLow);
                }
                build.frame.material = made;
                for (float joint : band.joints) {
                    tiledFace(joints, x, y, z, a, b, da, db, Math.max(from, joint - TILE_JOINT_HALF), Math.min(to, joint + TILE_JOINT_HALF), stretchLow, stretchHigh,
                            from, to, sealFrom, sealTo, startIsLow);
                }
                if (stretch.first > 0 && band.figured[stretch.first - 1]) {
                    float line = band.courses[stretch.first - 1];
                    tiledFace(joints, x, y, z, a, b, da, db, from, to, Math.max(low, line - halfUp), Math.min(high, line + halfUp), from, to, sealFrom, sealTo, startIsLow);
                }
            }
        }
        build.frame.flat = 0;
        build.frame.material = made;
    }

    /**
     * Tiles laid over the wall on the north edge of the square (da 1) or the west edge, a
     * splashback or a panel, built where the overlay paints them: see WallTiles. Each course is
     * laid as runs of the tiles side by side in it that are as high as each other, each run one
     * face with its joints laid by the picture; where no tile is beside a tile, at the overlay's
     * outline, the face stops at the tile's edge and draws no joint there. A course with a figure
     * shows the overlay's dots tile by tile. False, adding nothing, for an overlay of no sheet of
     * tiles (sheet null), one not read yet or one that reads as no tiles, which is then drawn as painted.
     */
    private boolean addTiledOverlay(SpriteGeometry.Mesh mesh, Texture texture, String sheet, int x, int y, int z, float da, float db) {
        if (sheet == null || !Own.textures(build.playerIndex)) {
            return false;
        }
        boolean north = da != 0f;
        WallTiles.Wall over = WallTiles.of(texture, build.frame, north, true);
        if (over == null) {
            build.building.signed &= !WallTiles.unread(texture, north, true);
            return false;
        }
        // Its own dots are shown stood square: the sprite stops a few dots short of the wall's end, and there a tile of a figure would show a slit.
        int stood = build.flatOf(texture, north ? Flats.NORTH : Flats.WEST, 0f, 0f, 0f);
        SpriteGeometry.Mesh picture = stood != 0 ? WorldScene.solid.get(stood) : mesh;
        int pictureFlat = stood == 0 ? 0 : north ? SpriteGeometry.FLAT_NORTH : SpriteGeometry.FLAT_WEST;
        float made = build.frame.material;
        for (WallTiles.Face face : WallTiles.overlayFaces(over, TILE_JOINT_HALF, TILE_JOINT_HALF / ViewCamera.LEVEL_HEIGHT)) {
            WallTiles.Band band = face.band;
            if (face.kind == WallTiles.Face.PICTURE) {
                build.frame.material = band.tiled ? WorldScene.TILE : made;
                build.frame.flat = pictureFlat;
                tiledFace(picture, x, y, z, 0f, 0f, da, db, face.start, face.stop, face.low, face.high, 0f, 1f, 0f, 0f, true);
                build.frame.flat = 0;
            } else if (face.kind == WallTiles.Face.JOINTED) {
                build.frame.material = WorldScene.TILE;
                SpriteGeometry geometry = build.geometry;
                geometry.alongA = da;
                geometry.alongB = db;
                geometry.jointPhase = band.phase;
                geometry.jointStep = band.step;
                geometry.footH = face.foot;
                geometry.footV = face.footV;
                geometry.perLevel = face.perLevel;
                tiledFace(WorldScene.solid.get(WorldScene.PLAIN, WallSides.laid(band.red, band.green, band.blue, band.jointRed, band.jointGreen, band.jointBlue, WallSides.TILED)),
                        x, y, z, 0f, 0f, da, db, face.start, face.stop, face.low, face.high, 0f, 1f, 0f, 0f, true);
                geometry.jointStep = Float.NaN;
            } else {
                build.frame.material = made;
                tiledFace(WorldScene.solid.get(texture.getID(), WallSides.laid(band.red, band.green, band.blue, 0f, 0f, 0f, WallSides.FINE)),
                        x, y, z, 0f, 0f, da, db, face.start, face.stop, face.low, face.high, 0f, 1f, 0f, 0f, true);
            }
        }
        build.frame.material = made;
        return true;
    }

    /** The mesh a face of a tiled wall goes in to be the colour (r, g, b), lit as the place is: see WallSides.FINE. */
    private SpriteGeometry.Mesh painted(float r, float g, float b) {
        return WorldScene.solid.get(tileBase, WallSides.laid(r, g, b, 0f, 0f, 0f, WallSides.FINE));
    }

    /** One face of a tiled wall, from start to end along it and from low to high, sealed as the piece from from to to is at its ends. Nothing where it has no size. */
    private void tiledFace(SpriteGeometry.Mesh on, int x, int y, int z, float a, float b, float da, float db, float start, float end, float low, float high,
            float from, float to, float sealFrom, float sealTo, boolean startIsLow) {
        if (end <= start || high <= low) {
            return;
        }
        float sealStart = start == from ? sealFrom : 0f, sealEnd = end == to ? sealTo : 0f;
        build.geometry.sealLow = startIsLow ? sealStart : sealEnd;
        build.geometry.sealHigh = startIsLow ? sealEnd : sealStart;
        build.geometry.addFace(on, build.frame, x, y, z, a + da * start, b + db * start, low, da * (end - start), db * (end - start), 0f, 0f, 0f, high - low);
    }

    /**
     * The tiles of the wall sprite on the north edge of its square (da 1) or its west edge.
     * A wall with a door or a window in it (opens), or one whose own reading says it has one,
     * takes those of the nearest whole wall of its sheet along the same line, either way: round
     * an opening the sprite says too little about its courses. The search keeps to the wall's
     * room, stops at the first square that has no wall of the sheet on that edge, and takes a
     * whole wall only where what the wall reads of itself, if anything, agrees with it. Null for
     * a wall with none to take, which keeps its sprite, and while a sprite it needs is unread,
     * when the build is not signed and is made again.
     */
    WallTiles.Wall tilesOf(Texture texture, int x, int y, int z, float da, float db, boolean opens) {
        boolean north = da != 0f;
        WallTiles.Wall own = WallTiles.of(texture, build.frame, north, false);
        if (own == null && WallTiles.unread(texture, north, false)) {
            build.building.signed = false;
            return null;
        }
        if (!opens && (own == null || own.whole)) {
            return own;
        }
        IsoGridSquare here = build.cell.getGridSquare(x, y, z);
        long room = here == null ? -1L : here.getRoomID();
        IsoFlagType along = north ? IsoFlagType.WallN : IsoFlagType.WallW;
        for (int way = -1; way <= 1; way += 2) {
            for (int step = 1; step <= TILES_REACH; step++) {
                // A square not loaded is on a chunk round this one, and the build is made again when that chunk loads.
                IsoGridSquare square = build.cell.getGridSquare(x + way * step * (int) da, y + way * step * (int) db, z);
                if (square == null || square.getRoomID() != room) {
                    break;
                }
                IsoObject wall = wallOfSheet(square, along);
                if (wall == null) {
                    break;
                }
                Texture other = wall.getSprite().getTextureForCurrentFrame(wall.getDir());
                if (!WorldScene.usable(other)) {
                    break;
                }
                tileFrame.scale = build.frame.scale;
                WorldScene.frameOf(tileFrame, wall, other);
                WallTiles.Wall whole = WallTiles.of(other, tileFrame, north, false);
                if (whole == null && WallTiles.unread(other, north, false)) {
                    build.building.signed = false;
                    return null;
                }
                if (whole != null && whole.whole && WallTiles.agrees(own, whole)) {
                    return whole;
                }
            }
        }
        if ((north ? KEPT_NORTH : KEPT_WEST).add(texture.getName())) {
            if (DevBuild.ON) {
                Log.info("wall tiles: " + texture.getName() + (north ? " north" : " west") + " at " + x + "," + y + "," + z
                        + " has an opening, and no whole wall of its sheet along it in its room agrees with it: it keeps its sprite");
            }
        }
        return null;
    }

    /** The wall of the sheet being added that stands on the north edge of the square (along WallN) or the west edge, or null. */
    private IsoObject wallOfSheet(IsoGridSquare square, IsoFlagType along) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && sprite.getName() != null && sprite.getName().startsWith(build.tiledSheet)
                    && (sprite.getProperties().has(along) || sprite.getProperties().has(IsoFlagType.WallNW))) {
                return objects.get(i);
            }
        }
        return null;
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
     * than one. At DOORWAY_START and DOORWAY_END, the one clear run across the sprite even where
     * it reaches the wall's end, as half of a doorway two squares wide does, or a start below 0.
     * Read once for each sprite on each edge.
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
        known[at + DOORWAY_START] = -1f;
        known[at + OPENING_TOP] = wallTop(texture, a, b, da, db);
        int pixels = Math.round(Math.abs(SpriteGeometry.frameU(build.frame, a + da, b + db) - SpriteGeometry.frameU(build.frame, a, b)));
        int first = -1, last = -1, gaps = 0, run = -1, runs = 0, runFirst = -1, runLast = -1;
        for (int pixel = 0; pixel <= pixels; pixel++) {
            boolean clear = pixel < pixels && !paintedOnWall(texture, a, b, da, db, (pixel + 0.5f) / pixels, OPENING_ROW, 0f);
            if (clear && run < 0) {
                run = pixel;
            } else if (!clear && run >= 0) {
                if (pixel - run >= NARROWEST_OPENING * pixels) {
                    runs++;
                    runFirst = run;
                    runLast = pixel;
                }
                // A gap that reaches the wall's end is the sprite stopping short there, not an opening.
                if (run > 0 && pixel < pixels && pixel - run >= NARROWEST_OPENING * pixels) {
                    gaps++;
                    first = run;
                    last = pixel;
                }
                run = -1;
            }
        }
        if (runs == 1) {
            known[at + DOORWAY_START] = (float) runFirst / pixels;
            known[at + DOORWAY_END] = (float) runLast / pixels;
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
        // Above the ground a square of open air is often no square at all. On the ground it is map not loaded yet.
        if (beyond == null && build.cell.getGridSquare(beyondX, beyondY, 0) == null) {
            return 0;
        }
        int ownLight = (build.waterLocalX + 1) * WorldScene.LIGHT_SPAN + build.waterLocalY + 1;
        if (own == far) {
            // A loft under the roof and the open air beside it are no rooms, and still two places:
            // the gable's outside is lit by the day, not as the dark loft behind it, and its inside as the loft.
            if (own == -1L && build.here.isOutside() != (beyond == null || beyond.isOutside())) {
                farLight = north ? ownLight - 1 : ownLight - WorldScene.LIGHT_SPAN;
            }
            return 0;
        }
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
    void addCourses(float[] opening, int at, int x, int y, int z, float a, float b, float da, float db, float outX, float outY) {
        SpriteGeometry.Mesh plinth = WorldScene.solid.get(WorldScene.PLAIN, Courses.paint(farColour, Courses.PLINTH_SHADE));
        SpriteGeometry.Mesh course = WorldScene.solid.get(WorldScene.PLAIN, Courses.paint(farColour, Courses.COURSE_SHADE));
        plinth.open = course.open = build.frame.open;
        plinth.sway = course.sway = 0f;
        float doorFrom = -1f, doorTo = -1f;
        if (opening[at + OPENING_START] >= 0f && opening[at + OPENING_FOOT] < Courses.DOORWAY_FOOT) {
            doorFrom = opening[at + OPENING_START];
            doorTo = opening[at + OPENING_END];
        }
        float base = z * ViewCamera.LEVEL_HEIGHT;
        float lift0 = build.geometry.liftAt(a, b), lift1 = build.geometry.liftAt(a + da, b + db);
        Courses.onEdge(plinth, course, x, y, a, b, da, db, base + lift0, base + lift1, outX, outY, doorFrom, doorTo,
                build.frame.red, build.frame.green, build.frame.blue);
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
