package com.cameraunlock.zomboid;

import java.util.Arrays;

/** Tests of the proportions a broad-leaved tree's wood is grown to: see TreeForm and Flora.addCrown. */
final class TreeFormTests {
    private TreeFormTests() {}

    static void run() {
        Checks.run("evergreensAndOtherTilesHaveNoForm", TreeFormTests::evergreensAndOtherTilesHaveNoForm);
        Checks.run("aThinningCrownsWoodIsItsBareTreesWood", TreeFormTests::aThinningCrownsWoodIsItsBareTreesWood);
        Checks.run("aSpeciesWoodCostsAboutWhatAnyTreesDoes", TreeFormTests::aSpeciesWoodCostsAboutWhatAnyTreesDoes);
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
            Checks.check(wood.triangles() > 0 && wood.triangles() < 1.4f * plain.triangles(),
                    "kind " + kind + ": " + wood.triangles() + " triangles of wood against " + plain.triangles() + " for any tree");
        }
    }
}
