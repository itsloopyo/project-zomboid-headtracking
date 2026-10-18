package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
 * screen because Config marks it writable, and nothing here names one.
 *
 * Each player's microphone is a name and no option of core's: the page lists the microphones
 * connected as it opens, for each player, below the settings.
 *
 * The game saves what its mod page shows in a file of its own, ModOptions.ini, and reads that
 * back when it builds the page. The mod never takes a value from there: the controls are filled
 * from core each time the screen opens, and what the player applies there the session takes at
 * once and CameraUnlock.ini is saved with. The rows of [Graphics] show the picture of the graphics
 * mode the session is in, and a change to any of them goes to the Custom mode: see
 * Tracking.applyScreen.
 *
 * Everything runs on the game's logic thread, which is the one its Lua runs on.
 */
final class OptionsPage {
    /** The section's name in the game's Lua, and its heading on the page. */
    private static final String SECTION = "CameraUnlock", HEADING = "Head Tracking";

    /**
     * One control. `table` is the game's Lua table for it, `values` core's value for each line
     * of a list in the order the list shows them, and `shown` the value the control had when the
     * screen opened or was last applied.
     */
    private static final class Control {
        final int option;
        final CameraUnlock.Option described;
        final KahluaTable table;
        final int[] values;
        double shown;

        Control(int option, CameraUnlock.Option described, KahluaTable table, int[] values) {
            this.option = option;
            this.described = described;
            this.table = table;
            this.values = values;
        }
    }

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

    private static final String MICROPHONE_TIP = "Read for Talking mouths alone, and only for how loud it is.";

    private static final JavaFunction OPENED = OptionsPage::opened, APPLIED = OptionsPage::applied;

    private static List<Control> controls = List.of();
    private static List<MicrophoneList> microphones = List.of();
    /** Whether the controls hold what core gave them as the screen last opened: until they do, what they hold is the game's own file's. */
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
        KahluaTable section = (KahluaTable) call(modOptions, "create", SECTION, HEADING);
        List<Control> made = new ArrayList<>();
        for (int option = 0; option < options.length; option++) {
            Control control = control(section, option, options[option]);
            show(control, Tracking.optionValue(option));
            made.add(control);
        }
        List<MicrophoneList> lists = new ArrayList<>();
        for (int player = 0; player < Config.MICROPHONE_ROWS.length; player++) {
            KahluaTable table = (KahluaTable) call(section, "addComboBox", Config.MICROPHONE_ROWS[player],
                    player == 0 ? "Microphone" : "Player " + (player + 1) + "'s microphone",
                    MICROPHONE_TIP);
            MicrophoneList list = new MicrophoneList(player, table);
            list(list, Tracking.microphones().get(player));
            lists.add(list);
        }
        filled = false;
        section.rawset("apply", APPLIED);
        gameOpens = screen.rawget("toUI");
        screen.rawset("toUI", OPENED);
        controls = made;
        microphones = lists;
        Log.info("the game's options screen has " + made.size() + " of the mod's settings and " + lists.size() + " lists of microphones under "
                + HEADING);
    }

    private static Control control(KahluaTable section, int option, CameraUnlock.Option described) {
        double value = Tracking.optionValue(option);
        switch (described.kind) {
            case CameraUnlock.OPTION_BOOL -> {
                Object table = call(section, "addTickBox", described.id, described.label, value != 0, described.comment);
                return new Control(option, described, (KahluaTable) table, null);
            }
            case CameraUnlock.OPTION_INT, CameraUnlock.OPTION_FLOAT -> {
                Object table = call(section, "addSlider", described.id, described.label, described.min, described.max, described.step, value,
                        described.comment);
                return new Control(option, described, (KahluaTable) table, null);
            }
            case CameraUnlock.OPTION_ENUM -> {
                KahluaTable table = (KahluaTable) call(section, "addComboBox", described.id, described.label, described.comment);
                int[] values = described.source == CameraUnlock.OPTION_AIM_MODE ? Tracking.AIM_MODES : everyChoice(described);
                boolean graphics = Tracking.qualityPlayer(described.id) >= 0;
                for (int choice : values) {
                    call(table, "addItem", graphics ? Quality.choice(choice) : described.choices[choice], choice == (int) value);
                }
                return new Control(option, described, table, values);
            }
            default -> throw new IllegalStateException(described.id + " is of kind " + described.kind + ", which the page has no control for");
        }
    }

    private static int[] everyChoice(CameraUnlock.Option described) {
        int[] values = new int[described.choices.length];
        Arrays.setAll(values, choice -> choice);
        return values;
    }

    /**
     * In place of MainOptions.toUI, which the game calls as its options screen opens: the
     * controls are given what core holds now, which a hotkey or an edit of the file may have
     * changed since the screen was last open, and then the game's own fills the screen from them.
     */
    private static int opened(LuaCallFrame frame, int arguments) {
        filled = false;
        try {
            for (Control control : controls) {
                show(control, Tracking.optionValue(control.option));
                nameLevel(control);
            }
            for (MicrophoneList list : microphones) {
                list(list, Tracking.microphones().get(list.player));
            }
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
                if (control.values != null) {
                    // The game gives a tick box of the mod page its tooltip and a list none, though its lists show one.
                    KahluaTable tips = LuaManager.platform.newTable();
                    tips.rawset("defaultTooltip", control.described.comment);
                    call(element, "setToolTipMap", tips);
                } else if (control.described.kind != CameraUnlock.OPTION_BOOL) {
                    // A slider holds the notch nearest what it was given, and that is what it hands back unmoved.
                    control.shown = (Double) call(element, "getCurrentValue");
                }
            }
        } catch (RuntimeException failure) {
            filled = false;
            Log.error("the mod's controls on the game's options screen were not read back, and what is applied there until it opens again is not taken", failure);
        }
        try {
            for (Control control : controls) {
                if (control.values == null && control.described.kind != CameraUnlock.OPTION_BOOL) {
                    KahluaTable slider = (KahluaTable) control.table.rawget("element");
                    // The game makes a slider's name and the number beside it labels of their own and gives neither the tooltip it is handed.
                    nameOf(control, slider).rawset("tooltip", control.described.comment);
                    ((KahluaTable) slider.rawget("label")).rawset("tooltip", control.described.comment);
                }
            }
        } catch (RuntimeException failure) {
            // Apart from the read back: a page laid out otherwise (another mod's) loses the sliders' tooltips, and the settings are still taken.
            Log.error("the sliders of the mod's settings on the game's options screen were not given their tooltips", failure);
        }
        return 0;
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
                    throw new IllegalStateException("what the game's options screen has before " + control.described.id + "'s slider is no label");
                }
                return before;
            }
        }
        throw new IllegalStateException("the game's options screen does not hold " + control.described.id + "'s slider where it was put");
    }

    /**
     * Fills a list of microphones with none, Windows' own default and each microphone connected
     * now, with the one the player has chosen among them, said to be not connected where it is not,
     * and that one chosen: in the page's own list, which a screen made after this is made from, and
     * in the list on the screen.
     */
    private static void list(MicrophoneList list, String chosen) {
        List<String> present = Microphones.present();
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
            // The game gives a list of the mod page no tooltip, though its lists show one.
            KahluaTable tips = LuaManager.platform.newTable();
            tips.rawset("defaultTooltip", MICROPHONE_TIP);
            call(element, "setToolTipMap", tips);
        }
        list.names = names;
        list.shown = chosen;
    }

    private static void show(Control control, double value) {
        control.shown = value;
        if (control.described.kind == CameraUnlock.OPTION_BOOL) {
            control.table.rawset("value", value != 0);
        } else if (control.values == null) {
            // On a notch, as the slider will hold it: the game writes this number beside the slider as it is.
            control.table.rawset("value", notch(control, value));
        } else {
            control.table.rawset("selected", (double) (lineOf(control, (int) value) + 1));
        }
    }

    /**
     * The graphics mode's list names the level Automatic is at, which changes as it steps: in the
     * page's own list, which a screen made after this is made from, and in the list on the screen.
     */
    private static void nameLevel(Control control) {
        if (Tracking.qualityPlayer(control.described.id) < 0) {
            return;
        }
        Double line = (double) (lineOf(control, Quality.AUTOMATIC) + 1);
        String name = Quality.choice(Quality.AUTOMATIC);
        ((KahluaTable) control.table.rawget("values")).rawset(line, name);
        if (control.table.rawget("element") instanceof KahluaTable element) {
            ((KahluaTable) element.rawget("options")).rawset(line, name);
        }
    }

    private static int lineOf(Control control, int value) {
        for (int line = 0; line < control.values.length; line++) {
            if (control.values[line] == value) {
                return line;
            }
        }
        throw new IllegalStateException(control.described.id + " holds " + value + ", which is none of the lines its list shows");
    }

    /**
     * The section's apply, which the game calls when the player applies the screen, after it has
     * put each control's value in that control's table: the session takes what the player changed.
     */
    private static int applied(LuaCallFrame frame, int arguments) {
        if (!filled) {
            return 0;
        }
        int count = controls.size();
        int[] options = new int[count];
        CameraUnlock.Option[] described = new CameraUnlock.Option[count];
        double[] shown = new double[count], held = new double[count];
        for (int i = 0; i < count; i++) {
            Control control = controls.get(i);
            options[i] = control.option;
            described[i] = control.described;
            shown[i] = control.shown;
            try {
                held[i] = held(control);
            } catch (RuntimeException failure) {
                // Thrown on, it would stop the game from applying and saving its own options, and the mod's other settings with them.
                held[i] = control.shown;
                Log.error(control.described.id + " as applied on the game's options screen was not read, and is not taken", failure);
            }
        }
        Tracking.applyScreen(options, described, shown, held);
        for (int i = 0; i < count; i++) {
            controls.get(i).shown = shown[i];
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
        // The screen stays open after Apply. The graphics mode and the rows of [Graphics] may now read otherwise than the
        // screen holds: Custom after a row was changed, or another mode's picture after the mode was.
        for (Control control : controls) {
            if (Tracking.qualityPlayer(control.described.id) < 0 && Config.graphicsPart(control.described.id) < 0) {
                continue;
            }
            try {
                nameLevel(control);
                double now = Tracking.optionValue(control.option);
                if (notch(control, now) != control.shown) {
                    put(control, now);
                }
            } catch (RuntimeException failure) {
                Log.error(control.described.id + " on the game's options screen was not set to what it is now, and shows what was applied until the screen opens again", failure);
            }
        }
        return 0;
    }

    /** Whether the game's menu is up, the one Esc brings up in a save, which the options screen is opened from there. Logic thread. */
    static boolean menuShown() {
        return LuaManager.env.rawget("MainScreen") instanceof KahluaTable screen && screen.rawget("instance") instanceof KahluaTable instance
                && instance.rawget("javaObject") instanceof UIElement element && element.isVisible();
    }

    /** A value as the control holds it: a slider's on its nearest notch. */
    private static double notch(Control control, double value) {
        return control.described.kind == CameraUnlock.OPTION_BOOL || control.values != null ? value : Tracking.notch(value, control.described.step);
    }

    /** Sets a control on the open screen to a value, through the game's own setValue of the control, which moves what is drawn. */
    private static void put(Control control, double value) {
        if (control.described.kind == CameraUnlock.OPTION_BOOL) {
            call(control.table, "setValue", value != 0);
            control.shown = value;
        } else if (control.values == null) {
            call(control.table, "setValue", notch(control, value));
            control.shown = (Double) call((KahluaTable) control.table.rawget("element"), "getCurrentValue");
        } else {
            call(control.table, "setValue", (double) (lineOf(control, (int) value) + 1));
            control.shown = value;
        }
    }

    /** What a control's table holds, as core's value for the option. */
    private static double held(Control control) {
        if (control.described.kind == CameraUnlock.OPTION_BOOL) {
            return Boolean.TRUE.equals(control.table.rawget("value")) ? 1 : 0;
        }
        if (control.values == null) {
            return (Double) control.table.rawget("value");
        }
        return control.values[(int) (double) (Double) control.table.rawget("selected") - 1];
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
