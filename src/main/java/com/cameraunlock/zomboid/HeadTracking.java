package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.util.Locale;

/**
 * One view's head tracking. cameraunlock-core has the tracker's pose and everything done to it:
 * receiving, interpolation, smoothing, the limits, the zoom, the aim modes, the lean's hold off
 * a wall and its handover to the weapon. What is here is this game's: the conversion of the pose
 * to this view's axes and the map the lean is measured against. Whether tracking is on, its
 * modes and its settings are every view's alike, and Tracking's.
 *
 * Only the picture is turned and moved. The view direction the mouse or the stick sets, which
 * the game's aim, movement and facing follow, is never written here.
 *
 * Everything runs on the game's logic thread.
 */
final class HeadTracking {
    /** Which of core's views this is: the player's number at this machine. */
    private final int index;
    private final FieldOfView fieldOfView;
    private final HeadFollowLight light;

    HeadTracking(int index, FieldOfView fieldOfView, HeadFollowLight light) {
        this.index = index;
        this.fieldOfView = fieldOfView;
        this.light = light;
    }

    /** A frame longer than this is a hitch, and core takes it as this long: whole, it would snap the smoothing to its target. */
    private static final float MAX_FRAME_SECONDS = 0.25f;
    /** A tracker that keeps losing the face comes and goes many times a minute: the log says so at most this often. */
    private static final long FRESH_SAID_EVERY_NANOS = 10_000_000_000L;
    /**
     * A lean held right at a wall's distance crosses in and out of contact on the tracker's
     * noise, as often as every frame, and each log line is a write to disk.
     */
    private static final long CONTACT_LOG_INTERVAL_NANOS = 1_000_000_000L;

    private final CameraUnlock.FrameInput input = new CameraUnlock.FrameInput();
    private final CameraUnlock.Frame frame = new CameraUnlock.Frame();
    private final CameraUnlock.Obstruction found = new CameraUnlock.Obstruction();
    private final CameraUnlock.Lean lean = new CameraUnlock.Lean();

    /** The view rides a vehicle, which turns and tilts under it: the head's yaw is about the view's own up there, whatever is saved. */
    private boolean seated;
    /** Whether the view is the character's head's, led by the game's animation (BodyLed), which can have it on its back or its side. */
    private boolean bodyLed;

    private boolean applied;
    private boolean fresh;
    /** Whether the tracker's poses arriving or not was last said as it is now, when, and how many times it changed unsaid since. */
    private boolean saidFresh;
    private long freshSaidAt = Long.MIN_VALUE / 2;
    private int freshChangesUnsaid;
    private boolean contactLogged;
    private long contactLoggedNanos = System.nanoTime() - CONTACT_LOG_INTERVAL_NANOS;
    private boolean unmeasuredLogged;
    /** The head's yaw, pitch and roll this frame in radians, in the tracker's senses, while headTurned. */
    private final float[] headTurn = new float[3];
    private boolean headTurned;
    private volatile float zoomFactor = 1f;


    /** The factor core scaled the head's turn by on the newest frame: 1 while nothing is zoomed. */
    float zoomFactor() {
        return zoomFactor;
    }

    /**
     * How far the head is turned this frame, for the character's own head: yaw to the right,
     * pitch up and roll to the left, in radians. Null while tracking turns nothing.
     */
    float[] headTurn() {
        return headTurned ? headTurn : null;
    }

    /**
     * How far the weapon and the shot are moved with the eye this frame, in world axes: the
     * part of the lean across the aim, while the sights are up in a mode that keeps the eye
     * on them. Nought at the hip, in free look and whenever no lean is applied.
     */
    float[] carried() {
        return lean.rig;
    }

    /** How far the eye alone is moved this frame, in world axes: the part of the lean the weapon is not moved with. */
    float[] eyeAlone() {
        return lean.camera;
    }

    /**
     * The matrix that takes the tracker's position to a lean in the world's axes, three rows of
     * three. The tracker sends x positive to the head's left and z positive away from the
     * screen, and both are turned round here, after core's limits, so the larger forward
     * allowance stays on the forward lean. The lean follows where the body faces and stays
     * level: looking down does not turn a lean forward into a lean into the floor.
     *
     * From a vehicle's seat the lean goes with the vehicle as it lies.
     *
     * @param cleanYaw the yaw the clean view was built from
     * @param tilt     how the vehicle sat in is turned from level, as Cabins.tilt gives it, or null on foot
     */
    static void trackerToWorld(float cleanYaw, float[] tilt, float[] matrix) {
        float cos = (float) Math.cos(cleanYaw);
        float sin = (float) Math.sin(cleanYaw);
        // right = -x and forward = -z, then world x = -sin * right + cos * forward and world y = cos * right + sin * forward.
        matrix[0] = sin;
        matrix[1] = 0f;
        matrix[2] = -cos;
        matrix[3] = -cos;
        matrix[4] = 0f;
        matrix[5] = -sin;
        matrix[6] = 0f;
        matrix[7] = 1f;
        matrix[8] = 0f;
        if (tilt != null) {
            for (int column = 0; column < 3; column++) {
                float x = matrix[column], y = matrix[3 + column], up = matrix[6 + column];
                matrix[column] = tilt[0] * x + tilt[1] * y + tilt[2] * up;
                matrix[3 + column] = tilt[3] * x + tilt[4] * y + tilt[5] * up;
                matrix[6 + column] = tilt[6] * x + tilt[7] * y + tilt[8] * up;
            }
        }
    }

    /**
     * Turns a view by the head's angles as core hands them over, in degrees. The tracker's
     * senses, as its apps send them, are this camera's too: yaw positive to the right, pitch
     * positive up, roll positive tilting the head to the left.
     */
    static void turn(ViewCamera.Pose view, float yawDegrees, float pitchDegrees, float rollDegrees, boolean worldYaw) {
        ViewCamera.turn(view, (float) Math.toRadians(yawDegrees), (float) Math.toRadians(pitchDegrees),
                (float) Math.toRadians(rollDegrees), worldYaw);
    }

    /**
     * The map's answer in core's terms. Core asks where the surface is and holds the eye the
     * margin short of it. The map answers how far the eye itself can go, having swept three rays
     * and allowed for a wall met at a slant, so the surface core is told of is the margin beyond
     * that.
     *
     * @param room  how far the eye can go along the lean, capped at reach
     * @param reach the lean's own length
     */
    static void report(float room, float reach, float margin, CameraUnlock.Obstruction found) {
        found.queried = true;
        found.blocked = room < reach;
        found.distance = room + margin;
    }

    private void ask(boolean active, int more) {
        input.flags = CameraUnlock.FRAME_CLOCK | (active ? CameraUnlock.FRAME_ACTIVE : 0) | more;
        input.deltaSeconds = MAX_FRAME_SECONDS;
        input.tanHalfFov = fieldOfView.tanHalfVertical();
        input.tanHalfFovBase = FieldOfView.setTanHalfVertical();
        Tracking.frame(index, input, frame);
        zoomFactor = frame.zoomFactor;
        boolean nowFresh = (frame.flags & CameraUnlock.STATE_FRESH) != 0;
        if (active && nowFresh != fresh && (frame.flags & CameraUnlock.STATE_POSE) != 0) {
            fresh = nowFresh;
            freshChangesUnsaid++;
        }
        long now = System.nanoTime();
        if (freshChangesUnsaid > 0 && now - freshSaidAt >= FRESH_SAID_EVERY_NANOS) {
            int besides = fresh != saidFresh ? freshChangesUnsaid - 1 : freshChangesUnsaid;
            if (fresh != saidFresh || besides > 0) {
                String changes = besides > 0 ? " (it changed " + besides + " more times since the last of these lines)" : "";
                Log.info((fresh ? "the tracker's poses are arriving"
                        : "no pose taken from the tracker for " + Tracking.settings().dataFreshnessMs + " ms, its last one eases out of the view") + changes);
                saidFresh = fresh;
                freshSaidAt = now;
            }
            freshChangesUnsaid = 0;
        }
    }

    /** The head's turn as the character's head and the carried light take it, from the frame core answered. */
    private void takeTheTurn(ViewCamera.Pose clean) {
        headTurned = (frame.flags & CameraUnlock.STATE_ROTATION) != 0;
        if (!headTurned) {
            light.release();
            return;
        }
        headTurn[0] = (float) Math.toRadians(frame.yaw);
        headTurn[1] = (float) Math.toRadians(frame.pitch);
        headTurn[2] = (float) Math.toRadians(frame.roll);
        if (clean == null) {
            light.release();
        } else {
            light.follow(clean, (float) Math.toRadians(frame.lightYaw), (float) Math.toRadians(frame.lightPitch),
                    (float) Math.toRadians(frame.lightRoll), yawAboutWorldUp());
        }
    }

    /**
     * The game's own view, which the mod has no camera in: the head's turn is kept up for
     * the character's head and nothing else is done with the pose.
     *
     * @param inGameplay the game is running and the character is alive
     */
    void follow(boolean inGameplay) {
        ride(false);
        ask(inGameplay && Tracking.enabled(), 0);
        if ((frame.flags & CameraUnlock.STATE_POSE) == 0) {
            atRest();
            return;
        }
        applied = false;
        noLean();
        takeTheTurn(null);
    }

    /**
     * Writes the view the frame is drawn from: the clean view, turned and
     * moved by the head when tracking applies.
     *
     * @param inGameplay the first-person view is on, the game is running and the character is alive, or dying with the eye still led by its head
     * @param cleanYaw   the yaw the clean view was built from, which the lean follows
     * @param tilt       how the clean view is turned from level at that yaw: by the vehicle sat in, or by the head while it leads, or null for neither
     * @param sightsUp   whether a firearm's sights are up this frame
     * @param rigAvailable whether the weapon can be moved with the eye: on foot, and not where players fight
     * @param forwardStop with the sights up, how far along the aim a lean may take the eye
     * @param inVehicle  whether the view is from a vehicle's seat
     * @param bodyLed    whether the view is led by the character's head (BodyLed): yaw turns about the view's own up
     */
    void view(boolean inGameplay, ViewCamera.Pose clean, float cleanYaw, float[] tilt, WorldQuery world, boolean sightsUp,
            boolean rigAvailable, float forwardStop, boolean inVehicle, boolean bodyLed, ViewCamera.Pose out) {
        ride(inVehicle);
        this.bodyLed = bodyLed;
        out.set(clean);
        input.forwardStop = forwardStop;
        input.aimForward[0] = clean.forwardX;
        input.aimForward[1] = clean.forwardY;
        input.aimForward[2] = clean.forwardUp;
        trackerToWorld(cleanYaw, tilt, input.trackerToWorld);
        ask(inGameplay && Tracking.enabled(), CameraUnlock.FRAME_LEAN | (sightsUp ? CameraUnlock.FRAME_AIMING : 0)
                | (rigAvailable ? CameraUnlock.FRAME_RIG_AVAILABLE : 0));
        if ((frame.flags & CameraUnlock.STATE_POSE) == 0) {
            atRest();
            return;
        }
        applied = true;
        if ((frame.flags & CameraUnlock.STATE_ROTATION) != 0) {
            turn(out, frame.yaw, frame.pitch, frame.roll, yawAboutWorldUp());
        }
        takeTheTurn(clean);
        if ((frame.flags & CameraUnlock.STATE_LEAN) == 0) {
            noLean();
            return;
        }

        // With no map to ask, the query could not run, and core is told so: it passes the lean
        // unheld and says it did.
        found.queried = false;
        found.blocked = false;
        if ((frame.flags & CameraUnlock.STATE_LEAN_QUERY) != 0 && world.mapped()) {
            float margin = Tracking.settings().collisionMargin;
            float reach = frame.queryReach - margin;
            report(world.roomForTheEye(clean.eyeX, clean.eyeY, frame.queryDirection[0], frame.queryDirection[1], reach, margin),
                    reach, margin, found);
        }
        Tracking.lean(index, found, lean);
        out.eyeX += lean.camera[0] + lean.rig[0];
        out.eyeY += lean.camera[1] + lean.rig[1];
        out.eyeUp += lean.camera[2] + lean.rig[2];

        boolean unmeasured = (lean.flags & CameraUnlock.LEAN_QUERY_FAILED) != 0;
        if (unmeasured != unmeasuredLogged) {
            unmeasuredLogged = unmeasured;
            Log.info(unmeasured ? "the lean is not held off walls: there is no map to measure it against"
                    : "the lean is held off walls again");
        }
        boolean contact = (lean.flags & CameraUnlock.LEAN_CONTACT) != 0;
        long now = System.nanoTime();
        if (DevBuild.ON && contact != contactLogged && now - contactLoggedNanos >= CONTACT_LOG_INTERVAL_NANOS) {
            contactLogged = contact;
            contactLoggedNanos = now;
            Log.info(String.format(Locale.ROOT, contact
                    ? "lean held off a wall: asked for %.3f, room for %.3f, eye at (%.2f, %.2f)"
                    : "lean clear of the wall again: asked for %.3f, given %.3f, eye at (%.2f, %.2f)",
                    lean.asked, lean.given, clean.eyeX, clean.eyeY));
        }
    }

    /**
     * Tracking does not apply from here: a save was left. Core drops its state, so the next time
     * tracking applies it starts from the tracker's pose then, with no wall from before the break
     * rationing the lean.
     */
    void rest() {
        ride(false);
        ask(false, 0);
        atRest();
    }

    private void atRest() {
        applied = false;
        headTurned = false;
        contactLogged = false;
        light.release();
        noLean();
    }

    private void noLean() {
        lean.flags = 0;
        lean.asked = 0f;
        lean.given = 0f;
        for (int i = 0; i < 3; i++) {
            lean.camera[i] = 0f;
            lean.rig[i] = 0f;
        }
    }

    private boolean yawAboutWorldUp() {
        return Tracking.worldYaw() && !seated && !bodyLed;
    }

    private void ride(boolean inVehicle) {
        Tracking.rides(index, inVehicle);
        if (inVehicle == seated) {
            return;
        }
        seated = inVehicle;
        if (DevBuild.ON && Tracking.worldYaw()) {
            Log.info(seated ? "in a vehicle's seat: head yaw turns about the view's own up" : "out of the vehicle: head yaw turns about straight up again");
        }
    }

    /** One line with every term of the frame's pose, for the trace. */
    String describe() {
        return String.format(Locale.ROOT,
                "tracking %s mode %s yaw about %s fresh %b share %.3f remote %b head (yaw %.2f pitch %.2f roll %.2f)"
                        + " tracker position (%.3f, %.3f, %.3f) lean asked %.3f given %.3f wall %b"
                        + " %s stock share %.3f zoom %.4f view has (%.3f, %.3f, %.3f) weapon has (%.3f, %.3f, %.3f) %s",
                applied ? "applied" : Tracking.enabled() ? "idle" : "off", Tracking.trackingModeLabel(), yawAboutWorldUp() ? "world up" : "view up",
                (frame.flags & CameraUnlock.STATE_FRESH) != 0, frame.trackerShare, (frame.flags & CameraUnlock.STATE_REMOTE) != 0,
                frame.headYaw, frame.headPitch, frame.headRoll, frame.headX, frame.headY, frame.headZ, lean.asked, lean.given,
                (lean.flags & CameraUnlock.LEAN_CONTACT) != 0, Tracking.aimModeLabel(), frame.poseShare, frame.zoomFactor,
                lean.camera[0], lean.camera[1], lean.camera[2], lean.rig[0], lean.rig[1], lean.rig[2], light.describe());
    }
}
