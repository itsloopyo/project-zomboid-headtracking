package zombie.iso;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * The tests' stand-in for the game's pictures of what containers hold, kept as the game keeps them: by the sprite each
 * is painted over, an entry of a picture of many things and one of few.
 */
public class ContainerOverlays {
    public static final ContainerOverlays instance = new ContainerOverlays();

    /** Painted over test_shelf_0: TEST_MANY full, TEST_FEW nearly empty. TEST_BOTH is many over test_shelf_1 and few over test_shelf_2. */
    public static final String TEST_MANY = "test_overlay_many_0", TEST_FEW = "test_overlay_few_0", TEST_BOTH = "test_overlay_both_0";

    static final class ContainerOverlay {
        public final ArrayList<ContainerOverlayEntry> entries = new ArrayList<>();
    }

    static final class ContainerOverlayEntry {
        public String manyItems;
        public String fewItems;
    }

    private final HashMap<String, ContainerOverlay> overlayMap = new HashMap<>();
    private final HashMap<String, ArrayList<String>> overlayNameToUnderlyingName = new HashMap<>();

    private ContainerOverlays() {
        add("test_shelf_0", TEST_MANY, TEST_FEW);
        add("test_shelf_1", TEST_BOTH, "test_overlay_few_1");
        add("test_shelf_2", "test_overlay_many_2", TEST_BOTH);
        add("test_bin_0", "test_overlay_many_3", null);
    }

    /** As the game's Lua adds them: over under, many painted while it holds many things, few (or null for none) while it holds few. */
    public void add(String under, String many, String few) {
        ContainerOverlayEntry entry = new ContainerOverlayEntry();
        entry.manyItems = many;
        entry.fewItems = few;
        overlayMap.computeIfAbsent(under, name -> new ContainerOverlay()).entries.add(entry);
        for (String picture : new String[] {many, few}) {
            if (picture != null) {
                overlayNameToUnderlyingName.computeIfAbsent(picture, name -> new ArrayList<>()).add(under);
            }
        }
    }

    public ArrayList<String> getUnderlyingSpriteNames(String overlay) {
        return overlayNameToUnderlyingName.get(overlay);
    }
}
