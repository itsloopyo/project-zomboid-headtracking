package zombie.iso;

import gnu.trove.map.hash.THashMap;
import java.util.ArrayList;

public class ContainerOverlays {
    public static ContainerOverlays instance;
    private THashMap<String, ContainerOverlay> overlayMap;
    public ArrayList<String> getUnderlyingSpriteNames(String overlay) { throw new UnsupportedOperationException("reference stub"); }

    static final class ContainerOverlay {
        public final ArrayList<ContainerOverlayEntry> entries = new ArrayList<>();
    }

    static final class ContainerOverlayEntry {
        public String manyItems;
        public String fewItems;
    }
}
