package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Random;

/** Tests of the room kept for each chunk's triangles in the one buffer the graphics card holds them in. */
final class BufferTests {
    private BufferTests() {}

    static void run() {
        blocksAreTakenFromTheFirstGapThatHoldsThem();
        blocksGivenBackJoinTheirNeighbours();
        aBlockThatWasNotTakenIsRefused();
        blocksComingAndGoingNeverOverlap();
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static void blocksAreTakenFromTheFirstGapThatHoldsThem() {
        BufferSpace space = new BufferSpace();
        check(space.take(12) == -1, "a buffer never made has no room");
        space.reset(100);
        check(space.take(40) == 0 && space.take(30) == 40 && space.take(30) == 70, "blocks follow one another");
        check(space.free() == 0 && space.take(1) == -1, "and a full buffer has no room");
        space.give(40, 30);
        check(space.take(31) == -1, "a gap too short is passed over");
        check(space.take(10) == 40 && space.take(20) == 50, "and a block goes at the start of the first that is not");
    }

    private static void blocksGivenBackJoinTheirNeighbours() {
        BufferSpace space = new BufferSpace();
        space.reset(90);
        space.take(30);
        space.take(30);
        space.take(30);
        space.give(0, 30);
        space.give(60, 30);
        check(space.take(60) == -1, "two gaps apart are not one");
        space.give(30, 30);
        check(space.free() == 90 && space.take(90) == 0, "the block between them makes the three one");
        space.give(0, 90);
        space.take(30);
        space.take(30);
        space.give(30, 30);
        check(space.take(60) == 30, "a block joins the gap after it");
        space.give(30, 60);
        space.give(0, 30);
        check(space.take(90) == 0, "and the gap before it");
    }

    private static void aBlockThatWasNotTakenIsRefused() {
        BufferSpace space = new BufferSpace();
        space.reset(60);
        space.take(60);
        space.give(12, 24);
        boolean refused = false;
        try {
            space.give(24, 24);
        } catch (IllegalStateException expected) {
            refused = true;
        }
        check(refused, "a block that lies over a gap was never taken");
    }

    private static void blocksComingAndGoingNeverOverlap() {
        int capacity = 1 << 16;
        BufferSpace space = new BufferSpace();
        space.reset(capacity);
        boolean[] used = new boolean[capacity];
        ArrayList<int[]> held = new ArrayList<>();
        Random random = new Random(7);
        int inUse = 0;
        for (int step = 0; step < 20000; step++) {
            if (!held.isEmpty() && random.nextInt(100) < 48) {
                int[] block = held.remove(random.nextInt(held.size()));
                space.give(block[0], block[1]);
                java.util.Arrays.fill(used, block[0], block[0] + block[1], false);
                inUse -= block[1];
            } else {
                int size = 12 * (1 + random.nextInt(300));
                int at = space.take(size);
                if (at < 0) {
                    continue;
                }
                check(at + size <= capacity, "a block lies inside the buffer");
                for (int i = at; i < at + size; i++) {
                    check(!used[i], "a block is given to one holder at a time");
                    used[i] = true;
                }
                held.add(new int[] {at, size});
                inUse += size;
            }
            check(space.free() == capacity - inUse, "what is free is what is not held");
        }
        for (int[] block : held) {
            space.give(block[0], block[1]);
        }
        check(space.take(capacity) == 0, "with everything given back the buffer is one gap again");
    }
}
