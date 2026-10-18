package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.textures.Texture;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.objects.IsoCurtain;
import zombie.iso.objects.IsoDeadBody;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;
import zombie.iso.objects.IsoWorldInventoryObject;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;
import zombie.vehicles.BaseVehicle;

/**
 * Dev only, with -Dcameraunlock.zomboid.sightline=true: for everything near the eye, the frame
 * the eye first had a clear line to it and the frame it was first in what the picture draws,
 * with what held it back between the two, as SIGHT lines in the log.
 *
 * The line is this class's own: straight along the map from the eye, head tracking's lean
 * included, to the thing, clear when it crosses no wall, no shut door and no drawn curtain as
 * the map has them that frame, and counted only while the thing is within the angle the
 * picture takes in. What stands in the way on a square (a wardrobe) is not counted, so the
 * line is clear a little more often than the thing can be seen. A thing is in what the
 * picture draws when the build of its chunk the frame shows was made with it.
 */
final class SightTrace {
    static final boolean ON = Boolean.getBoolean("cameraunlock.zomboid.sightline");
    /** What a run is doing at the moment, set by the rig's probe: every line carries it. */
    static volatile String tag = "";

    /** How far from the eye things are followed, in tiles. */
    private static final int REACH = 16;
    /** A thing not met for this many frames is forgotten. */
    private static final int FORGOTTEN_AFTER = 900;

    private static final class Thing {
        String kind, what;
        int x, y, z;
        /** The frame the line to it last opened, or the frame it went out of the picture with the line open. Less than 0 while there is no line. */
        int openFrame = -1;
        long openNanos;
        float openDistance;
        /** Whether the line is open and the picture is without it, and what holds it back. */
        boolean waiting;
        String held;
        /** Whether the line opened with the game not yet having shown the thing's room. */
        boolean beforeTheGame;
        /** The part of the run it was last said to be on time in. */
        String onTimeIn;
        int metFrame;
    }

    private final WorldScene scene;
    private final IdentityHashMap<Object, Thing> things = new IdentityHashMap<>();
    private final GridTrace trace = new GridTrace();
    private final GridTrace.Edges hides = this::hides;
    private final ArrayList<IsoMovingObject> movers = new ArrayList<>();
    private IsoCell cell;
    private int level;
    private String chunksTag = "";
    private int chunkFrames, waitingFrames, mostWaiting;
    private float nearestWaiting = Float.MAX_VALUE;

    SightTrace(WorldScene scene) {
        this.scene = scene;
    }

    /** Whether the edge between two squares that share a side hides what is beyond it. */
    private boolean hides(int fromX, int fromY, int toX, int toY) {
        IsoGridSquare from = cell.getGridSquare(fromX, fromY, level), to = cell.getGridSquare(toX, toY, level);
        if (from == null || to == null) {
            return false;
        }
        if (from.isWindowTo(to)) {
            boolean north = fromY != toY;
            return curtained(fromY < toY || fromX < toX ? to : from, north, false) || curtained(fromY < toY || fromX < toX ? from : to, north, true);
        }
        return from.isBlockedTo(to);
    }

    /** Whether a curtain is drawn on a square's north or west edge, or with far on its south or east edge. */
    private static boolean curtained(IsoGridSquare square, boolean north, boolean far) {
        IsoObjectType wanted = north ? far ? IsoObjectType.curtainS : IsoObjectType.curtainN : far ? IsoObjectType.curtainE : IsoObjectType.curtainW;
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) instanceof IsoCurtain curtain && !curtain.IsOpen() && curtain.getType() == wanted) {
                return true;
            }
        }
        return false;
    }

    /**
     * One frame, after its chunks are built.
     *
     * @param forwardX the way the view faces along the map, a unit vector, or nought for a view straight up or down
     * @param cosCone  the cosine of the half angle the picture takes in along the map
     * @param waiting  the chunks in view the frame left without a build, and how far off the nearest was
     * @param rooms    the rooms the eye's picture draws the contents of, or null for every room: see RoomSight
     */
    void frame(IsoCell cell, IsoPlayer player, ViewCamera.Pose eye, float forwardX, float forwardY, float cosCone, float tanAcross, float tanUp, int frame, int waiting,
            float nearest, boolean asTheGameShows, RoomSight.Seen rooms) {
        if (Online.contested()) {
            // Where players can fight, nothing is written of what the game is not showing.
            return;
        }
        this.cell = cell;
        level = (int) Math.floor(player.getZ());
        long now = System.nanoTime();
        int playerIndex = player.getIndex();
        chunks(frame, waiting, nearest);
        int eyeX = (int) Math.floor(eye.eyeX), eyeY = (int) Math.floor(eye.eyeY);
        linesFollowed = false;
        for (int z = Math.max(0, level - 1); z <= level + 1; z++) {
            for (int x = eyeX - FAR; x <= eyeX + FAR; x++) {
                for (int y = eyeY - FAR; y <= eyeY + FAR; y++) {
                    // Near and on the eye's level, this class's own line along the map; else what stands in a room, held to lines through the picture.
                    boolean near = z == level && Math.abs(x - eyeX) <= REACH && Math.abs(y - eyeY) <= REACH;
                    IsoGridSquare square = cell.getGridSquare(x, y, z);
                    if (square == null || !near && square.getRoomID() == -1L) {
                        continue;
                    }
                    WorldScene.ChunkMeshes build = scene.latestOf(Math.floorDiv(x, WorldScene.CHUNK_TILES), Math.floorDiv(y, WorldScene.CHUNK_TILES));
                    boolean shown = build != null && build.shownFrame == frame;
                    IsoObject floor = square.getFloor();
                    PZArrayList<IsoObject> objects = square.getObjects();
                    boolean centreAsked = false, centreOpen = false;
                    for (int i = 0; i < objects.size(); i++) {
                        IsoObject object = objects.get(i);
                        IsoSprite sprite = object.getSprite();
                        if (sprite == null || object == floor) {
                            continue;
                        }
                        String kind = kindOf(object, sprite);
                        if (kind == null) {
                            continue;
                        }
                        boolean onEdge = kind.equals("door") || kind.equals("window");
                        boolean open;
                        if (!near) {
                            open = inPicture(eye, tanAcross, tanUp, x + 0.5f, y + 0.5f, z) && reached(square.getRoomID(), eye, tanAcross, tanUp);
                        } else if (onEdge) {
                            open = openTo(eye, forwardX, forwardY, cosCone, edgeX(object, x, eye), edgeY(object, y, eye));
                        } else {
                            if (!centreAsked) {
                                centreAsked = true;
                                centreOpen = openTo(eye, forwardX, forwardY, cosCone, x + 0.5f, y + 0.5f);
                            }
                            open = centreOpen;
                        }
                        boolean built = shown && build.traced != null && build.traced.contains(object);
                        boolean culled = built && !ChunkBuffers.listed(WorldScene.withinItsRoom(object, sprite) ? square.getRoomID() : RoomSight.OUTSIDE, rooms);
                        if (culled && open && !reached(square.getRoomID(), eye, tanAcross, tanUp)) {
                            open = false;
                        }
                        String blank = built && !culled ? blank(object, sprite, build) : null;
                        boolean drawn = built && !culled && blank == null;
                        String held = blank;
                        if (!drawn && blank == null) {
                            Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
                            held = culled ? "the eye's picture does not see into its room" : build == null ? "its chunk has no build yet" : !shown ? "its chunk's build is not in the frame"
                                    : Sightline.leftOut(asTheGameShows, false, square.getRoomID() != -1L, square.isSeen(playerIndex)) ? "the game has not shown its room"
                                            : !WorldScene.usable(texture) ? "its sprite has no texture yet" : "its chunk's build is older than it";
                        }
                        met(object, kind, sprite.getName(), x, y, z, open, drawn, held, square.getRoomID() == -1L || square.isSeen(playerIndex), frame, now, eye);
                    }
                    ArrayList<IsoMovingObject> bodies = near ? square.getStaticMovingObjects() : NO_BODIES;
                    for (int i = 0; i < bodies.size(); i++) {
                        if (bodies.get(i) instanceof IsoDeadBody body) {
                            if (!centreAsked) {
                                centreAsked = true;
                                centreOpen = openTo(eye, forwardX, forwardY, cosCone, x + 0.5f, y + 0.5f);
                            }
                            boolean built = shown && build.traced != null && build.traced.contains(body);
                            boolean culled = built && !ChunkBuffers.listed(square.getRoomID(), rooms);
                            boolean bodyOpen = centreOpen && !(culled && !reached(square.getRoomID(), eye, tanAcross, tanUp));
                            boolean drawn = built && !culled;
                            String held = drawn ? null : culled ? "the eye's picture does not see into its room" : build == null ? "its chunk has no build yet" : !shown ? "its chunk's build is not in the frame"
                                    : Sightline.leftOut(asTheGameShows, false, square.getRoomID() != -1L, square.isSeen(playerIndex)) ? "the game has not shown its room"
                                            : body.getAtlasTexture() == null ? "the game has made no picture of the body" : "its chunk's build is older than it";
                            met(body, "body", "body", x, y, level, bodyOpen, drawn, held, square.getRoomID() == -1L || square.isSeen(playerIndex), frame, now, eye);
                        }
                    }
                }
            }
        }
        movers.clear();
        movers.addAll(cell.getObjectList());
        movers.addAll(cell.getVehicles());
        for (int i = 0; i < movers.size(); i++) {
            IsoMovingObject mover = movers.get(i);
            if (mover == player || Math.abs(mover.getX() - eye.eyeX) > REACH || Math.abs(mover.getY() - eye.eyeY) > REACH || (int) Math.floor(mover.getZ()) != level) {
                continue;
            }
            String kind = mover instanceof IsoZombie ? "zombie" : mover instanceof IsoPlayer ? "player" : mover instanceof BaseVehicle ? "vehicle"
                    : mover instanceof IsoGameCharacter ? "character" : null;
            if (kind == null) {
                continue;
            }
            boolean open = openTo(eye, forwardX, forwardY, cosCone, mover.getX(), mover.getY());
            // The picture draws the models the game draws, and the game draws by this.
            boolean drawn = mover.getAlpha(playerIndex) > 0.5f;
            IsoGridSquare on = mover.getCurrentSquare();
            String held = drawn ? null : "the game has it faded out, its square "
                    + (on == null ? "none" : on.isSeen(playerIndex) ? on.isCanSee(playerIndex) ? "in the game's sight" : "seen, out of the game's sight" : "never seen by the game");
            met(mover, kind, kind, (int) Math.floor(mover.getX()), (int) Math.floor(mover.getY()), level, open, drawn, held, true, frame, now, eye);
        }
        for (Iterator<Map.Entry<Object, Thing>> each = things.entrySet().iterator(); each.hasNext();) {
            Thing thing = each.next().getValue();
            if (frame - thing.metFrame > FORGOTTEN_AFTER) {
                if (thing.waiting) {
                    said("never", thing, frame, now, thing.held);
                }
                each.remove();
            }
        }
    }

    /**
     * Whether a line through the picture reaches a room, followed the slow way (RoomRays) once
     * a frame when first asked. The line of this class is held to the drawn cone, which is wider
     * than the picture: a room the walk leaves out that no line through the picture reaches is
     * not in the picture at all, and what stands in it is not counted as held back.
     */
    private boolean reached(long room, ViewCamera.Pose eye, float tanAcross, float tanUp) {
        if (!linesFollowed) {
            linesFollowed = true;
            byLines.clear();
            RoomRays.trace(scene.roomMap, eye, tanAcross, tanUp, LINES_ACROSS, LINES_UP, FAR + 4, level, byLines);
        }
        return byLines.contains(room);
    }

    private static final int LINES_ACROSS = 192, LINES_UP = 108;
    /** How far from the eye what stands in a room is followed, on the eye's level and the levels over and under it, in tiles. */
    private static final int FAR = 40;
    private static final ArrayList<IsoMovingObject> NO_BODIES = new ArrayList<>();

    /** Whether a point a little over the floor of a square on level z, or one a level's height higher, is inside the picture. */
    private static boolean inPicture(ViewCamera.Pose eye, float tanAcross, float tanUp, float x, float y, int z) {
        for (float over = 0.3f; over < ViewCamera.LEVEL_HEIGHT; over += 0.9f) {
            float dx = x - eye.eyeX, dy = y - eye.eyeY, du = z * ViewCamera.LEVEL_HEIGHT + over - eye.eyeUp;
            float along = dx * eye.forwardX + dy * eye.forwardY + du * eye.forwardUp;
            if (along < 0.05f) {
                continue;
            }
            float across = (dx * eye.rightX + dy * eye.rightY + du * eye.rightUp) / along, up = (dx * eye.upX + dy * eye.upY + du * eye.upUp) / along;
            if (Math.abs(across) <= tanAcross && Math.abs(up) <= tanUp) {
                return true;
            }
        }
        return false;
    }

    /**
     * What leaves a thing a build holds and a picture lists drawn blank or as a stand-in, or
     * null: the mod's picture in a frame not on the graphics card yet, or a piece the mod has a
     * model of drawn as the game's sprite because its model could not be stood when built.
     */
    private static String blank(IsoObject object, IsoSprite sprite, WorldScene.ChunkMeshes build) {
        Furniture piece = Furniture.known(sprite);
        if (piece == null) {
            return null;
        }
        if (piece.wall != WallHung.NONE && piece.picture != Decor.NONE && Own.pictures() && build.modelled != null && build.modelled.contains(object) && !Decor.onCard(piece.picture)) {
            return "its picture is not on the graphics card yet";
        }
        if (Own.furniture() && piece.model != null && piece.wall == WallHung.NONE && !piece.follows && !piece.basin && !piece.countertop && build.modelled != null
                && !build.modelled.contains(object)) {
            return "drawn as the game's sprite, its model not stood";
        }
        return null;
    }
    private final RoomSight.Seen byLines = new RoomSight.Seen();
    private boolean linesFollowed;

    private boolean openTo(ViewCamera.Pose eye, float forwardX, float forwardY, float cosCone, float x, float y) {
        float toX = x - eye.eyeX, toY = y - eye.eyeY;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance > 1f && toX * forwardX + toY * forwardY < cosCone * distance) {
            return false;
        }
        return Sightline.clear(trace, hides, eye.eyeX, eye.eyeY, x, y);
    }

    /** Where along the map a door or a window is looked at: the middle of its edge, a little to the eye's side of it. */
    private static float edgeX(IsoObject object, int x, ViewCamera.Pose eye) {
        return north(object) ? x + 0.5f : x + (eye.eyeX < x ? -0.05f : 0.05f);
    }

    private static float edgeY(IsoObject object, int y, ViewCamera.Pose eye) {
        return north(object) ? y + (eye.eyeY < y ? -0.05f : 0.05f) : y + 0.5f;
    }

    private static boolean north(IsoObject object) {
        return object instanceof IsoDoor door ? door.north : object instanceof IsoThumpable built ? built.north : ((IsoWindow) object).getNorth();
    }

    /** What a thing on a square is, for the table, or null for the house itself: a wall, a floor, stairs, a roof. */
    private static String kindOf(IsoObject object, IsoSprite sprite) {
        if (object instanceof IsoDoor || object instanceof IsoThumpable thumpable && thumpable.isDoor()) {
            return "door";
        }
        if (object instanceof IsoWindow) {
            return "window";
        }
        if (object instanceof IsoCurtain) {
            return "curtain";
        }
        if (object instanceof IsoWorldInventoryObject) {
            return "item";
        }
        if (WorldScene.partOfTheBuilding(object, sprite)) {
            return null;
        }
        if (sprite.getType() == IsoObjectType.tree || sprite.getProperties().has(IsoFlagType.vegitation)) {
            return "plant";
        }
        if (sprite.getType() == IsoObjectType.lightswitch || sprite.getName() != null && sprite.getName().startsWith("lighting_")) {
            return "light";
        }
        Furniture piece = Furniture.known(sprite);
        if (piece != null) {
            return piece.wall != WallHung.NONE ? "hung on a wall" : "modelled";
        }
        return sprite.getProperties().has(IsoFlagType.attachedN) || sprite.getProperties().has(IsoFlagType.attachedW) || sprite.getProperties().has(IsoFlagType.attachedE)
                || sprite.getProperties().has(IsoFlagType.attachedS) || sprite.getProperties().has(IsoFlagType.WallOverlay) ? "on a wall, flat" : "flat sprite";
    }

    /**
     * @param roomShown whether the game has shown the room the thing is in, or it is in none: with the line open before that, the frame
     *                  the game comes to show it is said, which is the soonest a build that waited for the game could have had the thing
     */
    private void met(Object key, String kind, String what, int x, int y, int z, boolean open, boolean drawn, String held, boolean roomShown, int frame, long now,
            ViewCamera.Pose eye) {
        Thing thing = things.get(key);
        if (thing == null) {
            thing = new Thing();
            thing.kind = kind;
            thing.what = what;
            things.put(key, thing);
        }
        thing.x = x;
        thing.y = y;
        thing.z = z;
        thing.metFrame = frame;
        if (!open) {
            thing.openFrame = -1;
            thing.waiting = false;
            thing.beforeTheGame = false;
            return;
        }
        if (thing.openFrame >= 0 && thing.beforeTheGame && roomShown) {
            thing.beforeTheGame = false;
            said("gameshown", thing, frame, now, null);
        }
        if (thing.openFrame < 0) {
            thing.openFrame = frame;
            thing.openNanos = now;
            thing.openDistance = (float) Math.hypot(x + 0.5f - eye.eyeX, y + 0.5f - eye.eyeY);
            thing.waiting = !drawn;
            thing.held = held;
            thing.beforeTheGame = !roomShown;
            // A line that opens and shuts on something drawn all the while is said once for each part of a run.
            if (!drawn || !tag.equals(thing.onTimeIn)) {
                said(drawn ? "ontime" : "open", thing, frame, now, held);
            }
            if (drawn) {
                thing.onTimeIn = tag;
            }
        } else if (thing.waiting && drawn) {
            thing.waiting = false;
            said("late", thing, frame, now, thing.held);
        } else if (thing.waiting && !held.equals(thing.held)) {
            thing.held = held;
            said("held", thing, frame, now, held);
        } else if (!thing.waiting && !drawn) {
            // Out of the picture with the line still open: a wait that begins here.
            thing.waiting = true;
            thing.openFrame = frame;
            thing.openNanos = now;
            thing.held = held;
            said("gone", thing, frame, now, held);
        }
    }

    private void said(String event, Thing thing, int frame, long now, String held) {
        Log.info(String.format(Locale.ROOT, "SIGHT %s | %s | %s | %s | at %d,%d,%d | %.1f tiles | open frame %d | frame %d | after %d frames %.0f ms | %s", event, tag, thing.kind, thing.what,
                thing.x, thing.y, thing.z, thing.openDistance, thing.openFrame, frame, thing.openFrame < 0 ? 0 : frame - thing.openFrame,
                thing.openFrame < 0 ? 0.0 : (now - thing.openNanos) / 1e6, held == null ? "-" : held));
    }

    /** The chunks in view left without a build, tallied for as long as the run's tag stands. */
    private void chunks(int frame, int waiting, float nearest) {
        String now = tag;
        if (!now.equals(chunksTag)) {
            if (chunkFrames > 0) {
                Log.info(String.format(Locale.ROOT, "SIGHT chunks | %s | frames %d | frames with a chunk in view unbuilt %d | most unbuilt at once %d | nearest unbuilt %s tiles", chunksTag, chunkFrames,
                        waitingFrames, mostWaiting, waitingFrames == 0 ? "-" : String.format(Locale.ROOT, "%.0f", nearestWaiting)));
            }
            chunksTag = now;
            chunkFrames = waitingFrames = mostWaiting = 0;
            nearestWaiting = Float.MAX_VALUE;
        }
        chunkFrames++;
        if (waiting > 0) {
            waitingFrames++;
            mostWaiting = Math.max(mostWaiting, waiting);
            nearestWaiting = Math.min(nearestWaiting, nearest);
        }
    }
}
