package com.cameraunlock.zomboid;

import java.util.Locale;
import zombie.GameTime;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.input.GameKeyboard;
import zombie.input.Mouse;
import zombie.iso.IsoCamera;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;
import zombie.iso.Vector2;
import zombie.network.GameClient;
import zombie.vehicles.BaseVehicle;

/**
 * The first-person view: whether it is on, where it looks, and the three
 * places it meets the game.
 *
 * The game keeps running exactly as it does in its own view. Its world render
 * still runs every frame, so everything that render keeps up to date (what has
 * been seen, what the cursor is over) stays as it was, and the first-person
 * picture is drawn over the result. The mouse turns the view; the game's cursor
 * is held on the ground ahead of the character along the view, so the game's
 * own aim, which runs from the character to the cursor, is the view direction.
 * The movement keys are turned so that forward is where the view faces. Head
 * tracking turns and moves the picture alone, and the game's reticle is drawn
 * where the aim meets the world in that picture.
 *
 * Everything here runs on the game's logic thread.
 */
public final class FirstPerson {
    /** F8 in the key codes the game's keyboard uses. Nothing in the game's default bindings uses it. */
    private static final int TOGGLE_KEY = 66;
    /** F9. Pressed in the game's own view, it has the picture draw that view. */
    private static final int OVERHEAD_KEY = 67;
    /** How far off the overhead camera stands, in tiles: far enough that the picture has next to no perspective. */
    private static final float OVERHEAD_AWAY = 6000f;
    private static final float OVERHEAD_YAW = (float) (-0.75 * Math.PI), OVERHEAD_PITCH = (float) Math.toRadians(-30);
    private static boolean overhead;
    private static int overheadSaid;
    private static final ViewCamera.Pose overheadView = new ViewCamera.Pose(), standingView = new ViewCamera.Pose();
    /** The name the game stores its inventory key under. */
    private static final String INVENTORY_BINDING = "Toggle Inventory";
    private static final int ESCAPE_KEY = 1;
    /**
     * The middle mouse button, which brings the pointer out for as long as it is held. Nothing
     * in the game's default bindings uses it, and every letter key is taken.
     */
    private static final int POINT_BUTTON = 2;
    private static final int AIM_BUTTON = 1;
    /** How long after the aim button is let go the game may still be opening what it opens on a right click. */
    private static final long AFTER_AIM_NANOS = 400_000_000L;
    private static long aimedAt = Long.MIN_VALUE / 2;

    /** How far a pixel of mouse travel turns the view unzoomed. Zoomed it turns the view less, to move the picture as far. */
    private static final float RADIANS_PER_PIXEL = 0.0025f;
    /** A frame longer than this is a hitch, and taking it whole would snap the zoom to its target. */
    private static final float MAX_FRAME_SECONDS = 0.25f;
    private static final float PITCH_LIMIT = (float) Math.toRadians(89);
    /** How far ahead of the character the game's cursor is held, in tiles. */
    private static final float CURSOR_DISTANCE = 3f;
    /** The direction the forward key moves the character in the game's own view: up the screen. */
    private static final float STOCK_FORWARD_YAW = (float) Math.toRadians(-135);

    private static final float EYE_HEIGHT_STANDING = 1.6f;
    private static final float EYE_HEIGHT_SNEAKING = 1.1f;
    private static final float EYE_HEIGHT_SITTING = 0.9f;
    private static final float[] eye = new float[3];
    static final boolean TRACE = Boolean.getBoolean("cameraunlock.zomboid.trace");
    private static final long TRACE_INTERVAL_NANOS = 1_000_000_000L;

    private static boolean on;
    private static boolean wasRiding, onForTheRide;
    /** The view is on, the game is running and the character is alive. */
    private static boolean active;
    private static boolean lookCaptured;
    private static final PointerMode pointer = new PointerMode();
    /**
     * Leaving for the main menu stops the frames with the look still marked as captured, and
     * the game goes on reading its keys there.
     */
    private static final long CAPTURE_LASTS_NANOS = 500_000_000L;
    private static long lastFrameNanos;
    private static boolean zoomSaid;
    private static float yaw;
    private static float pitch;

    /**
     * How the picture is keeping up, said whoever is playing: a report of a slow game is of no
     * use without the frame time, the memory in use and the size of the scene. Once soon after
     * the view comes on and then once a minute, so that an evening's play leaves a log the
     * other lines can still be found in.
     */
    private static final long PACE_FIRST_NANOS = 5_000_000_000L;
    private static final long PACE_EVERY_NANOS = TRACE ? PACE_FIRST_NANOS : 60_000_000_000L;
    private static long paceWait = PACE_FIRST_NANOS;
    private static long paceSince, paceWorst;
    private static int paceFrames;
    private static WorldScene paceScene;
    private static boolean haveRawX, haveRawY;
    private static int lastRawX, lastRawY;
    private static int cursorX, cursorY;

    /** Where the reticle is drawn when the aim point is not in the picture: off the screen. */
    private static final int RETICLE_HIDDEN = -100_000;

    private static final ViewCamera.Pose cleanView = new ViewCamera.Pose();
    private static final ViewCamera.Pose trackedView = new ViewCamera.Pose();
    /**
     * The aim: the clean view from where the shot starts, which is the clean eye moved by
     * whatever of a lean the weapon has. The weapon is drawn in this view and the shot is
     * traced from it.
     */
    private static final ViewCamera.Pose aimView = new ViewCamera.Pose();
    private static final WorldQuery world = new WorldQuery();
    private static final float[] ndc = new float[2];
    private static int reticleX, reticleY;
    private static float shotDistance;

    private static final Vector2 forward = new Vector2();
    private static final Vector2 traceAim = new Vector2();
    private static float traceMoveX, traceMoveY;
    private static long lastTrace;

    private FirstPerson() {}

    static boolean isOn() {
        return on;
    }

    static boolean overhead() {
        return overhead && !on;
    }

    /** The clean view direction: where the mouse has turned the view, with nothing else mixed in. */
    static float yaw() {
        return yaw;
    }

    static float pitch() {
        return pitch;
    }

    /** Whether the mouse is turning the view, for what the game asks between frames. */
    static boolean lookCaptured() {
        return lookCaptured && System.nanoTime() - lastFrameNanos < CAPTURE_LASTS_NANOS;
    }

    /** Whether the first person view is being played in, with the mouse turning the view or out as a pointer. */
    static boolean active() {
        return active && System.nanoTime() - lastFrameNanos < CAPTURE_LASTS_NANOS;
    }

    static void setLook(float newYaw, float newPitch) {
        // Turning one way all session would otherwise grow the yaw until a float no longer
        // holds a pixel's worth of turn.
        yaw = (float) Math.IEEEremainder(newYaw, 2 * Math.PI);
        pitch = Math.clamp(newPitch, -PITCH_LIMIT, PITCH_LIMIT);
    }

    static void toggle() {
        IsoPlayer player = IsoPlayer.getInstance();
        if (!on && !allowed(player)) {
            Log.info("first person stays off: " + whyNot(player));
            return;
        }
        on = !on;
        if (on) {
            Vector2 facing = player.getForwardDirection();
            setLook((float) Math.atan2(facing.y, facing.x), 0f);
        }
        Log.info("first person " + (on ? "on" : "off"));
    }

    private static boolean allowed(IsoPlayer player) {
        return whyNot(player) == null;
    }

    private static String whyNot(IsoPlayer player) {
        if (player == null) {
            return "no player";
        }
        if (GameClient.client) {
            return "multiplayer";
        }
        if (IsoPlayer.numPlayers != 1) {
            return "split screen";
        }
        String gap = Agent.firstPersonGap();
        if (gap == null) {
            gap = Panels.gap();
        }
        return gap != null ? gap : Pipeline.failure();
    }

    /** Called from IngameState.renderFrameInternal straight after the game has drawn its world. */
    public static void afterWorldRender() {
        if (GameKeyboard.isKeyPressed(TOGGLE_KEY)) {
            toggle();
        }
        HeadTracking.pollHotkeys();
        DevKeys.poll();
        if (!on && GameKeyboard.isKeyPressed(OVERHEAD_KEY)) {
            overhead = !overhead && allowed(IsoPlayer.getInstance());
            Log.info("the game's own view is drawn by " + (overhead ? "the picture" : "the game"));
        }
        if (Terrain.level != overhead()) {
            Terrain.level(overhead());
            WorldScene.forgetAll();
        }
        IsoPlayer player = IsoPlayer.getInstance();
        followTheRide(player);
        if (on && !allowed(player)) {
            on = false;
            Log.info("first person off: " + whyNot(player));
        }
        active = on && !GameTime.isGamePaused() && !player.isDead();
        if (!on) {
            pointer.reset();
        } else if (active) {
            point();
        }
        lookCaptured = active && !pointer.on();
        long now = System.nanoTime();
        long frameNanos = now - lastFrameNanos;
        float sinceLastFrame = Math.min(frameNanos / 1e9f, MAX_FRAME_SECONDS);
        notePace(now, frameNanos);
        lastFrameNanos = now;
        Characters.wanted = on || overhead && allowed(player) && !player.isDead();
        MouseGrab.wanted = lookCaptured;
        MouseGrab.kept = active && pointer.on();
        if (!on) {
            FieldOfView.rest();
            HeadTracking.rest();
            ViewModel.rest();
            Crosshair.hide();
            if (MouseGrab.applied || MouseGrab.keeping) {
                SpriteRenderer.instance.drawGeneric(MouseGrab.DRAWER);
            }
            if (Characters.wanted) {
                drawOverhead(player);
            }
            return;
        }

        float zoom = Core.getInstance().getZoom(player.getIndex());
        holdTheCursorAhead(player, zoom);
        float floorUp = player.getZ() * ViewCamera.LEVEL_HEIGHT + Terrain.lift(player.getX(), player.getY(), player.getZ());
        eye[0] = player.getX();
        eye[1] = player.getY();
        eye[2] = floorUp + eyeHeight(player);
        Headlights.gather(eye[0], eye[1]);
        BaseVehicle vehicle = player.getVehicle();
        Ride.note(player, vehicle, floorUp, eye);
        boolean sightsUp = Controls.sightsUp(player);
        FieldOfView.ease(sightsUp, sinceLastFrame);
        ViewModel.update(player, yaw, pitch, sinceLastFrame, sightsUp, shotDistance);
        if (!zoomSaid) {
            zoomSaid = true;
            Log.info(String.format(Locale.ROOT,
                    "head tracking zoom factor %.4f: the picture is drawn at %.2f degrees and the field of view is set to %.2f",
                    ZoomCompensation.factor(FieldOfView.tanHalfVertical(), FieldOfView.setTanHalfVertical()),
                    FieldOfView.drawnDegrees(), FieldOfView.degrees()));
        }
        ViewCamera.clean(cleanView, eye[0], eye[1], eye[2], yaw, pitch);
        world.look(IsoWorld.instance.currentCell, (int) Math.floor(player.getZ()));
        HeadTracking.view(active, cleanView, yaw, world, sightsUp, vehicle == null, trackedView);
        float[] carried = HeadTracking.carried();
        aimView.set(cleanView);
        aimView.eyeX += carried[0];
        aimView.eyeY += carried[1];
        aimView.eyeUp += carried[2];
        WorldScene scene = WorldScene.next();
        scene.build(player, trackedView, aimView, FieldOfView.tanHalfVertical());
        paceScene = scene;
        SpriteRenderer.instance.drawGeneric(scene);
        placeReticle(scene, floorUp);
        // The game draws its own reticle while the aim is held, and nothing while it is not.
        if (reticleX != RETICLE_HIDDEN && lookCaptured && ViewModel.held != null && !player.isAiming()) {
            Crosshair.show(ndc[0], ndc[1], ViewModel.spread());
        } else {
            Crosshair.hide();
        }

        if (TRACE) {
            trace(player, scene, zoom);
        }
    }

    /**
     * Getting into a vehicle is getting behind its wheel: the view goes to the seat by itself,
     * and goes back to how it was on getting out.
     */
    private static void followTheRide(IsoPlayer player) {
        boolean riding = player != null && player.getVehicle() != null;
        if (riding && !wasRiding && !on) {
            toggle();
            onForTheRide = on;
        } else if (!riding && wasRiding && onForTheRide && on) {
            toggle();
        }
        if (!riding) {
            onForTheRide = false;
        }
        wasRiding = riding;
    }

    private static void notePace(long now, long frameNanos) {
        if (!on) {
            paceWait = PACE_FIRST_NANOS;
            paceSince = now;
            paceFrames = 0;
            paceWorst = 0;
            return;
        }
        paceFrames++;
        paceWorst = Math.max(paceWorst, frameNanos);
        if (now - paceSince > paceWait) {
            Runtime memory = Runtime.getRuntime();
            Log.info(String.format(Locale.ROOT, "first person pace: %.1f ms a frame, worst %.1f, heap %d of %d MB, %s",
                    (now - paceSince) / 1e6 / paceFrames, paceWorst / 1e6, (memory.totalMemory() - memory.freeMemory()) >> 20,
                    memory.maxMemory() >> 20, paceScene == null ? "no scene yet" : paceScene.describe()));
            paceWait = PACE_EVERY_NANOS;
            paceSince = now;
            paceFrames = 0;
            paceWorst = 0;
        }
    }

    /**
     * The game aims from its aim origin to the point under the cursor at the origin's own
     * height, so that is the height the cursor's point is taken at.
     */
    private static void holdTheCursorAhead(IsoPlayer player, float zoom) {
        float cursorAtX = player.getAimOriginPosX() + (float) Math.cos(yaw) * CURSOR_DISTANCE;
        float cursorAtY = player.getAimOriginPosY() + (float) Math.sin(yaw) * CURSOR_DISTANCE;
        float aimHeight = player.getAimOriginPosZ();
        cursorX = Math.round(IsoUtils.XToScreenExact(cursorAtX, cursorAtY, aimHeight, 0) / zoom);
        cursorY = Math.round(IsoUtils.YToScreenExact(cursorAtX, cursorAtY, aimHeight, 0) / zoom);
    }

    /**
     * Draws the game's own view with the picture: the same world from the same place, at the
     * game's own angle and zoom, so that everything is where the game has it on the screen and
     * the pointer still points at what it is on. The game's view has no perspective. A camera
     * a long way off with a very narrow field of view has next to none, and everything the
     * picture does works with it as it stands.
     */
    private static void drawOverhead(IsoPlayer player) {
        int index = player.getIndex();
        float zoom = Core.getInstance().getZoom(index);
        float x = player.getX(), y = player.getY(), level = player.getZ();
        float atX = IsoUtils.XToScreenExact(x, y, level, 0) / zoom, atY = IsoUtils.YToScreenExact(x, y, level, 0) / zoom;
        float eastX = IsoUtils.XToScreenExact(x + 1f, y, level, 0) / zoom - atX, eastY = IsoUtils.YToScreenExact(x + 1f, y, level, 0) / zoom - atY;
        float southX = IsoUtils.XToScreenExact(x, y + 1f, level, 0) / zoom - atX, southY = IsoUtils.YToScreenExact(x, y + 1f, level, 0) / zoom - atY;
        float wide = IsoCamera.getScreenWidth(index), high = IsoCamera.getScreenHeight(index);
        // The place on the player's floor that is at the middle of the screen.
        float toX = wide / 2f - atX, toY = high / 2f - atY;
        float det = eastX * southY - southX * eastY;
        float middleX = x + (toX * southY - southX * toY) / det, middleY = y + (eastX * toY - toX * eastY) / det;
        // A tile's diagonal lies along the screen: its length there over its length on the map.
        float pixelsToTile = (float) (Math.hypot(eastX - southX, eastY - southY) / Math.sqrt(2.0));
        float halfHigh = high / 2f / pixelsToTile;
        if (TRACE && (overheadSaid++ & 127) == 0) {
            Log.info(String.format(Locale.ROOT, "overhead: player on screen at (%.1f, %.1f) of %.0fx%.0f, zoom %.2f, east (%.2f, %.2f) south (%.2f, %.2f), camera offset (%.1f, %.1f), middle of screen at tile (%.2f, %.2f), player (%.2f, %.2f, %.2f)",
                    atX, atY, wide, high, zoom, eastX, eastY, southX, southY, IsoCamera.getOffX(), IsoCamera.getOffY(), middleX, middleY, x, y, level));
        }
        float floorUp = level * ViewCamera.LEVEL_HEIGHT;
        ViewCamera.clean(overheadView, 0f, 0f, 0f, OVERHEAD_YAW, OVERHEAD_PITCH);
        overheadView.eyeX = middleX - overheadView.forwardX * OVERHEAD_AWAY;
        overheadView.eyeY = middleY - overheadView.forwardY * OVERHEAD_AWAY;
        overheadView.eyeUp = floorUp - overheadView.forwardUp * OVERHEAD_AWAY;
        ViewCamera.clean(standingView, x, y, floorUp + Terrain.lift(x, y, level) + eyeHeight(player), OVERHEAD_YAW, OVERHEAD_PITCH);
        if (TRACE && (overheadSaid & 127) == 1) {
            ViewCamera.project(overheadView, halfHigh / OVERHEAD_AWAY, wide / high, x, y, floorUp + Terrain.lift(x, y, level), ndc);
            Log.info(String.format(Locale.ROOT, "overhead: the picture has the player's feet at (%.1f, %.1f), lift %.3f",
                    (ndc[0] * 0.5f + 0.5f) * wide, (0.5f - ndc[1] * 0.5f) * high, Terrain.lift(x, y, level)));
        }
        Headlights.gather(x, y);
        Ride.seated = false;
        world.look(IsoWorld.instance.currentCell, (int) Math.floor(level));
        WorldScene scene = WorldScene.next();
        scene.build(player, standingView, standingView, FieldOfView.tanHalfVertical());
        scene.seenFromOverhead(overheadView, halfHigh / OVERHEAD_AWAY, OVERHEAD_AWAY);
        paceScene = scene;
        SpriteRenderer.instance.drawGeneric(scene);
    }

    /**
     * The reticle marks a point, not a direction: where the clean aim, from where the shot
     * starts, first meets the world this frame. With the head leaned the picture is drawn from
     * another eye, and that point is no longer straight ahead of it. With nothing in reach the
     * aim is at infinity and only its direction is left to project.
     */
    private static void placeReticle(WorldScene scene, float floorUp) {
        shotDistance = world.shotDistance(aimView, floorUp, WorldScene.REACH,
                scene.characterX, scene.characterY, scene.characterBase, scene.characters);
        // The game looks for a shot's target along a line through this point. With nothing
        // in reach it is a point as far along the view as anything could have been. The
        // picture raises the land and the game's map is flat, so the point's height is
        // taken above the flat map, in floor levels.
        float aimed = shotDistance == Float.POSITIVE_INFINITY ? WorldScene.REACH : shotDistance;
        float aimedX = aimView.eyeX + aimView.forwardX * aimed, aimedY = aimView.eyeY + aimView.forwardY * aimed;
        float aimedUp = aimView.eyeUp + aimView.forwardUp * aimed;
        float level = IsoPlayer.getInstance().getZ();
        Controls.aimAt(aimedX, aimedY, (aimedUp - Terrain.lift(aimedX, aimedY, level)) / ViewCamera.LEVEL_HEIGHT);

        float aspect = WorldScene.drawnAspect();
        if (aspect == 0f) {
            // No frame has been drawn yet, so the shape of the picture is not known.
            hideReticle();
            return;
        }
        float along = shotDistance;
        ViewCamera.Pose from = aimView;
        if (along == Float.POSITIVE_INFINITY) {
            along = 1f;
            from = trackedView;
        }
        boolean inFront = ViewCamera.project(trackedView, scene.tanHalfFieldOfView(), aspect,
                from.eyeX + aimView.forwardX * along, from.eyeY + aimView.forwardY * along,
                from.eyeUp + aimView.forwardUp * along, ndc);
        if (!inFront) {
            hideReticle();
            return;
        }
        int index = IsoPlayer.getInstance().getIndex();
        reticleX = Math.round((ndc[0] * 0.5f + 0.5f) * IsoCamera.getScreenWidth(index));
        reticleY = Math.round((0.5f - ndc[1] * 0.5f) * IsoCamera.getScreenHeight(index));
    }

    private static void hideReticle() {
        reticleX = RETICLE_HIDDEN;
        reticleY = RETICLE_HIDDEN;
    }

    /**
     * Lets go of the mouse or takes it back. The inventory key is handled here and kept from
     * the game's own handler, which only flips the panels between shown and hidden: they are
     * shown folded from the start, so its first press would hide them.
     */
    private static void point() {
        Core.KeyBinding inventory = Core.getInstance().getKeyBinding(INVENTORY_BINDING);
        boolean inventoryKey = pressed(inventory.keyValue()) | pressed(inventory.altKey());
        boolean escape = GameKeyboard.isKeyPressed(ESCAPE_KEY);
        if (escape && pointer.byKey()) {
            // Or the same press would go on to open the game's menu.
            GameKeyboard.eatKeyPress(ESCAPE_KEY);
        }
        String was = TRACE ? pointer.describe() : null;
        int panels = Panels.open();
        long now = System.nanoTime();
        if (Mouse.isButtonDown(AIM_BUTTON)) {
            aimedAt = now;
        }
        if (now - aimedAt < AFTER_AIM_NANOS && !pointer.byKey()) {
            pointer.passOver(panels);
        }
        PointerMode.Inventory inventoryPanels = pointer.update(inventoryKey, escape, Mouse.isButtonDown(POINT_BUTTON), panels);
        if (inventoryPanels != PointerMode.Inventory.LEAVE) {
            Panels.showInventory(inventoryPanels == PointerMode.Inventory.OPEN);
        }
        if (TRACE && !was.equals(pointer.describe())) {
            Log.info("pointer " + pointer.describe());
        }
    }

    /** Whether a key of a binding went down this frame. The game eats the press, so its own handlers never see the key come up. */
    private static boolean pressed(int key) {
        if (key == 0 || !GameKeyboard.isKeyPressed(key)) {
            return false;
        }
        GameKeyboard.eatKeyPress(key);
        return true;
    }

    /** Called where the game's reticle and aim cursor read the screen column to draw at. */
    public static int reticleX(int stock) {
        return on ? reticleX : stock;
    }

    /** Called where the game's reticle and aim cursor read the screen row to draw at, counted from the top. */
    public static int reticleY(int stock) {
        return on ? reticleY : stock;
    }

    private static float eyeHeight(IsoPlayer player) {
        if (player.isSitOnGround() || player.isSittingOnFurniture()) {
            return EYE_HEIGHT_SITTING;
        }
        return player.isSneaking() ? EYE_HEIGHT_SNEAKING : EYE_HEIGHT_STANDING;
    }

    /**
     * Called as the game's window is about to show or hide its cursor to suit the game. The
     * game shows and hides its pointer as the player aims and fires, and each change puts the
     * window's cursor back where it was before: the look, which goes by how far the cursor
     * has moved, took that for a turn and jumped. While the look has the mouse the window's
     * cursor is the look's alone.
     */
    public static boolean holdsMouse() {
        return !MouseGrab.ISOLATED && (MouseGrab.applied || MouseGrab.keeping);
    }

    /** Called at the end of the game's Mouse.update with the cursor column it read. */
    public static int cursorX(int raw) {
        if (!lookCaptured) {
            haveRawX = false;
            return raw;
        }
        if (MouseGrab.moved.get() > 0) {
            haveRawX = false;
        }
        if (haveRawX && !MouseGrab.ISOLATED) {
            setLook(yaw + (raw - lastRawX) * RADIANS_PER_PIXEL * FieldOfView.zoom(), pitch);
        }
        lastRawX = raw;
        haveRawX = true;
        return cursorX;
    }

    /** Called at the end of the game's Mouse.update with the cursor row it read, counted from the top. */
    public static int cursorY(int raw) {
        if (!lookCaptured) {
            haveRawY = false;
            return raw;
        }
        if (MouseGrab.moved.get() > 0) {
            haveRawY = false;
            MouseGrab.moved.decrementAndGet();
        }
        if (haveRawY && !MouseGrab.ISOLATED) {
            setLook(yaw, pitch - (raw - lastRawY) * RADIANS_PER_PIXEL * FieldOfView.zoom());
        }
        lastRawY = raw;
        haveRawY = true;
        return cursorY;
    }

    /**
     * Called with the way the game takes the player to be looking when it works out what
     * they can see. The game hides whoever is outside that cone, and its cone is the body's:
     * walking sideways the body faces the way it walks, and a zombie straight ahead of the
     * view, an arm's length off, was gone and back again as the body turned. In first person
     * what the player can see is what the view faces.
     */
    public static Vector2 sightVector(Vector2 look) {
        if (!on) {
            return look;
        }
        return look.set((float) Math.cos(yaw), (float) Math.sin(yaw));
    }

    /**
     * Called with the direction the movement keys ask for, in world axes. In
     * the game's own view forward is up the screen; here it is where the view
     * faces. With no key down and no aim held the character is turned to face
     * the view, since nothing else in the game does that without the cursor.
     */
    public static Vector2 moveVector(Vector2 move) {
        traceMoveX = move.x;
        traceMoveY = move.y;
        if (!on) {
            return move;
        }
        IsoPlayer player = IsoPlayer.getInstance();
        if (player == null || player.getVehicle() != null) {
            return move;
        }
        if (move.x == 0f && move.y == 0f) {
            if (!player.isAiming()) {
                player.setForwardDirection(forward.set((float) Math.cos(yaw), (float) Math.sin(yaw)));
            }
            return move;
        }
        float turn = yaw - STOCK_FORWARD_YAW;
        float cos = (float) Math.cos(turn);
        float sin = (float) Math.sin(turn);
        return move.set(move.x * cos - move.y * sin, move.x * sin + move.y * cos);
    }

    private static void trace(IsoPlayer player, WorldScene scene, float zoom) {
        long now = System.nanoTime();
        if (now - lastTrace < TRACE_INTERVAL_NANOS) {
            return;
        }
        lastTrace = now;
        Vector2 facing = player.getForwardDirection();
        player.getAimVector(traceAim);
        Log.info(String.format(Locale.ROOT,
                "trace yaw %.1f pitch %.1f at (%.2f, %.2f, %.2f) facing (%.2f, %.2f) aim (%.2f, %.2f) aiming %b keys (%.2f, %.2f)"
                        + " cursor (%d, %d) zoom %.2f offset (%.0f, %.0f) captured %b pointer %s | %s | reticle (%d, %d) ndc (%.4f, %.4f)"
                        + " shot distance %.3f render eye (%.3f, %.3f, %.3f) | %s | %s | %s",
                Math.toDegrees(yaw), Math.toDegrees(pitch), player.getX(), player.getY(), player.getZ(),
                facing.x, facing.y, traceAim.x, traceAim.y, player.isAiming(), traceMoveX, traceMoveY,
                cursorX, cursorY, zoom, IsoCamera.getOffX(), IsoCamera.getOffY(), lookCaptured, pointer.describe(), HeadTracking.describe(),
                reticleX, reticleY, ndc[0], ndc[1], shotDistance, trackedView.eyeX, trackedView.eyeY, trackedView.eyeUp,
                scene.describe(), Controls.describe(), ViewModel.describe()));
    }
}
