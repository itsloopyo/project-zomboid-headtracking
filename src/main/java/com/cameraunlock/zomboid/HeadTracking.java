package com.cameraunlock.zomboid;

import java.util.Locale;
import zombie.input.GameKeyboard;

/**
 * Head tracking for the first-person view: the tracker's pose, the hotkeys,
 * and the one place the pose is converted to the view's axes.
 *
 * Only the picture is turned and moved. The view direction the mouse sets,
 * which the game's aim, movement and facing follow, is never written here.
 *
 * Everything runs on the game's logic thread except the receiver's own thread.
 */
final class HeadTracking {
    private enum Mode {
        FULL("rotation and position", true, true),
        ROTATION_ONLY("rotation only", true, false),
        POSITION_ONLY("position only", false, true);

        final String label;
        final boolean rotation;
        final boolean position;

        Mode(String label, boolean rotation, boolean position) {
            this.label = label;
            this.rotation = rotation;
            this.position = position;
        }
    }

    // The key codes the game's keyboard uses.
    private static final int KEY_END = 207, KEY_PAGE_UP = 201, KEY_PAGE_DOWN = 209;
    private static final int KEY_Y = 21, KEY_G = 34, KEY_H = 35;
    private static final int KEY_LEFT_CONTROL = 29, KEY_RIGHT_CONTROL = 157, KEY_LEFT_SHIFT = 42, KEY_RIGHT_SHIFT = 54;

    /** A frame longer than this is a hitch, and taking it whole would snap the smoothing to its target. */
    private static final float MAX_FRAME_SECONDS = 0.25f;
    /**
     * A lean held right at a wall's distance crosses in and out of contact on the tracker's
     * noise, as often as every frame, and each log line is a write to disk.
     */
    private static final long CONTACT_LOG_INTERVAL_NANOS = 1_000_000_000L;

    private static final OpenTrackReceiver receiver =
            new OpenTrackReceiver(OpenTrackReceiver.parsePort(System.getProperty(OpenTrackReceiver.PORT_PROPERTY)));
    private static final PoseFilter filter = new PoseFilter();
    private static final LeanClamp clamp = new LeanClamp();
    private static final float[] lean = new float[3];

    /** Where this frame's lean starts and the map it is traced through. */
    private static WorldQuery leanWorld;
    private static float leanEyeX, leanEyeY;
    private static final LeanClamp.Room roomForTheLean = (directionX, directionY, directionUp, reach) ->
            leanWorld.roomForTheEye(leanEyeX, leanEyeY, directionX, directionY, reach);

    private static boolean enabled = true;
    private static Mode mode = Mode.FULL;
    private static boolean worldYaw = true;
    private static long lastFrameNanos;
    private static boolean applied;
    private static boolean contactLogged;
    private static long contactLoggedNanos = System.nanoTime() - CONTACT_LOG_INTERVAL_NANOS;
    private static float leanAsked;

    private HeadTracking() {}

    static void start() {
        String lightMultiplier = System.getProperty(HeadFollowLight.PROPERTY);
        if (lightMultiplier != null) {
            HeadFollowLight.setMultiplier(lightMultiplier);
        }
        Log.info("a carried light turns " + HeadFollowLight.multiplier() + " times as far as the head");
        receiver.start();
    }

    static boolean applied() {
        return applied;
    }

    static void pollHotkeys() {
        boolean chord = (GameKeyboard.isKeyDown(KEY_LEFT_CONTROL) || GameKeyboard.isKeyDown(KEY_RIGHT_CONTROL))
                && (GameKeyboard.isKeyDown(KEY_LEFT_SHIFT) || GameKeyboard.isKeyDown(KEY_RIGHT_SHIFT));
        if (GameKeyboard.isKeyPressed(KEY_END) || chord && GameKeyboard.isKeyPressed(KEY_Y)) {
            toggleEnabled();
        }
        if (GameKeyboard.isKeyPressed(KEY_PAGE_UP) || chord && GameKeyboard.isKeyPressed(KEY_G)) {
            cycleMode();
        }
        if (GameKeyboard.isKeyPressed(KEY_PAGE_DOWN) || chord && GameKeyboard.isKeyPressed(KEY_H)) {
            toggleWorldYaw();
        }
    }

    static void toggleEnabled() {
        enabled = !enabled;
        Log.info("head tracking " + (enabled ? "on" : "off"));
    }

    static void cycleMode() {
        mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
        Log.info("head tracking mode: " + mode.label);
    }

    static void toggleWorldYaw() {
        worldYaw = !worldYaw;
        Log.info("head yaw turns about " + (worldYaw ? "straight up" : "the view's own up"));
    }

    /**
     * Writes the view the frame is drawn from: the clean view, turned and
     * moved by the head when tracking applies.
     *
     * @param inGameplay the first-person view is on, the game is running and the character is alive
     * @param cleanYaw   the yaw the clean view was built from, which the lean follows
     */
    static void view(boolean inGameplay, ViewCamera.Pose clean, float cleanYaw, WorldQuery world, ViewCamera.Pose out) {
        long now = System.nanoTime();
        float delta = Math.min((now - lastFrameNanos) / 1e9f, MAX_FRAME_SECONDS);
        lastFrameNanos = now;

        out.set(clean);
        lean[0] = 0f;
        lean[1] = 0f;
        lean[2] = 0f;
        leanAsked = 0f;
        OpenTrackReceiver.Sample sample = receiver.latest();
        if (!inGameplay || !enabled || sample == null) {
            rest();
            return;
        }
        applied = true;
        // A tracker that has gone quiet leaves the last pose in place: the filter is fed the
        // same sample and holds it.
        filter.update(sample, receiver.isRemote(), delta);

        if (mode.rotation) {
            // The tracker's senses, as its apps send them: yaw positive to the right, pitch
            // positive up, roll positive tilting the head to the left. They are this camera's too.
            float yaw = (float) Math.toRadians(filter.yaw());
            float pitch = (float) Math.toRadians(filter.pitch());
            float roll = (float) Math.toRadians(filter.roll());
            ViewCamera.turn(out, yaw, pitch, roll, worldYaw);
            HeadFollowLight.follow(clean, yaw, pitch, roll, worldYaw);
        } else {
            HeadFollowLight.release();
        }
        if (!mode.position) {
            clamp.reset();
            return;
        }
        // The tracker sends x positive to the head's left and z positive away from the screen.
        // Both are turned round here, after the limits, so the larger forward allowance stays
        // on the forward lean. The lean follows where the body faces and stays level: looking
        // down does not turn a lean forward into a lean into the floor.
        float right = -filter.x();
        float up = filter.y();
        float forward = -filter.z();
        float cos = (float) Math.cos(cleanYaw);
        float sin = (float) Math.sin(cleanYaw);
        lean[0] = -sin * right + cos * forward;
        lean[1] = cos * right + sin * forward;
        lean[2] = up;
        leanAsked = (float) Math.sqrt(right * right + up * up + forward * forward);
        leanWorld = world;
        leanEyeX = clean.eyeX;
        leanEyeY = clean.eyeY;
        clamp.apply(lean, delta, roomForTheLean);
        if (clamp.inContact() != contactLogged && now - contactLoggedNanos >= CONTACT_LOG_INTERVAL_NANOS) {
            contactLogged = clamp.inContact();
            contactLoggedNanos = now;
            Log.info(String.format(Locale.ROOT, contactLogged
                    ? "lean held off a wall: asked for %.3f, room for %.3f, eye at (%.2f, %.2f)"
                    : "lean clear of the wall again: asked for %.3f, given %.3f, eye at (%.2f, %.2f)",
                    leanAsked, length(lean), clean.eyeX, clean.eyeY));
        }
        out.eyeX += lean[0];
        out.eyeY += lean[1];
        out.eyeUp += lean[2];
    }

    /**
     * Tracking does not apply this frame. The next time it does it starts from the tracker's
     * pose then, with no wall from before the break rationing the lean, and a sample interval
     * measured across the break is not taken for the tracker's rate.
     */
    static void rest() {
        applied = false;
        filter.reset();
        clamp.reset();
        contactLogged = false;
        HeadFollowLight.release();
    }

    private static float length(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    /** One line with every term of the frame's pose, for the trace. */
    static String describe() {
        return String.format(Locale.ROOT,
                "tracking %s mode %s yaw about %s fresh %b remote %b head (yaw %.2f pitch %.2f roll %.2f)"
                        + " tracker position (%.3f, %.3f, %.3f) lean asked %.3f given world (%.3f, %.3f, %.3f) wall %b %s",
                applied ? "applied" : enabled ? "idle" : "off", mode.label, worldYaw ? "world up" : "view up",
                receiver.isFresh(), receiver.isRemote(), filter.yaw(), filter.pitch(), filter.roll(),
                filter.x(), filter.y(), filter.z(), leanAsked, lean[0], lean[1], lean[2], clamp.inContact(),
                HeadFollowLight.describe());
    }
}
