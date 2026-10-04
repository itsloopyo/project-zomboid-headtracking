package com.cameraunlock.zomboid;

import zombie.GameTime;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.input.GameKeyboard;
import zombie.iso.IsoCamera;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;
import zombie.iso.Vector2;
import zombie.network.GameClient;

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

    private static final float RADIANS_PER_PIXEL = 0.0025f;
    private static final float PITCH_LIMIT = (float) Math.toRadians(89);
    /** How far ahead of the character the game's cursor is held, in tiles. */
    private static final float CURSOR_DISTANCE = 3f;
    /** The direction the forward key moves the character in the game's own view: up the screen. */
    private static final float STOCK_FORWARD_YAW = (float) Math.toRadians(-135);

    private static final float EYE_HEIGHT_STANDING = 1.6f;
    private static final float EYE_HEIGHT_SNEAKING = 1.1f;
    private static final float EYE_HEIGHT_SITTING = 0.9f;

    private static final boolean TRACE = Boolean.getBoolean("cameraunlock.zomboid.trace");
    private static final long TRACE_INTERVAL_NANOS = 1_000_000_000L;

    private static boolean on;
    private static boolean lookCaptured;
    private static float yaw;
    private static float pitch;

    private static boolean haveRawX, haveRawY;
    private static int lastRawX, lastRawY;
    private static int cursorX, cursorY;

    /** Where the reticle is drawn when the aim point is not in the picture: off the screen. */
    private static final int RETICLE_HIDDEN = -100_000;

    private static final ViewCamera.Pose cleanView = new ViewCamera.Pose();
    private static final ViewCamera.Pose trackedView = new ViewCamera.Pose();
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

    /** The clean view direction: where the mouse has turned the view, with nothing else mixed in. */
    static float yaw() {
        return yaw;
    }

    static float pitch() {
        return pitch;
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
        return gap != null ? gap : Pipeline.failure();
    }

    /** Called from IngameState.renderFrameInternal straight after the game has drawn its world. */
    public static void afterWorldRender() {
        if (GameKeyboard.isKeyPressed(TOGGLE_KEY)) {
            toggle();
        }
        HeadTracking.pollHotkeys();
        IsoPlayer player = IsoPlayer.getInstance();
        if (on && !allowed(player)) {
            on = false;
            Log.info("first person off: " + whyNot(player));
        }
        lookCaptured = on && !GameTime.isGamePaused() && !player.isDead();
        Characters.wanted = on;
        MouseGrab.wanted = lookCaptured;
        if (!on) {
            HeadTracking.rest();
            if (MouseGrab.applied) {
                SpriteRenderer.instance.drawGeneric(MouseGrab.DRAWER);
            }
            return;
        }

        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        float zoom = Core.getInstance().getZoom(player.getIndex());
        // The game aims from its aim origin to the point under the cursor at the origin's own
        // height, so that is the height the cursor's point has to be taken at.
        float aheadX = player.getAimOriginPosX() + cos * CURSOR_DISTANCE;
        float aheadY = player.getAimOriginPosY() + sin * CURSOR_DISTANCE;
        float aimHeight = player.getAimOriginPosZ();
        cursorX = Math.round(IsoUtils.XToScreenExact(aheadX, aheadY, aimHeight, 0) / zoom);
        cursorY = Math.round(IsoUtils.YToScreenExact(aheadX, aheadY, aimHeight, 0) / zoom);

        float floorUp = player.getZ() * ViewCamera.LEVEL_HEIGHT + Terrain.lift(player.getX(), player.getY(), player.getZ());
        ViewCamera.clean(cleanView, player.getX(), player.getY(), floorUp + eyeHeight(player), yaw, pitch);
        world.look(IsoWorld.instance.currentCell, (int) Math.floor(player.getZ()));
        HeadTracking.view(lookCaptured, cleanView, yaw, world, trackedView);
        WorldScene scene = WorldScene.next();
        scene.build(player, trackedView, FieldOfView.tanHalfVertical());
        SpriteRenderer.instance.drawGeneric(scene);
        placeReticle(scene, floorUp);

        if (TRACE) {
            trace(player, scene, zoom);
        }
    }

    /**
     * The reticle marks a point, not a direction: where the clean aim, from the eye the mouse
     * turns, first meets the world this frame. With the head leaned the picture is drawn from
     * another eye, and that point is no longer straight ahead of it. With nothing in reach the
     * aim is at infinity and only its direction is left to project.
     */
    private static void placeReticle(WorldScene scene, float floorUp) {
        float aspect = WorldScene.drawnAspect();
        if (aspect == 0f) {
            // No frame has been drawn yet, so the shape of the picture is not known.
            reticleX = RETICLE_HIDDEN;
            reticleY = RETICLE_HIDDEN;
            return;
        }
        shotDistance = world.shotDistance(cleanView, floorUp, WorldScene.REACH,
                scene.characterX, scene.characterY, scene.characterBase, scene.characters);
        float along = shotDistance;
        ViewCamera.Pose from = cleanView;
        if (along == Float.POSITIVE_INFINITY) {
            along = 1f;
            from = trackedView;
        }
        boolean inFront = ViewCamera.project(trackedView, scene.tanHalfFieldOfView(), aspect,
                from.eyeX + cleanView.forwardX * along, from.eyeY + cleanView.forwardY * along,
                from.eyeUp + cleanView.forwardUp * along, ndc);
        if (!inFront) {
            reticleX = RETICLE_HIDDEN;
            reticleY = RETICLE_HIDDEN;
            return;
        }
        int index = IsoPlayer.getInstance().getIndex();
        reticleX = Math.round((ndc[0] * 0.5f + 0.5f) * IsoCamera.getScreenWidth(index));
        reticleY = Math.round((0.5f - ndc[1] * 0.5f) * IsoCamera.getScreenHeight(index));
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

    /** Called at the end of the game's Mouse.update with the cursor column it read. */
    public static int cursorX(int raw) {
        if (!lookCaptured) {
            haveRawX = false;
            return raw;
        }
        if (haveRawX) {
            setLook(yaw + (raw - lastRawX) * RADIANS_PER_PIXEL, pitch);
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
        if (haveRawY) {
            setLook(yaw, pitch - (raw - lastRawY) * RADIANS_PER_PIXEL);
        }
        lastRawY = raw;
        haveRawY = true;
        return cursorY;
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
        Log.info(String.format(java.util.Locale.ROOT,
                "trace yaw %.1f pitch %.1f at (%.2f, %.2f, %.2f) facing (%.2f, %.2f) aim (%.2f, %.2f) aiming %b keys (%.2f, %.2f)"
                        + " cursor (%d, %d) zoom %.2f offset (%.0f, %.0f) captured %b | %s | reticle (%d, %d) ndc (%.4f, %.4f)"
                        + " shot distance %.3f render eye (%.3f, %.3f, %.3f) | %s",
                Math.toDegrees(yaw), Math.toDegrees(pitch), player.getX(), player.getY(), player.getZ(),
                facing.x, facing.y, traceAim.x, traceAim.y, player.isAiming(), traceMoveX, traceMoveY,
                cursorX, cursorY, zoom, IsoCamera.getOffX(), IsoCamera.getOffY(), lookCaptured, HeadTracking.describe(),
                reticleX, reticleY, ndc[0], ndc[1], shotDistance, trackedView.eyeX, trackedView.eyeY, trackedView.eyeUp,
                scene.describe()));
    }
}
