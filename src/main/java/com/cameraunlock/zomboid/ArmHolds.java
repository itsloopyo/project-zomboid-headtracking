package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * Where the mod's own hands go on what the viewer holds, and the arms behind them: the right
 * hand round the grip of a firearm or the handle of something swung, and the left, on a
 * firearm held in both, under its fore-end. While the weapon's action is worked or it is
 * loaded, each hand goes to the part of the weapon that a hand works: the bolt's knob, the
 * slide, the magazine in its well, the port the rounds go in at.
 *
 * Everything is in the axes of the view the frame is drawn from, ViewModelMath's: x to the
 * right, y up, z towards the viewer.
 */
final class ArmHolds {
    private ArmHolds() {}

    /** Where a body's shoulders are from its eye, in metres: to the side, below, and behind. */
    static final float[] SHOULDER = {0.19f, -0.22f, 0.07f};
    /**
     * The same behind a long gun, the right's then the left's: the body is turned to the gun,
     * with its butt at the right shoulder and the left shoulder brought round to beside the
     * eye. No further: the arm ends at its shoulder, and a shoulder ahead of the eye comes
     * into the picture when the head is tipped down.
     */
    static final float[][] LONG_SHOULDERS = {{0.18f, -0.22f, 0.10f}, {-0.15f, -0.22f, -0.02f}};
    /** How near a long gun's grip the hand under its fore-end comes when the arm reaches no further along it, in metres. */
    static final float NEAREST_FORE = 0.16f;
    /**
     * How much of its whole length the arm to a fore-end is let reach. Short of straight its
     * elbow hangs under the line from the shoulder to the hand, which from the eye is under
     * the foot of the picture.
     */
    static final float REACH_EASY = 0.96f;
    /**
     * Where each forearm seems to come from, the right's then the left's, in metres from the
     * eye: below the foot of the picture and out to its side, so that an arm rises into the
     * picture from its lower corner and is seen along its length.
     */
    static final float[][] FROM = {{0.34f, -0.56f, 0.12f}, {-0.42f, -0.52f, -0.05f}};
    /** How far the line of the knuckles leans forward from a firearm's upright on the grip of a pistol and on the wrist of a stock, in radians. */
    static final float PISTOL_RAKE = (float) Math.toRadians(-6), STOCK_RAKE = (float) Math.toRadians(-15);
    /** How far above the middle of the game's hand, and ahead of it, a pistol is held, in metres. */
    static final float[] PISTOL_HIGHER = {0.006f, 0.004f};
    /** How far the far end of a handle leans from square to the fingers that hold it, in radians. */
    static final float HANDLE_SLANT = (float) Math.toRadians(18);
    /**
     * The hand under a fore-end is turned to meet its arm. Its palm faces the fore-end from
     * the side that lays the arm's line flat in it, from under it this much besides, in parts
     * of that, and from the side the arm comes from this much, so that the forearm is not
     * turned right over to bring the palm up. Its fingers run on from the arm's line, across
     * the fore-end, and lean forward along the barrel from square to it by no more than this,
     * in radians.
     */
    static final float PALM_UNDER = 0.6f, PALM_ASIDE = 0.35f, FINGERS_LEAN = (float) Math.toRadians(50);
    /** How far down a handle from the right fist the left one is, in metres. */
    static final float LOWER_FIST = 0.095f;
    /** Where the left hand is when it is off the weapon, fetching a round or a magazine: below the picture, in metres from the eye. */
    static final float[] AWAY = {-0.20f, -0.52f, -0.30f};

    /**
     * Where the parts of a firearm that a hand works are, in metres from the middle of its
     * grip: ahead along the barrel, up, and to the right. The bore is `bore` above the grip.
     * The bolt's knob turns up about the bore by `lift` radians and is drawn back by `travel`.
     */
    record Parts(float bore, float[] knob, float lift, float travel, float[] port, float[] well, float[] slide, float slideTravel) {}

    /** A long gun of no particular make, and a handgun. */
    static final Parts LONG = new Parts(0.062f, new float[] {0.046f, 0.036f, 0.06f}, (float) Math.toRadians(55), 0.09f, new float[] {0.186f, 0.029f, 0f},
            new float[] {0.11f, -0.05f, 0f}, new float[] {0.02f, 0.08f, 0f}, 0.05f);
    static final Parts HAND = new Parts(0.05f, new float[] {0f, 0.05f, 0.02f}, 0f, 0f, new float[] {0.02f, 0.045f, -0.02f},
            new float[] {-0.01f, -0.075f, 0f}, new float[] {-0.035f, 0.06f, 0f}, 0.03f);

    /**
     * The weapon as the view has it: where the hand holds it, where its parts are given from,
     * the way it points and the way its top faces, each of length one, where its fore-end is
     * now, where it is with the weapon held still and its fore-end forward, without the kick
     * of a shot, the sway of a step or a pump's fore-end drawn back, and its parts. A weapon
     * of the mod's own has its parts measured from its own origin. The game's are taken to lie
     * about the middle of the hand the game holds them in.
     */
    record Weapon(boolean firearm, boolean handgun, float[] grip, float[] origin, float[] pointing, float[] over, float[] foreEnd, float[] foreSteady, Parts parts) {}

    /**
     * What is being done to the weapon.
     *
     * @param aim    0 at the hip, 1 with the sights up: the first finger goes to the trigger
     * @param both   whether the left hand is on the weapon too
     * @param action how the weapon's action is worked and how it is loaded: one of ArmActs' kinds
     * @param cycled how long ago the shot was that the action is being worked after, in seconds, or less than nought
     * @param doing  what the holder is doing to it besides holding it: one of ArmActs' doings
     * @param done   for how long, in seconds
     * @param round  how far through loading one round the hand is, 0 to 1
     * @param pace   how long the game is taken to be over it, in seconds
     * @param seated how long ago the game finished loading a magazine, in seconds, or less than nought
     * @param clip   whether the weapon has a magazine in it
     * @param out    how long ago the magazine left the weapon while it is being loaded or unloaded, in seconds, or less than nought if none has
     * @param fetching how long a magazine has been on its way to the weapon, in seconds, or less than nought
     * @param fetchTakes how long the game is taken to be over that, in seconds
     */
    record Doing(float aim, boolean both, int action, float cycled, int doing, float done, float round, float pace, float seated,
            boolean clip, float out, float fetching, float fetchTakes) {}

    /** What the left hand carries to the weapon or away from it: nothing, a magazine, a shotgun's shell or a cartridge. */
    static final int NOTHING = 0, MAGAZINE = 1, SHELL = 2, CARTRIDGE = 3;

    /** What the left hand carries, and the matrix that puts it in the hand: its length along y, in metres, to the view. */
    static final class Carried {
        int what;
        final float[] at = new float[16];
    }

    /** A hand on something: where the middle of what it holds is, the way across it to its thumb's side, the way out of its palm, and how its fingers are bent. */
    private static final class Hold {
        final float[] at = new float[3], across = new float[3], out = new float[3];
        final float[] weights;
        /** Whether what is held is a handle, round about the line across the hand, which a fist may be anywhere round. */
        boolean round;

        Hold(ArmModel model) {
            weights = new float[model.poses.length];
        }

        /** A hold at a place, its palm facing `palm` and its fingers running the way of `fingers`, in a pose. */
        Hold set(float[] place, float[] palm, float[] fingers, boolean left, int pose) {
            System.arraycopy(place, 0, at, 0, 3);
            ArmPose.unit(palm[0], palm[1], palm[2], out);
            // The thumb's side is square to both, on the side that hand has its thumb.
            if (left) {
                ArmPose.cross(out, fingers, across);
            } else {
                ArmPose.cross(fingers, out, across);
            }
            Arrays.fill(weights, 0f);
            weights[pose] = 1f;
            return this;
        }

        /**
         * This hold taken `share` of the way to another. The hand is turned from the one way
         * round to the other by the one turn that does it, a share of that turn at a time: its
         * lines taken each a share of the way would pass through a hand facing nowhere a wrist goes.
         */
        Hold towards(Hold other, float share) {
            float[] from = frame(across, out), to = frame(other.across, other.out);
            // The turn from the one to the other, and the line it is about.
            float[] turn = new float[9];
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    turn[column * 3 + row] = to[row] * from[column] + to[3 + row] * from[3 + column] + to[6 + row] * from[6 + column];
                }
            }
            float cos = Math.clamp((turn[0] + turn[4] + turn[8] - 1f) / 2f, -1f, 1f);
            float[] about = {turn[5] - turn[7], turn[6] - turn[2], turn[1] - turn[3]};
            if (cos > -0.999f && ArmPose.unit(about[0], about[1], about[2], about)) {
                float[] part = new float[9];
                ArmPose.about(about, (float) Math.acos(cos) * share, part);
                float[] a = {from[0], from[1], from[2]}, o = {from[3], from[4], from[5]};
                turn(part, a, across);
                turn(part, o, out);
            } else if (cos <= -0.999f) {
                // Half a turn has no one line to be about: the lines go straight across.
                for (int axis = 0; axis < 3; axis++) {
                    across[axis] += (other.across[axis] - across[axis]) * share;
                    out[axis] += (other.out[axis] - out[axis]) * share;
                }
            }
            for (int axis = 0; axis < 3; axis++) {
                at[axis] += (other.at[axis] - at[axis]) * share;
            }
            for (int pose = 0; pose < weights.length; pose++) {
                weights[pose] += (other.weights[pose] - weights[pose]) * share;
            }
            round = share < 0.5f ? round : other.round;
            return this;
        }

        /** The hand's three lines, square to each other and of length one: across it, out of its palm, and the third. */
        static float[] frame(float[] across, float[] out) {
            float[] a = new float[3], o = new float[3], third = new float[3];
            ArmPose.unit(across[0], across[1], across[2], a);
            float along = ArmPose.dot(out, a);
            ArmPose.unit(out[0] - a[0] * along, out[1] - a[1] * along, out[2] - a[2] * along, o);
            ArmPose.cross(a, o, third);
            return new float[] {a[0], a[1], a[2], o[0], o[1], o[2], third[0], third[1], third[2]};
        }
    }

    /**
     * Where the left hand was last, kept by whoever places the arms from frame to frame, so
     * that a hand whose hold changes to one that is not beside it crosses over to it and is
     * not simply there. Holds are kept in the weapon's own axes: a hand on a fore-end moves
     * with the weapon and is not behind it.
     */
    static final class Settled {
        /** How long ago the frame before this was, in seconds. */
        float seconds;
        private Hold aimed, shown, from;
        private float crossed = 1f;
    }

    /** The weapon's own axes in the view, and where its parts are given from. */
    private static final class Axes {
        final float[] ahead, up = new float[3], right = new float[3], grip;
        final float scale;

        Axes(Weapon weapon, float scale) {
            ahead = weapon.pointing();
            grip = weapon.origin();
            this.scale = scale;
        }

        /** A place given from the grip, in metres: ahead, up, and to the right. */
        float[] at(float a, float u, float r) {
            float[] out = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                out[axis] = grip[axis] + (ahead[axis] * a + up[axis] * u + right[axis] * r) * scale;
            }
            return out;
        }

        /** A hold of the view's, in the weapon's own axes and metres, and back. */
        Hold within(Hold hold, ArmModel model) {
            Hold out = copy(new Hold(model), hold);
            float[] off = {hold.at[0] - grip[0], hold.at[1] - grip[1], hold.at[2] - grip[2]};
            float[][] lines = {off, hold.across, hold.out}, into = {out.at, out.across, out.out};
            for (int line = 0; line < 3; line++) {
                float by = line == 0 ? 1f / scale : 1f;
                into[line][0] = ArmPose.dot(lines[line], ahead) * by;
                into[line][1] = ArmPose.dot(lines[line], up) * by;
                into[line][2] = ArmPose.dot(lines[line], right) * by;
            }
            return out;
        }

        Hold seen(Hold hold, ArmModel model) {
            Hold out = copy(new Hold(model), hold);
            System.arraycopy(at(hold.at[0], hold.at[1], hold.at[2]), 0, out.at, 0, 3);
            System.arraycopy(way(hold.across[0], hold.across[1], hold.across[2]), 0, out.across, 0, 3);
            System.arraycopy(way(hold.out[0], hold.out[1], hold.out[2]), 0, out.out, 0, 3);
            return out;
        }

        /** A way, in parts of ahead, up and to the right. */
        float[] way(float a, float u, float r) {
            float[] out = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                out[axis] = ahead[axis] * a + up[axis] * u + right[axis] * r;
            }
            return out;
        }
    }

    /** 0 before `from`, 1 after `to`, and eased between. */
    private static float between(float time, float from, float to) {
        return ViewModelMath.smooth((time - from) / (to - from));
    }

    /**
     * Places the arms on a weapon.
     *
     * @param scale   the model's metres to tiles
     * @param fromAim the matrix from the aim's axes, which the shoulders are fixed in, to the view's
     * @param skins   the answer, right then left: for each bone the matrix ArmPose gives
     * @param said    the answer, right then left: what ArmPose says of each arm
     * @param carried the answer: what the left hand carries
     * @param settled where the left hand was last frame, and how long ago that was
     * @return how many arms are placed: 2 with the left hand on the weapon or busy with it, 1 without, and 0 if an arm cannot be placed
     */
    static int place(ArmModel model, float scale, Weapon weapon, Doing doing, float[] fromAim, float[][] skins, float[][] said, Carried carried, Settled settled) {
        Axes axes = new Axes(weapon, scale);
        float tipped = ArmPose.dot(weapon.over(), axes.ahead);
        if (!ArmPose.unit(weapon.over()[0] - axes.ahead[0] * tipped, weapon.over()[1] - axes.ahead[1] * tipped, weapon.over()[2] - axes.ahead[2] * tipped, axes.up)) {
            return 0;
        }
        ArmPose.cross(axes.ahead, axes.up, axes.right);
        Hold right = right(model, weapon, axes, doing);
        if (!solve(model, weapon, scale, right, fromAim, 0, skins, said)) {
            return 0;
        }
        carried.what = NOTHING;
        Hold left = left(model, weapon, axes, doing, fromAim);
        if (left == null) {
            settled.aimed = null;
            return 1;
        }
        Hold aimed = axes.within(left, model);
        if (settled.aimed != null) {
            float[] before = Hold.frame(settled.aimed.across, settled.aimed.out), now = Hold.frame(aimed.across, aimed.out);
            boolean jumped = ArmPose.apart(settled.aimed.at, aimed.at) > ArmActs.JUMPS
                    || before[3] * now[3] + before[4] * now[4] + before[5] * now[5] < ArmActs.JUMPS_TURNING
                    || before[0] * now[0] + before[1] * now[1] + before[2] * now[2] < ArmActs.JUMPS_TURNING;
            if (jumped) {
                settled.from = settled.shown;
                settled.crossed = 0f;
            }
            if (settled.crossed < 1f) {
                settled.crossed = Math.min(settled.crossed + settled.seconds / ArmActs.CROSSES_IN, 1f);
            }
        } else {
            settled.crossed = 1f;
        }
        settled.aimed = aimed;
        settled.shown = settled.crossed < 1f ? copy(new Hold(model), settled.from).towards(aimed, ViewModelMath.smooth(settled.crossed)) : aimed;
        left = axes.seen(settled.shown, model);
        int carries = carries(doing);
        if (carries != NOTHING) {
            // A magazine stands in the fist, its top out of the thumb's side. A round lies along the fingers.
            float[] long3 = new float[3], out3 = new float[3], third = new float[3];
            ArmPose.unit(left.out[0], left.out[1], left.out[2], out3);
            if (carries == MAGAZINE) {
                float along = ArmPose.dot(left.across, out3);
                ArmPose.unit(left.across[0] - out3[0] * along, left.across[1] - out3[1] * along, left.across[2] - out3[2] * along, long3);
            } else {
                ArmPose.cross(left.across, out3, long3);
                ArmPose.unit(long3[0], long3[1], long3[2], long3);
            }
            ArmPose.cross(out3, long3, third);
            float back = carries == MAGAZINE ? MAGAZINE_LONG / 2f : ROUND_BEHIND;
            for (int row = 0; row < 3; row++) {
                carried.at[row] = out3[row] * scale;
                carried.at[4 + row] = long3[row] * scale;
                carried.at[8 + row] = third[row] * scale;
                carried.at[12 + row] = left.at[row] - long3[row] * back * scale;
            }
            carried.at[3] = 0f;
            carried.at[7] = 0f;
            carried.at[11] = 0f;
            carried.at[15] = 1f;
            carried.what = carries;
        }
        return solve(model, weapon, scale, left, fromAim, 1, skins, said) ? 2 : 0;
    }

    /** How long a magazine is, and how far behind where it is pinched a round's base is, in metres. */
    static final float MAGAZINE_LONG = 0.115f, ROUND_BEHIND = 0.02f;

    /** What the left hand has in it at this moment of what is being done. */
    static int carries(Doing doing) {
        int action = doing.action();
        boolean magazine = action == ArmActs.HANDGUN || action == ArmActs.BOLT;
        if (magazine) {
            if (doing.doing() != ArmActs.LOADING && doing.doing() != ArmActs.UNLOADING) {
                return doing.seated() >= 0f && doing.seated() < ArmActs.HOME_IN ? MAGAZINE : NOTHING;
            }
            // The one that came out until it is out of sight, and then the one on its way in. One in the weapon is the weapon's.
            boolean old = doing.out() >= 0f && doing.out() < ArmActs.TAKEN_IN;
            return !doing.clip() && (old || doing.fetching() >= 0f) ? MAGAZINE : NOTHING;
        }
        if (doing.doing() != ArmActs.LOADING) {
            return NOTHING;
        }
        if (doing.round() >= ArmActs.ROUND_IN) {
            return NOTHING;
        }
        return action == ArmActs.PUMP || action == ArmActs.DOUBLE || action == ArmActs.DOUBLE_SAWN ? SHELL : CARTRIDGE;
    }

    /** The right hand: on the grip or the handle, and on a rifle's bolt or its lever while that is worked. */
    private static Hold right(ArmModel model, Weapon weapon, Axes axes, Doing doing) {
        Hold hold = new Hold(model);
        System.arraycopy(weapon.grip(), 0, hold.at, 0, 3);
        if (weapon.handgun()) {
            // The game's hand has a pistol low on its grip. A hand holds one a little higher, its thumb still under the slide.
            for (int axis = 0; axis < 3; axis++) {
                hold.at[axis] += (axes.up[axis] * PISTOL_HIGHER[0] + axes.ahead[axis] * PISTOL_HIGHER[1]) * axes.scale;
            }
        }
        if (!weapon.firearm()) {
            // A fist holds a handle a little aslant, its far end leaning on towards the fingers.
            float cos = (float) Math.cos(HANDLE_SLANT), sin = (float) Math.sin(HANDLE_SLANT);
            float[] fingers = new float[3];
            ArmPose.cross(axes.ahead, axes.right, fingers);
            for (int axis = 0; axis < 3; axis++) {
                hold.across[axis] = axes.ahead[axis] * cos - fingers[axis] * sin;
                hold.out[axis] = -axes.right[axis];
            }
            hold.weights[model.pose("handle")] = 1f;
            hold.round = true;
            return hold;
        }
        float rake = weapon.handgun() ? PISTOL_RAKE : STOCK_RAKE;
        float cos = (float) Math.cos(rake), sin = (float) Math.sin(rake);
        for (int axis = 0; axis < 3; axis++) {
            hold.across[axis] = axes.up[axis] * cos + axes.ahead[axis] * sin;
            hold.out[axis] = -axes.right[axis];
        }
        float raised = ViewModelMath.smooth(doing.aim());
        hold.weights[model.pose("grip")] = 1f - raised;
        hold.weights[model.pose("trigger")] = raised;
        float cycled = doing.cycled();
        Parts parts = weapon.parts();
        if (cycled >= 0f && (doing.action() == ArmActs.BOLT || doing.action() == ArmActs.BOLT_SINGLE)) {
            // The hand goes to the knob as the bolt is about to be worked, turns it up, draws
            // it back, shoves it home, turns it down, and goes back to the grip.
            float[] worked = new float[2];
            OwnRifle.worked(Math.max(cycled - ArmActs.BOLT_AFTER, 0f), worked);
            float on = between(cycled, ArmActs.BOLT_REACHED_FROM, ArmActs.BOLT_AFTER)
                    - between(cycled, ArmActs.BOLT_AFTER + OwnRifle.WORKED_FOR, ArmActs.BOLT_AFTER + OwnRifle.WORKED_FOR + ArmActs.BOLT_LEFT_IN);
            float turned = parts.lift() * worked[0];
            float knobRight = parts.knob()[2], knobUp = parts.knob()[1] - parts.bore();
            float r = knobRight * (float) Math.cos(turned) - knobUp * (float) Math.sin(turned);
            float u = knobRight * (float) Math.sin(turned) + knobUp * (float) Math.cos(turned);
            float[] knob = axes.at(parts.knob()[0] - parts.travel() * worked[1], parts.bore() + u, r);
            // The palm is under the knob and outside it, and the fingers run forward past it.
            Hold onKnob = new Hold(model).set(knob, axes.way(0f, 0.55f, -0.83f), axes.way(0.9f, 0.3f, -0.3f), false, model.pose("knob"));
            hold.towards(onKnob, on);
        } else if (cycled >= 0f && doing.action() == ArmActs.LEVER) {
            // The hand in the lever's loop swings it down and forward about its pin and back.
            float swung = (between(cycled, 0.05f, 0.2f) - between(cycled, 0.22f, 0.4f)) * ArmActs.LEVER_SWINGS;
            float[] pin = axes.at(0.06f, 0.0f, 0f), turn = new float[9], arm = new float[3], turnedArm = new float[3];
            ArmPose.about(axes.right, -swung, turn);
            for (int axis = 0; axis < 3; axis++) {
                arm[axis] = hold.at[axis] - pin[axis];
            }
            turn(turn, arm, turnedArm);
            for (int axis = 0; axis < 3; axis++) {
                hold.at[axis] = pin[axis] + turnedArm[axis];
            }
            turn(turn, hold.across.clone(), hold.across);
            turn(turn, hold.out.clone(), hold.out);
        }
        return hold;
    }

    private static void turn(float[] turn, float[] line, float[] out) {
        float x = line[0], y = line[1], z = line[2];
        out[0] = turn[0] * x + turn[3] * y + turn[6] * z;
        out[1] = turn[1] * x + turn[4] * y + turn[7] * z;
        out[2] = turn[2] * x + turn[5] * y + turn[8] * z;
    }

    /** The left hand, or null when it is not on the weapon nor busy with it. */
    private static Hold left(ArmModel model, Weapon weapon, Axes axes, Doing doing, float[] fromAim) {
        Hold hold = null;
        if (doing.both() && !weapon.firearm()) {
            // Something swung in both hands: the left fist is on the handle under the right, the same way round.
            float cos = (float) Math.cos(HANDLE_SLANT), sin = (float) Math.sin(HANDLE_SLANT);
            float[] fingers = new float[3], slanted = new float[3];
            ArmPose.cross(axes.ahead, axes.right, fingers);
            for (int axis = 0; axis < 3; axis++) {
                slanted[axis] = fingers[axis] * cos + axes.ahead[axis] * sin;
            }
            Hold fist = new Hold(model).set(axes.at(-LOWER_FIST, 0f, 0f), axes.right, slanted, true, model.pose("handle"));
            fist.round = true;
            return fist;
        }
        if (doing.both() && !weapon.handgun()) {
            hold = new Hold(model);
            System.arraycopy(weapon.foreEnd(), 0, hold.at, 0, 3);
            // The hand is turned to its arm, brought back to where the arm reaches, and turned to the arm it then has.
            underForeEnd(model, weapon, axes, hold, fromAim);
            withinReach(model, weapon, axes, hold, fromAim);
            underForeEnd(model, weapon, axes, hold, fromAim);
        }
        boolean seating = doing.seated() >= 0f && doing.doing() != ArmActs.LOADING;
        if (!weapon.firearm() || doing.doing() == ArmActs.HOLDING && !seating) {
            return hold;
        }
        Parts parts = weapon.parts();
        float[] off = new float[3];
        ViewModelMath.place(fromAim, AWAY[0] * axes.scale, AWAY[1] * axes.scale, AWAY[2] * axes.scale, off);
        // Off the weapon the hand is closed on what it fetches or takes away, or about to be.
        int action = doing.action();
        boolean magazine = action == ArmActs.HANDGUN || action == ArmActs.BOLT;
        Hold away = new Hold(model).set(off, axes.way(0.2f, 0.9f, 0.4f), axes.way(0.8f, 0f, 0.6f), true,
                model.pose(doing.doing() == ArmActs.RACKING ? "open" : magazine ? "magazine" : "pinch"));
        // Where the hand is when this is over: back under the fore-end, or off the weapon.
        Hold rest = hold != null ? hold : away;
        // How far through what the game is doing, by how long it is taken to take: 1 when it should be over.
        float done = doing.done() / Math.max(doing.pace(), ArmActs.SHORTEST);
        Hold now = new Hold(model);
        if (seating) {
            // The game has the magazine in: the hand, which has been holding it under the well, pushes it home and lets go.
            float[] well = parts.well();
            float under = (1f - between(doing.seated(), 0f, ArmActs.HOME_IN)) * ArmActs.MAGAZINE_DRAWN;
            Hold onMagazine = new Hold(model).set(axes.at(well[0], well[1] - under, well[2]), axes.way(0f, 0f, 1f), axes.way(1f, 0.1f, 0f), true, model.pose("magazine"));
            return copy(now, onMagazine).towards(rest, between(doing.seated(), 0.3f, ArmActs.SEATED_FOR));
        }
        if (doing.doing() == ArmActs.RACKING) {
            if (action != ArmActs.HANDGUN) {
                // A pump's fore-end is slid by the hand that is on it, and a bolt is the right hand's.
                return hold;
            }
            // Over the top of the slide from behind and to the left, drawn back with it, let go.
            float back = (between(done, 0.3f, 0.5f) - between(done, 0.5f, 0.56f)) * parts.slideTravel();
            Hold onSlide = new Hold(model).set(axes.at(parts.slide()[0] - back, parts.slide()[1], parts.slide()[2]), axes.way(0f, -1f, 0.1f), axes.way(0.3f, 0.15f, 0.94f), true,
                    model.pose("slide"));
            float on = between(done, 0f, 0.3f) - between(done, 0.6f, 0.95f);
            return copy(now, rest).towards(onSlide, on);
        }
        if (magazine) {
            // The magazine is held as a handle is, its top out of the thumb's side of the fist. Where it is, the game says of the weapon.
            float[] well = parts.well();
            if (doing.clip()) {
                // In the weapon: the hand goes to it and takes hold of it, to draw it out when the game lets it go.
                Hold inWell = new Hold(model).set(axes.at(well[0], well[1], well[2]), axes.way(0f, 0f, 1f), axes.way(1f, 0.1f, 0f), true, model.pose("magazine"));
                return copy(now, rest).towards(inWell, between(doing.done(), 0f, 0.3f));
            }
            float out = doing.out();
            if (out >= 0f && (out < ArmActs.TAKEN_IN || doing.fetching() < 0f)) {
                // Out of the weapon: drawn clear of the well and taken away below, and the hand back to where it rests if no other is to come.
                float drawn = between(out, 0f, ArmActs.DRAWN_CLEAR_IN) * ArmActs.MAGAZINE_DRAWN;
                Hold clear = new Hold(model).set(axes.at(well[0], well[1] - drawn, well[2]), axes.way(0f, 0f, 1f), axes.way(1f, 0.1f, 0f), true, model.pose("magazine"));
                copy(now, clear).towards(away, between(out, ArmActs.DRAWN_CLEAR_IN, ArmActs.TAKEN_IN));
                return now.towards(rest, between(out, ArmActs.TAKEN_IN, ArmActs.TAKEN_IN + 0.3f));
            }
            // Another is brought up under the well, to be there well before the game is done, which may be sooner than it was last time,
            // and held there until it is: see `seating`.
            float began = out >= 0f ? ArmActs.TAKEN_IN : 0f;
            float through = (doing.fetching() - began) / Math.max(doing.fetchTakes() - began, ArmActs.SHORTEST);
            Hold under = new Hold(model).set(axes.at(well[0], well[1] - ArmActs.MAGAZINE_DRAWN, well[2]), axes.way(0f, 0f, 1f), axes.way(1f, 0.1f, 0f), true,
                    model.pose("magazine"));
            return copy(now, away).towards(under, between(through, 0.15f, 0.7f));
        }
        if (doing.doing() == ArmActs.UNLOADING) {
            return hold;
        }
        // One round at a time: fetched from below, brought to the port under the palm's thumb, pushed in.
        float round = doing.round();
        float pushed = between(round, 0.45f, 0.6f) * ArmActs.ROUND_PUSHED;
        float[] port = parts.port();
        Hold atPort = action == ArmActs.PUMP
                ? new Hold(model).set(axes.at(port[0] - 0.05f + pushed, port[1] - 0.03f, port[2]), axes.way(0f, 1f, 0f), axes.way(1f, 0.05f, 0.15f), true, model.pose("pinch"))
                : new Hold(model).set(axes.at(port[0] - 0.03f + pushed, port[1], port[2] - 0.04f), axes.way(0.1f, 0.2f, 1f), axes.way(1f, 0.3f, 0f), true, model.pose("pinch"));
        float on = between(round, 0f, 0.42f) - between(round, 0.62f, 1f);
        return copy(now, away).towards(atPort, on);
    }

    /** Lays the left hand under a fore-end, where the hold is, turned to meet the arm that comes to it from its shoulder. */
    private static void underForeEnd(ArmModel model, Weapon weapon, Axes axes, Hold hold, float[] fromAim) {
        float[] shoulder = new float[3], from = new float[3], arm = new float[3], palm = new float[3], square = new float[3], fingers = new float[3];
        starts(weapon, 1, axes.scale, fromAim, shoulder, from);
        ArmPose.unit(hold.at[0] - shoulder[0], hold.at[1] - shoulder[1], hold.at[2] - shoulder[2], arm);
        // Square to the barrel and to the arm, on the side that is under the fore-end. An arm along the barrel is square to it every way round.
        ArmPose.cross(axes.ahead, arm, palm);
        float under = ArmPose.dot(palm, axes.up) < 0f ? -1f : 1f;
        if (!ArmPose.unit(palm[0] * under, palm[1] * under, palm[2] * under, palm)) {
            palm[0] = 0f;
            palm[1] = 0f;
            palm[2] = 0f;
        }
        // The left arm comes to the fore-end from the weapon's left, and the palm faces a little away from it.
        ArmPose.unit(palm[0] + axes.up[0] * PALM_UNDER + axes.right[0] * PALM_ASIDE, palm[1] + axes.up[1] * PALM_UNDER + axes.right[1] * PALM_ASIDE,
                palm[2] + axes.up[2] * PALM_UNDER + axes.right[2] * PALM_ASIDE, palm);
        // The arm's line laid in the palm, and the way across the barrel in the palm that it runs.
        float into = ArmPose.dot(arm, palm);
        float[] laid = {arm[0] - palm[0] * into, arm[1] - palm[1] * into, arm[2] - palm[2] * into};
        float forward = ArmPose.dot(laid, axes.ahead);
        if (!ArmPose.unit(laid[0] - axes.ahead[0] * forward, laid[1] - axes.ahead[1] * forward, laid[2] - axes.ahead[2] * forward, square)) {
            ArmPose.cross(axes.ahead, palm, square);
        }
        float lean = Math.clamp((float) Math.atan2(forward, ArmPose.dot(laid, square)), -FINGERS_LEAN, FINGERS_LEAN);
        float cos = (float) Math.cos(lean), sin = (float) Math.sin(lean);
        for (int axis = 0; axis < 3; axis++) {
            fingers[axis] = square[axis] * cos + axes.ahead[axis] * sin;
        }
        hold.set(hold.at.clone(), palm, fingers, true, model.pose("fore"));
    }

    /**
     * Brings the hand under a fore-end back along the barrel to where its arm reaches it
     * easily, and no nearer the grip than NEAREST_FORE. A hand left further out than the arm
     * is long has the shoulder brought out after it, into the picture. How far back is
     * settled by where the fore-end is with the weapon held still: the hand has one place on
     * the fore-end and goes with it as a shot kicks the weapon, a step sways it and a pump's
     * fore-end is drawn back.
     */
    private static void withinReach(ArmModel model, Weapon weapon, Axes axes, Hold hold, float[] fromAim) {
        float[] shoulder = new float[3], from = new float[3], wrist = new float[3];
        starts(weapon, 1, axes.scale, fromAim, shoulder, from);
        if (!ArmPose.wrist(model, axes.scale, hold.weights, hold.at, hold.across, hold.out, from, true, hold.round, wrist)) {
            return;
        }
        float reach = ArmPose.whole(model) * axes.scale * REACH_EASY;
        float[] still = {weapon.foreSteady()[0] - weapon.foreEnd()[0], weapon.foreSteady()[1] - weapon.foreEnd()[1], weapon.foreSteady()[2] - weapon.foreEnd()[2]};
        float[] off = {wrist[0] + still[0] - shoulder[0], wrist[1] + still[1] - shoulder[1], wrist[2] + still[2] - shoulder[2]};
        float along = ArmPose.dot(off, axes.ahead), over = ArmPose.dot(off, off) - reach * reach;
        if (over <= 0f) {
            return;
        }
        // Back to where the wrist is within reach, or if it is nowhere on the barrel, to where it is nearest.
        float within = along * along - over;
        float back = within > 0f ? along - (float) Math.sqrt(within) : along;
        float[] grip = weapon.grip();
        float spare = (hold.at[0] - grip[0]) * axes.ahead[0] + (hold.at[1] - grip[1]) * axes.ahead[1] + (hold.at[2] - grip[2]) * axes.ahead[2] - NEAREST_FORE * axes.scale;
        back = Math.clamp(back, 0f, Math.max(spare, 0f));
        for (int axis = 0; axis < 3; axis++) {
            hold.at[axis] -= axes.ahead[axis] * back;
        }
    }

    /** Where an arm on this weapon starts and where its forearm is to seem to come from, in the view. */
    private static void starts(Weapon weapon, int side, float scale, float[] fromAim, float[] shoulder, float[] from) {
        float sign = side == 1 ? -1f : 1f;
        boolean longGun = weapon.firearm() && !weapon.handgun();
        float[] at = longGun ? LONG_SHOULDERS[side] : new float[] {SHOULDER[0] * sign, SHOULDER[1], SHOULDER[2]};
        ViewModelMath.place(fromAim, at[0] * scale, at[1] * scale, at[2] * scale, shoulder);
        ViewModelMath.place(fromAim, FROM[side][0] * scale, FROM[side][1] * scale, FROM[side][2] * scale, from);
    }

    private static Hold copy(Hold into, Hold from) {
        System.arraycopy(from.at, 0, into.at, 0, 3);
        System.arraycopy(from.across, 0, into.across, 0, 3);
        System.arraycopy(from.out, 0, into.out, 0, 3);
        System.arraycopy(from.weights, 0, into.weights, 0, from.weights.length);
        into.round = from.round;
        return into;
    }

    private static boolean solve(ArmModel model, Weapon weapon, float scale, Hold hold, float[] fromAim, int side, float[][] skins, float[][] said) {
        float[] shoulder = new float[3], from = new float[3];
        starts(weapon, side, scale, fromAim, shoulder, from);
        return ArmPose.solve(model, scale, hold.weights, hold.at, hold.across, hold.out, shoulder, from, side == 1, hold.round, skins[side], said[side]);
    }
}
