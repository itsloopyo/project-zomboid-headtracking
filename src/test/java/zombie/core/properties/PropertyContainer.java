package zombie.core.properties;

import java.util.Map;
import zombie.iso.SpriteDetails.IsoFlagType;

/**
 * The tests' stand-in for the game's class of the same name: a sprite's properties as the tile
 * definitions give them, a flag being a property with no value. It has the members the mod
 * calls, with the stub's signatures, so the mod's compiled code runs against it. The tests' class
 * path holds no game, so only what a test hands the mod is ever one of these.
 *
 * Every property is in one map here, asked for by name or as an IsoFlagType alike. The game's
 * set(String, String, boolean) can put a name that is an IsoFlagType among the flags instead,
 * where has(String) does not find it: a rule that asks for such a flag by its name passes here
 * and may not in the game.
 */
public final class PropertyContainer {
    private final Map<String, String> all;

    public PropertyContainer(Map<String, String> all) {
        this.all = all;
    }

    public boolean has(IsoFlagType flag) {
        return all.containsKey(flag.name());
    }

    /** As the game reads Surface: a whole number from 0 to 127, and 0 for anything else. */
    public int getSurface() {
        return smallWhole("Surface");
    }

    /** As the game reads ItemHeight: a whole number from 0 to 127, and 0 for anything else. */
    public int getItemHeight() {
        return smallWhole("ItemHeight");
    }

    private int smallWhole(String property) {
        try {
            int number = Integer.parseInt(all.getOrDefault(property, ""));
            return number >= 0 && number <= 127 ? number : 0;
        } catch (NumberFormatException notAWholeNumber) {
            return 0;
        }
    }

    public boolean isTable() {
        return all.containsKey("IsTable");
    }

    public boolean has(String property) {
        return all.containsKey(property);
    }

    public String get(String property) {
        return all.get(property);
    }
}
