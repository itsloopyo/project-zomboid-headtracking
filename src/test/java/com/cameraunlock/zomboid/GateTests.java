package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.nio.ByteBuffer;

/** Tests of the gates in the map's fences as Gates reads them off their sprites: which are built, their posts, rails and uprights. */
final class GateTests {
    private GateTests() {}

    static void run() {
        Checks.run("aGateIsBuiltAsTheFenceItStandsInIs", GateTests::aGateIsBuiltAsTheFenceItStandsInIs);
        Checks.run("aPostStandingHigherThanTheLeafIsReadApartFromIt", GateTests::aPostStandingHigherThanTheLeafIsReadApartFromIt);
        Checks.run("aFarmGatesRailsAndUprightsAreReadAsItPaintsThem", GateTests::aFarmGatesRailsAndUprightsAreReadAsItPaintsThem);
        Checks.run("aPostWithNoLeafBesideItIsNoGate", GateTests::aPostWithNoLeafBesideItIsNoGate);
        Checks.run("aGatesPartsMoveWithItsLeafWhenItIsStood", GateTests::aGatesPartsMoveWithItsLeafWhenItIsStood);
        Checks.run("aGateSwungOpenKeepsItsSizeItsRailsAndItsPost", GateTests::aGateSwungOpenKeepsItsSizeItsRailsAndItsPost);
        Checks.run("aTubeStoodUprightIsAsRoundAsOneLaidAcross", GateTests::aTubeStoodUprightIsAsRoundAsOneLaidAcross);
        Checks.run("aShotBesideAGatesLeafMeetsItsPostOrNothing", GateTests::aShotBesideAGatesLeafMeetsItsPostOrNothing);
    }

    private static void aGateIsBuiltAsTheFenceItStandsInIs() {
        check(Gates.of("fixtures_doors_fences_01_5", "WoodGateSmall") == Gates.BOARDS, "a small wooden gate is boards");
        check(Gates.of("fixtures_doors_fences_01_12", "WoodGate") == Gates.BOARDS, "and so is a wooden gate");
        check(Gates.of("fixtures_doors_fences_01_120", "FarmGate") == Gates.PIPES, "a farm gate is tube");
        check(Gates.of("fixtures_doors_fences_01_16", "MetalGate") == Gates.LINK && Gates.of("fixtures_doors_fences_01_65", "MetalGate") == Gates.LINK
                && Gates.of("fixtures_doors_fences_01_129", "MetalGate") == Gates.LINK, "the small, double and tall chain link gates are chain link");
        check(Gates.of("fixtures_doors_fences_01_24", "MetalGate") == Gates.NONE && Gates.of("fixtures_doors_fences_01_84", "MetalGate") == Gates.NONE,
                "a gate of branches lashed together stays its sprite");
        check(Gates.of("fixtures_doors_fences_01_32", "MetalPoleGateSmall") == Gates.NONE, "and so does an iron one");
        check(Gates.of("walls_logs_80", "WoodLogGate") == Gates.NONE && Gates.of("fixtures_doors_01_5", "WoodDoor") == Gates.NONE, "and anything off the sheet of fence gates");
        check(Gates.fencePosts("fixtures_doors_fences_01_16") && Gates.fencePosts("fixtures_doors_fences_01_128") && !Gates.fencePosts("fixtures_doors_fences_01_65"),
                "the fence stands the post of the small and the tall chain link gate, and leaves the double gates' to them");
    }

    /** Dots of a doubled sprite laid flat, a sheet with each dot's colour at four times its place, and where each dot is in it. */
    private static final class Laid {
        final int across = Windows.across(2f), high = Windows.high(2f);
        final byte[] dots = new byte[across * high];
        final ByteBuffer sheet = ByteBuffer.allocate(across * high * 4);
        final int[] at = new int[across * high];

        Laid() {
            for (int dot = 0; dot < at.length; dot++) {
                at[dot] = 4 * dot;
            }
        }

        /** Paints columns c0 to before c1 of rows r0 to before r1 solid in the colour red, green, blue out of 255. */
        void paint(int c0, int c1, int r0, int r1, int red, int green, int blue) {
            for (int row = r0; row < r1; row++) {
                for (int column = c0; column < c1; column++) {
                    int dot = row * across + column;
                    dots[dot] = Windows.FRAME;
                    sheet.put(at[dot], (byte) red).put(at[dot] + 1, (byte) green).put(at[dot] + 2, (byte) blue);
                }
            }
        }

        Windows.Shape measure(Doors.Shape parts) {
            return Gates.measure(sheet, dots, at, across, high, across, parts);
        }
    }

    private static void aPostStandingHigherThanTheLeafIsReadApartFromIt() {
        Laid gate = new Laid();
        gate.paint(2, 7, 40, gate.high, 102, 51, 0);
        gate.paint(7, 62, 70, gate.high, 204, 153, 102);
        Doors.Shape parts = new Doors.Shape();
        Windows.Shape leaf = gate.measure(parts);
        check(leaf != null, "a leaf beside a post is a gate");
        check(Math.abs(parts.postStart - 2f / gate.across) < 1e-5f && Math.abs(parts.postEnd - 7f / gate.across) < 1e-5f,
                "its post is the run of columns at its end standing higher: " + parts.postStart + ".." + parts.postEnd);
        check(Math.abs(parts.postTop - (1f - 40f / gate.high)) < 1e-5f, "as high as it is painted: " + parts.postTop);
        check(Math.abs(parts.postRed - 0.4f) < 1e-3f && Math.abs(parts.postGreen - 0.2f) < 1e-3f && parts.postBlue == 0f, "in the colour it is painted");
        check(leaf != null && Math.abs(leaf.start - 7f / gate.across) < 1e-5f && Math.abs(leaf.end - 62f / gate.across) < 1e-5f,
                "and the leaf is the rest, from the post on: " + (leaf == null ? "none" : leaf.start + ".." + leaf.end));
        check(leaf != null && Math.abs(leaf.head - (1f - 70f / gate.high)) < 1e-5f && Math.abs(leaf.foot) < 1e-5f, "from the ground up to its own head");
        int left = 0;
        for (int row = 0; row < gate.high; row++) {
            for (int column = 2; column < 7; column++) {
                left += gate.dots[row * gate.across + column] != Windows.EMPTY ? 1 : 0;
            }
        }
        check(left == 0, "with the post's dots cleared, so what is read of the leaf after is its own: " + left);

        Laid plain = new Laid();
        plain.paint(4, 60, 70, plain.high, 204, 153, 102);
        plain.paint(4, 60, 69, 70, 204, 153, 102);
        Doors.Shape none = new Doors.Shape();
        check(plain.measure(none) != null && Float.isNaN(none.postStart), "a leaf standing level from end to end has no post");
    }

    private static void aFarmGatesRailsAndUprightsAreReadAsItPaintsThem() {
        Laid gate = new Laid();
        int[] rails = {80, 120, 160, 186};
        for (int rail : rails) {
            gate.paint(4, 60, rail, rail + 4, 180, 180, 180);
        }
        gate.paint(4, 8, 80, 190, 180, 180, 180);
        gate.paint(56, 60, 80, 190, 180, 180, 180);
        Doors.Shape parts = new Doors.Shape();
        Windows.Shape leaf = gate.measure(parts);
        check(leaf != null && Float.isNaN(parts.postStart), "a farm gate's uprights at its ends stand no higher than its rails, so it has no post");
        check(parts.rails.length == 2 * rails.length, "each rail it paints across is a rail: " + parts.rails.length / 2);
        for (int at = 0; at < parts.rails.length && at / 2 < rails.length; at += 2) {
            int rail = rails[at / 2];
            check(Math.abs(parts.rails[at] - (1f - (rail + 4f) / gate.high)) < 1e-5f && Math.abs(parts.rails[at + 1] - (1f - (float) rail / gate.high)) < 1e-5f,
                    "from its foot to its head, the highest first: " + parts.rails[at] + ".." + parts.rails[at + 1]);
        }
        check(parts.stiles.length == 4 && Math.abs(parts.stiles[0] - 4f / gate.across) < 1e-5f && Math.abs(parts.stiles[3] - 60f / gate.across) < 1e-5f,
                "and its uprights are the columns painted up all of it, and not the stretches of rail between them: " + java.util.Arrays.toString(parts.stiles));
    }

    private static void aPostWithNoLeafBesideItIsNoGate() {
        Laid post = new Laid();
        post.paint(2, 7, 40, post.high, 102, 51, 0);
        post.paint(7, 14, 70, post.high, 204, 153, 102);
        check(post.measure(new Doors.Shape()) == null, "a post with a leaf beside it narrower than a fifth of the square is no gate");
        check(new Laid().measure(new Doors.Shape()) == null, "and a sprite that paints nothing is none");
    }

    private static void aGatesPartsMoveWithItsLeafWhenItIsStood() {
        Doors.Shape gate = new Doors.Shape();
        gate.leaf = new Windows.Shape();
        gate.leaf.start = 0.3f;
        gate.leaf.end = 0.6f;
        gate.leaf.foot = 0.1f;
        gate.leaf.head = 0.5f;
        gate.postStart = 0.25f;
        gate.postEnd = 0.3f;
        gate.postTop = 0.6f;
        gate.rails = new float[] {0.1f, 0.15f};
        gate.stiles = new float[] {0.25f, 0.35f};
        int across = 64, high = 192;
        check(Doors.standing(gate, across, high, 32, 0, false), "a gate painted on its square is stood on it");
        // Read with 32 dots' margin each side the row is twice the square, so a share of it is twice a share of the edge.
        check(Math.abs(gate.leaf.start - 0.1f) < 1e-5f && Math.abs(gate.leaf.end - 0.7f) < 1e-5f, "its leaf is where it stands on the square: " + gate.leaf.start + ".." + gate.leaf.end);
        check(Math.abs(gate.postStart) < 1e-5f && Math.abs(gate.postEnd - 0.1f) < 1e-5f, "and its post moves along with it: " + gate.postStart + ".." + gate.postEnd);
        check(Math.abs(gate.postTop - 0.6f) < 1e-5f && Math.abs(gate.rails[0] - 0.1f) < 1e-5f, "at the height it was read, on a leaf with its foot on the floor");
        check(Math.abs(gate.stiles[0] - 0.1f) < 1e-5f && Math.abs(gate.stiles[1] - 0.2f) < 1e-5f, "and its uprights are kept on the leaf: " + java.util.Arrays.toString(gate.stiles));
    }

    private static Doors.Shape gate(float start, float end, float head) {
        Doors.Shape gate = new Doors.Shape();
        gate.leaf = new Windows.Shape();
        gate.leaf.start = start;
        gate.leaf.end = end;
        gate.leaf.head = head;
        return gate;
    }

    private static void aGateSwungOpenKeepsItsSizeItsRailsAndItsPost() {
        // A farm gate shut from 0.1 to 0.9 along, 0.3 of a level high, hung on its post at the start with an upright beside its latch
        // at the end; its open sprite, seen end on, paints it from 0.05 to 0.45 along the other edge and 0.36 high.
        Doors.Shape shut = gate(0.1f, 0.9f, 0.3f), open = gate(0.05f, 0.45f, 0.36f);
        shut.handleAtEnd = true;
        shut.postStart = 0f;
        shut.postEnd = 0.1f;
        shut.postTop = 0.4f;
        shut.rails = new float[] {0.02f, 0.05f, 0.26f, 0.3f};
        shut.stiles = new float[] {0.8f, 0.9f};
        open.postStart = 0.3f;
        open.postEnd = 0.4f;
        open.rails = new float[] {0.1f, 0.2f};
        Doors.Shape swung = Doors.swung(open, shut, true);
        check(Math.abs(swung.leaf.head - 0.3f) < 1e-5f, "a gate swung open is as high as it is shut, not as its open sprite paints it: " + swung.leaf.head);
        check(Math.abs(swung.leaf.start - 0.1f) < 1e-5f && Math.abs(swung.leaf.end - 0.9f) < 1e-5f,
                "and as long, from as far from the corner as it hung shut: " + swung.leaf.start + ".." + swung.leaf.end);
        Doors.Shape atEnd = gate(0.2f, 0.9f, 0.3f);
        Doors.Shape other = Doors.swung(open, atEnd, true);
        check(Math.abs(other.leaf.start - 0.1f) < 1e-5f && Math.abs(other.leaf.end - 0.8f) < 1e-5f,
                "one hung at its end, from as far from that end's corner as it hung there: " + other.leaf.start + ".." + other.leaf.end);
        check(java.util.Arrays.equals(swung.rails, shut.rails), "with its rails where they are shut: " + java.util.Arrays.toString(swung.rails));
        check(swung.stiles.length == 2 && Math.abs(swung.stiles[0] - 0.8f) < 1e-5f && Math.abs(swung.stiles[1] - 0.9f) < 1e-5f,
                "its upright laid on the open leaf as a door's panels are, beside the latch at its end: " + java.util.Arrays.toString(swung.stiles));
        check(swung.postStart == 0f && swung.postEnd == 0.1f && swung.postTop == 0.4f, "and its post where the shut sprite paints it, which does not swing");
        Doors.Shape door = Doors.swung(open, shut, false);
        check(Math.abs(door.leaf.head - 0.36f) < 1e-5f && door.leaf.start == 0.05f && door.leaf.end == 0.45f, "a door is where its open sprite paints it");
    }

    /** A farm gate's uprights and a chain link gate's ends are tubes standing straight up, which have no one level way across them. */
    private static void aTubeStoodUprightIsAsRoundAsOneLaidAcross() {
        int sides = 8;
        float radius = 0.03f;
        SpriteGeometry.Mesh tube = new SpriteGeometry.Mesh();
        LightSiteTests.constant(tube);
        SpriteGeometry.addTube(tube, new float[] {1.5f, 2.5f, 0.1f}, new float[] {1.5f, 2.5f, 0.9f}, radius, sides, 1f, 1f, 1f);
        boolean round = true;
        for (int at = 0; at < sides * 6 * SpriteGeometry.VERTEX_FLOATS; at += SpriteGeometry.VERTEX_FLOATS) {
            float ox = tube.vertices[at] - 1.5f, oy = tube.vertices[at + 1] - 2.5f, up = tube.vertices[at + 2];
            float nx = tube.vertices[at + 9], ny = tube.vertices[at + 10], nz = tube.vertices[at + 11];
            round &= Math.abs((float) Math.sqrt(ox * ox + oy * oy) - radius) < 1e-5f && (Math.abs(up - 0.1f) < 1e-5f || Math.abs(up - 0.9f) < 1e-5f)
                    && Math.abs(nz) < 1e-5f && Math.abs(nx * nx + ny * ny - 1f) < 1e-5f && nx * ox + ny * oy > 0f;
        }
        check(round, "an upright tube's side stands its radius out from its axis, from its foot to its head, and faces straight out");
        boolean capped = true;
        for (int at = sides * 6 * SpriteGeometry.VERTEX_FLOATS; at < tube.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            capped &= Math.abs(Math.abs(tube.vertices[at + 11]) - 1f) < 1e-5f && !Float.isNaN(tube.vertices[at]) && !Float.isNaN(tube.vertices[at + 1]);
        }
        check(capped && tube.floats == sides * 12 * SpriteGeometry.VERTEX_FLOATS, "and its ends are closed facing straight up and down");
    }

    private static void aShotBesideAGatesLeafMeetsItsPostOrNothing() {
        Doors.Shape shut = gate(0.15f, 0.9f, 0.3f);
        shut.postStart = 0.05f;
        shut.postEnd = 0.15f;
        check(Gates.met(shut, false, 0.5f) == Gates.LEAF, "a shot across a shut gate's leaf meets the leaf");
        check(Gates.met(shut, false, 0.1f) == Gates.POST && Gates.met(shut, true, 0.1f) == Gates.POST, "across its post the post, shut or open");
        check(Gates.met(shut, false, 0.02f) == Gates.OPEN && Gates.met(shut, false, 0.95f) == Gates.OPEN, "and beside both, nothing, for the gate leaves that open");
        check(Gates.met(shut, true, 0.5f) == Gates.OPEN, "and where its leaf hangs shut, nothing once it is swung open");
        Doors.Shape postless = gate(0.1f, 0.9f, 0.3f);
        postless.postStart = postless.postEnd = Float.NaN;
        check(Gates.met(postless, false, 0.05f) == Gates.OPEN && Gates.met(postless, false, 0.5f) == Gates.LEAF, "a gate with no post is its leaf and nothing");
        check(Gates.met((Doors.Shape) null, false, 0.02f) == Gates.LEAF && Gates.met((Doors.Shape) null, true, 0.5f) == Gates.OPEN,
                "and one not measured yet is its leaf all along its edge shut, as a door is, and nothing open");
    }
}
