package zombie.iso;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** The tests' stand-in for the game's random things set out over tiles: which tiles each picture is laid over. */
public class TileOverlays {
    public static final TileOverlays instance = new TileOverlays();

    private final HashMap<String, ArrayList<String>> overlayNameToUnderlyingName = new HashMap<>();

    /** As the game's Lua adds them: picture is set out over each of under. */
    public void add(String picture, String... under) {
        overlayNameToUnderlyingName.computeIfAbsent(picture, name -> new ArrayList<>()).addAll(List.of(under));
    }

    public ArrayList<String> getUnderlyingSpriteNames(String overlay) {
        return overlayNameToUnderlyingName.get(overlay);
    }
}
