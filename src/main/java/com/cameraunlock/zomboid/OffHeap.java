package com.cameraunlock.zomboid;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Memory outside the Java heap that goes back to the system the moment it is freed.
 *
 * A buffer from ByteBuffer.allocateDirect goes back only when the collector next finds the
 * small object that stands for it, and this game's collector may not look for minutes. What
 * is taken here is the C library's, and is freed by whoever took it. Java's own way of
 * freeing by hand, a shared Arena, stops every thread of the game for each block freed.
 */
@SuppressWarnings("restricted")
final class OffHeap {
    private static final MethodHandle MALLOC, FREE;

    static {
        Linker linker = Linker.nativeLinker();
        MALLOC = linker.downcallHandle(linker.defaultLookup().findOrThrow("malloc"),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        FREE = linker.downcallHandle(linker.defaultLookup().findOrThrow("free"), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    }

    /**
     * A piece of memory and the means to give it back. Nothing checks a read or a write of the
     * bytes once free has run, from Java or from the graphics driver, which is handed their
     * address: whoever frees a block must hold the only reference to its bytes.
     */
    static final class Block {
        final ByteBuffer bytes;
        private final MemorySegment memory;
        private boolean freed;

        private Block(MemorySegment memory, int size) {
            this.memory = memory;
            this.bytes = memory.reinterpret(size).asByteBuffer().order(ByteOrder.nativeOrder());
        }

        synchronized void free() {
            if (freed) {
                throw new IllegalStateException("a block freed twice");
            }
            freed = true;
            try {
                FREE.invokeExact(memory);
            } catch (Throwable thrown) {
                throw new IllegalStateException(thrown);
            }
        }
    }

    private OffHeap() {}

    /** A block of this many bytes in the machine's byte order, holding whatever the memory last held, which any thread may use and any thread may free. */
    static Block take(int bytes) {
        MemorySegment memory;
        try {
            memory = (MemorySegment) MALLOC.invokeExact((long) bytes);
        } catch (Throwable thrown) {
            throw new IllegalStateException(thrown);
        }
        if (memory.address() == 0L) {
            throw new OutOfMemoryError("no " + bytes + " bytes outside the Java heap");
        }
        return new Block(memory, bytes);
    }
}
