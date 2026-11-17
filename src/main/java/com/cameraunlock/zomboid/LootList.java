package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * The rows of the quick loot list beside the crosshair (QuickLoot), which one is highlighted and
 * which are in view. A row is every item of one name, as the game's inventory pane groups them
 * (ISInventoryPane.refreshContainer: hidden items left out, one entry a name, its weight the sum
 * of the items' unequipped weights). Rows are listed by name. Nothing here reads the game.
 *
 * Logic thread only.
 *
 * @param <T> an item
 */
final class LootList<T> {
    /** How many rows are in view at once; the rest are scrolled to. */
    static final int SHOWN = 8;

    /** Every item of one name. */
    record Row<T>(String name, double weight, List<T> items) {
        int count() {
            return items.size();
        }
    }

    private static final Comparator<Row<?>> BY_NAME = Comparator.<Row<?>, String>comparing(row -> row.name(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Row::name);

    private Object of;
    private List<Row<T>> rows = List.of();
    private int highlight, top;

    /** A weight to two places, with no trailing zeros. */
    static String round(double weight) {
        String said = String.format(java.util.Locale.ROOT, "%.2f", Math.round(weight * 100.0) / 100.0);
        int end = said.length();
        while (said.charAt(end - 1) == '0') {
            end--;
        }
        return said.substring(0, said.charAt(end - 1) == '.' ? end - 1 : end);
    }

    /** The rows of items, as the game's inventory pane groups them, by name. */
    static <T> List<Row<T>> rows(List<T> items, Function<T, String> name, ToDoubleFunction<T> weight, Predicate<T> hidden) {
        Map<String, List<T>> byName = new LinkedHashMap<>();
        Map<String, double[]> weights = new LinkedHashMap<>();
        for (T item : items) {
            if (hidden.test(item)) {
                continue;
            }
            String named = name.apply(item);
            byName.computeIfAbsent(named, k -> new ArrayList<>()).add(item);
            weights.computeIfAbsent(named, k -> new double[1])[0] += weight.applyAsDouble(item);
        }
        List<Row<T>> rows = new ArrayList<>(byName.size());
        for (Map.Entry<String, List<T>> each : byName.entrySet()) {
            rows.add(new Row<>(each.getKey(), weights.get(each.getKey())[0], List.copyOf(each.getValue())));
        }
        rows.sort(BY_NAME);
        return rows;
    }

    /**
     * Shows the rows of what of is. Another thing than before starts on its first row; the same
     * thing keeps the highlight on the row of the same name where it is still there, and otherwise
     * on the same place in the list, as when the highlighted row was taken.
     */
    void show(Object of, List<Row<T>> rows) {
        if (of != this.of) {
            this.of = of;
            this.rows = rows;
            highlight = 0;
            top = 0;
            return;
        }
        String was = highlighted() == null ? null : highlighted().name();
        this.rows = rows;
        int found = -1;
        for (int i = 0; was != null && i < rows.size(); i++) {
            if (rows.get(i).name().equals(was)) {
                found = i;
                break;
            }
        }
        highlight = found >= 0 ? found : Math.min(highlight, Math.max(0, rows.size() - 1));
        keepInView();
    }

    /** Nothing shown. */
    void clear() {
        of = null;
        rows = List.of();
        highlight = 0;
        top = 0;
    }

    /** Moves the highlight by rows, down the list for more than 0, stopping at either end. */
    void step(int by) {
        if (rows.isEmpty()) {
            return;
        }
        highlight = Math.max(0, Math.min(rows.size() - 1, highlight + by));
        keepInView();
    }

    private void keepInView() {
        if (highlight < top) {
            top = highlight;
        } else if (highlight >= top + SHOWN) {
            top = highlight - SHOWN + 1;
        }
        top = Math.max(0, Math.min(top, Math.max(0, rows.size() - SHOWN)));
    }

    /** What the rows are of, or null where nothing is shown. */
    Object of() {
        return of;
    }

    List<Row<T>> rows() {
        return rows;
    }

    /** The highlighted row, or null where there are none. */
    Row<T> highlighted() {
        return rows.isEmpty() ? null : rows.get(highlight);
    }

    int highlight() {
        return highlight;
    }

    /** The first row in view. */
    int top() {
        return top;
    }

    /** How many rows are in view. */
    int inView() {
        return Math.min(SHOWN, rows.size() - top);
    }
}
