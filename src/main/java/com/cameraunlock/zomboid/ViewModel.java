package com.cameraunlock.zomboid;

import java.util.Locale;
import org.joml.Vector3f;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.scripting.objects.ModelAttachment;
import zombie.scripting.objects.ModelScript;

/**
 * The weapon in the viewer's hands as a first person game shows it: low and to the right at
 * the hip, raised to the middle of the picture with the sights up, lagging a turn of the view,
 * swaying with each step, kicking with each shot, and swung across the view when it is
 * something to hit with.
 *
 * Nothing the game simulates is touched. The game goes on posing the character as it does for
 * its own view, and the shot leaves where it always did. Only where the picture draws the
 * weapon and the hands on it changes: they are moved from where the game's animation has
 * them to a place before the eye, and the arms behind the hands are the picture's own, from
 * where a person's shoulders are. Each hand is turned on what it holds to meet its arm.
 *
 * The game's logic thread works out how the weapon is held this frame and the render thread
 * reads the last of it whole.
 */
public final class ViewModel {
    private ViewModel() {}

    /**
     * How the weapon is held this frame.
     *
     * @param item      the game's name for what is held
     * @param firearm   whether it is aimed down its sights, as against swung
     * @param bothHands whether it is held in the left hand too
     * @param swing     how far through a swing it is: 0 when it is not being swung, up to 1
     * @param aim       0 at the hip, 1 with the sights up
     * @param nearness  one over how far off the aim meets the world, in tiles: 0 with nothing in reach
     * @param lowered   0 held ready, 1 lowered to run
     * @param swayYaw   how far the weapon is behind a turn of the view to the right, radians
     * @param swayPitch the same for a turn upwards
     * @param bobX      where the step has it, to the right, tiles
     * @param bobY      and upwards
     * @param recoil    1 on the frame of a shot, falling to nought
     * @param dip       1 just after landing or crouching, falling to nought
     */
    record Held(String item, boolean firearm, boolean bothHands, float swing, float aim, float nearness, float lowered,
            float swayYaw, float swayPitch, float bobX, float bobY, float recoil, float dip) {}

    /** What is held, or null with no weapon in hand. */
    static volatile Held held;

    /** The muzzle's attachment in the game's model scripts. Its own z axis runs along the barrel. */
    private static final String MUZZLE = "muzzle";

    /** A kick is over, to within a twentieth, in three of these: 150 ms. */
    static final float RECOIL_SECONDS = 0.05f;
    /** Shots faster than the kick falls away do not stack the weapon into the viewer's face. */
    private static final float RECOIL_MOST = 1.5f;
    private static final float LOWER_SECONDS = 0.12f;
    private static final float SWAY_SECONDS = 0.08f;
    /** How far behind the view the weapon is for each radian a second the view turns. */
    private static final float SWAY_LAG = 0.03f;
    private static final float SWAY_MOST = (float) Math.toRadians(4);
    private static final float BOB_SECONDS = 0.15f;
    /** The speed of a walk, in tiles a second: the bob is whole at it. */
    private static final float WALK_SPEED = 1.6f;
    private static final float BOB_MOST = 1.6f;
    /** How far a step carries, in tiles. */
    private static final float STRIDE = 0.75f;
    /** Further than this in a second is not walking: the character was put somewhere else. */
    private static final float FASTEST = 12f;
    private static final float DIP_FALLS = 0.12f, DIP_SHOWS = 0.04f;
    /**
     * How long the picture's swing takes. The game says when an attack starts and not how far
     * through it is, so the swing is timed from the start.
     */
    static final float SWING_SECONDS = 0.5f;
    private static final float NOT_SWINGING = -1f;

    /** The barrel turns to a new aim point over this long: the point jumps from a doorpost to the far wall behind it. */
    private static final float CONVERGE_SECONDS = 0.1f;
    /** Nearer than this the aim point is taken to be this far off: a muzzle against a wall does not point across the view. */
    private static final float NEAREST_AIM = 1.5f;

    private static float nearness;
    private static float aim, lowered, swayYaw, swayPitch, bobAmount, bobPhase, recoil, dipTo, dip;
    private static float swingFor = NOT_SWINGING;
    private static int kicks;
    private static boolean placed, wasSneaking, wasFalling, wasAttacking;
    private static float lastYaw, lastPitch, lastX, lastY;
    private static final float[] bob = new float[2];
    private static boolean gapSaid;

    /**
     * A pump action is worked after every shot: the hand on the fore-end draws it back and
     * shoves it forward again. The game's own animation of that is made for its distant view
     * and turns the hand about; here the hand keeps its hold and slides.
     */
    private static final float PUMP_AFTER = 0.16f, PUMP_BACK = 0.14f, PUMP_FORWARD = 0.16f;
    private static final float NOT_PUMPING = -1f;
    private static boolean racked;
    private static float sinceShot = NOT_PUMPING;
    /** How far back the fore-end hand is, 0 to 1. Written by the logic thread, read where the weapon is placed. */
    static volatile float pumped;

    /** How far back the hand is at a time after the shot: still, then back, then forward. */
    static float pumpAt(float seconds) {
        float back = (seconds - PUMP_AFTER) / PUMP_BACK;
        float forward = (seconds - PUMP_AFTER - PUMP_BACK) / PUMP_FORWARD;
        return ViewModelMath.smooth(back) - ViewModelMath.smooth(forward);
    }

    /** Called by the game straight after it starts its own muzzle flash, which is where a firearm's shot is taken. */
    public static void shot() {
        if (held != null) {
            kicks++;
            MuzzleFlash.fire();
            if (racked) {
                sinceShot = 0f;
            }
        }
    }

    /**
     * Works out how the weapon is held this frame. Logic thread.
     *
     * @param yaw      the clean view's yaw, radians
     * @param pitch    the clean view's pitch, radians
     * @param seconds  the length of the frame
     * @param sightsUp whether a firearm's sights are up
     * @param aimedAt  how far off the aim meets the world, in tiles, or infinity
     */
    static void update(IsoPlayer player, float yaw, float pitch, float seconds, boolean sightsUp, float aimedAt) {
        InventoryItem item = player.getVehicle() == null ? player.getPrimaryHandItem() : null;
        if (!(item instanceof HandWeapon weapon)) {
            rest();
            return;
        }
        if (!gapSaid) {
            gapSaid = true;
            String gap = Agent.shotGap();
            if (gap != null) {
                Log.info("the held weapon has no recoil and no muzzle flash: " + gap);
            }
        }
        float x = player.getX(), y = player.getY();
        boolean sneaking = player.isSneaking(), falling = player.isFalling();
        if (!placed) {
            placed = true;
            lastYaw = yaw;
            lastPitch = pitch;
            lastX = x;
            lastY = y;
            wasSneaking = sneaking;
            wasFalling = falling;
        }
        aim = ViewModelMath.ease(aim, sightsUp ? 1f : 0f, seconds, FieldOfView.ZOOM_SECONDS);
        nearness = ViewModelMath.ease(nearness, 1f / Math.max(aimedAt, NEAREST_AIM), seconds, CONVERGE_SECONDS);
        lowered = ViewModelMath.ease(lowered, player.isSprinting() ? 1f : 0f, seconds, LOWER_SECONDS);
        float speed = 0f;
        if (seconds > 1e-4f) {
            float behindYaw = Math.clamp(-ViewModelMath.turned(lastYaw, yaw) / seconds * SWAY_LAG, -SWAY_MOST, SWAY_MOST);
            float behindPitch = Math.clamp(-(pitch - lastPitch) / seconds * SWAY_LAG, -SWAY_MOST, SWAY_MOST);
            swayYaw = ViewModelMath.ease(swayYaw, behindYaw, seconds, SWAY_SECONDS);
            swayPitch = ViewModelMath.ease(swayPitch, behindPitch, seconds, SWAY_SECONDS);
            speed = (float) Math.hypot(x - lastX, y - lastY) / seconds;
            if (speed > FASTEST) {
                speed = 0f;
            }
        }
        bobAmount = ViewModelMath.ease(bobAmount, Math.min(speed / WALK_SPEED, BOB_MOST), seconds, BOB_SECONDS);
        bobPhase = (float) ((bobPhase + speed * seconds / STRIDE * Math.PI) % (2 * Math.PI));
        ViewModelMath.bob(bobPhase, bobAmount, bob);
        recoil = Math.min(ViewModelMath.ease(recoil, 0f, seconds, RECOIL_SECONDS) + kicks, RECOIL_MOST);
        kicks = 0;
        if (sneaking != wasSneaking || wasFalling && !falling) {
            dipTo = 1f;
        }
        dip = ViewModelMath.ease(dip, dipTo, seconds, DIP_SHOWS);
        dipTo = ViewModelMath.ease(dipTo, 0f, seconds, DIP_FALLS);
        boolean firearm = weapon.isAimedFirearm();
        // A swing once begun runs to its end, whenever the game's own animation stops: cut
        // off part way, what is swung would jump back to where it is held.
        boolean attacking = !firearm && (player.isPerformingAttackAnimation() || player.isShoving());
        if (attacking && !wasAttacking) {
            swingFor = 0f;
        } else if (swingFor != NOT_SWINGING) {
            swingFor += seconds;
            if (swingFor >= SWING_SECONDS) {
                swingFor = NOT_SWINGING;
            }
        }
        lastYaw = yaw;
        lastPitch = pitch;
        lastX = x;
        lastY = y;
        wasSneaking = sneaking;
        wasFalling = falling;
        wasAttacking = attacking;
        racked = firearm && weapon.isRackAfterShoot();
        if (sinceShot != NOT_PUMPING) {
            sinceShot += seconds;
            if (sinceShot > PUMP_AFTER + PUMP_BACK + PUMP_FORWARD) {
                sinceShot = NOT_PUMPING;
            }
        }
        pumped = sinceShot == NOT_PUMPING ? 0f : pumpAt(sinceShot);
        held = new Held(item.getFullType(), firearm, item == player.getSecondaryHandItem(),
                swingFor == NOT_SWINGING ? 0f : swingFor / SWING_SECONDS, aim, nearness, lowered, swayYaw, swayPitch, bob[0], bob[1], recoil, dip);
    }

    /** Nothing is held up: the next weapon starts from the hip, still. */
    static void rest() {
        sinceShot = NOT_PUMPING;
        pumped = 0f;
        held = null;
        placed = false;
        aim = 0f;
        nearness = 0f;
        lowered = 0f;
        swayYaw = 0f;
        swayPitch = 0f;
        bobAmount = 0f;
        recoil = 0f;
        dipTo = 0f;
        dip = 0f;
        kicks = 0;
        swingFor = NOT_SWINGING;
        wasAttacking = false;
    }

    /** For the trace. */
    static String describe() {
        Held now = held;
        return now == null ? "held nothing" : String.format(Locale.ROOT, "held %s%s%s aim %.2f recoil %.2f bob phase %.2f at (%.3f, %.3f) lowered %.2f swing %.2f, %s",
                now.item, now.firearm ? " (firearm)" : "", now.bothHands ? " in both hands" : "", now.aim, now.recoil, bobPhase, now.bobX, now.bobY,
                now.lowered, now.swing, Characters.describeArms());
    }

    /** How far apart the hip crosshair's ticks stand: 0 standing still, more moving and just after a shot. */
    static float spread() {
        Held now = held;
        return now == null ? 0f : Math.min(bobAmount, 1f) * 0.6f + Math.min(now.recoil, 1f);
    }

    // What follows is the render thread's.

    /**
     * Where a person's shoulders are from the eye, in the view's axes, which are
     * ViewModelMath's: x to the right, y up, z towards the viewer, in tiles. Below the eye, to
     * either side of it and a little behind it.
     */
    static final float[] RIGHT_SHOULDER = {0.36f, -0.4f, 0.1f}, LEFT_SHOULDER = {-0.36f, -0.4f, 0.1f};
    /**
     * The way each elbow drops: down, and half as far out from the body. A forearm then comes
     * up to the grip from below and behind it, and leaves the picture by its foot.
     */
    static final float[] RIGHT_ELBOW = {0.5f, -1f, 0f}, LEFT_ELBOW = {-0.5f, -1f, 0f};

    /**
     * Where a firearm is held, in tiles from the eye: the line of its barrel to the right and
     * up, and its grip ahead. At the hip, and with the sights up, where the barrel is under the
     * middle of the picture by about the height of the sights above it.
     */
    private static final float HIP_X = 0.13f, HIP_Y = -0.14f, HIP_AHEAD = 0.27f;
    private static final float AIMED_X = 0f, AIMED_Y = -0.06f, AIMED_AHEAD = 0.36f;
    /** At the hip a firearm is tipped over towards the middle. */
    private static final float HIP_CANT = (float) Math.toRadians(8);
    /**
     * How far a lean along the aim may take the eye towards the weapon while the sights are
     * up: to the rear sight, which is over the grip, less the near plane the weapon is drawn
     * with and a margin.
     */
    static final float FORWARD_STOP = AIMED_AHEAD - 0.08f - 0.02f;
    /** Between the hip and the sights the weapon goes by way of a dip and a reach forward, at the most this far off the straight line. */
    private static final float RAISE_DIPS = 0.025f, RAISE_REACHES = 0.03f;
    /**
     * Where the grip of something swung is held, and how it is tipped: low and to the right,
     * forward and up, and a little across towards the middle.
     */
    private static final float SWUNG_X = 0.2f, SWUNG_Y = -0.22f, SWUNG_AHEAD = 0.38f;
    private static final float SWUNG_PITCH = (float) Math.toRadians(35), SWUNG_YAW = (float) Math.toRadians(-12), SWUNG_ROLL = (float) Math.toRadians(-10);
    /** Lowered to run: the muzzle down and across the body. */
    private static final float LOWERED_PITCH = (float) Math.toRadians(-35), LOWERED_YAW = (float) Math.toRadians(-25), LOWERED_DOWN = 0.07f;
    private static final float KICK_PITCH = (float) Math.toRadians(5), KICK_PITCH_AIMED = (float) Math.toRadians(2.5), KICK_BACK = 0.045f, KICK_UP = 0.008f;
    private static final float DIP_DOWN = 0.04f;
    /** With the sights up the step moves the weapon this much of what it does at the hip. */
    private static final float BOB_AIMED = 0.3f;
    /** Along a firearm's mesh the barrel is y and its top is z; what is swung is taken to be made the same way. */
    private static final float[] MESH_ALONG = {0f, 1f, 0f}, MESH_TOP = {0f, 0f, 1f};

    private static final float[] origin = new float[3], forward = new float[3], top = new float[3], end = new float[3];
    private static final float[] along = new float[3], grip = new float[3], swung = new float[6];
    /**
     * Where the left palm is held on a firearm: this far from the grip to the muzzle, and this
     * far under that line, in tiles. The game's animation moves the left hand about against
     * the weapon as the character turns, steps and settles, and an arm drawn to it there goes
     * wherever it goes.
     */
    private static final float FORE_END = 0.42f, FORE_END_BELOW = 0.035f;
    /** How much of the way from grip to muzzle the fore-end travels. */
    private static final float PUMP_TRAVEL = 0.12f;
    /** Where the left palm is held on the weapon last placed, in the view: only with a muzzle. */
    static final float[] foreEnd = new float[3];
    private static final float[] over = new float[3];
    /** Where the muzzle of the weapon last placed is in the view, and whether it has one. */
    static final float[] muzzle = new float[3];
    static boolean hasMuzzle;
    private static String noMuzzleSaidOf;

    /**
     * The matrix that draws the viewer's model with the weapon held before the eye. The weapon
     * is held by its pose of this frame, so nothing of the game's own animation of it against
     * the body shows: its swing is the picture's.
     *
     * @param weapon     where the game has the weapon, in its character's own axes: the sixteen
     *                   floats of the part's transform, read by rows
     * @param finalScale the scale the game draws the character's parts at
     * @param instance   the weapon's model
     * @param scale      the model's units to tiles
     * @param fromAim    the matrix from the aim's axes to those of the view the frame is drawn
     *                   from: the weapon is held before the aim's eye, not the picture's
     * @param out        the matrix, from the model's axes to the view's
     * @return false while the game has no pose for the weapon
     */
    static boolean place(float[] weapon, float finalScale, ModelInstance instance, Held how, float scale, float[] fromAim, float[] out) {
        origin[0] = weapon[3] * finalScale;
        origin[1] = weapon[7] * finalScale;
        origin[2] = weapon[11] * finalScale;
        ModelScript script = instance.modelScript;
        ModelAttachment barrel = how.firearm && script != null ? script.getAttachmentById(MUZZLE) : null;
        hasMuzzle = barrel != null;
        if (how.firearm && !hasMuzzle && !how.item.equals(noMuzzleSaidOf)) {
            noMuzzleSaidOf = how.item;
            Log.info("the model of " + how.item + " has no attachment called " + MUZZLE + ": it is held by its grip and its shots have no flash");
        }
        if (hasMuzzle) {
            Vector3f offset = barrel.getOffset(), rotate = barrel.getRotate();
            // The attachment is turned about x, then y, then z, and its own z is the barrel:
            // only the first two move that axis.
            double aboutX = Math.toRadians(rotate.x), aboutY = Math.toRadians(rotate.y);
            along[0] = (float) Math.sin(aboutY);
            along[1] = (float) (-Math.sin(aboutX) * Math.cos(aboutY));
            along[2] = (float) (Math.cos(aboutX) * Math.cos(aboutY));
            turned(weapon, along, forward);
            end[0] = (weapon[0] * offset.x + weapon[1] * offset.y + weapon[2] * offset.z + weapon[3]) * finalScale;
            end[1] = (weapon[4] * offset.x + weapon[5] * offset.y + weapon[6] * offset.z + weapon[7]) * finalScale;
            end[2] = (weapon[8] * offset.x + weapon[9] * offset.y + weapon[10] * offset.z + weapon[11]) * finalScale;
        } else {
            turned(weapon, MESH_ALONG, forward);
        }
        turned(weapon, MESH_TOP, top);

        float atX, atY, ahead;
        float pitch = how.swayPitch + how.lowered * LOWERED_PITCH + how.recoil * (KICK_PITCH + (KICK_PITCH_AIMED - KICK_PITCH) * how.aim);
        float yaw = how.swayYaw + how.lowered * LOWERED_YAW;
        float roll;
        float moveX = 0f, moveY = 0f, moveBack = 0f;
        if (how.firearm) {
            float raised = ViewModelMath.smooth(how.aim), off = ViewModelMath.arc(raised);
            atX = HIP_X + (AIMED_X - HIP_X) * raised;
            atY = HIP_Y + (AIMED_Y - HIP_Y) * raised - RAISE_DIPS * off;
            ahead = HIP_AHEAD + (AIMED_AHEAD - HIP_AHEAD) * raised + RAISE_REACHES * off;
            // With the sights up the line of the sights runs along the aim. Off that place the
            // barrel is turned to point at where the aim meets the world, so a weapon at the
            // hip points at the reticle and not past it.
            yaw -= (float) Math.atan((atX - AIMED_X) * how.nearness);
            pitch -= (float) Math.atan((atY - AIMED_Y) * how.nearness);
            roll = HIP_CANT * (1f - raised);
        } else {
            atX = SWUNG_X;
            atY = SWUNG_Y;
            ahead = SWUNG_AHEAD;
            ViewModelMath.swing(how.swing, swung);
            pitch += SWUNG_PITCH + swung[0];
            yaw += SWUNG_YAW + swung[1];
            roll = SWUNG_ROLL + swung[2];
            moveX = swung[3];
            moveY = swung[4];
            moveBack = swung[5];
        }
        if (!ViewModelMath.hold(origin, forward, top, hasMuzzle ? end : null, scale, atX, atY, ahead, out)) {
            return false;
        }
        float steady = 1f - (1f - BOB_AIMED) * how.aim;
        // The grip is where the weapon turns about: it is the hand.
        float gripX = atX, gripY = atY;
        if (hasMuzzle) {
            ViewModelMath.place(out, origin[0], origin[1], origin[2], grip);
            gripX = grip[0];
            gripY = grip[1];
        }
        ViewModelMath.turn(out, pitch, yaw, roll, gripX, gripY, -ahead,
                moveX + how.bobX * steady, moveY + how.bobY * steady + how.recoil * KICK_UP - how.lowered * LOWERED_DOWN - how.dip * DIP_DOWN,
                moveBack + how.recoil * KICK_BACK);
        ViewModelMath.onto(fromAim, out);
        if (hasMuzzle) {
            ViewModelMath.place(out, end[0], end[1], end[2], muzzle);
            float along = FORE_END - PUMP_TRAVEL * pumped;
            ViewModelMath.place(out, origin[0] + (end[0] - origin[0]) * along, origin[1] + (end[1] - origin[1]) * along,
                    origin[2] + (end[2] - origin[2]) * along, foreEnd);
            ViewModelMath.turned(out, top, over);
            float length = (float) Math.sqrt(over[0] * over[0] + over[1] * over[1] + over[2] * over[2]);
            for (int axis = 0; axis < 3; axis++) {
                foreEnd[axis] -= over[axis] / length * FORE_END_BELOW;
            }
        }
        return true;
    }

    /** A direction of the weapon's own, in its character's axes. */
    private static void turned(float[] weapon, float[] direction, float[] out) {
        out[0] = weapon[0] * direction[0] + weapon[1] * direction[1] + weapon[2] * direction[2];
        out[1] = weapon[4] * direction[0] + weapon[5] * direction[1] + weapon[6] * direction[2];
        out[2] = weapon[8] * direction[0] + weapon[9] * direction[1] + weapon[10] * direction[2];
    }
}
