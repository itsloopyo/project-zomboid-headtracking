package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * What the options page (OptionsPage) holds for each of the mod's settings from the moment the
 * screen opens until it is applied, and what follows on the page from a change, with no Lua in
 * it: OptionsPage puts it on the game's controls. A value is always core's number for the option,
 * whatever the control shows: a row of [Content] is ticked for 0.
 */
final class OptionsDraft {
    /** One setting of the page. */
    static final class Setting {
        final int option;
        final CameraUnlock.Option described;
        /** Core's value for each line of a list, in the order the list shows them; null for a tick box or a slider. */
        final int[] values;
        /** The value the screen showed as it opened or was last applied, which an apply compares with. */
        double shown;
        /** The value the control holds now, which the player or a quick list may have changed since. */
        double staged;
        /** For a row of [Graphics]: the value it was last given, which its slider may hold rounded to a notch, and what it held for it. */
        double given, givenShown;

        Setting(int option, CameraUnlock.Option described) {
            this.option = option;
            this.described = described;
            values = described.kind != CameraUnlock.OPTION_ENUM ? null
                    : described.source == CameraUnlock.OPTION_AIM_MODE ? Tracking.AIM_MODES : everyChoice(described);
        }

        private static int[] everyChoice(CameraUnlock.Option described) {
            int[] values = new int[described.choices.length];
            Arrays.setAll(values, choice -> choice);
            return values;
        }

        boolean slider() {
            return described.kind != CameraUnlock.OPTION_BOOL && values == null;
        }

        /** The place in the list of the line that stands for a value. */
        int lineOf(int value) {
            for (int line = 0; line < values.length; line++) {
                if (values[line] == value) {
                    return line;
                }
            }
            throw new IllegalStateException(described.id + " holds " + value + ", which is none of the lines its list shows");
        }
    }

    /** The game's controls, which the draft sets. */
    interface Controls {
        /** Sets a setting's control to a value, and answers what the control then holds: a slider holds the notch nearest it. */
        double put(Setting setting, double value);
    }

    /** The settings in the order the page shows them. */
    final List<Setting> settings = new ArrayList<>();
    final List<OptionsLayout.QuickList> quickLists;
    private final Map<String, Setting> byId = new HashMap<>();
    /** Player 1's first person graphics mode, and the rows of [Graphics], which show its picture. */
    private final Setting quality;
    private final List<Setting> graphics = new ArrayList<>();
    /**
     * Whether the rows of [Graphics] were last given Custom's own picture, as the screen opened
     * in Custom or as Custom was chosen. Left so, they ask Custom for nothing: an edit of the file
     * taken since stands.
     */
    private boolean rowsFromCustom;

    /** The settings of a page laid out as OptionsLayout.of lays out options. */
    OptionsDraft(CameraUnlock.Option[] options, int[][] layout) {
        for (int[] group : layout) {
            for (int option : group) {
                Setting setting = new Setting(option, options[option]);
                settings.add(setting);
                byId.put(setting.described.id, setting);
                if (Config.graphicsPart(setting.described.id) >= 0) {
                    graphics.add(setting);
                }
            }
        }
        if (graphics.size() != Config.GRAPHICS.length) {
            throw new IllegalStateException("the options screen has " + graphics.size() + " of the " + Config.GRAPHICS.length + " rows of [Graphics]");
        }
        quality = setting(Config.qualityKey(0));
        quickLists = OptionsLayout.quickLists(options, layout);
    }

    Setting setting(String id) {
        Setting setting = byId.get(id);
        if (setting == null) {
            throw new IllegalStateException(id + " is no setting of the options screen");
        }
        return setting;
    }

    /** What the page holds now for a setting, by its key. */
    double staged(String id) {
        return setting(id).staged;
    }

    /** A value as a setting's control holds it: a slider's on its nearest notch. */
    static double onNotch(Setting setting, double value) {
        return setting.slider() ? Tracking.notch(value, setting.described.step) : value;
    }

    /** Each setting given what the session holds now, as the screen opens: what it shows, and what an apply compares with. */
    void fill() {
        for (Setting setting : settings) {
            double value = Tracking.optionValue(setting.option);
            setting.given = value;
            setting.shown = setting.staged = setting.givenShown = onNotch(setting, value);
        }
        rowsFromCustom = quality.staged == Quality.CUSTOM;
    }

    /** What a slider holds once the game has filled the screen: the notch it shows, which an apply compares with. */
    void read(Setting setting, double holds) {
        setting.shown = setting.staged = setting.givenShown = holds;
    }

    /** A setting the player changed on the screen, to a value. What follows from it is put on the controls. */
    void changed(Setting setting, double value, Controls controls) {
        double before = setting.staged;
        setting.staged = value;
        follow(setting, before, controls);
    }

    /** A line of a quick list chosen, 0 for Mixed: the settings it switches are put on the controls. Mixed puts nothing. */
    void chosen(int list, int line, Controls controls) {
        if (line == 0) {
            return;
        }
        for (Map.Entry<String, Double> target : quickLists.get(list).sets().get(line - 1).given(this::staged).entrySet()) {
            Setting setting = setting(target.getKey());
            if (setting.staged != target.getValue()) {
                double before = setting.staged;
                setting.staged = controls.put(setting, target.getValue());
                follow(setting, before, controls);
            }
        }
    }

    /** The line of a quick list whose settings the page holds, or 0 for Mixed. */
    int lineHeld(int list) {
        return OptionsLayout.lineHeld(quickLists.get(list), this::staged);
    }

    /**
     * What follows on the page from a setting set to another value: player 1's first person
     * graphics mode has the rows of [Graphics] show its picture, and a row of [Graphics] changed
     * switches that mode to Custom.
     */
    private void follow(Setting setting, double before, Controls controls) {
        if (setting == quality) {
            if (setting.staged == before) {
                return;
            }
            double[] picture = Config.values(Quality.pictureOf((int) setting.staged));
            for (Setting row : graphics) {
                double value = picture[Config.graphicsPart(row.described.id)];
                row.staged = controls.put(row, value);
                row.given = value;
                row.givenShown = row.staged;
            }
            rowsFromCustom = setting.staged == Quality.CUSTOM;
        } else if (Config.graphicsPart(setting.described.id) >= 0 && quality.staged != Quality.CUSTOM) {
            quality.staged = controls.put(quality, Quality.CUSTOM);
        }
    }

    /**
     * The screen applied, its controls holding what held reads from them: the session takes each
     * setting that differs from what the screen showed (Tracking.applyScreen), and with player 1's
     * first person graphics mode at Custom, the rows of [Graphics] as the picture it draws, where
     * they hold other than Custom's own as they were given it and that differs from Custom's now.
     * A row the player left alone keeps the value it was given, which its slider may hold rounded.
     * Answers the keys of the settings taken.
     */
    Set<String> apply(ToDoubleFunction<Setting> held) {
        Map<String, Double> changed = new LinkedHashMap<>();
        for (Setting setting : settings) {
            setting.staged = held.applyAsDouble(setting);
            if (setting.staged != setting.shown && Config.graphicsPart(setting.described.id) < 0) {
                changed.put(setting.described.id, setting.staged);
            }
        }
        double[] picture = new double[Config.GRAPHICS.length];
        boolean rowsChanged = false;
        for (Setting row : graphics) {
            picture[Config.graphicsPart(row.described.id)] = row.staged == row.givenShown ? row.given : row.staged;
            rowsChanged |= row.staged != row.givenShown;
        }
        Quality.Picture asked = Config.picture(picture);
        boolean asks = quality.staged == Quality.CUSTOM && (rowsChanged || !rowsFromCustom) && !asked.equals(Quality.custom());
        Set<String> taken = Tracking.applyScreen(changed, asks ? asked : null);
        if (taken.contains(quality.described.id)) {
            rowsFromCustom = quality.staged == Quality.CUSTOM;
        }
        for (Setting setting : settings) {
            if (taken.contains(setting.described.id)) {
                setting.shown = setting.staged;
                int part = Config.graphicsPart(setting.described.id);
                if (part >= 0) {
                    setting.given = picture[part];
                    setting.givenShown = setting.staged;
                }
            }
        }
        return taken;
    }
}
