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
 * swaying with each step, kicking with each shot.
 *
 * Nothing the game simulates is touched. The game goes on posing the character as it does for
 * its own view, and the shot leaves where it always did. Only where the picture draws the
 * weapon and the arms that hold it changes: they are moved, as one rigid piece, from where the
 * game's animation has them to a place before the eye.
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
     * @param bothHands whether the left hand is on it too
     * @param swinging  whether the game is playing an attack with it
     * @param aim       0 at the hip, 1 with the sights up
     * @param lowered   0 held ready, 1 lowered to run
     * @param swayYaw   how far the weapon is behind a turn of the view to the right, radians
     * @param swayPitch the same for a turn upwards
     * @param bobX      where the step has it, to the right, tiles
     * @param bobY      and upwards
     * @param recoil    1 on the frame of a shot, falling to nought
     * @param dip       1 just after landing or crouching, falling to nought
     */
    record Held(String item, boolean firearm, boolean bothHands, boolean swinging, float aim, float lowered,
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

    private static float aim, lowered, swayYaw, swayPitch, bobAmount, bobPhase, recoil, dipTo, dip;
    private static int kicks;
    private static boolean placed, wasSneaking, wasFalling;
    private static float lastYaw, lastPitch, lastX, lastY;
    private static final float[] bob = new float[2];
    private static boolean gapSaid;

    /** Called by the game straight after it starts its own muzzle flash, which is where a firearm's shot is taken. */
    public static void shot() {
        if (held != null) {
            kicks++;
            MuzzleFlash.fire();
        }
    }

    /**
     * Works out how the weapon is held this frame. Logic thread.
     *
     * @param yaw      the clean view's yaw, radians
     * @param pitch    the clean view's pitch, radians
     * @param seconds  the length of the frame
     * @param sightsUp whether a firearm's sights are up
     */
    static void update(IsoPlayer player, float yaw, float pitch, float seconds, boolean sightsUp) {
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
        lastYaw = yaw;
        lastPitch = pitch;
        lastX = x;
        lastY = y;
        wasSneaking = sneaking;
        wasFalling = falling;
        held = new Held(item.getFullType(), weapon.isAimedFirearm(), item == player.getSecondaryHandItem(),
                player.isPerformingAttackAnimation() || player.isShoving(), aim, lowered, swayYaw, swayPitch, bob[0], bob[1], recoil, dip);
    }

    /** Nothing is held up: the next weapon starts from the hip, still. */
    static void rest() {
        held = null;
        placed = false;
        aim = 0f;
        lowered = 0f;
        swayYaw = 0f;
        swayPitch = 0f;
        bobAmount = 0f;
        recoil = 0f;
        dipTo = 0f;
        dip = 0f;
        kicks = 0;
    }

    /** For the trace. */
    static String describe() {
        Held now = held;
        return now == null ? "held nothing" : String.format(Locale.ROOT, "held %s%s aim %.2f recoil %.2f bob phase %.2f at (%.3f, %.3f) lowered %.2f swinging %b",
                now.item, now.firearm ? " (firearm)" : "", now.aim, now.recoil, bobPhase, now.bobX, now.bobY, now.lowered, now.swinging);
    }

    /** How far apart the hip crosshair's ticks stand: 0 standing still, more moving and just after a shot. */
    static float spread() {
        Held now = held;
        return now == null ? 0f : Math.min(bobAmount, 1f) * 0.6f + Math.min(now.recoil, 1f);
    }

    // What follows is the render thread's.

    /**
     * Where a firearm is held, in tiles from the eye: the line of its barrel to the right and
     * up, and its grip ahead. At the hip, and with the sights up, where the barrel is under the
     * middle of the picture by about the height of the sights above it.
     */
    private static final float HIP_X = 0.17f, HIP_Y = -0.16f, HIP_AHEAD = 0.42f;
    private static final float AIMED_X = 0f, AIMED_Y = -0.06f, AIMED_AHEAD = 0.36f;
    /** Where the grip of something swung is held, and how it is tipped: up and across, towards the middle. */
    private static final float SWUNG_X = 0.22f, SWUNG_Y = -0.25f, SWUNG_AHEAD = 0.4f;
    private static final float SWUNG_PITCH = (float) Math.toRadians(55), SWUNG_YAW = (float) Math.toRadians(-20), SWUNG_ROLL = (float) Math.toRadians(-10);
    /** Lowered to run: the muzzle down and across the body. */
    private static final float LOWERED_PITCH = (float) Math.toRadians(-35), LOWERED_YAW = (float) Math.toRadians(-25), LOWERED_DOWN = 0.07f;
    private static final float KICK_PITCH = (float) Math.toRadians(5), KICK_PITCH_AIMED = (float) Math.toRadians(2.5), KICK_BACK = 0.045f, KICK_UP = 0.008f;
    private static final float DIP_DOWN = 0.04f;
    /** With the sights up the step moves the weapon this much of what it does at the hip. */
    private static final float BOB_AIMED = 0.3f;
    /** How quickly the hold follows the game's pose of something swung, between swings. */
    private static final float FOLLOW_SECONDS = 0.12f;
    /** Along a firearm's mesh the barrel is y and its top is z; what is swung is taken to be made the same way. */
    private static final float[] MESH_ALONG = {0f, 1f, 0f}, MESH_TOP = {0f, 0f, 1f};

    private static final float[] origin = new float[3], forward = new float[3], top = new float[3], end = new float[3];
    private static final float[] along = new float[3], grip = new float[3];
    /** The pose something swung had before the swing, which is what is held still while it is swung. */
    private static final float[] restAt = new float[3], restForward = new float[3], restTop = new float[3];
    private static Object followedOf;
    private static long followedAt;
    /** A frame longer than this is a hitch, and the hold would jump to the game's pose over it. */
    private static final float LONGEST_FRAME = 0.25f;
    /** Where the muzzle of the weapon last placed is in the view, and whether it has one. */
    static final float[] muzzle = new float[3];
    static boolean hasMuzzle;

    /**
     * The matrix that draws the viewer's model with the weapon held before the eye.
     *
     * @param weapon     where the game has the weapon, in its character's own axes: the sixteen
     *                   floats of the part's transform, read by rows
     * @param finalScale the scale the game draws the character's parts at
     * @param instance   the weapon's model
     * @param scale      the model's units to tiles
     * @param out        the matrix, from the model's axes to the view's
     * @return false while the game has no pose for the weapon
     */
    static boolean place(float[] weapon, float finalScale, ModelInstance instance, Held how, float scale, float[] out) {
        origin[0] = weapon[3] * finalScale;
        origin[1] = weapon[7] * finalScale;
        origin[2] = weapon[11] * finalScale;
        ModelScript script = instance.modelScript;
        ModelAttachment barrel = how.firearm && script != null ? script.getAttachmentById(MUZZLE) : null;
        hasMuzzle = barrel != null;
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

        float[] by = origin, byForward = forward, byTop = top;
        if (!how.firearm) {
            // A swing is the game's animation of the weapon against the body. Held to the
            // weapon's pose of each frame it would not show at all, so while it plays the
            // hold stays on the pose from before it, and between swings it follows.
            long now = System.nanoTime();
            float seconds = Math.min((now - followedAt) / 1e9f, LONGEST_FRAME);
            followedAt = now;
            if (followedOf != instance) {
                followedOf = instance;
                System.arraycopy(origin, 0, restAt, 0, 3);
                System.arraycopy(forward, 0, restForward, 0, 3);
                System.arraycopy(top, 0, restTop, 0, 3);
            } else if (!how.swinging) {
                for (int i = 0; i < 3; i++) {
                    restAt[i] = ViewModelMath.ease(restAt[i], origin[i], seconds, FOLLOW_SECONDS);
                    restForward[i] = ViewModelMath.ease(restForward[i], forward[i], seconds, FOLLOW_SECONDS);
                    restTop[i] = ViewModelMath.ease(restTop[i], top[i], seconds, FOLLOW_SECONDS);
                }
            }
            by = restAt;
            byForward = restForward;
            byTop = restTop;
        } else {
            followedOf = null;
        }

        float atX, atY, ahead;
        if (how.firearm) {
            atX = HIP_X + (AIMED_X - HIP_X) * how.aim;
            atY = HIP_Y + (AIMED_Y - HIP_Y) * how.aim;
            ahead = HIP_AHEAD + (AIMED_AHEAD - HIP_AHEAD) * how.aim;
        } else {
            atX = SWUNG_X;
            atY = SWUNG_Y;
            ahead = SWUNG_AHEAD;
        }
        if (!ViewModelMath.hold(by, byForward, byTop, hasMuzzle ? end : null, scale, atX, atY, ahead, out)) {
            return false;
        }
        float steady = 1f - (1f - BOB_AIMED) * how.aim;
        float pitch = how.swayPitch + how.lowered * LOWERED_PITCH + how.recoil * (KICK_PITCH + (KICK_PITCH_AIMED - KICK_PITCH) * how.aim);
        float yaw = how.swayYaw + how.lowered * LOWERED_YAW;
        float roll = 0f;
        if (!how.firearm) {
            pitch += SWUNG_PITCH;
            yaw += SWUNG_YAW;
            roll = SWUNG_ROLL;
        }
        // The grip is where the weapon turns about: it is the hand.
        float gripX = atX, gripY = atY;
        if (hasMuzzle) {
            ViewModelMath.place(out, origin[0], origin[1], origin[2], grip);
            gripX = grip[0];
            gripY = grip[1];
        }
        ViewModelMath.turn(out, pitch, yaw, roll, gripX, gripY, -ahead,
                how.bobX * steady, how.bobY * steady + how.recoil * KICK_UP - how.lowered * LOWERED_DOWN - how.dip * DIP_DOWN, how.recoil * KICK_BACK);
        if (hasMuzzle) {
            ViewModelMath.place(out, end[0], end[1], end[2], muzzle);
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
