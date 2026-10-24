package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Collection;

/**
 * Values by a long key, none of them null, with nothing made to look one up: a HashMap keyed
 * by Long makes a Long for every get. Nothing is taken out but by clear. One thread.
 */
final class LongTable<V> {
    private long[] keys = new long[64];
    private Object[] values = new Object[64];
    private int size;

    V get(long key) {
        int mask = keys.length - 1;
        for (int at = place(key, keys.length); ; at = at + 1 & mask) {
            Object value = values[at];
            if (value == null) {
                return null;
            }
            if (keys[at] == key) {
                @SuppressWarnings("unchecked")
                V held = (V) value;
                return held;
            }
        }
    }

    boolean containsKey(long key) {
        return get(key) != null;
    }

    void put(long key, V value) {
        if (value == null) {
            throw new IllegalArgumentException("a LongTable holds no null, and " + key + " was given one");
        }
        int mask = keys.length - 1;
        int at = place(key, keys.length);
        while (values[at] != null) {
            if (keys[at] == key) {
                values[at] = value;
                return;
            }
            at = at + 1 & mask;
        }
        keys[at] = key;
        values[at] = value;
        if (++size * 2 > keys.length) {
            grow();
        }
    }

    void clear() {
        if (size > 0) {
            Arrays.fill(values, null);
            size = 0;
        }
    }

    void addValuesTo(Collection<? super V> into) {
        for (Object value : values) {
            if (value != null) {
                @SuppressWarnings("unchecked")
                V held = (V) value;
                into.add(held);
            }
        }
    }

    private void grow() {
        long[] oldKeys = keys;
        Object[] oldValues = values;
        keys = new long[oldKeys.length * 2];
        values = new Object[oldValues.length * 2];
        int mask = keys.length - 1;
        for (int from = 0; from < oldKeys.length; from++) {
            if (oldValues[from] != null) {
                int at = place(oldKeys[from], keys.length);
                while (values[at] != null) {
                    at = at + 1 & mask;
                }
                keys[at] = oldKeys[from];
                values[at] = oldValues[from];
            }
        }
    }

    /** Keys packed from two ints that differ in their low bits alone would land side by side: multiplied, every bit has its say in the top ones. */
    private static int place(long key, int length) {
        return (int) (key * 0x9E3779B97F4A7C15L >>> 64 - Integer.numberOfTrailingZeros(length));
    }
}
