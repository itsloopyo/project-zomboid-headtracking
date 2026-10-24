package com.cameraunlock.zomboid;

/**
 * The mod's own models of the game's self-loading rifles and carbine (the assault rifle, the
 * battle rifle, the self-loading rifle and the carbine), drawn in the viewer's hands in place of
 * the game's: made by tools/models/selfloaders.py and kept in the jar (OwnGun says how each is
 * held, lit and drawn).
 *
 * The bolt carrier or bolt, with the operating rod or bolt handle that rides on it on all but the
 * assault rifle, is worked as a pistol's slide is (SelfLoading.Slide), on a rifle's timings:
 * every shot the game takes sends it back and home again, quickly enough for the next round of an
 * automatic burst, and throws the fired case out of the port; the shot that empties the magazine
 * leaves it held back until a round is chambered; and when the game racks the rifle the right
 * hand draws the charging handle straight back with it and lets it go. The box magazine is in its
 * well while the game has it in; the left hand draws it out and lets it fall, and brings the next
 * up and pushes it home. A scope's base is drawn on the battle rifle only with a scope on it; the
 * assault rifle's scope stands on its carrying handle, and the others' on their rails.
 *
 * All of it is the render thread's.
 */
final class OwnSelfLoader extends OwnGun {
    /** The parts the file's layout names, and how many it has. */
    static final int FIXED = 0, HANDLE = 1, BOLT = 2, CASE = 3, MAGAZINE = 4, MOUNT = 5, PARTS = 6;
    /** The part that slides toward the eye, which the forward stop keeps the eye short of all the way back. */
    static final int SLIDES = BOLT;
    /**
     * The numbers after the parts in the model's file: where the hand takes hold of what it works
     * to charge the rifle, from the grip in the file's axes, and whether the charging part goes
     * back with the bolt on every shot (1) or only when the hand draws it (0).
     */
    private static final int GRAB = 0, RIDES = 3;
    static final int MOVES = 4;

    /** How long the bolt takes to go back after a shot and home again, in seconds: an automatic's next shot comes 0.08 s after. */
    static final float BACK_IN = 0.028f, HOME_IN = 0.032f;

    /** Fired cases: thrown out to the right, a little up and back, tumbling fast; up to a second's worth of automatic fire in the air. */
    private final Thrown cases = new Thrown(14, 0.8f, 2.2f, 0.9f, 0.35f, 7.5f, 24f);
    /** A magazine let fall out of the hand: it drops straight. */
    private final Thrown dropped = new Thrown(1, 0.7f, 0f, -0.3f, 0f, 7.5f, 0f);
    private final SelfLoading.Slide action = new SelfLoading.Slide(BACK_IN, 0f, HOME_IN);
    private final float[] hand = new float[16], scratch = new float[3], grab = new float[3];
    /** How far back the bolt and the charging part are, 0 to 1. */
    private float bolt, handle;
    private boolean magazineIn, magazineDropped, lockedBefore;
    /** How many rounds the magazine in the well held when last seen there: one that comes out empty is let fall, a loaded one kept. */
    private int roundsInWell;
    /** Which way the magazine let fall is handed, as the hand had it. */
    private float droppedHanded = 1f;
    /** The game's frame the action was last moved on in. */
    private int workedIn = Integer.MIN_VALUE;
    /** The run of shots each less than a burst's gap after the last: how many, and the shortest and longest gap, in seconds. */
    private static final float BURST_GAP = 0.25f;
    private int burst;
    private float gapSince, shortestGap, longestGap;

    OwnSelfLoader(View view) {
        // Black alloy and plastic, and parkerised steel and oiled walnut: neither mirrors the sky.
        super(view, 0.6f, 0.30f, 1f);
    }

    @Override
    float[] handle(OwnModel model) {
        float[] moves = model.moves;
        System.arraycopy(moves, GRAB, grab, 0, 3);
        return grab;
    }

    @Override
    boolean ownMagazine() {
        return true;
    }

    @Override
    void work(ViewCamera.Pose camera, float seconds) {
        ViewModel.Held held = view.viewModel.held;
        if (held != null) {
            int frame = Pipeline.frame();
            float through = held.done() / Math.max(held.pace(), ArmActs.SHORTEST);
            action.step(held.itemId(), workedIn == frame - 1, held.shots(), held.chambered(), held.rounds(), held.clip(), held.doing(), through, seconds);
            workedIn = frame;
            for (int one = 0; one < action.thrown; one++) {
                cases.out(camera, model, drawn.port[0], drawn.port[1], drawn.port[2]);
                noteShot();
            }
            gapSince += seconds;
            if (burst > 1 && gapSince >= BURST_GAP) {
                if (DevBuild.ON) {
                    Log.info(String.format(java.util.Locale.ROOT, "%s cycled its bolt for a burst of %d shots, %.0f to %.0f ms apart", drawn.what, burst,
                            shortestGap * 1000f, longestGap * 1000f));
                }
                burst = 0;
            }
            if (action.locked() != lockedBefore) {
                lockedBefore = action.locked();
                if (DevBuild.ON) {
                    Log.info(drawn.what + (lockedBefore ? " held its bolt back on an empty magazine" : " let its bolt go home"));
                }
            }
            bolt = action.back;
            // The assault rifle's handle goes back only with the hand that racks it; the battle rifle's rides on its bolt.
            handle = drawn.moves[RIDES] > 0.5f ? bolt : held.doing() == ArmActs.RACKING ? ArmHolds.slideRacked(through) : 0f;
            // With the game's own arms nothing of ours is in the hand: the magazine is in its well whenever the game has it in.
            magazineIn = view.characters.carried() == null ? held.clip() : SelfLoading.magazineIn(held.clip(), held.doing(), held.seated());
            if (held.clip()) {
                roundsInWell = held.rounds();
            }
            dropMagazine(camera, held);
        }
        cases.fly(camera, seconds);
        dropped.fly(camera, seconds);
    }

    /** Counts a shot towards the burst it is in. */
    private void noteShot() {
        if (burst > 0 && gapSince < BURST_GAP) {
            shortestGap = Math.min(shortestGap, gapSince);
            longestGap = Math.max(longestGap, gapSince);
        } else {
            burst = 0;
            shortestGap = Float.POSITIVE_INFINITY;
            longestGap = 0f;
        }
        burst++;
        gapSince = 0f;
    }

    /** Whether the magazine in the hand is the one that came out, as against the next on its way in. */
    private static boolean outgoing(ViewModel.Held held) {
        return held.out() >= 0f && (held.out() < ArmActs.TAKEN_IN || held.fetching() < 0f);
    }

    /** Lets an empty magazine the hand has drawn out of its well fall, once it is clear. A loaded one is kept, as the game keeps it. */
    private void dropMagazine(ViewCamera.Pose camera, ViewModel.Held held) {
        if (!outgoing(held)) {
            magazineDropped = false;
            return;
        }
        if (!magazineDropped && roundsInWell == 0 && held.out() >= ArmActs.DRAWN_CLEAR_IN && inHand()) {
            magazineDropped = true;
            dropped.out(camera, hand, 0f, 0f, 0f);
            droppedHanded = handedness(hand);
            if (DevBuild.ON) {
                Log.info(drawn.what + " let its magazine fall from the hand");
            }
        }
    }

    /**
     * Fills hand with the matrix that puts the model's magazine where the left hand has a
     * magazine: from the model's axes to the view's. False where the hand has none.
     */
    private boolean inHand() {
        ArmHolds.Carried carried = view.characters.carried();
        if (carried == null || carried.what != ArmHolds.MAGAZINE) {
            return false;
        }
        inHand(carried.at, drawn.shape, metre(), scratch, hand);
        return true;
    }

    /**
     * Fills hand with the matrix that puts a model's magazine where a hand carrying one has it, from
     * the model's axes to the view's, given what the hand carries and how long a metre of the model is
     * in the view. The hand holds it where it holds one in its well (ArmHolds), along the magazine's
     * line, with the foot of the arms' own that far under it.
     */
    static void inHand(float[] c, WeaponShape shape, float metre, float[] well, float[] hand) {
        shape.place(ArmHolds.magazineAlong(shape), ArmHolds.magazineUp(shape), 0f, well);
        // The hand's own measure against the model's: the magazine is drawn the size the model is.
        float handMetre = (float) Math.sqrt(c[4] * c[4] + c[5] * c[5] + c[6] * c[6]);
        float k = metre / handMetre;
        // The model's x is the gun's left, y along the barrel, z up; the hand's x is out of its
        // palm, the gun's right, y up the magazine and z the way that leaves, back.
        for (int row = 0; row < 3; row++) {
            hand[row] = -k * c[row];
            hand[4 + row] = -k * c[8 + row];
            hand[8 + row] = k * c[4 + row];
        }
        hand[3] = hand[7] = hand[11] = 0f;
        float footUp = ArmHolds.MAGAZINE_LONG / 2f;
        for (int row = 0; row < 3; row++) {
            hand[12 + row] = c[12 + row] + c[4 + row] * footUp - (hand[row] * well[0] + hand[4 + row] * well[1] + hand[8 + row] * well[2]);
        }
        hand[15] = 1f;
    }

    @Override
    void parts(Program drawing) {
        part(FIXED);
        drawing.set("uSlide", handle * drawn.travel);
        part(HANDLE);
        drawing.set("uSlide", bolt * drawn.travel);
        part(BOLT);
        drawing.set("uSlide", 0f);
        if (view.scope.fitted() != null) {
            part(MOUNT);
        }
        ViewModel.Held held = view.viewModel.held;
        if (held != null && inHand() && !(outgoing(held) && magazineDropped)) {
            drawing.set("uModel", hand);
            drawing.set("uHanded", handedness(hand));
            part(MAGAZINE);
            drawing.set("uModel", model);
            drawing.set("uHanded", handedness(model));
        } else if (magazineIn) {
            part(MAGAZINE);
        }
        cases.draw(this, drawing, CASE);
        drawing.set("uHanded", droppedHanded);
        dropped.draw(this, drawing, MAGAZINE);
        drawing.set("uHanded", handedness(model));
    }

    /** -1 where a matrix turns what it carries into its mirror image, 1 where it does not. */
    private static float handedness(float[] m) {
        float turned = m[0] * (m[5] * m[10] - m[9] * m[6]) - m[4] * (m[1] * m[10] - m[9] * m[2]) + m[8] * (m[1] * m[6] - m[5] * m[2]);
        return turned < 0f ? -1f : 1f;
    }
}
