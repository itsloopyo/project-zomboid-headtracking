package com.cameraunlock.zomboid;

/** Tests of where the wires between the big pylons are strung across their squares. */
final class PylonWireTests {
    private PylonWireTests() {}

    static void run() {
        Checks.run("aRunOfPylonWireMeetsEndToEndOnTheNextSquare", PylonWireTests::aRunOfPylonWireMeetsEndToEndOnTheNextSquare);
    }

    private static void aRunOfPylonWireMeetsEndToEndOnTheNextSquare() {
        for (int place = 160; place <= 167; place++) {
            float[] span = PylonWires.of("electricity_pylon_" + place);
            Checks.check(span != null, "electricity_pylon_" + place + " is a wire");
            if (span == null) {
                continue;
            }
            // The next square of the run is the one the wire's far end is in, as many squares on as the wire runs.
            float stepX = Math.round(span[2] - span[0]), stepY = Math.round(span[3] - span[1]);
            Checks.check(Math.abs(span[0] + stepX - span[2]) < 1e-5f && Math.abs(span[1] + stepY - span[3]) < 1e-5f,
                    "electricity_pylon_" + place + " ends where the same wire starts on the next square of its run");
            Checks.check(stepX != 0f || stepY != 0f, "electricity_pylon_" + place + " runs on to another square");
        }
        for (int place = 170; place <= 171; place++) {
            String name = "electricity_pylon_" + place;
            float along = PylonWires.ball(name);
            Checks.check(PylonWires.of(name) != null && along > 0f && along < 1f, name + " is a wire with a marker ball along it");
        }
        Checks.check(Float.isNaN(PylonWires.ball("electricity_pylon_162")), "a wire with no ball has none");
        Checks.check(PylonWires.of("electricity_pylon_159") == null && PylonWires.of("electricity_pylon_168") == null
                && PylonWires.of("appliances_com_01_96") == null, "only the pylons' wires are");
    }
}
