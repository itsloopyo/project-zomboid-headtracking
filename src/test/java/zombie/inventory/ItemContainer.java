package zombie.inventory;

import java.util.ArrayList;

/** The tests' stand-in for what a container on the map holds. */
public class ItemContainer {
    private final ArrayList<Object> items = new ArrayList<>();

    public ArrayList<Object> getItems() {
        return items;
    }
}
