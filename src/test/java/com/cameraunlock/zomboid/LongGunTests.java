package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;

/** Tests of how far the eye leans in to the mod's own long guns with the sights up, and where what the game fits to them goes. */
final class LongGunTests {
    private LongGunTests() {}

    static void run() {
        Checks.run("theEyeIsStoppedShortOfWhatStandsInTheLineOfSight", LongGunTests::theEyeIsStoppedShortOfWhatStandsInTheLineOfSight);
        Checks.run("aRedDotOrLaserGoesWhereTheModelHasAPlaceForIt", LongGunTests::aRedDotOrLaserGoesWhereTheModelHasAPlaceForIt);
        Checks.run("aVersionSixFileSaysWhereItsRedDotAndLaserGo", LongGunTests::aVersionSixFileSaysWhereItsRedDotAndLaserGo);
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
            java.util.Map.entry("dbarrel", Float.NaN), java.util.Map.entry("dbarrelsawn", Float.NaN), java.util.Map.entry("tactical", Float.NaN),
            java.util.Map.entry("assault", -0.029f), java.util.Map.entry("battle", 0.011f));

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

    /**
     * The game fits a red dot sight to the self-loading rifle and carbine and the tactical shotgun, a laser to the carbine,
     * the tactical rifle and the three pistols, and a light to the pistols: each model says where, and of a gun the game
     * fits none to, nothing. A red dot sight stands on the gun's top with its tube's axis over the open sights' line,
     * which is where the eye is raised to; a laser and a light hang under the bore, ahead of the hand on the grip.
     */
    private static void aRedDotOrLaserGoesWhereTheModelHasAPlaceForIt() throws IOException {
        String[][] fitted = {{"shotgun", "", "", ""}, {"sawnoff", "", "", ""}, {"rifle", "", "", ""}, {"dbarrel", "", "", ""}, {"dbarrelsawn", "", "", ""},
                {"varmint", "", "", ""}, {"leverrifle", "", "", ""}, {"levercarbine", "", "", ""}, {"toy", "", "", ""}, {"js14", "reddot", "", ""},
                {"trapper", "reddot", "laser", ""}, {"js3t", "reddot", "", ""}, {"tactical", "", "laser", ""}, {"revolver", "", "", ""},
                {"magnum", "", "", ""}, {"snubnose", "", "", ""}, {"capgun", "", "", ""}, {"pistol", "", "laser", "gunlight"},
                {"pistol2", "", "laser", "gunlight"}, {"pistol3", "", "laser", "gunlight"},
                {"assault", "reddot", "laser", ""}, {"battle", "reddot", "laser", ""}};
        for (OwnModel model : OwnModel.ALL) {
            String name = model.mesh.substring("/models/".length(), model.mesh.length() - ".mesh.gz".length());
            String[] gun = null;
            for (String[] row : fitted) {
                gun = row[0].equals(name) ? row : gun;
            }
            check(gun != null, name + " says what the game fits to it");
            if (gun == null) {
                continue;
            }
            ByteBuffer read = file(name);
            OwnModel.Layout layout = OwnModel.layout(read, name);
            float[] mounts = layout.mounts(), top = model.top();
            for (int kind = 1; kind <= 3; kind++) {
                int at = kind == 1 ? OwnModel.RED_DOT : kind == 2 ? OwnModel.LASER : OwnModel.GUN_LIGHT;
                boolean has = mounts != null && !Float.isNaN(mounts[at]);
                check(has == !gun[kind].isEmpty(), name + (has ? " has" : " has no") + " place for the game's " + new String[] {"", "red dot sight", "laser", "light"}[kind]);
                if (!has) {
                    continue;
                }
                float over = (mounts[at] - layout.muzzle()[0]) * top[0] + (mounts[at + 1] - layout.muzzle()[1]) * top[1] + (mounts[at + 2] - layout.muzzle()[2]) * top[2];
                if (at == OwnModel.RED_DOT) {
                    float axis = over + OwnModel.RED_DOT_AXIS / Characters.PER_METRE;
                    check(over > 0f && axis > layout.sightLine(), name + "'s red dot sight stands on its top, " + over + " over the bore, its axis "
                            + axis + " over the open sights' " + layout.sightLine());
                    check(mounts[at + 1] > -0.1f && mounts[at + 1] < layout.muzzle()[1] / 2f, "and over its receiver, " + mounts[at + 1] + " ahead of the grip");
                } else {
                    check(over < 0f, name + "'s " + gun[kind] + " hangs under its bore, " + over);
                    check(mounts[at + 1] > 0f && mounts[at + 1] < layout.muzzle()[1], "ahead of the grip and short of the muzzle: " + mounts[at + 1]);
                }
            }
        }
    }

    /** A file of version 6 with no triangles, read: its three places after the numbers that say how its parts move. */
    private static void aVersionSixFileSaysWhereItsRedDotAndLaserGo() throws IOException {
        ByteBuffer file = ByteBuffer.allocate(4 + 4 + 12 * 4 + 4 + 4 + 4 + 4 + 4 * 2 + 9 * 4).order(ByteOrder.LITTLE_ENDIAN);
        file.putInt('C' | 'U' << 8 | 'W' << 16 | 'M' << 24).putInt(6);
        for (float number : new float[] {0f, 0.8f, 0.05f, 0f, 0.3f, -0.03f, 0f, 0f, 0f, 0f, 0.03f, 0.13f}) {
            file.putFloat(number);
        }
        file.putInt(0).putInt(1).putInt(0).putInt(2).putFloat(0.5f).putFloat(0.25f);
        for (float number : new float[] {0f, -0.02f, 0.071f, Float.NaN, Float.NaN, Float.NaN, 0f, 0.1f, -0.03f}) {
            file.putFloat(number);
        }
        file.flip();
        OwnModel.Layout layout = OwnModel.layout(file, "a version 6 file");
        float[] mounts = layout.mounts();
        check(mounts != null && mounts.length == OwnModel.MOUNT_NUMBERS, "it has its places");
        check(mounts[OwnModel.RED_DOT + 1] == -0.02f && mounts[OwnModel.RED_DOT + 2] == 0.071f, "the red dot sight's, as written");
        check(Float.isNaN(mounts[OwnModel.LASER]), "no laser's");
        check(mounts[OwnModel.GUN_LIGHT + 2] == -0.03f, "and the light's");
        check(layout.moves().length == 2 && layout.moves()[1] == 0.25f, "after its moves, which are as they were");
        check(file.remaining() == 0, "and nothing is left over");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}
