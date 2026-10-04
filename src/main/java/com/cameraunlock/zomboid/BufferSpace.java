package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * Which parts of one long buffer are free. A block is taken from the first gap that holds it,
 * and a block given back joins the gaps on either side of it, so that blocks of much the same
 * size coming and going every frame keep finding room where the last ones were.
 */
final class BufferSpace {
    /** The gaps, in the order they lie in the buffer. No two touch. */
    private int[] start = new int[64], length = new int[64];
    private int gaps;
    private int free;

    /** Makes the whole of a buffer this long free. */
    void reset(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("a buffer of " + capacity);
        }
        gaps = 0;
        free = capacity;
        if (capacity > 0) {
            start[0] = 0;
            length[0] = capacity;
            gaps = 1;
        }
    }

    /** How much is free in all, which may be in more pieces than one. */
    int free() {
        return free;
    }

    /** Where a block of this size now is, or -1 when no gap is long enough. */
    int take(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("a block of " + size);
        }
        for (int gap = 0; gap < gaps; gap++) {
            if (length[gap] < size) {
                continue;
            }
            int at = start[gap];
            if (length[gap] == size) {
                gaps--;
                System.arraycopy(start, gap + 1, start, gap, gaps - gap);
                System.arraycopy(length, gap + 1, length, gap, gaps - gap);
            } else {
                start[gap] += size;
                length[gap] -= size;
            }
            free -= size;
            return at;
        }
        return -1;
    }

    /** Frees a block that take handed out. */
    void give(int at, int size) {
        if (size <= 0 || at < 0) {
            throw new IllegalArgumentException("a block of " + size + " at " + at);
        }
        int after = 0;
        while (after < gaps && start[after] < at) {
            after++;
        }
        boolean joinsBefore = after > 0 && start[after - 1] + length[after - 1] == at;
        boolean joinsAfter = after < gaps && at + size == start[after];
        if (after > 0 && start[after - 1] + length[after - 1] > at || after < gaps && at + size > start[after]) {
            throw new IllegalStateException("the block of " + size + " at " + at + " is not one that was taken");
        }
        free += size;
        if (joinsBefore && joinsAfter) {
            length[after - 1] += size + length[after];
            gaps--;
            System.arraycopy(start, after + 1, start, after, gaps - after);
            System.arraycopy(length, after + 1, length, after, gaps - after);
        } else if (joinsBefore) {
            length[after - 1] += size;
        } else if (joinsAfter) {
            start[after] = at;
            length[after] += size;
        } else {
            if (gaps == start.length) {
                start = Arrays.copyOf(start, gaps * 2);
                length = Arrays.copyOf(length, gaps * 2);
            }
            System.arraycopy(start, after, start, after + 1, gaps - after);
            System.arraycopy(length, after, length, after + 1, gaps - after);
            start[after] = at;
            length[after] = size;
            gaps++;
        }
    }
}
