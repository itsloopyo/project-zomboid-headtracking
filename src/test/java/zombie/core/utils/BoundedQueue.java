package zombie.core.utils;

import java.util.ArrayList;

/**
 * The tests' stand-in for the game's queue of the same name, which holds a chunk's floor blood.
 * It has the members the mod calls, with the stub's signatures, and no bound.
 */
public final class BoundedQueue<E> {
    private final ArrayList<E> held = new ArrayList<>();

    public E get(int index) {
        return held.get(index);
    }

    public void add(E element) {
        held.add(element);
    }

    public E remove(int index) {
        return held.remove(index);
    }

    public int size() {
        return held.size();
    }
}
