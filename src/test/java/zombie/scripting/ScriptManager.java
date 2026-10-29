package zombie.scripting;

import java.util.HashMap;
import zombie.scripting.objects.Item;

/** The test's stand-in for the game's scripts: FindItem looks a name up in a map, and counts its lookups. */
public final class ScriptManager {
    public final HashMap<String, Item> items = new HashMap<>();
    public int lookups;
    /** Run in the middle of each lookup, as the scripts changing on another thread would be. */
    public Runnable duringLookup;

    public Item FindItem(String name, boolean orBase) {
        lookups++;
        if (duringLookup != null) {
            duringLookup.run();
        }
        return items.get(name);
    }
}
