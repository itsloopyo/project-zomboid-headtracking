package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/** Tests that a LongTable answers as a HashMap keyed by Long does. */
final class LongTableTests {
    private LongTableTests() {}

    static void run() {
        Checks.run("aLongTableAnswersAsAHashMapDoes", LongTableTests::aLongTableAnswersAsAHashMapDoes);
        Checks.run("aLongTableTakesNoNull", LongTableTests::aLongTableTakesNoNull);
    }

    private static void aLongTableAnswersAsAHashMapDoes() {
        Random random = new Random(7);
        LongTable<Integer> table = new LongTable<>();
        HashMap<Long, Integer> model = new HashMap<>();
        // Keys as Terrain packs chunks and places, round zero and the far ends of a long, so that many share their low bits or their high ones.
        long[] some = new long[6000];
        for (int at = 0; at < some.length; at++) {
            int x = random.nextInt(200) - 100, y = random.nextInt(200) - 100;
            some[at] = switch (at % 4) {
                case 0 -> (long) x << 32 | y & 0xFFFFFFFFL;
                case 1 -> Footings.key(x + 0.5f, y + 0.5f);
                case 2 -> Long.MIN_VALUE + random.nextInt(50);
                default -> random.nextInt(50) - 25;
            };
        }
        for (int step = 0; step < 200_000; step++) {
            long key = some[random.nextInt(some.length)];
            int what = random.nextInt(1000);
            if (what == 0) {
                table.clear();
                model.clear();
            } else if (what < 400) {
                table.put(key, step);
                model.put(key, step);
            } else {
                Checks.check(java.util.Objects.equals(table.get(key), model.get(key)), "get " + key + " at step " + step);
                Checks.check(table.containsKey(key) == model.containsKey(key), "containsKey " + key + " at step " + step);
            }
        }
        List<Integer> values = new ArrayList<>();
        table.addValuesTo(values);
        values.sort(null);
        List<Integer> expected = new ArrayList<>(model.values());
        expected.sort(null);
        Checks.check(values.equals(expected), "the values are those the map holds: " + values.size() + " of " + expected.size());
    }

    private static void aLongTableTakesNoNull() {
        boolean refused = false;
        try {
            new LongTable<Integer>().put(1L, null);
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        Checks.check(refused, "a null value is refused");
    }
}
