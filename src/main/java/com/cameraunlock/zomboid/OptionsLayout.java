package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The order of the mod's settings on the game's options screen (OptionsPage), under titles of their own. */
final class OptionsLayout {
    /**
     * A title on the page and the settings under it: those named, in that order, then every
     * other of a section of the file, in core's order.
     */
    record Group(String title, String section, String... ids) {}

    /** The page from the top. Voice is last because each player's microphone list follows its settings. */
    static final Group[] PAGE = {
        new Group("Head tracking", null, "TrackingMode", "AimMode", "WorldSpaceYaw"),
        new Group("First person", null, "FieldOfView", "Crosshair", "MuzzleFlash", "RegripTwoHandedWeapons", "HillHeight"),
        new Group("Graphics", null, "Quality", "IsometricQuality", "Style"),
        new Group("Custom graphics", "Graphics"),
        new Group("Vanilla content", "Content"),
        new Group("Offline rules", "Rules"),
        new Group("Split screen", "Players", "SplitScreen"),
        new Group("Voice", "Voice"),
    };

    private OptionsLayout() {}

    /**
     * The places in options of the settings under each group of PAGE, in the order the page shows
     * them. Throws for a setting under no title or under two, and for a name that is no setting.
     */
    static int[][] of(CameraUnlock.Option[] options) {
        int[] groupOf = new int[options.length];
        Arrays.fill(groupOf, -1);
        int[][] layout = new int[PAGE.length][];
        for (int group = 0; group < PAGE.length; group++) {
            List<Integer> under = new ArrayList<>();
            for (String id : PAGE[group].ids) {
                int option = 0;
                while (option < options.length && !options[option].id.equals(id)) {
                    option++;
                }
                if (option == options.length) {
                    throw new IllegalStateException(id + " is put under " + PAGE[group].title + " on the options screen and is no setting there");
                }
                under.add(option);
            }
            for (int option = 0; option < options.length; option++) {
                if (options[option].section.equals(PAGE[group].section) && !under.contains(option)) {
                    under.add(option);
                }
            }
            for (int option : under) {
                if (groupOf[option] >= 0) {
                    throw new IllegalStateException(options[option].id + " is under both " + PAGE[groupOf[option]].title + " and " + PAGE[group].title
                            + " on the options screen");
                }
                groupOf[option] = group;
            }
            layout[group] = under.stream().mapToInt(Integer::intValue).toArray();
        }
        for (int option = 0; option < options.length; option++) {
            if (groupOf[option] < 0) {
                throw new IllegalStateException(options[option].id + " of [" + options[option].section + "] is under no title of the options screen");
            }
        }
        return layout;
    }
}
