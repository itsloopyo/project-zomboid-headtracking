package com.cameraunlock.zomboid;

import java.util.Arrays;

/** Tests of the proportions a broad-leaved tree's wood is grown to: see TreeForm and Flora.addCrown. */
final class TreeFormTests {
    private TreeFormTests() {}

    static void run() {
        Checks.run("evergreensAndOtherTilesHaveNoForm", TreeFormTests::evergreensAndOtherTilesHaveNoForm);
        Checks.run("aThinningCrownsWoodIsItsBareTreesWood", TreeFormTests::aThinningCrownsWoodIsItsBareTreesWood);
        Checks.run("aSpeciesWoodCostsAboutWhatAnyTreesDoes", TreeFormTests::aSpeciesWoodCostsAboutWhatAnyTreesDoes);
        Checks.run("noWoodRunsStraightForLong", TreeFormTests::noWoodRunsStraightForLong);
        Checks.run("aLeaderPartsIntoLimbsBelowTheTop", TreeFormTests::aLeaderPartsIntoLimbsBelowTheTop);
        Checks.run("woodCarriesItsThicknessForItsSnow", TreeFormTests::woodCarriesItsThicknessForItsSnow);
    }

    private static void evergreensAndOtherTilesHaveNoForm() {
        Checks.check(TreeForm.kindOf("e_americanholly_1_3") == TreeForm.NONE, "holly keeps its leaves");
        Checks.check(TreeForm.kindOf("e_canadianhemlockJUMBO_1_1") == TreeForm.NONE, "hemlock keeps its leaves");
        Checks.check(TreeForm.kindOf("e_virginiapine_1_2") == TreeForm.NONE, "pine keeps its leaves");
        Checks.check(TreeForm.kindOf("fencing_burnt_01_16") == TreeForm.NONE, "a dead tree");
        Checks.check(TreeForm.kindOf("e_redmaple_1_3") == TreeForm.MAPLE, "red maple");
        Checks.check(TreeForm.kindOf("e_riverbirchJUMBO_1_1") == TreeForm.BIRCH, "a big river birch");
        Checks.check(TreeForm.footed(TreeForm.BIRCH) && TreeForm.footed(TreeForm.HAWTHORN), "birch and hawthorn are stems from a short foot");
        Checks.check(!TreeForm.footed(TreeForm.REDBUD) && !TreeForm.footed(TreeForm.LINDEN) && !TreeForm.footed(TreeForm.NONE), "redbud opens from its bole, linden has a leader");
    }

    /** Branches painted in the upper half of a sprite 128 by 256, as a bare tree's trunk sprite paints them, over a trunk. */
    private static boolean trunk(int u, int v) {
        float du = u - 64f, dv = v - 90f;
        return du * du / 2500f + dv * dv / 3600f < 1f && ((u + v) % 7 < 2 || Math.abs(du) < 3f) || Math.abs(du) < 5f && v > 90;
    }

    /** A crown of leaves the same tree bears in autumn, rounder and a little lower. */
    private static boolean leaves(int u, int v) {
        float du = u - 64f, dv = v - 96f;
        return du * du + dv * dv < 52f * 52f;
    }

    private static SpriteGeometry.Frame frame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 1f;
        frame.width = 128f;
        frame.height = 256f;
        frame.uStart = 0.25f;
        frame.uEnd = 0.5f;
        frame.vStart = 0.5f;
        frame.vEnd = 1f;
        frame.open = 1f;
        frame.light(0.8f, 0.8f, 0.8f);
        return frame;
    }

    private static final float FOOT_U = 64f, FOOT_V = 240f;

    /**
     * From the first leaf a broad-leaved tree loses to bare, its wood is the bare tree's: grown
     * to the outline its trunk sprite paints, whatever crown of leaves it bears, so it never
     * changes shape as the leaves go.
     */
    private static void aThinningCrownsWoodIsItsBareTreesWood() {
        SpriteGeometry.Frame frame = frame();
        float[] bareAt = Flora.leafPlaces(TreeFormTests::trunk, frame), leafAt = Flora.leafPlaces(TreeFormTests::leaves, frame);
        float[] shape = Flora.woodShape(frame, bareAt, FOOT_U, FOOT_V);
        Checks.check(shape != null, "the painted branches have an outline");
        for (int kind = 0; kind < TreeForm.KINDS; kind++) {
            SpriteGeometry.Mesh bareWood = new SpriteGeometry.Mesh(), thinWood = new SpriteGeometry.Mesh();
            Checks.check(Flora.addCrown(new SpriteGeometry.Mesh(), bareWood, frame, bareAt, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0.08f, 0.8f, Flora.BARK_MAPLE,
                    Flora.BARE, 99, Flora.OPEN, 1f, 0, 0f, kind, shape), "kind " + kind + " bare");
            for (float leaf : new float[] {0.85f, 0.5f, 0.1f}) {
                thinWood.clear();
                Checks.check(Flora.addCrown(new SpriteGeometry.Mesh(), thinWood, frame, leafAt, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0.08f, 0.8f, Flora.BARK_MAPLE,
                        Flora.MAPLE, 99, Flora.OPEN, leaf, Flora.SCARLET_LEAF, 0.5f, kind, shape), "kind " + kind + " with " + leaf + " of its leaves");
                Checks.check(Arrays.equals(bareWood.vertices, 0, bareWood.floats, thinWood.vertices, 0, thinWood.floats),
                        "kind " + kind + " with " + leaf + " of its leaves has its bare tree's wood: " + thinWood.floats + " floats against " + bareWood.floats);
            }
        }
    }

    /**
     * Every ring of a crown's wood carries how thick it is there, for the ridge of snow along
     * its top (SpriteGeometry.WOOD, SnowShader.RIDGE): as thick as the wood is, and marked as
     * carrying no ridge where the wood ends free, so the ridge runs out to nothing at a tip and
     * leaves no open end standing; and nowhere on a twig among leaves, whose snow is on the
     * leaves. A bole carries none.
     */
    private static void woodCarriesItsThicknessForItsSnow() {
        int f = SpriteGeometry.VERTEX_FLOATS;
        for (int kind = -1; kind < TreeForm.KINDS; kind++) {
            SpriteGeometry.Mesh wood = bareWood(kind < 0 ? TreeForm.NONE : kind, kind < 0 ? null : shape());
            int ridged = 0, free = 0;
            for (int at = 0; at < wood.floats; at += f) {
                float thick = wood.vertices[at + SpriteGeometry.ALONG_AT];
                Checks.check(wood.vertices[at + SpriteGeometry.WAY_AT] == SpriteGeometry.WOOD, "kind " + kind + ": every vertex of the wood is wood");
                Checks.check(Math.abs(thick) >= 0.0039f && Math.abs(thick) < 0.2f, "kind " + kind + ": wood is as thick as it is, not " + thick);
                if (thick > 0f) {
                    ridged++;
                } else {
                    free++;
                }
            }
            Checks.check(ridged > free && free > 0, "kind " + kind + ": most wood carries a ridge, its free ends none: " + ridged + " and " + free);
            // Every vertex at one place carries one thickness, so the ridge has no step there.
            java.util.Map<String, Float> at = new java.util.HashMap<>();
            int met = 0;
            for (int v = 0; v < wood.floats; v += f) {
                float thick = wood.vertices[v + SpriteGeometry.ALONG_AT];
                if (thick <= 0f) {
                    continue;
                }
                String place = wood.vertices[v] + "," + wood.vertices[v + 1] + "," + wood.vertices[v + 2];
                Float other = at.putIfAbsent(place, thick);
                if (other != null && other != thick) {
                    met++;
                }
            }
            Checks.check(met == 0, "kind " + kind + ": a ring carries one thickness: " + met + " differ");
        }
        // A crown in leaf, stood under, with no bole to bear limbs: all its wood is twigs among its leaves.
        SpriteGeometry.Frame frame = frame();
        SpriteGeometry.Mesh twigs = new SpriteGeometry.Mesh();
        Checks.check(Flora.addCrown(new SpriteGeometry.Mesh(), twigs, frame, Flora.leafPlaces(TreeFormTests::leaves, frame), 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 1f,
                0f, 0f, Flora.BARK_MAPLE, Flora.MAPLE, 99, Flora.OPEN, 1f, 0, 0f), "a crown in leaf");
        Checks.check(twigs.floats > 0, "stood under, a crown in leaf has twigs");
        for (int at = 0; at < twigs.floats; at += f) {
            Checks.check(twigs.vertices[at + SpriteGeometry.WAY_AT] == SpriteGeometry.WOOD && twigs.vertices[at + SpriteGeometry.ALONG_AT] < 0f,
                    "a twig among leaves carries no ridge");
        }
        SpriteGeometry.Mesh bole = new SpriteGeometry.Mesh();
        new Flora().addTrunk(bole, 10.5f, 20.5f, 0f, 0.12f, 0.8f, true, Flora.BARK_MAPLE, 99, frame);
        for (int at = 0; at < bole.floats; at += f) {
            Checks.check(bole.vertices[at + SpriteGeometry.WAY_AT] != SpriteGeometry.WOOD, "a bole carries no ridge");
        }
    }

    /** A bare crown's wood of the kind (NONE for any tree), standing on (10.5, 20.5). */
    private static SpriteGeometry.Mesh bareWood(int kind, float[] shape) {
        SpriteGeometry.Frame frame = frame();
        SpriteGeometry.Mesh wood = new SpriteGeometry.Mesh();
        Checks.check(Flora.addCrown(new SpriteGeometry.Mesh(), wood, frame, Flora.leafPlaces(TreeFormTests::trunk, frame), 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 0f,
                0.08f, 0.8f, Flora.BARK_MAPLE, Flora.BARE, 99, Flora.OPEN, 1f, 0, 0f, kind, shape), "kind " + kind + " has a bare crown");
        return wood;
    }

    private static float[] shape() {
        SpriteGeometry.Frame frame = frame();
        return Flora.woodShape(frame, Flora.leafPlaces(TreeFormTests::trunk, frame), FOOT_U, FOOT_V);
    }

    /**
     * Wood grows from bud to bud, bending a little at each: no piece of a bare tree's wood,
     * leader, limb, branch or twig, runs straight for more than a fifth of the crown's height,
     * so no long straight rod stands out of its outline.
     */
    private static void noWoodRunsStraightForLong() {
        float[] shape = shape();
        float most = 0.2f * shape[Flora.TALL];
        for (int kind = TreeForm.NONE; kind < TreeForm.KINDS; kind++) {
            SpriteGeometry.Mesh wood = bareWood(kind, shape);
            float longest = 0f;
            // Each side of a piece is a quad of six vertices, the first at the piece's start and the last at its end.
            for (int at = 0; at + 6 * SpriteGeometry.VERTEX_FLOATS <= wood.floats; at += 6 * SpriteGeometry.VERTEX_FLOATS) {
                int end = at + 5 * SpriteGeometry.VERTEX_FLOATS;
                float dx = wood.vertices[end] - wood.vertices[at], dy = wood.vertices[end + 1] - wood.vertices[at + 1], dz = wood.vertices[end + 2] - wood.vertices[at + 2];
                longest = Math.max(longest, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            Checks.check(longest <= most, "kind " + kind + ": a straight piece of wood " + longest + " tiles long, in a crown " + shape[Flora.TALL] + " tall");
        }
    }

    /** A leader ends by parting into limbs below the crown's top: none stands up through it as a pole, but a silverbell's, which runs on up. */
    private static void aLeaderPartsIntoLimbsBelowTheTop() {
        float[] shape = shape();
        for (int kind = TreeForm.NONE; kind < TreeForm.KINDS; kind++) {
            SpriteGeometry.Mesh wood = bareWood(kind, shape);
            float highest = 0f;
            for (int at = 0; at < wood.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                if (Math.hypot(wood.vertices[at] - 10.5f, wood.vertices[at + 1] - 20.5f) < 0.06f) {
                    highest = Math.max(highest, wood.vertices[at + 2]);
                }
            }
            float top = shape[Flora.LOWEST] + (kind == TreeForm.SILVERBELL ? 1f : 0.76f) * shape[Flora.TALL];
            Checks.check(highest <= top, "kind " + kind + ": wood up the tree's middle to " + highest + ", over " + top);
        }
    }

    /** No species grows much more wood than any other tree of its size does. */
    private static void aSpeciesWoodCostsAboutWhatAnyTreesDoes() {
        SpriteGeometry.Frame frame = frame();
        float[] bareAt = Flora.leafPlaces(TreeFormTests::trunk, frame);
        float[] shape = Flora.woodShape(frame, bareAt, FOOT_U, FOOT_V);
        SpriteGeometry.Mesh plain = new SpriteGeometry.Mesh();
        Flora.addCrown(new SpriteGeometry.Mesh(), plain, frame, bareAt, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0.08f, 0.8f, Flora.BARK_MAPLE, Flora.BARE, 99,
                Flora.OPEN, 1f, 0, 0f, TreeForm.NONE, shape);
        for (int kind = 0; kind < TreeForm.KINDS; kind++) {
            SpriteGeometry.Mesh wood = new SpriteGeometry.Mesh();
            Flora.addCrown(new SpriteGeometry.Mesh(), wood, frame, bareAt, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0.08f, 0.8f, Flora.BARK_MAPLE, Flora.BARE, 99,
                    Flora.OPEN, 1f, 0, 0f, kind, shape);
            Checks.check(wood.triangles() > 0 && wood.triangles() < 1.6f * plain.triangles(),
                    "kind " + kind + ": " + wood.triangles() + " triangles of wood against " + plain.triangles() + " for any tree");
        }
    }
}
