package com.cameraunlock.zomboid;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * What the mod works out once for a key and keeps, which any thread may ask for at once: the
 * logic thread and the build threads. A key is equal only to itself unless its class says
 * otherwise, as the game's sprites, textures and rooms do not. Two threads that work out the
 * same key at once both keep what they found, and whichever kept first is what every asker gets
 * from then on (keep). None is kept as well as anything.
 */
final class Known<K, V> {
    private static final Object NONE = new Object();
    private final ConcurrentHashMap<K, Object> kept = new ConcurrentHashMap<>();

    /** What is kept for the key: null for nothing kept, or none. */
    @SuppressWarnings("unchecked")
    V get(K key) {
        Object known = kept.get(key);
        return known == NONE ? null : (V) known;
    }

    /** Whether anything is kept for the key, none included. */
    boolean has(K key) {
        return kept.containsKey(key);
    }

    /** Keeps what was worked out for the key, unless something was kept for it first, and gives what is kept. */
    @SuppressWarnings("unchecked")
    V keep(K key, V value) {
        Object was = kept.putIfAbsent(key, value == null ? NONE : value);
        return was == null ? value : was == NONE ? null : (V) was;
    }

    /** Keeps what was worked out again for the key in place of what was. */
    void replace(K key, V value) {
        kept.put(key, value == null ? NONE : value);
    }

    /** Lets go of what is kept for every key whose value answers to the test. */
    @SuppressWarnings("unchecked")
    void dropIf(Predicate<V> test) {
        kept.values().removeIf(known -> test.test(known == NONE ? null : (V) known));
    }

    /** What is kept now, as another that changes apart from this one. */
    Known<K, V> copy() {
        Known<K, V> copy = new Known<>();
        copy.kept.putAll(kept);
        return copy;
    }

    /** Keeps what the other keeps in place of what this one does. */
    void keepAll(Known<K, V> other) {
        kept.clear();
        kept.putAll(other.kept);
    }

    void clear() {
        kept.clear();
    }

    int size() {
        return kept.size();
    }
}
