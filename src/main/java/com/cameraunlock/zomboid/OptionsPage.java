package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.LuaCallFrame;
import zombie.Lua.LuaManager;
import zombie.ui.UIElement;
import zombie.ui.UIManager;

/**
 * The mod's settings on the game's own options screen, under the page the game keeps for mods
 * (PZAPI.ModOptions in its Lua). The controls are cameraunlock-core's options of CameraUnlock.ini:
 * its writable rows, with the tracking mode and the aim mode as a list each. A row is on the
 * screen because Config marks it writable. OptionsLayout puts it under a title and names it, and a
 * writable row it puts under none stops the section from being made.
 *
 * Quick lists head the page (OptionsLayout.quickLists), each switching a class of the settings
 * below together. Choosing a line sets those settings on the page, and each list shows the line
 * the page holds, or Mixed where it holds none of them.
 *
 * Each player's microphone is a name and no option of core's: the page lists the microphones
 * connected as it opens, for each player, under Voice. The keys, which are no options either, are
 * the game's own key controls under the last title (OptionsLayout.KEY_ROWS). Choosing one opens the
 * game's own dialog, which waits for a key and says when another of the game's keys or the mods'
 * has it. Esc there cancels, and a key a row of [Hotkeys] cannot name is refused with the dialog
 * left open (KeyNames). A key list of several keys shows its first, and its tooltip says what the
 * file holds.
 *
 * What the page holds until the screen is applied, and what follows on it from a change, is
 * OptionsDraft's. Nothing is taken until then: the session takes every setting that changed
 * together, then saves them to CameraUnlock.ini (Tracking.applyScreen). Leaving without
 * applying leaves everything as it was, and the page shows the session's settings again the next
 * time it opens. The game saves what its mod page shows in a file of its own, ModOptions.ini, and
 * reads that back when it builds the page: the mod never takes a value from there.
 *
 * Everything runs on the game's logic thread, which is the one its Lua runs on.
 */
final class OptionsPage {
    /** The section's name in the game's Lua, and its heading on the page. */
    private static final String SECTION = "CameraUnlock", HEADING = "Head Tracking";

    /** One setting's control: `table` is the game's Lua table for it. */
    private record Control(OptionsDraft.Setting setting, KahluaTable table, String tip) {}

    /**
     * The list of microphones for one player: `table` is the game's Lua table for it, `names`
     * the name of the microphone each line stands for, as a row of [Voice] holds it, in the order
     * the list shows them, and `shown` the name the list had when the screen opened or was last
     * applied.
     */
    private static final class MicrophoneList {
        final int player;
        final KahluaTable table;
        List<String> names = List.of();
        String shown;

        MicrophoneList(int player, KahluaTable table) {
            this.player = player;
            this.table = table;
        }
    }

    private static final String MICROPHONE_TIP = "Used only to move your character's mouth with what you say, while Talking mouths is on. "
            + "The sound is never recorded or sent.";

    /** One key row's control: `table` is the game's Lua table for it, and `shown` the key it was given as the screen opened or was last applied. */
    private static final class KeyControl {
        final OptionsLayout.KeyRow row;
        final KahluaTable table;
        KeyNames.Binding shown;

        KeyControl(OptionsLayout.KeyRow row, KahluaTable table, KeyNames.Binding shown) {
            this.row = row;
            this.table = table;
            this.shown = shown;
        }
    }

    private static final String REFUSED_KEY = "The mod cannot listen for that key.\nPress another, or Esc to cancel.";

    private static final JavaFunction OPENED = OptionsPage::opened, APPLIED = OptionsPage::applied, CHANGED = OptionsPage::changed,
            QUICK_CHOSEN = OptionsPage::quickChosen, KEY_PRESSED = OptionsPage::keyPressed;

    private static OptionsDraft draft;
    private static List<Control> controls = List.of();
    private static Map<String, Control> byId = Map.of();
    private static Map<KahluaTable, Control> byTable = Map.of();
    /** The game's Lua tables for the quick lists, in the order of the draft's. */
    private static List<KahluaTable> quickTables = List.of();
    private static List<MicrophoneList> microphones = List.of();
    private static List<KeyControl> keyControls = List.of();
    /** MainOptions as the game's Lua has it, and its keyPressHandler, which its key dialog hands each key to. */
    private static KahluaTable mainOptions;
    private static Object gameTakesKey;
    /** Whether the controls hold what the session gave them as the screen last opened: until they do, what they hold is the game's own file's. */
    private static boolean filled;
    /** MainOptions.toUI as the game's Lua has it, which fills the screen's controls as it opens. */
    private static Object gameOpens;

    private OptionsPage() {}

    /**
     * Puts the section in the game's Lua. Called each time the game has loaded its Lua, which it
     * does at its start and again when the mods in use change, and each load forgets the section.
     */
    static void register() {
        if (!(LuaManager.env.rawget("PZAPI") instanceof KahluaTable api) || !(api.rawget("ModOptions") instanceof KahluaTable modOptions)) {
            Log.info("the mod's settings are not on the game's options screen: the game's Lua has no PZAPI.ModOptions");
            return;
        }
        if (!(LuaManager.env.rawget("MainOptions") instanceof KahluaTable screen) || screen.rawget("toUI") == null) {
            Log.info("the mod's settings are not on the game's options screen: the game's Lua has no MainOptions.toUI");
            return;
        }
        if (screen.rawget("keyPressHandler") == null) {
            Log.info("the mod's settings are not on the game's options screen: the game's Lua has no MainOptions.keyPressHandler");
            return;
        }
        if (screen.rawget("toUI") == OPENED) {
            // The Lua that was loaded is the Lua the section is already in.
            return;
        }

        // Before the section is made: one made and then given up on would stay on the screen, working nothing.
        CameraUnlock.Option[] options = Tracking.options();
        for (CameraUnlock.Option described : options) {
            if (!Tracking.takes(described)) {
                throw new IllegalStateException(described.id + " is a setting the session has nowhere to put when it is applied");
            }
            if (described.kind != CameraUnlock.OPTION_BOOL && described.kind != CameraUnlock.OPTION_ENUM && !(described.step > 0)) {
                throw new IllegalStateException(described.id + " has no range for a slider to cover");
            }
        }
        int[][] layout = OptionsLayout.of(OptionsLayout.PAGE, options);
        OptionsDraft drafted = new OptionsDraft(options, layout);
        String[] tips = new String[options.length];
        for (int option = 0; option < options.length; option++) {
            tips[option] = OptionsLayout.tip(options[option], options);
        }
        drafted.fill();

        KahluaTable section = (KahluaTable) call(modOptions, "create", SECTION, HEADING);
        List<Control> made = new ArrayList<>();
        List<KahluaTable> listTables = new ArrayList<>();
        List<MicrophoneList> microphoneLists = new ArrayList<>();
        for (int group = 0; group < OptionsLayout.PAGE.length; group++) {
            OptionsLayout.Group under = OptionsLayout.PAGE[group];
            call(section, "addTitle", under.title());
            if (under.note() != null) {
                call(section, "addDescription", under.note());
            }
            for (int option : layout[group]) {
                made.add(control(section, drafted.setting(options[option].id), tips[option]));
            }
            if (group == 0) {
                for (OptionsLayout.QuickList list : drafted.quickLists) {
                    KahluaTable table = (KahluaTable) call(section, "addComboBox", list.id(), list.label(), list.tip());
                    call(table, "addItem", OptionsLayout.MIXED, false);
                    for (String line : list.lines()) {
                        call(table, "addItem", line, false);
                    }
                    table.rawset("onChange", QUICK_CHOSEN);
                    listTables.add(table);
                }
            }
            if (under.title().equals(OptionsLayout.VOICE)) {
                for (int player = 0; player < Config.MICROPHONE_ROWS.length; player++) {
                    KahluaTable table = (KahluaTable) call(section, "addComboBox", Config.MICROPHONE_ROWS[player],
                            player == 0 ? "Microphone" : "Player " + (player + 1) + "'s microphone", MICROPHONE_TIP);
                    microphoneLists.add(new MicrophoneList(player, table));
                }
            }
        }
        call(section, "addTitle", OptionsLayout.KEYS);
        call(section, "addDescription", OptionsLayout.keysNote());
        List<KeyControl> keys = new ArrayList<>();
        for (OptionsLayout.KeyRow row : OptionsLayout.KEY_ROWS) {
            KeyNames.Binding shown = KeyNames.shown(Tracking.keys(row.id()));
            KahluaTable table = (KahluaTable) call(section, "addKeyBind", row.id(), row.label(), shown.code(), row.tip());
            // What the game's key dialog sets for Default, which addKeyBind takes from the key it was handed.
            table.rawset("defaultkey", (double) KeyNames.shown(Config.DEFAULT_KEYS.get(row.id())).code());
            keys.add(new KeyControl(row, table, shown));
        }

        Map<String, Control> ids = new HashMap<>();
        Map<KahluaTable, Control> tables = new IdentityHashMap<>();
        for (Control control : made) {
            ids.put(control.setting.described.id, control);
            tables.put(control.table, control);
        }
        draft = drafted;
        controls = made;
        byId = ids;
        byTable = tables;
        quickTables = listTables;
        microphones = microphoneLists;
        keyControls = keys;
        listMicrophones();
        showQuickLists();
        filled = false;
        section.rawset("apply", APPLIED);
        gameOpens = screen.rawget("toUI");
        screen.rawset("toUI", OPENED);
        mainOptions = screen;
        gameTakesKey = screen.rawget("keyPressHandler");
        screen.rawset("keyPressHandler", KEY_PRESSED);
        Log.info("the game's options screen has " + made.size() + " of the mod's settings, " + drafted.quickLists.size() + " quick lists, "
                + microphoneLists.size() + " lists of microphones and " + keys.size() + " keys under " + HEADING);
    }

    private static Control control(KahluaTable section, OptionsDraft.Setting setting, String tip) {
        CameraUnlock.Option described = setting.described;
        String label = OptionsLayout.label(described);
        KahluaTable table;
        switch (described.kind) {
            case CameraUnlock.OPTION_BOOL -> table = (KahluaTable) call(section, "addTickBox", described.id, label, false, tip);
            case CameraUnlock.OPTION_INT, CameraUnlock.OPTION_FLOAT -> table = (KahluaTable) call(section, "addSlider", described.id, label, described.min,
                    described.max, described.step, described.min, tip);
            case CameraUnlock.OPTION_ENUM -> {
                table = (KahluaTable) call(section, "addComboBox", described.id, label, tip);
                String[] lines = OptionsLayout.lines(described);
                boolean graphicsMode = Tracking.qualityPlayer(described.id) >= 0;
                for (int choice : setting.values) {
                    call(table, "addItem", graphicsMode ? Quality.choice(choice) : lines[choice], false);
                }
            }
            default -> throw new IllegalStateException(described.id + " is of kind " + described.kind + ", which the page has no control for");
        }
        // The game calls a control's onChange as soon as the player changes it, before the screen is applied.
        table.rawset("onChange", CHANGED);
        Control control = new Control(setting, table, tip);
        write(control);
        return control;
    }

    /**
     * In place of MainOptions.toUI, which the game calls as its options screen opens: the
     * controls are given what the session holds now, which a hotkey or an edit of the file may
     * have changed since the screen was last open, and then the game's own fills the screen from
     * them.
     */
    private static int opened(LuaCallFrame frame, int arguments) {
        filled = false;
        try {
            draft.fill();
            for (Control control : controls) {
                nameLevel(control);
                write(control);
            }
            listMicrophones();
            showQuickLists();
            for (KeyControl key : keyControls) {
                showKeys(key);
            }
            filled = true;
        } catch (RuntimeException failure) {
            // Thrown on, it would stop the game's options screen from opening.
            Log.error("the game's options screen was not given the mod's settings as they are, and what is applied there until it opens again is not taken", failure);
        }
        // The game's own fault, or another mod's, goes on to whoever opened the screen, as it would without this in between.
        ran("MainOptions.toUI", gameOpens, frame.get(0));
        try {
            for (Control control : controls) {
                KahluaTable element = (KahluaTable) control.table.rawget("element");
                if (control.setting.values != null) {
                    // The game gives a tick box of the mod page its tooltip and a list none, though its lists show one.
                    tip(element, control.tip);
                } else if (control.setting.slider()) {
                    // A slider holds the notch nearest what it was given, and that is what it hands back unmoved.
                    draft.read(control.setting, (Double) call(element, "getCurrentValue"));
                }
            }
            for (int list = 0; list < quickTables.size(); list++) {
                tip((KahluaTable) quickTables.get(list).rawget("element"), draft.quickLists.get(list).tip());
            }
        } catch (RuntimeException failure) {
            filled = false;
            Log.error("the mod's controls on the game's options screen were not read back, and what is applied there until it opens again is not taken", failure);
        }
        try {
            for (Control control : controls) {
                if (control.setting.slider()) {
                    KahluaTable slider = (KahluaTable) control.table.rawget("element");
                    // The game makes a slider's name and the number beside it labels of their own and gives neither the tooltip it is handed.
                    nameOf(control, slider).rawset("tooltip", control.tip);
                    ((KahluaTable) slider.rawget("label")).rawset("tooltip", control.tip);
                }
            }
        } catch (RuntimeException failure) {
            // Apart from the read back: a page laid out otherwise (another mod's) loses the sliders' tooltips, and the settings are still taken.
            Log.error("the sliders of the mod's settings on the game's options screen were not given their tooltips", failure);
        }
        return 0;
    }

    private static void tip(KahluaTable list, String tip) {
        KahluaTable tips = LuaManager.platform.newTable();
        tips.rawset("defaultTooltip", tip);
        call(list, "setToolTipMap", tips);
    }

    /**
     * The label with a slider's name. MainOptions.addSlider adds it to the page just before the
     * panel that holds the slider and the number beside it, and keeps it nowhere else.
     */
    private static KahluaTable nameOf(Control control, KahluaTable slider) {
        Object panel = slider.rawget("parent");
        KahluaTable page = (KahluaTable) ((KahluaTable) panel).rawget("parent");
        KahluaTable inOrder = (KahluaTable) page.rawget("childrenInOrder");
        for (int at = 2; at <= inOrder.len(); at++) {
            if (inOrder.rawget(at) == panel) {
                KahluaTable before = (KahluaTable) inOrder.rawget(at - 1);
                if (!"ISLabel".equals(UIManager.tableget(before, "Type"))) {
                    throw new IllegalStateException("what the game's options screen has before " + control.setting.described.id + "'s slider is no label");
                }
                return before;
            }
        }
        throw new IllegalStateException("the game's options screen does not hold " + control.setting.described.id + "'s slider where it was put");
    }

    /** Fills each player's list of microphones with those connected now and the one they have chosen. */
    private static void listMicrophones() {
        List<String> present = Microphones.present();
        for (MicrophoneList list : microphones) {
            list(list, Tracking.microphones().get(list.player), present);
        }
    }

    /**
     * Fills a list of microphones with none, Windows' own default and each microphone connected
     * now (present), with the one the player has chosen among them, said to be not connected where
     * it is not, and that one chosen: in the page's own list, which a screen made after this is made
     * from, and in the list on the screen.
     */
    private static void list(MicrophoneList list, String chosen, List<String> present) {
        List<String> names = new ArrayList<>(List.of(Microphones.NONE, Microphones.DEFAULT));
        names.addAll(present);
        if (!names.contains(chosen)) {
            names.add(chosen);
        }
        KahluaTable lines = LuaManager.platform.newTable();
        for (int line = 0; line < names.size(); line++) {
            String name = names.get(line);
            lines.rawset(line + 1, name.equals(Microphones.NONE) ? "None" : name.equals(Microphones.DEFAULT) ? "Windows default"
                    : present.contains(name) ? name : name + " (not connected)");
        }
        Double selected = (double) (names.indexOf(chosen) + 1);
        list.table.rawset("values", lines);
        list.table.rawset("selected", selected);
        if (list.table.rawget("element") instanceof KahluaTable element) {
            call(element, "clear");
            for (int line = 1; line <= lines.len(); line++) {
                call(element, "addOption", lines.rawget(line));
            }
            element.rawset("selected", selected);
            tip(element, MICROPHONE_TIP);
        }
        list.names = names;
        list.shown = chosen;
    }

    /** Writes what the draft holds for a setting in its control's own table, which the game fills the screen from as it opens. */
    private static void write(Control control) {
        OptionsDraft.Setting setting = control.setting;
        if (setting.described.kind == CameraUnlock.OPTION_BOOL) {
            control.table.rawset("value", ticked(setting, setting.staged));
        } else if (setting.values == null) {
            // On a notch, as the slider will hold it: the game writes this number beside the slider as it is.
            control.table.rawset("value", setting.staged);
        } else {
            control.table.rawset("selected", (double) (setting.lineOf((int) setting.staged) + 1));
        }
    }

    /** Whether a tick box is ticked for a value: a row of [Content] for the mod's own, which is 0. */
    private static boolean ticked(OptionsDraft.Setting setting, double value) {
        return (value != 0) != OptionsLayout.INVERTED.contains(setting.described.id);
    }

    /**
     * The graphics mode's list names the level Automatic is at, which changes as it steps: in the
     * page's own list, which a screen made after this is made from, and in the list on the screen.
     * A list shows Automatic only while its player's mode has not yet been picked (Quality.choices).
     */
    private static void nameLevel(Control control) {
        if (Tracking.qualityPlayer(control.setting.described.id) < 0 || control.setting.values[0] != Quality.AUTOMATIC) {
            return;
        }
        Double line = (double) (control.setting.lineOf(Quality.AUTOMATIC) + 1);
        String name = Quality.choice(Quality.AUTOMATIC);
        ((KahluaTable) control.table.rawget("values")).rawset(line, name);
        if (control.table.rawget("element") instanceof KahluaTable element) {
            ((KahluaTable) element.rawget("options")).rawset(line, name);
        }
    }

    /** A control's onChange, which the game calls as the player changes it: what follows from it on the page is set there too. */
    private static int changed(LuaCallFrame frame, int arguments) {
        Control control = byTable.get(frame.get(0));
        try {
            if (control == null) {
                throw new IllegalStateException("a setting the page did not make was handed to its controls' onChange");
            }
            OptionsDraft.Setting setting = control.setting;
            Object value = frame.get(1);
            draft.changed(setting, setting.described.kind == CameraUnlock.OPTION_BOOL ? unticked(setting, value)
                    : setting.values != null ? setting.values[line(value)] : (Double) value, OptionsPage::put);
            showQuickLists();
        } catch (RuntimeException failure) {
            // Thrown on, it would stop the game's options screen.
            Log.error((control == null ? "a setting" : control.setting.described.id) + " changed on the game's options screen was not followed on the page",
                    failure);
        }
        return 0;
    }

    /** A line of a quick list chosen: its settings are set on the page. Mixed sets nothing, and the list goes back to the line the page holds. */
    private static int quickChosen(LuaCallFrame frame, int arguments) {
        int list = -1;
        for (int at = 0; at < quickTables.size(); at++) {
            if (quickTables.get(at) == frame.get(0)) {
                list = at;
            }
        }
        try {
            if (list < 0) {
                throw new IllegalStateException("a list the page did not make was handed to its quick lists' onChange");
            }
            // The game's lines count from 1, and the first is Mixed.
            draft.chosen(list, line(frame.get(1)), OptionsPage::put);
            showQuickLists();
        } catch (RuntimeException failure) {
            // Thrown on, it would stop the game's options screen.
            Log.error("a quick list of the game's options screen was not set on the page", failure);
        }
        return 0;
    }

    /** Sets each quick list to the line the page holds, or Mixed: in the page's own table and in the list on the screen. */
    private static void showQuickLists() {
        for (int list = 0; list < quickTables.size(); list++) {
            call(quickTables.get(list), "setValue", (double) (draft.lineHeld(list) + 1));
        }
    }

    /**
     * The section's apply, which the game calls when the player applies the screen, after it has
     * put each control's value in that control's table: the session takes what the player changed.
     */
    private static int applied(LuaCallFrame frame, int arguments) {
        if (!filled) {
            return 0;
        }
        try {
            draft.apply(OptionsPage::held);
        } catch (RuntimeException failure) {
            // Thrown on, it would stop the game from applying and saving its own options.
            Log.error("the mod's settings as applied on the game's options screen were not taken whole", failure);
        }
        for (MicrophoneList list : microphones) {
            try {
                String chosen = list.names.get(line(list.table.rawget("selected")));
                if (!chosen.equals(list.shown)) {
                    Tracking.applyMicrophone(list.player, chosen);
                    list.shown = chosen;
                }
            } catch (RuntimeException failure) {
                // Thrown on, it would stop the game from applying and saving its own options.
                Log.error(Config.MICROPHONE_ROWS[list.player] + " as applied on the game's options screen was not read, and is not taken", failure);
            }
        }
        for (KeyControl key : keyControls) {
            try {
                KahluaTable element = (KahluaTable) key.table.rawget("element");
                KeyNames.Binding chosen = new KeyNames.Binding((int) (double) (Double) element.rawget("keyCode"), Boolean.TRUE.equals(element.rawget("shift")),
                        Boolean.TRUE.equals(element.rawget("ctrl")), Boolean.TRUE.equals(element.rawget("alt")));
                if (!chosen.equals(key.shown)) {
                    String keys = KeyNames.text(chosen);
                    Tracking.applyKeys(key.row.id(), keys);
                    key.shown = chosen;
                    call((KahluaTable) element.rawget("btn"), "setTooltip", OptionsLayout.keyTip(key.row, keys));
                }
            } catch (RuntimeException failure) {
                // Thrown on, it would stop the game from applying and saving its own options.
                Log.error(key.row.id() + " as applied on the game's options screen was not read, and is not taken", failure);
            }
        }
        try {
            for (Control control : controls) {
                nameLevel(control);
            }
            showQuickLists();
        } catch (RuntimeException failure) {
            Log.error("the graphics modes and quick lists of the game's options screen were not set to what is applied", failure);
        }
        return 0;
    }

    /**
     * Gives a key control the key its row holds now, with its modifier, which the game's toUI then
     * writes on its button, and its tooltip, which says what the file holds where the button cannot
     * show all of it.
     */
    private static void showKeys(KeyControl key) {
        String held = Tracking.keys(key.row.id());
        KeyNames.Binding shown = KeyNames.shown(held);
        if (!(key.table.rawget("element") instanceof KahluaTable element)) {
            throw new IllegalStateException("the game's options screen made no control for " + key.row.id());
        }
        element.rawset("keyCode", (double) shown.code());
        element.rawset("shift", shown.shift());
        element.rawset("ctrl", shown.ctrl());
        element.rawset("alt", shown.alt());
        key.table.rawset("key", (double) shown.code());
        call((KahluaTable) element.rawget("btn"), "setTooltip", OptionsLayout.keyTip(key.row, held));
        key.shown = shown;
    }

    /**
     * In place of MainOptions.keyPressHandler, which the game's key dialog hands the key it
     * waited for: for one of the mod's keys, Esc closes the dialog with nothing changed, and a key
     * a row of [Hotkeys] cannot name leaves it open and says so. Every other key, and every key of
     * a dialog that is not the mod's, goes on to the game's own.
     */
    private static int keyPressed(LuaCallFrame frame, int arguments) {
        Object[] passed = new Object[arguments];
        for (int i = 0; i < arguments; i++) {
            passed[i] = frame.get(i);
        }
        try {
            if (mainOptions.rawget("setKeybindDialog") instanceof KahluaTable dialog && Boolean.TRUE.equals(dialog.rawget("isModBind"))
                    && ourKey(dialog.rawget("keybindName")) && passed.length > 0 && passed[0] instanceof Double pressed && pressed > 0) {
                int code = (int) (double) pressed;
                if (code == KeyNames.ESCAPE) {
                    call(dialog, "destroy");
                    return 0;
                }
                if (!KeyNames.usable(code)) {
                    say(dialog, REFUSED_KEY);
                    return 0;
                }
            }
        } catch (RuntimeException failure) {
            // Thrown on, it would stop the game's key dialog; the key goes on to the game's own as it would without this in between.
            Log.error("a key pressed in the game's key dialog was not looked at for the mod's keys", failure);
        }
        ran("MainOptions.keyPressHandler", gameTakesKey, passed);
        return 0;
    }

    /** Whether a key dialog's name is one of the mod's key rows', which the game names it by: the row's label. */
    private static boolean ourKey(Object name) {
        for (KeyControl key : keyControls) {
            if (key.row.label().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Puts a line of the mod's in place of what the game's key dialog says. */
    private static void say(KahluaTable dialog, String text) {
        KahluaTable inOrder = (KahluaTable) dialog.rawget("childrenInOrder");
        for (int at = 1; at <= inOrder.len(); at++) {
            if (inOrder.rawget(at) instanceof KahluaTable child && "ISLabel".equals(UIManager.tableget(child, "Type"))) {
                call(child, "setName", text);
                return;
            }
        }
        throw new IllegalStateException("the game's key dialog has no label to say why a key is refused");
    }

    /** Whether the game's menu is up, the one Esc brings up in a save, which the options screen is opened from there. Logic thread. */
    static boolean menuShown() {
        return LuaManager.env.rawget("MainScreen") instanceof KahluaTable screen && screen.rawget("instance") instanceof KahluaTable instance
                && instance.rawget("javaObject") instanceof UIElement element && element.isVisible();
    }

    /**
     * Sets a setting's control on the open screen to a value, through the game's own setValue of
     * the control, which moves what is drawn, and answers what the control then holds.
     */
    private static double put(OptionsDraft.Setting setting, double value) {
        KahluaTable table = byId.get(setting.described.id).table;
        if (setting.described.kind == CameraUnlock.OPTION_BOOL) {
            call(table, "setValue", ticked(setting, value));
            return value;
        }
        if (setting.values != null) {
            call(table, "setValue", (double) (setting.lineOf((int) value) + 1));
            return value;
        }
        call(table, "setValue", OptionsDraft.onNotch(setting, value));
        KahluaTable slider = (KahluaTable) table.rawget("element");
        double holds = (Double) call(slider, "getCurrentValue");
        // The game writes the number beside a slider as the screen opens and as the slider is dragged, and not when it is set.
        call((KahluaTable) slider.rawget("label"), "setName", luaText(holds));
        return holds;
    }

    /** A number as the game's Lua writes it, which is how the screen writes it beside a slider as it opens. */
    private static String luaText(double value) {
        return (String) ran("tostring", LuaManager.env.rawget("tostring"), value)[1];
    }

    /** What a setting's control table holds, as core's value for the option. */
    private static double held(OptionsDraft.Setting setting) {
        KahluaTable table = byId.get(setting.described.id).table;
        if (setting.described.kind == CameraUnlock.OPTION_BOOL) {
            return unticked(setting, table.rawget("value"));
        }
        if (setting.values == null) {
            return (Double) table.rawget("value");
        }
        return setting.values[line(table.rawget("selected"))];
    }

    /** Core's value for a tick box the game hands back ticked or not: the inverse of ticked. */
    private static double unticked(OptionsDraft.Setting setting, Object ticked) {
        return Boolean.TRUE.equals(ticked) != OptionsLayout.INVERTED.contains(setting.described.id) ? 1 : 0;
    }

    /** The place from 0 of a list's line the game's Lua names, which counts from 1. */
    private static int line(Object luaLine) {
        return (int) (double) (Double) luaLine - 1;
    }

    /** Calls a method of a Lua object and answers what it returned first. Throws when the game's Lua has no such method or it fails. */
    private static Object call(KahluaTable object, String method, Object... arguments) {
        Object function = UIManager.tableget(object, method);
        if (function == null) {
            throw new IllegalStateException("the game's Lua has no " + method + " where the mod calls it");
        }
        Object[] passed = new Object[arguments.length + 1];
        passed[0] = object;
        for (int i = 0; i < arguments.length; i++) {
            // Every number of the game's Lua is a double.
            passed[i + 1] = arguments[i] instanceof Number number ? (Object) number.doubleValue() : arguments[i];
        }
        Object[] ran = ran(method, function, passed);
        return ran.length > 1 ? ran[1] : null;
    }

    /** Runs a function of the game's Lua by name and answers what pcall hands back, its success first. Throws when it fails. */
    private static Object[] ran(String name, Object function, Object... passed) {
        Object[] ran = LuaManager.caller.pcall(UIManager.getDefaultThread(), function, passed);
        if (!Boolean.TRUE.equals(ran[0])) {
            throw new IllegalStateException("the game's " + name + " failed: " + Arrays.toString(ran));
        }
        return ran;
    }
}
