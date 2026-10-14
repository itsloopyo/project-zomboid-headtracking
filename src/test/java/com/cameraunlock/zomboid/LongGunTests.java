package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;

/** Tests of how far the eye leans in to the mod's own long guns with the sights up. */
final class LongGunTests {
    private LongGunTests() {}

    static void run() {
        Checks.run("theEyeIsStoppedShortOfWhatStandsInTheLineOfSight", LongGunTests::theEyeIsStoppedShortOfWhatStandsInTheLineOfSight);
    }

    private static ByteBuffer file(String name) throws IOException {
        try (InputStream packed = new GZIPInputStream(LongGunTests.class.getResourceAsStream("/models/" + name + ".mesh.gz"))) {
            return ByteBuffer.wrap(packed.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    /**
     * What stops the eye on each own long gun with the sights up is its rear sight, or where the
     * eye looks along a rib or a rail to a front one, that one, this far ahead of the grip in its
     * metres: NaN where nothing stands in the line. On a lever gun it is the hammer's spur, which
     * the bolt pushes back past cocked into the line as the lever is worked. Every own long gun is
     * here.
     */
    private static final java.util.Map<String, Float> SIGHTS = java.util.Map.ofEntries(java.util.Map.entry("rifle", 0.450f),
            java.util.Map.entry("varmint", 0.469f), java.util.Map.entry("leverrifle", 0.015f), java.util.Map.entry("levercarbine", 0.015f),
            java.util.Map.entry("js14", 0.000f), java.util.Map.entry("trapper", 0.274f), java.util.Map.entry("js3t", 0.039f),
            java.util.Map.entry("toy", 0.018f), java.util.Map.entry("shotgun", Float.NaN), java.util.Map.entry("sawnoff", Float.NaN),
            java.util.Map.entry("dbarrel", Float.NaN), java.util.Map.entry("dbarrelsawn", Float.NaN), java.util.Map.entry("tactical", Float.NaN));

    private static void theEyeIsStoppedShortOfWhatStandsInTheLineOfSight() throws IOException {
        for (OwnModel model : OwnModel.ALL) {
            if (model.handgun) {
                continue;
            }
            String name = model.mesh.substring("/models/".length(), model.mesh.length() - ".mesh.gz".length());
            check(SIGHTS.containsKey(name), name + " says where its rear sight is");
            if (!SIGHTS.containsKey(name)) {
                continue;
            }
            ByteBuffer read = file(name);
            OwnModel.Layout layout = OwnModel.layout(read, name);
            float found = OwnModel.inTheWay(model.reached(layout, layout.places(read), layout.corners(read)), layout.muzzle(), layout.sightLine(),
                    layout.eyeBehind(), model.top());
            float expected = SIGHTS.get(name);
            check(Float.isNaN(expected) ? Float.isNaN(found) : Math.abs(found - expected) < 0.005f,
                    name + "'s eye is stopped at its rear sight, " + expected + " ahead of the grip: found " + found);
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}
