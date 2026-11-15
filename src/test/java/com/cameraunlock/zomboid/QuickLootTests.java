package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.List;

/** Tests of the quick loot list's rows, its scrolling and the inputs it takes (LootList, PadRepeat, ClickRules.tapTakesRow). */
final class QuickLootTests {
    private QuickLootTests() {}

    /** An item as the tests have it: a name, a weight, and whether the game hides it. */
    private record Thing(String name, double weight, boolean hidden) {}

    static void run() {
        Checks.run("rowsGroupItemsByNameAsThePaneDoes", QuickLootTests::rowsGroupItemsByNameAsThePaneDoes);
        Checks.run("theHighlightScrollsTheRowsInView", QuickLootTests::theHighlightScrollsTheRowsInView);
        Checks.run("theHighlightStaysOnItsRowAcrossARead", QuickLootTests::theHighlightStaysOnItsRowAcrossARead);
        Checks.run("aHeldDpadStepsOnceThenRepeats", QuickLootTests::aHeldDpadStepsOnceThenRepeats);
        Checks.run("aTapTakesARowOnlyFromTheListedContainer", QuickLootTests::aTapTakesARowOnlyFromTheListedContainer);
        Checks.run("weightsAreWrittenToTwoPlaces", QuickLootTests::weightsAreWrittenToTwoPlaces);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static List<LootList.Row<Thing>> rows(Thing... things) {
        return LootList.rows(List.of(things), Thing::name, Thing::weight, Thing::hidden);
    }

    private static List<LootList.Row<Thing>> numbered(int count) {
        List<Thing> things = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            things.add(new Thing(String.format("Item %02d", i), 1.0, false));
        }
        return LootList.rows(things, Thing::name, Thing::weight, Thing::hidden);
    }

    private static void rowsGroupItemsByNameAsThePaneDoes() {
        List<LootList.Row<Thing>> rows = rows(new Thing("Tin", 0.5, false), new Thing("apple", 0.2, false), new Thing("Tin", 0.5, false),
                new Thing("Zed part", 1.0, true), new Thing("Bandage", 0.1, false));
        check(rows.size() == 3, "one row a name, with the hidden item left out");
        check(rows.get(0).name().equals("apple") && rows.get(1).name().equals("Bandage") && rows.get(2).name().equals("Tin"),
                "listed by name, whatever the case of the first letter");
        check(rows.get(2).count() == 2 && Math.abs(rows.get(2).weight() - 1.0) < 1e-9, "a row counts its items and adds up their weights");
        check(rows(new Thing("x", 1, true)).isEmpty(), "a container of hidden items alone has no rows");
    }

    private static void theHighlightScrollsTheRowsInView() {
        LootList<Thing> list = new LootList<>();
        Object shelf = new Object();
        list.show(shelf, numbered(12));
        check(list.highlight() == 0 && list.top() == 0 && list.inView() == LootList.SHOWN, "a new list starts on its first row, eight in view");
        list.step(-1);
        check(list.highlight() == 0, "it stops at the top");
        list.step(7);
        check(list.highlight() == 7 && list.top() == 0, "the eighth row is still in view");
        list.step(1);
        check(list.highlight() == 8 && list.top() == 1, "the ninth scrolls the list by one");
        list.step(100);
        check(list.highlight() == 11 && list.top() == 4 && list.inView() == LootList.SHOWN, "it stops at the bottom, with the last eight in view");
        list.step(-11);
        check(list.highlight() == 0 && list.top() == 0, "and back up to the top");
        list.show(new Object(), numbered(3));
        check(list.highlight() == 0 && list.inView() == 3, "another container starts again, with as many rows in view as it has");
        list.clear();
        check(list.of() == null && list.highlighted() == null, "cleared, nothing is shown or highlighted");
        list.step(1);
        check(list.highlight() == 0, "and a step does nothing");
    }

    private static void theHighlightStaysOnItsRowAcrossARead() {
        LootList<Thing> list = new LootList<>();
        Object shelf = new Object();
        list.show(shelf, rows(new Thing("Apple", 1, false), new Thing("Bean", 1, false), new Thing("Cup", 1, false)));
        list.step(1);
        list.show(shelf, rows(new Thing("Aaa", 1, false), new Thing("Apple", 1, false), new Thing("Bean", 1, false), new Thing("Cup", 1, false)));
        check(list.highlighted().name().equals("Bean"), "an item arriving above it leaves the highlight on the same row");
        list.show(shelf, rows(new Thing("Aaa", 1, false), new Thing("Apple", 1, false), new Thing("Cup", 1, false)));
        check(list.highlight() == 2 && list.highlighted().name().equals("Cup"), "the highlighted row taken, the highlight stays where it was");
        list.show(shelf, rows(new Thing("Aaa", 1, false)));
        check(list.highlight() == 0, "and comes back onto the last row where the list grew shorter");
        list.show(shelf, rows());
        check(list.highlighted() == null && list.of() == shelf, "an emptied container is still listed, with no row");
        list.show(shelf, numbered(20));
        list.step(15);
        list.show(shelf, numbered(10));
        check(list.highlight() == 9 && list.top() == 2, "a list that shrinks under the view scrolls back to its end");
    }

    private static void aHeldDpadStepsOnceThenRepeats() {
        PadRepeat repeat = new PadRepeat();
        long t = 1_000_000_000L;
        check(!repeat.step(false, t), "let go, nothing");
        check(repeat.step(true, t), "a press steps at once");
        check(!repeat.step(true, t), "asked again in the same frame, once only");
        check(!repeat.step(true, t + PadRepeat.FIRST_NANOS - 1), "held, not again before the first repeat");
        check(repeat.step(true, t + PadRepeat.FIRST_NANOS), "then again");
        check(!repeat.step(true, t + PadRepeat.FIRST_NANOS + PadRepeat.EVERY_NANOS - 1), "and not before the next");
        check(repeat.step(true, t + PadRepeat.FIRST_NANOS + PadRepeat.EVERY_NANOS), "then every repeat while held");
        check(repeat.down() && !repeat.step(false, t + 2 * PadRepeat.FIRST_NANOS) && !repeat.down(), "let go, it is up");
        check(repeat.step(true, t + 2 * PadRepeat.FIRST_NANOS + 1), "and the next press steps at once");
    }

    private static void aTapTakesARowOnlyFromTheListedContainer() {
        check(ClickRules.tapTakesRow(true, true, true), "with the list up on the container the tap is on, a tap takes the highlighted row");
        check(!ClickRules.tapTakesRow(true, true, false), "an empty container's tap shows it in the loot panel as before");
        check(!ClickRules.tapTakesRow(true, false, true), "a tap on another container than the list's does what it did");
        check(!ClickRules.tapTakesRow(false, true, true), "and with the list not taking the input, the tap is as before");
        check(ClickRules.held(false, true, false, false) == ClickRules.Held.TAKE_ALL, "holding the key on a listed container with something in it takes all");
        check(InteractWords.Does.TAKE_ROW.key.equals("ContextMenu_Grab") && !InteractWords.Does.TAKE_ROW.named,
                "the label says the take in the words of the game's Grab");
        check(InteractWords.line("E", "Grab", "Take All").equals("[E] Grab   [Hold E] Take All"), "and says both");
    }

    private static void weightsAreWrittenToTwoPlaces() {
        check(LootList.round(3.2).equals("3.2") && LootList.round(0.123).equals("0.12") && LootList.round(4.0).equals("4")
                && LootList.round(0.005).equals("0.01") && LootList.round(12.5).equals("12.5"), "a weight to two places, without trailing zeros");
    }
}
