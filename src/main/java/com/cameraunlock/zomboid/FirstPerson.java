package com.cameraunlock.zomboid;

import zombie.GameTime;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.input.GameKeyboard;
import zombie.iso.IsoCamera;
import zombie.iso.IsoUtils;
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
 * The movement keys are turned so that forward is where the view faces.
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
        yaw = newYaw;
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
        return null;
    }

    /** Called from IngameState.renderFrameInternal straight after the game has drawn its world. */
    public static void afterWorldRender() {
        if (GameKeyboard.isKeyPressed(TOGGLE_KEY)) {
            toggle();
        }
        IsoPlayer player = IsoPlayer.getInstance();
        if (on && !allowed(player)) {
            on = false;
            Log.info("first person off: " + whyNot(player));
        }
        lookCaptured = on && !GameTime.isGamePaused() && !player.isDead();
        MouseGrab.wanted = lookCaptured;
        if (!on) {
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

        float eyeUp = player.getZ() * ViewCamera.LEVEL_HEIGHT + eyeHeight(player);
        WorldScene scene = WorldScene.next();
        scene.build(player, player.getX(), player.getY(), eyeUp, yaw, pitch);
        SpriteRenderer.instance.drawGeneric(scene);

        if (TRACE) {
            trace(player, scene, zoom);
        }
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
                        + " cursor (%d, %d) zoom %.2f offset (%.0f, %.0f) captured %b | %s",
                Math.toDegrees(yaw), Math.toDegrees(pitch), player.getX(), player.getY(), player.getZ(),
                facing.x, facing.y, traceAim.x, traceAim.y, player.isAiming(), traceMoveX, traceMoveY,
                cursorX, cursorY, zoom, IsoCamera.getOffX(), IsoCamera.getOffY(), lookCaptured, scene.describe()));
    }
}
