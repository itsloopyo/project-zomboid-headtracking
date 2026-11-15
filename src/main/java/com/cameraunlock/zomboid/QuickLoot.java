package com.cameraunlock.zomboid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.LuaCallFrame;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.core.Translator;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.inventory.types.Food;
import zombie.ui.TextManager;
import zombie.ui.UIElement;
import zombie.ui.UIFont;
import zombie.ui.UIManager;

/**
 * The quick loot list ([FirstPerson] QuickLoot): while the crosshair is on a container or a body
 * within reach, the one a tap of Interact would show in the loot panel, a short list of what is in
 * it stands beside the crosshair, with the game's own icons and names, how many of each and their
 * weight, and the container's weight against what it holds. A tap of Interact takes the
 * highlighted row, holding it takes all (FirstPerson.holdUse). The mouse wheel, or the d-pad up and
 * down on a controller, moves the highlight. One for each player's view, drawn in that player's own
 * part of the screen.
 *
 * What the crosshair is on is looked for a few times a second, and what is in it is read again a
 * few times a second, in Java: the list asks nothing of the game's Lua while it is up. Only a take,
 * a look into a container never opened before (Panels.explore) and the controller's prompt call it.
 *
 * Logic thread only.
 */
final class QuickLoot {
    /** How often what the crosshair is on is looked for, and how often what is in it is read again. */
    static final long LOOK_EVERY_NANOS = 100_000_000L, READ_EVERY_NANOS = 250_000_000L;
    /** The game's words for the take and for a container with nothing in it. */
    private static final String GRAB = "ContextMenu_Grab", EMPTY = "ContextMenu_Empty";

    private static final int DESIGN_HEIGHT = 1080;
    /** How far right of the crosshair the list stands, and its narrowest and widest, in pixels on a screen 1080 high. */
    private static final float BESIDE = 56f, NARROWEST = 260f, WIDEST = 460f, PAD = 6f, ICON_GAP = 6f, BAR = 3f;
    /** A surface the game's own UI drawing calls are made on: never in the UI manager, at the screen's corner. */
    private static UIElement canvas;

    private final int index;
    private final LootList<InventoryItem> list = new LootList<>();
    private final PadRepeat up = new PadRepeat(), down = new PadRepeat();
    private boolean looked;
    private long lookedAt, readAt;
    private int readCount;
    /** The container last looked into for its player (Panels.explore), so it is asked once. */
    private ItemContainer explored;
    /** Whether the list takes the wheel, the d-pad and a tap of Interact this frame: it is up and the look has the input. */
    private boolean taking;
    /** What the list was last worded as, at its last read. */
    private String title, weight;
    private final List<String> named = new ArrayList<>();
    private final List<String> weights = new ArrayList<>();
    private int widest;

    QuickLoot(int index) {
        this.index = index;
    }

    /** Whether it is time to look again for what the crosshair is on. */
    boolean looking(long now) {
        if (looked && now - lookedAt < LOOK_EVERY_NANOS) {
            return false;
        }
        looked = true;
        lookedAt = now;
        return true;
    }

    /**
     * Shows the list for the container the crosshair is on, or none for null. A container never
     * looked into is looked into first, as the game's loot panel does; on a server what is in it
     * comes some frames later, and the list fills as it does.
     */
    void look(ItemContainer at, IsoPlayer player, long now) {
        if (at == null) {
            list.clear();
            return;
        }
        if (at != explored) {
            explored = at;
            if (!at.isExplored()) {
                Panels.explore(index, at, player);
            }
        }
        if (at != list.of() || at.getItems().size() != readCount || now - readAt >= READ_EVERY_NANOS) {
            read(at, player, now);
        }
    }

    private void read(ItemContainer at, IsoPlayer player, long now) {
        List<InventoryItem> items = at.getItems();
        readCount = items.size();
        readAt = now;
        list.show(at, LootList.rows(items, item -> name(item, player), InventoryItem::getUnequippedWeight, InventoryItem::isHidden));
        title = InteractLabel.title(at);
        weight = String.format(Locale.ROOT, "%s / %d", LootList.round(at.getCapacityWeight()), at.getEffectiveCapacity(player));
        named.clear();
        weights.clear();
        TextManager text = TextManager.instance;
        widest = text.MeasureStringX(UIFont.Small, title) + text.MeasureStringX(UIFont.Small, weight);
        for (LootList.Row<InventoryItem> row : list.rows()) {
            String name = row.count() > 1 ? row.name() + " (" + row.count() + ")" : row.name();
            String heavy = LootList.round(row.weight());
            named.add(name);
            weights.add(heavy);
            widest = Math.max(widest, text.MeasureStringX(UIFont.Small, name) + text.MeasureStringX(UIFont.Small, heavy) + text.getFontHeight(UIFont.Small) * 2);
        }
    }

    /**
     * An item's name as the game's inventory pane gives it: a berry or a mushroom is named for
     * what the character knows of it (ISInventoryPane.refreshContainer), anything else by the
     * game's name for it for that player.
     */
    private static String name(InventoryItem item, IsoPlayer player) {
        if (item instanceof Food food) {
            String herb = food.getHerbalistType();
            boolean berry = "Berry".equals(herb), mushroom = "Mushroom".equals(herb);
            if (berry || mushroom) {
                String key = !player.isRecipeActuallyKnown("Herbalist") ? (berry ? "IGUI_UnknownBerry" : "IGUI_UnknownMushroom")
                        : food.getPoisonPower() > 0 ? (berry ? "IGUI_PoisonousBerry" : "IGUI_PoisonousMushroom") : (berry ? "IGUI_Berry" : "IGUI_Mushroom");
                return Translator.getText(key);
            }
        }
        return item.getName(player);
    }

    /** Nothing shown: the look is off what is looted, or the list is not drawn this frame. */
    void hide() {
        list.clear();
        taking = false;
    }

    /** How long a frame's word that the list takes the input holds: a view that stops drawing frames stops asking. */
    private static final long TAKING_NANOS = 250_000_000L;
    private long takingAt;

    /** Whether the list takes the input this frame (see taking). */
    void taking(boolean takes, long now) {
        taking = takes && list.of() != null;
        takingAt = now;
    }

    private boolean taking(long now) {
        return taking && now - takingAt < TAKING_NANOS;
    }

    /** The container the list is up for, or null. */
    ItemContainer container() {
        return (ItemContainer) list.of();
    }

    /** Whether a tap of Interact on at takes the highlighted row: the list is up for it with a row to take. */
    boolean takes(ItemContainer at) {
        return ClickRules.tapTakesRow(taking(System.nanoTime()), at != null && at == list.of(), list.highlighted() != null);
    }

    /** Asks the game to take the highlighted row: those of its items that are still in the container. */
    void take(IsoPlayer player) {
        LootList.Row<InventoryItem> row = list.highlighted();
        ItemContainer from = container();
        List<InventoryItem> still = new ArrayList<>(row.count());
        for (InventoryItem item : row.items()) {
            if (item.getContainer() == from) {
                still.add(item);
            }
        }
        if (still.isEmpty()) {
            return;
        }
        boolean asked = Panels.grab(index, player, still);
        if (FirstPerson.TRACE) {
            Log.info("quick loot of player " + (index + 1) + (asked ? " asked the game to grab " : " could not grab ") + still.size() + " " + row.name() + " from the " + from.getType());
        }
    }

    /** The mouse wheel turned, 1 a notch towards the player: whether the list took it, moving the highlight. */
    boolean wheel(int notch) {
        if (!taking(System.nanoTime())) {
            return false;
        }
        list.step(notch);
        return true;
    }

    /**
     * What the game's Lua is to read of the d-pad up or down on this player's controller: nothing
     * while the list takes it, and nothing either for a press it took until it is let go, so a press
     * held past the list's going does not open the game's wheel for it.
     */
    boolean pad(boolean held, boolean upward, long now) {
        PadRepeat repeat = upward ? up : down;
        if (taking(now)) {
            if (repeat.step(held, now)) {
                list.step(upward ? -1 : 1);
            }
            return false;
        }
        if (repeat.down()) {
            repeat.step(held, now);
            return false;
        }
        return held;
    }

    /** The game's word for the take, in the language played in. */
    static String grabWords() {
        return Translator.getText(GRAB);
    }

    /**
     * Draws the list right of the crosshair, over the player's finished picture.
     *
     * @param x across the player's screen to the crosshair, in pixels
     * @param y down it
     * @param height the player's screen's height, in pixels
     */
    void draw(int x, int y, int height) {
        if (list.of() == null || title == null) {
            return;
        }
        if (canvas == null) {
            canvas = new UIElement();
        }
        float unit = (float) height / DESIGN_HEIGHT;
        TextManager text = TextManager.instance;
        int font = text.getFontHeight(UIFont.Small);
        double row = Math.max(font + 4.0 * unit, 22.0 * unit), icon = row - 4.0 * unit, pad = PAD * unit;
        double wide = Math.max(NARROWEST * unit, Math.min(WIDEST * unit, widest + icon + ICON_GAP * unit + pad * 3));
        int rows = Math.max(1, list.inView());
        double left = x + BESIDE * unit, top = y - row * 1.5, tall = row * (rows + 1) + pad;
        canvas.DrawTextureScaledColor(null, left, top, wide, tall, 0.0, 0.0, 0.0, 0.62);
        canvas.DrawTextureScaledColor(null, left, top, wide, row, 0.0, 0.0, 0.0, 0.35);
        double inset = (row - font) / 2.0;
        canvas.DrawText(UIFont.Small, title, left + pad, top + inset, 1.0, 1.0, 0.95, 1.0);
        canvas.DrawTextRight(UIFont.Small, weight, left + wide - pad, top + inset, 0.8, 0.8, 0.8, 1.0);
        double first = top + row + pad / 2.0;
        if (list.rows().isEmpty()) {
            canvas.DrawText(UIFont.Small, Translator.getText(EMPTY), left + pad, first + inset, 0.7, 0.7, 0.7, 1.0);
            return;
        }
        double scroll = list.rows().size() > LootList.SHOWN ? BAR * unit + pad / 2.0 : 0.0;
        for (int shown = 0; shown < list.inView(); shown++) {
            int i = list.top() + shown;
            double at = first + shown * row;
            boolean lit = i == list.highlight();
            if (lit) {
                canvas.DrawTextureScaledColor(null, left, at, wide - scroll, row, 1.0, 1.0, 1.0, 0.16);
                canvas.DrawTextureScaledColor(null, left, at, 2.0 * unit + 1.0, row, 1.0, 1.0, 0.9, 0.85);
            }
            canvas.DrawItemIcon(list.rows().get(i).items().get(0), left + pad, at + (row - icon) / 2.0, 1.0, icon, icon);
            double alpha = lit ? 1.0 : 0.82;
            canvas.DrawText(UIFont.Small, named.get(i), left + pad + icon + ICON_GAP * unit, at + inset, 1.0, 1.0, 1.0, alpha);
            canvas.DrawTextRight(UIFont.Small, weights.get(i), left + wide - pad - scroll, at + inset, 0.75, 0.75, 0.75, alpha);
        }
        if (scroll > 0.0) {
            double track = row * list.inView(), size = track * LootList.SHOWN / list.rows().size();
            double from = first + (track - size) * list.top() / (list.rows().size() - LootList.SHOWN);
            canvas.DrawTextureScaledColor(null, left + wide - BAR * unit - pad / 4.0, first, (double) (BAR * unit), track, 1.0, 1.0, 1.0, 0.12);
            canvas.DrawTextureScaledColor(null, left + wide - BAR * unit - pad / 4.0, from, (double) (BAR * unit), size, 1.0, 1.0, 1.0, 0.6);
        }
    }

    /**
     * The game's controller prompt (ISButtonPrompt) names the Interact button for what the list
     * does while it takes it, with nothing of the game's own on it: a tap there would otherwise do
     * both, such as take a tin and turn on the stove it is in. The game works out its prompts for
     * each binding a frame; the list's words go on the Interact button after each.
     */
    private static final String PROMPT = """
            local takes = ...
            local best = ISButtonPrompt.getBestButtonBindingAction
            ISButtonPrompt.getBestButtonBindingAction = function(self, buttonBinding, dir)
                best(self, buttonBinding, dir)
                local words = self.player and takes(self.player)
                if words then
                    self:setButtonBindingPrompt(CharacterJoypadButtonBinding.Interact, words, nil)
                end
            end
            """;

    private static final JavaFunction TAKES = QuickLoot::takesInteract;

    /** Called once the game has loaded its Lua, each time it does. */
    static void luaLoaded() {
        if (!(LuaManager.env.rawget("ISButtonPrompt") instanceof se.krka.kahlua.vm.KahluaTable prompt) || prompt.rawget("getBestButtonBindingAction") == null) {
            throw new IllegalStateException("the game's Lua has no ISButtonPrompt.getBestButtonBindingAction");
        }
        Object chunk;
        try {
            chunk = LuaCompiler.loadstring(PROMPT, "the quick loot list's controller prompt", LuaManager.env);
        } catch (IOException refused) {
            throw new IllegalStateException("the game's Lua would not compile the quick loot list's controller prompt", refused);
        }
        Object[] ran = LuaManager.caller.pcall(UIManager.getDefaultThread(), chunk, TAKES);
        if (!Boolean.TRUE.equals(ran[0])) {
            throw new IllegalStateException("the quick loot list's controller prompt failed in the game's Lua: " + Arrays.toString(ran));
        }
    }

    private static int takesInteract(LuaCallFrame frame, int arguments) {
        View view = frame.get(0) instanceof Double number ? View.of(number.intValue()) : null;
        return frame.push(view != null && view.firstPerson.quickLootTakes() ? grabWords() : null);
    }
}
