package com.cameraunlock.zomboid;

/**
 * The mod's own models of the lever action rifle and carbine, drawn in the viewer's hands in
 * place of the game's: made by tools/models/lever.py and kept in the jar (OwnGun says how each
 * is held, lit and drawn).
 *
 * The shot lets the hammer fall. After it the lever is swung down about its pin and back, as
 * the hand in its loop swings it (ArmHolds): the bolt slides back with it, pushes the hammer
 * back past cocked and throws the fired case out of the top, and comes forward again as the
 * lever is shut, leaving the hammer cocked.
 *
 * All of it is the render thread's.
 */
final class OwnLever extends OwnGun {
    /** The parts the file has, in its order. */
    private static final int FIXED = 0, LEVER = 1, BOLT = 2, HAMMER = 3, CASE = 4;
    /** The part that slides back toward the eye by the file's travel as the action is worked: its bolt. */
    static final int SLIDES = BOLT;
    static final int PARTS = 5, MOVES = 9;
    /**
     * Where the numbers after the parts are: the lever's pin along and over the bore, the
     * hammer's, the hammer's fall and its push back past cocked, and the middle of the loading
     * gate in the side of the receiver.
     */
    private static final int LEVER_ALONG = 0, LEVER_UP = 1, HAMMER_ALONG = 2, HAMMER_UP = 3, FALLS = 4, OVER = 5, GATE = 6;

    /** When the lever is swung down and back after the shot, in seconds, as the hand in its loop swings it. */
    private static final float DOWN_FROM = 0.05f, DOWN_TO = 0.2f, BACK_FROM = 0.22f, BACK_TO = 0.4f;
    /** The bolt throws the case out once it is this far back, on its way back. */
    private static final float THROWN_AT = 0.9f;

    /** Fired cases: thrown up out of the top, a little to the right and back, and tumbling. */
    private final Thrown cases = new Thrown(2, 0.9f, 0.45f, 1.5f, 0.25f, 7.5f, 19f);
    private final float[] turn = new float[16], gate = new float[3], pin = new float[2];
    private final Cycle cycle = new Cycle();
    /** The game's frame the lever was last moved on in. */
    private int workedIn = Integer.MIN_VALUE;
    private float open, hammer;

    /** With the sights up the hammer falls and the bolt pushes it back past cocked, and the lever swings down. */
    static float[][] poses(float[] moves) {
        return new float[][] {{HAMMER, 0f, moves[HAMMER_ALONG], moves[HAMMER_UP], 1f, 0f, 0f, -moves[FALLS]},
                {HAMMER, 0f, moves[HAMMER_ALONG], moves[HAMMER_UP], 1f, 0f, 0f, moves[OVER]},
                {LEVER, 0f, moves[LEVER_ALONG], moves[LEVER_UP], 1f, 0f, 0f, ArmActs.LEVER_SWINGS}};
    }

    OwnLever(View view) {
        // Oiled wood seen along its length does not turn into a mirror of the sky, and with the
        // sights up the head over the comb shades what faces the eye from close by.
        super(view, 1f, 0.30f, 1f);
    }

    /**
     * How far down the lever is swung at a time after the shot, 0 shut to 1 right down: the same
     * the hand in its loop is swung by.
     *
     * @param cycled how long ago the shot was, in seconds, or less than nought with none being worked after
     */
    static float swung(float cycled) {
        if (cycled < 0f) {
            return 0f;
        }
        return ViewModelMath.smooth((cycled - DOWN_FROM) / (DOWN_TO - DOWN_FROM)) - ViewModelMath.smooth((cycled - BACK_FROM) / (BACK_TO - BACK_FROM));
    }

    /**
     * How far the hammer is turned from cocked a time into working the lever, in radians, forward
     * less than nought: fallen from the shot until the lever opens if the shot began the working,
     * pushed back past cocked by the bolt as it opens, and left cocked as it shuts.
     *
     * @param fired whether a shot began the working, as against the hand racking a loaded gun
     */
    static float hammer(float cycled, boolean fired, float falls, float over) {
        if (cycled < 0f) {
            return 0f;
        }
        float opened = swung(cycled);
        float fallen = fired && cycled < DOWN_TO ? 1f : 0f;
        return (1f - opened) * -falls * fallen + opened * over;
    }

    /**
     * One working of the lever after another, from what the game says of the gun frame by
     * frame: whether a shot began each, which is what lets the hammer fall and leaves a fired case
     * in the chamber for the bolt to throw out, and when that case goes.
     */
    static final class Cycle {
        private float before = -1f;
        private int item = Integer.MIN_VALUE, shotsSeen;
        private boolean caseThrown;
        /** Whether a shot began the working under way. */
        boolean fired;
        /** Whether the step just taken threw the fired case out. */
        boolean thrown;

        /**
         * @param item      the game's id of the item held: another is another gun
         * @param carriedOn whether it was moved on in the frame before this: shots taken while it was not, with another
         *                  weapon or with this one not drawn, began no working of this one
         * @param cycled    how long ago the working began, in seconds, or less than nought with none under way
         * @param shots     ViewModel.Held's count of shots, which counts every firearm's
         */
        void step(int item, boolean carriedOn, float cycled, int shots) {
            thrown = false;
            if (item != this.item || !carriedOn) {
                this.item = item;
                shotsSeen = shots;
            }
            if (cycled >= 0f && (before < 0f || cycled < before)) {
                // A working begins: by a shot if one was taken since the last, else by the hand that racks the gun.
                fired = shots != shotsSeen;
                shotsSeen = shots;
                caseThrown = false;
            }
            before = cycled;
            if (cycled < 0f) {
                // A shot not worked yet leaves its case in the chamber for the next working to throw.
                fired = false;
                return;
            }
            // Once each working begun by a shot, as the bolt comes right back, however few frames show it there.
            if (fired && !caseThrown && cycled >= DOWN_FROM && (swung(cycled) >= THROWN_AT || cycled > DOWN_TO)) {
                caseThrown = true;
                thrown = true;
            }
        }
    }

    /** The rounds go in by the loading gate. */
    @Override
    float[] loadingPort(OwnModel own) {
        System.arraycopy(own.moves, GATE, gate, 0, 3);
        return gate;
    }

    @Override
    float[] leverPin(OwnModel own) {
        pin[0] = own.moves[LEVER_ALONG];
        pin[1] = own.moves[LEVER_UP];
        return pin;
    }

    @Override
    void work(ViewCamera.Pose camera, float seconds) {
        ViewModel.Held held = view.viewModel.held;
        if (held != null) {
            int frame = Pipeline.frame();
            cycle.step(held.itemId(), workedIn == frame - 1, held.cycled(), held.shots());
            workedIn = frame;
            open = swung(held.cycled());
            hammer = hammer(held.cycled(), cycle.fired, drawn.moves[FALLS], drawn.moves[OVER]);
            if (cycle.thrown) {
                cases.out(camera, model, drawn.port[0], drawn.port[1], drawn.port[2]);
            }
        }
        cases.fly(camera, seconds);
    }

    @Override
    void parts(Program drawing) {
        part(FIXED);
        // The loop swings down about the pin: by the right hand about the gun's own x, which turns
        // what is behind the pin downwards.
        turnAbout(turn, 0f, drawn.moves[LEVER_ALONG], drawn.moves[LEVER_UP], 1f, 0f, 0f, open * ArmActs.LEVER_SWINGS);
        drawing.set("uPart", turn);
        part(LEVER);
        turnAbout(turn, 0f, drawn.moves[HAMMER_ALONG], drawn.moves[HAMMER_UP], 1f, 0f, 0f, hammer);
        drawing.set("uPart", turn);
        part(HAMMER);
        drawing.set("uPart", Characters.IDENTITY);
        drawing.set("uSlide", open * drawn.travel);
        part(BOLT);
        drawing.set("uSlide", 0f);
        cases.draw(this, drawing, CASE);
    }
}
