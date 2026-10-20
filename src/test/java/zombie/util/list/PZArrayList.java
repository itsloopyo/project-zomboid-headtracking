package zombie.util.list;

import java.util.AbstractList;
import java.util.ArrayList;

/** The tests' stand-in for the game's list a square keeps its objects in. See zombie.core.properties.PropertyContainer. */
public final class PZArrayList<E> extends AbstractList<E> {
    private final ArrayList<E> items = new ArrayList<>();

    @Override
    public E get(int index) {
        return items.get(index);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean add(E item) {
        return items.add(item);
    }

    @Override
    public E remove(int index) {
        return items.remove(index);
    }
}
