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
 * said under the last title, with where to change them.
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

    private static final JavaFunction OPENED = OptionsPage::opened, APPLIED = OptionsPage::applied, CHANGED = OptionsPage::changed,
            QUICK_CHOSEN = OptionsPage::quickChosen;

    private static OptionsDraft draft;
    private static List<Control> controls = List.of();
    private static Map<String, Control> byId = Map.of();
    private static Map<KahluaTable, Control> byTable = Map.of();
    /** The game's Lua tables for the quick lists, in the order of the draft's. */
    private static List<KahluaTable> quickTables = List.of();
    private static List<MicrophoneList> microphones = List.of();
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
        call(section, "addTitle", "Keys");
        call(section, "addDescription", keysSaid());

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
        List<String> present = Microphones.present();
        for (MicrophoneList list : microphones) {
            list(list, Tracking.microphones().get(list.player), present);
        }
        showQuickLists();
        filled = false;
        section.rawset("apply", APPLIED);
        gameOpens = screen.rawget("toUI");
        screen.rawset("toUI", OPENED);
        Log.info("the game's options screen has " + made.size() + " of the mod's settings, " + drafted.quickLists.size() + " quick lists and "
                + microphoneLists.size() + " lists of microphones under " + HEADING);
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
     * What the Keys title says: the mod's own keys as CameraUnlock.ini names them, and core's three
     * as the mod gives them, since core reads its own key rows to itself.
     */
    private static String keysSaid() {
        return "Switch between first person and the isometric view: " + keys(Tracking.firstPersonKeys()) + " \n"
                + "Next graphics mode of the view you are in: " + keys(Tracking.cycleQualityKeys()) + " \n"
                + "Next picture style: " + keys(Tracking.cycleStyleKeys()) + " \n"
                + "Switch the split screen layout: " + keys(Tracking.splitScreenKeys()) + " \n"
                + "Next tracking mode: Page Up \n"
                + "Switch horizon-relative head turns: Page Down \n"
                + "Next aim mode: Insert <BR> "
                + "Tap the first person, graphics mode or picture style key once for player 1, twice for player 2, three times for player 3 "
                + "or four times for player 4, or hold it for every player. On a controller, tap Back twice to switch first person. The "
                + "last three are the keys the mod comes with: if you change them, this page still shows these. To change a key, edit the "
                + "Hotkeys section of CameraUnlock.ini in the game's folder. The same file holds the tracker's UDP ports and how far you "
                + "can lean.";
    }

    /** A key list of CameraUnlock.ini as words: "Delete", or "Delete or F6" for a list of two. */
    private static String keys(String list) {
        List<String> keys = new ArrayList<>();
        for (String key : list.split(",")) {
            if (!key.isBlank()) {
                keys.add(key.trim());
            }
        }
        return keys.isEmpty() ? "none" : String.join(" or ", keys);
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
            List<String> present = Microphones.present();
            for (MicrophoneList list : microphones) {
                list(list, Tracking.microphones().get(list.player), present);
            }
            showQuickLists();
            filled = true;
        } catch (RuntimeException failure) {
            // Thrown on, it would stop the game's options screen from opening.
            Log.error("the game's options screen was not given the mod's settings as they are, and what is applied there until it opens again is not taken", failure);
        }
        Object[] ran = LuaManager.caller.pcall(UIManager.getDefaultThread(), gameOpens, new Object[] {frame.get(0)});
        if (!Boolean.TRUE.equals(ran[0])) {
            // The game's own fault, or another mod's: it goes on to whoever opened the screen, as it would without this in between.
            throw new IllegalStateException("the game's MainOptions.toUI failed: " + Arrays.toString(ran));
        }
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
     */
    private static void nameLevel(Control control) {
        if (Tracking.qualityPlayer(control.setting.described.id) < 0) {
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
            draft.changed(setting, setting.described.kind == CameraUnlock.OPTION_BOOL
                    ? (Boolean.TRUE.equals(value) != OptionsLayout.INVERTED.contains(setting.described.id) ? 1 : 0)
                    : setting.values != null ? setting.values[(int) (double) (Double) value - 1] : (Double) value, OptionsPage::put);
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
            draft.chosen(list, (int) (double) (Double) frame.get(1) - 1, OptionsPage::put);
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
                String chosen = list.names.get((int) (double) (Double) list.table.rawget("selected") - 1);
                if (!chosen.equals(list.shown)) {
                    Tracking.applyMicrophone(list.player, chosen);
                    list.shown = chosen;
                }
            } catch (RuntimeException failure) {
                // Thrown on, it would stop the game from applying and saving its own options.
                Log.error(Config.MICROPHONE_ROWS[list.player] + " as applied on the game's options screen was not read, and is not taken", failure);
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
        Object[] ran = LuaManager.caller.pcall(UIManager.getDefaultThread(), LuaManager.env.rawget("tostring"), new Object[] {value});
        if (!Boolean.TRUE.equals(ran[0])) {
            throw new IllegalStateException("the game's tostring failed: " + Arrays.toString(ran));
        }
        return (String) ran[1];
    }

    /** What a setting's control table holds, as core's value for the option. */
    private static double held(OptionsDraft.Setting setting) {
        KahluaTable table = byId.get(setting.described.id).table;
        if (setting.described.kind == CameraUnlock.OPTION_BOOL) {
            return Boolean.TRUE.equals(table.rawget("value")) != OptionsLayout.INVERTED.contains(setting.described.id) ? 1 : 0;
        }
        if (setting.values == null) {
            return (Double) table.rawget("value");
        }
        return setting.values[(int) (double) (Double) table.rawget("selected") - 1];
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
        Object[] ran = LuaManager.caller.pcall(UIManager.getDefaultThread(), function, passed);
        if (!Boolean.TRUE.equals(ran[0])) {
            throw new IllegalStateException("the game's " + method + " failed: " + Arrays.toString(ran));
        }
        return ran.length > 1 ? ran[1] : null;
    }
}
