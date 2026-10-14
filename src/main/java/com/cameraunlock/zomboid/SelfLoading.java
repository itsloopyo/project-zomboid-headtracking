package com.cameraunlock.zomboid;

/**
 * The action of a self-loading gun the mod draws, a pistol's slide or a rifle's bolt and its
 * handle, worked from what the game says of the gun frame by frame: driven back and let run
 * forward by each shot, which throws the fired case out, held back by an empty magazine once
 * back from the last round or from racking, and drawn back and let go by the hand that racks it.
 */
final class SelfLoading {
    private SelfLoading() {}

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

    /** How far back the slide is at a time after a shot, 0 to 1. */
    static float slideAt(float seconds) {
        return cycledAt(seconds, BACK, HELD_BACK, FORWARD);
    }

    /** How far back a slide or bolt is at a time after a shot, 0 to 1, that is driven back in `back`, held there `held` and runs home in `forward`, in seconds. */
    static float cycledAt(float seconds, float back, float held, float forward) {
        return ViewModelMath.smooth(seconds / back) - ViewModelMath.smooth((seconds - back - held) / forward);
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
     * or from racking, and drawn back and let go by the hand that racks it. A self-loading
     * rifle's bolt carrier is worked the same way, on its own timings.
     */
    static final class Slide {
        /** How long it takes over each part of a shot, in seconds: driven back, a moment held there, run forward. */
        private final float backIn, heldFor, forwardIn;
        private int item = NO_ITEM;
        private int shotsSeen;
        private float sinceShot = NOT_CYCLING;
        private boolean locked, caseThrown;
        /** How far back the slide is, 0 to 1. */
        float back;
        /** How many fired cases the step just taken threw out: one for each shot, however many came in one frame. */
        int thrown;

        /** A pistol's slide. */
        Slide() {
            this(BACK, HELD_BACK, FORWARD);
        }

        Slide(float backIn, float heldFor, float forwardIn) {
            this.backIn = backIn;
            this.heldFor = heldFor;
            this.forwardIn = forwardIn;
        }

        /** Whether it is held back by an empty magazine. */
        boolean locked() {
            return locked;
        }

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
            thrown = 0;
            boolean open = heldOpen(chambered, rounds, clip);
            if (item != this.item || !carriedOn) {
                // Taken in hand or seen again: as the game has it now, its slide held back if it is empty.
                this.item = item;
                shotsSeen = shots;
                sinceShot = NOT_CYCLING;
                locked = open;
            }
            if (shots != shotsSeen) {
                // Shots taken between two frames each throw their case: all but the last at once, with the case of a
                // shot before them that had not yet left. The last is worked as any shot is.
                thrown += shots - shotsSeen - 1 + (sinceShot != NOT_CYCLING && !caseThrown ? 1 : 0);
                shotsSeen = shots;
                sinceShot = 0f;
                caseThrown = false;
            } else if (sinceShot != NOT_CYCLING) {
                sinceShot += seconds;
            }
            float drawn = 0f;
            if (sinceShot != NOT_CYCLING) {
                locked |= sinceShot >= backIn && open;
                drawn = locked && sinceShot >= backIn ? 1f : cycledAt(sinceShot, backIn, heldFor, forwardIn);
                if (!caseThrown && (drawn >= THROWN_AT || sinceShot >= backIn)) {
                    caseThrown = true;
                    thrown++;
                }
                if (sinceShot > backIn + heldFor + forwardIn) {
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

        /** The game's frame the action was last moved on in. */
        private int workedIn = Integer.MIN_VALUE;

        /**
         * Moves it on by the game's frame with what is held: shots taken in a frame it was not
         * moved on in the frame before are not worked.
         */
        void step(ViewModel.Held held, float seconds) {
            int frame = Pipeline.frame();
            step(held.itemId(), workedIn == frame - 1, held.shots(), held.chambered(), held.rounds(), held.clip(), held.doing(),
                    held.done() / Math.max(held.pace(), ArmActs.SHORTEST), seconds);
            workedIn = frame;
        }
    }

}
