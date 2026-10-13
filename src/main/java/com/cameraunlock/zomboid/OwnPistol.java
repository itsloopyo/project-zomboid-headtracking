package com.cameraunlock.zomboid;

/**
 * The mod's own models of the game's three pistols, drawn in the viewer's hands in place of the
 * game's: each a mesh, its colour and the shape and finish of its surface, made by
 * tools/models/pistol.py and kept in the jar, in the layout the long guns' files have (OwnGun
 * says how they are held, lit and drawn).
 *
 * Each shot drives the slide back and lets it run forward again, and as it goes back the fired
 * case is thrown out of the port. A shot that leaves the pistol with nothing in its chamber and
 * nothing in its magazine leaves the slide held back, as the empty magazine holds it, until a
 * round is chambered or the slide is racked. The magazine is drawn in its well while the game
 * has one in the pistol and the hand is not carrying it in or out.
 *
 * All of it is the render thread's.
 */
final class OwnPistol extends OwnGun {
    /** The parts the file's layout names. A pistol has nothing in the second, which is a pump's. */
    static final int PARTS = 5;
    private static final int FIXED = 0, SLIDE = 2, CASE = 3, MAGAZINE = 4;

    /**
     * How long the slide takes over each part of a shot, in seconds: driven back, a moment held
     * there, run forward. A real one is quicker; this is as quick as stays in sight for a
     * frame or two at each end.
     */
    private static final float BACK = 0.035f, HELD_BACK = 0.015f, FORWARD = 0.055f;
    /** How long the slide is about a shot. */
    static final float CYCLES_FOR = BACK + HELD_BACK + FORWARD;
    /** The case is thrown out once the slide is this far back, on its way back. */
    private static final float THROWN_AT = 0.7f;
    private static final float NOT_CYCLING = -1f;
    private static final int NO_ITEM = Integer.MIN_VALUE;

    /** Fired cases: thrown out to the right and up, a little back, and tumbling fast. */
    private final Thrown cases = new Thrown(4, 0.8f, 1.6f, 1.3f, 0.2f, 7.5f, 26f);
    private final Slide action = new Slide();
    private boolean magazineIn;
    /** The game's frame the action was last moved on in. */
    private int workedIn = Integer.MIN_VALUE;

    OwnPistol(View view) {
        // Plastic, rubber and oiled walnut grips seen along their length do not turn into mirrors of the sky either.
        super(view, 0.45f, 0.30f, 0f);
    }

    /** How far back the slide is at a time after a shot, 0 to 1. */
    static float slideAt(float seconds) {
        return ViewModelMath.smooth(seconds / BACK) - ViewModelMath.smooth((seconds - BACK - HELD_BACK) / FORWARD);
    }

    /**
     * Whether the magazine is in its well, as against in the hand that takes it out or puts it in:
     * ArmHolds.carries says when the hand has it.
     *
     * @param clip   whether the game has a magazine in the pistol
     * @param doing  what the holder is doing to it: one of ArmActs' doings
     * @param seated how long ago the game finished loading a magazine, in seconds, or less than nought
     */
    static boolean magazineIn(boolean clip, int doing, float seated) {
        boolean handled = doing == ArmActs.LOADING || doing == ArmActs.UNLOADING;
        boolean pushedHome = !handled && seated >= 0f && seated < ArmActs.HOME_IN;
        return clip && !pushedHome;
    }

    /** Whether an empty magazine holds the slide back: one in the pistol, no rounds in it and nothing in the chamber. */
    static boolean heldOpen(boolean chambered, int rounds, boolean clip) {
        return clip && rounds == 0 && !chambered;
    }

    /**
     * Where the slide is, from what the game says of the pistol frame by frame: driven back and
     * let run forward by each shot, held back by an empty magazine once back from the last round
     * or from racking, and drawn back and let go by the hand that racks it.
     */
    static final class Slide {
        private int item = NO_ITEM;
        private int shotsSeen;
        private float sinceShot = NOT_CYCLING;
        private boolean locked, caseThrown;
        /** How far back the slide is, 0 to 1. */
        float back;
        /** Whether the step just taken threw a fired case out. */
        boolean thrown;

        /**
         * Moves it on by a frame.
         *
         * @param item      the game's id of the item held: another is another pistol, whose slide is as it was put away
         * @param carriedOn whether it was moved on in the frame before this: shots taken while it was not, with another
         *                  weapon or with this one not drawn, are not worked now
         * @param shots     ViewModel.Held's count of shots, which counts every firearm's
         * @param chambered whether the pistol has a round in its chamber
         * @param rounds    how many rounds its magazine holds
         * @param clip      whether it has a magazine in it
         * @param doing     what the holder is doing to it: one of ArmActs' doings
         * @param through   how far through that the holder is, as a share of how long it is taken to take
         */
        void step(int item, boolean carriedOn, int shots, boolean chambered, int rounds, boolean clip, int doing, float through, float seconds) {
            thrown = false;
            boolean open = heldOpen(chambered, rounds, clip);
            if (item != this.item || !carriedOn) {
                // Taken in hand or seen again: as the game has it now, its slide held back if it is empty.
                this.item = item;
                shotsSeen = shots;
                sinceShot = NOT_CYCLING;
                locked = open;
            }
            if (shots != shotsSeen) {
                shotsSeen = shots;
                sinceShot = 0f;
                caseThrown = false;
            } else if (sinceShot != NOT_CYCLING) {
                sinceShot += seconds;
            }
            float drawn = 0f;
            if (sinceShot != NOT_CYCLING) {
                locked |= sinceShot >= BACK && open;
                drawn = locked && sinceShot >= BACK ? 1f : slideAt(sinceShot);
                if (!caseThrown && (drawn >= THROWN_AT || sinceShot >= BACK)) {
                    caseThrown = true;
                    thrown = true;
                }
                if (sinceShot > CYCLES_FOR) {
                    sinceShot = NOT_CYCLING;
                }
            }
            if (doing == ArmActs.RACKING) {
                // The slide goes with the hand that racks it, back, and once the hand lets go of it forward of itself,
                // or not, if the magazine it comes back over is empty.
                if (through >= ArmHolds.SLIDE_LET_GO) {
                    locked = open;
                }
                drawn = Math.max(drawn, ArmHolds.slideRacked(through));
            }
            // A round chambered, by racking it or by the slide let go, and it is shut.
            locked &= !chambered;
            back = locked ? 1f : drawn;
        }
    }

    @Override
    void work(ViewCamera.Pose camera, float seconds) {
        ViewModel.Held held = view.viewModel.held;
        if (held != null) {
            int frame = Pipeline.frame();
            action.step(held.itemId(), workedIn == frame - 1, held.shots(), held.chambered(), held.rounds(), held.clip(), held.doing(), held.done() / Math.max(held.pace(), ArmActs.SHORTEST),
                    seconds);
            if (action.thrown) {
                cases.out(camera, model, drawn.port[0], drawn.port[1] - action.back * drawn.travel * 0.5f, drawn.port[2]);
            }
            magazineIn = magazineIn(held.clip(), held.doing(), held.seated());
            workedIn = frame;
        }
        cases.fly(camera, seconds);
    }

    @Override
    void parts(Program drawing) {
        part(FIXED);
        drawing.set("uSlide", action.back * drawn.travel);
        part(SLIDE);
        drawing.set("uSlide", 0f);
        if (magazineIn) {
            part(MAGAZINE);
        }
        cases.draw(this, drawing, CASE);
    }
}
