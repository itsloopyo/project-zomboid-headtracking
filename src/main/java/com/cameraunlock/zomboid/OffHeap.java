package com.cameraunlock.zomboid;

import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Memory outside the Java heap that goes back to the system the moment it is freed.
 *
 * A buffer from ByteBuffer.allocateDirect goes back only when the collector next finds the
 * small object that stands for it, and this game's collector may not look for minutes: with
 * tens of megabytes of them made and dropped a second, standing still in first person held
 * two gigabytes more than was in use. What is taken here is freed by whoever took it.
 */
final class OffHeap {
    /** A piece of memory and the means to give it back. Nothing may read or write the bytes once free has run: the graphics driver reads them by address and would read what is no longer ours. */
    static final class Block {
        final ByteBuffer bytes;
        private final Arena arena;

        private Block(ByteBuffer bytes, Arena arena) {
            this.bytes = bytes;
            this.arena = arena;
        }

        void free() {
            arena.close();
        }
    }

    private OffHeap() {}

    /** A block of this many bytes in the machine's byte order, zeroed, which any thread may use and any thread may free. */
    static Block take(int bytes) {
        Arena arena = Arena.ofShared();
        return new Block(arena.allocate(bytes, Long.BYTES).asByteBuffer().order(ByteOrder.nativeOrder()), arena);
    }
}
