package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.Arrays;

/**
 * The pickets shots break out of a picket fence the picture stands as a model (ShotPickets): which picket a hit
 * breaks and how much of it, the masks saved with the fence, a line through the gap meeting nothing, and the mask
 * as the vertex programs read it off each instance.
 */
final class ShotPicketTests {
    private ShotPicketTests() {}

    static void run() {
        Checks.run("thePicketsOfEachFenceAreToldApart", ShotPicketTests::thePicketsOfEachFenceAreToldApart);
        Checks.run("aHitBreaksThePicketItStrikes", ShotPicketTests::aHitBreaksThePicketItStrikes);
        Checks.run("whatBreaksOffIsWhatWasLeftOfThePicket", ShotPicketTests::whatBreaksOffIsWhatWasLeftOfThePicket);
        Checks.run("aLineThroughTheGapMeetsNothing", ShotPicketTests::aLineThroughTheGapMeetsNothing);
        Checks.run("theSavedMasksReadBackTheSame", ShotPicketTests::theSavedMasksReadBackTheSame);
        Checks.run("anInstanceCarriesTheMaskAsTheVertexProgramsReadIt", ShotPicketTests::anInstanceCarriesTheMaskAsTheVertexProgramsReadIt);
        Checks.run("theMarksOnWhatBrokeOffGoWithIt", ShotPicketTests::theMarksOnWhatBrokeOffGoWithIt);
    }

    private static final ModelFences.Fence[] FENCES = {ModelFences.WHITE, ModelFences.ROUNDED};
    /** How many pickets each has, as tools/models/furniture.py makes them. */
    private static final int[] PICKETS = {6, 9};
    /** A height between the lowest two rails of both, and one under the lowest. */
    private static final float BETWEEN_RAILS = 0.35f, UNDER_RAILS = 0.05f;

    private static void thePicketsOfEachFenceAreToldApart() {
        for (int f = 0; f < FENCES.length; f++) {
            Models.Model model = Models.get(FENCES[f].model());
            for (Models.Model each : new Models.Model[] {model, model.plain}) {
                ShotPickets.Layout layout = ShotPickets.layout(each);
                String which = each.name + (each == model ? "" : " (plain)");
                check(layout.count() == PICKETS[f], which + " has its " + PICKETS[f] + " pickets told apart: " + layout.count());
                check(Math.abs(layout.foot() - 0.10f) < 0.005f, which + "'s lowest rail starts 0.10 m up: " + layout.foot());
                for (int k = 1; k < layout.count(); k++) {
                    check(layout.from()[k] > layout.to()[k - 1], which + "'s pickets are numbered in order along it, apart: " + k);
                }
                check(ShotPickets.layoutAt(each.from) == layout, which + " is looked up by its first corner as the picture draws it");
            }
            check(ShotPickets.layout(model).railTop() == ShotPickets.layout(model.plain).railTop(), FENCES[f].model() + "'s rails are as high in its plain model");
        }
        check(ShotPickets.layoutAt(Models.get(ModelFences.BARBED.model()).from) == null, "barbed wire has no pickets");
    }

    private static final float[] hit = new float[ModelFences.HIT_FLOATS];

    /** A height over a fence's highest rail, between two of the steps a picket breaks at there. */
    private static float overRails(ShotPickets.Layout layout) {
        return layout.railTop() + 0.55f * (layout.top() - layout.railTop());
    }

    /** Where along the north (or west) edge a picket of a length of fence stands, from its square's corner: see ShotPickets.shoot. */
    private static float alongOf(ShotPickets.Layout layout, int picket, boolean north) {
        float middle = (layout.from()[picket] + layout.to()[picket]) / 2f;
        return north ? 0.5f + middle : 0.5f - middle;
    }

    /** Whether a level line from the rails' side across the north (or west) edge of square (10, 20), that far along it and that high over the foot, meets the fence as broken says. */
    private static boolean meets(ModelFences.Fence fence, boolean north, float along, float up, long broken) {
        ViewCamera.Pose line = new ViewCamera.Pose();
        line.eyeAt(north ? 10f + along : 9f, north ? 19f : 20f + along, up);
        line.forwardX = north ? 0f : 1f;
        line.forwardY = north ? 1f : 0f;
        line.forwardUp = 0f;
        return ModelFences.struck(fence, 10, 20, north, 0f, line, 2f, broken, hit);
    }

    private static void aHitBreaksThePicketItStrikes() {
        for (ModelFences.Fence fence : FENCES) {
            ShotPickets.Layout layout = ShotPickets.layout(Models.get(fence.model()));
            for (boolean north : new boolean[] {true, false}) {
                String edge = fence.model() + " on a " + (north ? "north" : "west") + " edge";
                for (int k = 0; k < layout.count(); k++) {
                    boolean met = meets(fence, north, alongOf(layout, k, north), BETWEEN_RAILS, 0L);
                    check(met && hit[ModelFences.PICKET] == k + 1, edge + ": a line at picket " + (k + 1) + " meets it: " + hit[ModelFences.PICKET]);
                    check(Math.abs(hit[ModelFences.MODEL_UP] - BETWEEN_RAILS) < 1e-3f, edge + ": as high up the model as the line: " + hit[ModelFences.MODEL_UP]);
                }
            }
            long between = ShotPickets.broken(layout, 0L, 2, BETWEEN_RAILS);
            check(ShotPickets.of(between, 2) == ShotPickets.GONE && between >>> ShotPickets.BITS * 3 == 0L && (between & 0xFFL) == 0L,
                    fence.model() + ": struck between its rails a picket is knocked out, and no other: " + Long.toHexString(between));
            float high = overRails(layout);
            long topped = ShotPickets.broken(layout, 0L, 1, high);
            int code = ShotPickets.of(topped, 1);
            check(code > 0 && code < ShotPickets.GONE && ShotPickets.cut(layout, code) <= high && ShotPickets.cut(layout, code) > layout.railTop(),
                    fence.model() + ": struck over its highest rail it breaks at or below the place struck, over the rail: " + code);
            check(ShotPickets.broken(layout, topped, 1, layout.top() - 0.01f) == topped, fence.model() + ": a break over a lower one does not move it");
            long lower = ShotPickets.broken(layout, topped, 1, layout.railTop() + 0.001f);
            check(ShotPickets.of(lower, 1) == 1, fence.model() + ": one just over the rail breaks it there: " + ShotPickets.of(lower, 1));
            long footed = ShotPickets.broken(layout, topped, 1, UNDER_RAILS);
            check(ShotPickets.of(footed, 1) == (code | ShotPickets.FOOT), fence.model() + ": struck under its lowest rail its foot breaks off, and its top stays broken");
            check(ShotPickets.broken(layout, between, 2, high) == between, fence.model() + ": a picket knocked out stays so");
            check(ShotPickets.broken(layout, footed, 1, BETWEEN_RAILS) == (footed & ~(0xFL << ShotPickets.BITS) | (long) ShotPickets.GONE << ShotPickets.BITS),
                    fence.model() + ": a broken picket struck between its rails is knocked out");
        }
    }

    private static void whatBreaksOffIsWhatWasLeftOfThePicket() {
        ShotPickets.Layout layout = ShotPickets.layout(Models.get(ModelFences.WHITE.model()));
        float high = overRails(layout);
        long topped = ShotPickets.broken(layout, 0L, 0, high);
        float cut = ShotPickets.cut(layout, ShotPickets.of(topped, 0));
        check(Arrays.equals(ShotPickets.loosened(layout, 0L, topped, 0), new float[] {cut, layout.top()}), "its top falls from the break up");
        long footed = ShotPickets.broken(layout, topped, 0, UNDER_RAILS);
        check(Arrays.equals(ShotPickets.loosened(layout, topped, footed, 0), new float[] {layout.bottom(), layout.foot()}), "its foot falls from the ground to the rail");
        long gone = ShotPickets.broken(layout, footed, 0, BETWEEN_RAILS);
        check(Arrays.equals(ShotPickets.loosened(layout, footed, gone, 0), new float[] {layout.foot(), cut}), "knocked out, what was left of it falls");
        check(ShotPickets.loosened(layout, gone, gone, 0) == null && ShotPickets.loosened(layout, topped, topped, 1) == null, "nothing falls where nothing broke");
        check(ShotPickets.loosened(layout, 0L, ShotPickets.broken(layout, 0L, 0, BETWEEN_RAILS), 0)[1] == layout.top(), "a whole picket knocked out falls whole");
    }

    private static void aLineThroughTheGapMeetsNothing() {
        for (ModelFences.Fence fence : FENCES) {
            ShotPickets.Layout layout = ShotPickets.layout(Models.get(fence.model()));
            float high = overRails(layout);
            for (boolean north : new boolean[] {true, false}) {
                String edge = fence.model() + " on a " + (north ? "north" : "west") + " edge";
                float along = alongOf(layout, 3, north), beside = alongOf(layout, 4, north);
                long gone = ShotPickets.broken(layout, 0L, 3, BETWEEN_RAILS);
                check(!meets(fence, north, along, BETWEEN_RAILS, gone), edge + ": a line through a picket knocked out meets nothing");
                check(!meets(fence, north, along, high, gone) && !meets(fence, north, along, UNDER_RAILS, gone), edge + ": nor over or under its rails");
                check(meets(fence, north, beside, BETWEEN_RAILS, gone) && hit[ModelFences.PICKET] == 5, edge + ": the picket beside it still stands");
                check(meets(fence, north, along, (layout.foot() + 0.17f) / 2f, gone) && hit[ModelFences.PICKET] == 0, edge + ": and the rail behind it");
                long topped = ShotPickets.broken(layout, 0L, 3, high);
                float cut = ShotPickets.cut(layout, ShotPickets.of(topped, 3));
                check(!meets(fence, north, along, high, topped), edge + ": a line over the break meets nothing");
                check(meets(fence, north, along, cut - 0.01f, topped) && hit[ModelFences.PICKET] == 4, edge + ": one under it meets what is left of the picket");
                long footed = ShotPickets.broken(layout, 0L, 3, UNDER_RAILS);
                check(!meets(fence, north, along, UNDER_RAILS, footed) && meets(fence, north, along, BETWEEN_RAILS, footed), edge + ": its foot broken off, a line under the rails passes");
            }
        }
    }

    private static void theSavedMasksReadBackTheSame() {
        ShotPickets.Layout layout = ShotPickets.layout(Models.get(ModelFences.ROUNDED.model()));
        long north = ShotPickets.broken(layout, ShotPickets.broken(layout, 0L, 0, BETWEEN_RAILS), 8, UNDER_RAILS);
        long west = ShotPickets.broken(layout, 0L, 5, overRails(layout));
        long[] back = ShotPickets.parse(ShotPickets.write(north, west), layout.count());
        check(back[0] == north && back[1] == west, "the masks saved read back the same: " + Arrays.toString(back));
        check(Arrays.equals(ShotPickets.parse(ShotPickets.write(0L, 0L), 6), new long[2]), "nothing broken reads back as nothing");
        check(refused("7,0", 9), "a list without what it starts with is refused");
        check(refused("1:7", 9) && refused("1:7,0,0", 9), "and one with other than a mask for each edge");
        check(refused("1:x,0", 9) && refused("1:-1,0", 9), "and one with a mask that is no mask");
        check(refused(ShotPickets.write(1L << ShotPickets.BITS * 6, 0L), 6), "and one with pickets broken past those the fence has");
    }

    private static boolean refused(String saved, int pickets) {
        try {
            ShotPickets.parse(saved, pickets);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    /** The GLSL's shotPickets, worked in floats as the graphics card works it, from what an instance carries. */
    private static void asTheShaderDoes(ShotPickets.Layout layout, float low, float high, int picket, float[] at) {
        if (picket < 0.5f || low + high < 0.5f) {
            return;
        }
        float k = picket - 1f;
        float word = k < 5.5f ? low : high;
        float bits = mod((float) Math.floor(word * (float) Math.pow(2.0, -4.0 * mod(k, 6f))), 16f);
        float top = mod(bits, 8f);
        if (top > 6.5f) {
            at[0] = at[1] = at[2] = 0f;
            return;
        }
        if (bits > 7.5f) {
            at[2] = Math.max(at[2], layout.foot());
        }
        if (top > 0.5f) {
            at[2] = Math.min(at[2], layout.railTop() + (top - 1f) / 6f * (layout.top() - layout.railTop()));
        }
    }

    private static float mod(float x, float y) {
        return x - y * (float) Math.floor(x / y);
    }

    private static void anInstanceCarriesTheMaskAsTheVertexProgramsReadIt() {
        Models.Model model = Models.get(ModelFences.ROUNDED.model());
        ShotPickets.Layout layout = ShotPickets.layout(model);
        SpriteGeometry.Frame light = LightSiteTests.sited(new SpriteGeometry.Frame());
        Models.Instances pieces = new Models.Instances();
        pieces.add(model, light, 10, 20, 10.5f, 20f, 0f, 2, 1f, 1f, 0, 0, 0f, 0f, 0, 0f, 0f);
        check(pieces.floats[Models.Instances.PICKETS] == 0f && pieces.floats[Models.Instances.PICKETS + 1] == 0f, "a length with nothing broken carries no mask");
        // Every value a picket's bits take, on every picket.
        long mask = 0L;
        for (int k = 0; k < layout.count(); k++) {
            mask |= (long) ((k * 7 + 3) % 16) << ShotPickets.BITS * k;
        }
        pieces.pickets(mask);
        pieces.add(model, light, 11, 20, 11.5f, 20f, 0f, 2, 1f, 1f, 0, 0, 0f, 0f, 0, 0f, 0f);
        float low = pieces.floats[Models.Instances.PICKETS], high = pieces.floats[Models.Instances.PICKETS + 1];
        check(low == (mask & 0xFFFFFFL) && high == mask >>> 24, "the mask is carried whole in two floats: " + low + ", " + high);
        check(pieces.floats[Models.Instances.FLOATS + Models.Instances.PICKETS] == 0f, "and the next length carries none of it");
        float[] mine = new float[3], shader = new float[3];
        int differ = 0;
        for (Models.Model each : new Models.Model[] {model, model.plain}) {
            ShotPickets.Layout drawn = ShotPickets.layout(each);
            for (int vertex = 0; vertex < each.piece.length; vertex++) {
                for (int k = 0; k < 3; k++) {
                    mine[k] = shader[k] = each.vertices[vertex * Models.VERTEX_FLOATS + k];
                }
                ShotPickets.collapse(drawn, mask, each.piece[vertex], mine);
                asTheShaderDoes(drawn, low, high, each.piece[vertex], shader);
                differ += Arrays.equals(mine, shader) ? 0 : 1;
            }
        }
        check(differ == 0, "every vertex is moved as the vertex programs move it: " + differ + " differ");
        float[] top = {0f, 0f, layout.top()};
        ShotPickets.collapse(layout, ShotPickets.broken(layout, 0L, 0, BETWEEN_RAILS), 1, top);
        check(top[0] == 0f && top[1] == 0f && top[2] == 0f, "a picket knocked out has every vertex at one place");
    }

    private static void theMarksOnWhatBrokeOffGoWithIt() {
        float[] box = {10.2f, 19.97f, 0.5f, 10.3f, 20.03f, 0.9f};
        Impacts.Struck on = mark(10.25f, 0.7f), off = mark(10.4f, 0.7f), under = mark(10.25f, 0.4f);
        float[] rims = BlastHoles.withRim(BlastHoles.withRim(BlastHoles.withRim(new float[0], under), on), off);
        float[] left = BlastHoles.withoutRimsIn(rims, box);
        check(left.length == 2 * BlastHoles.RIM_FLOATS && left[0] == under.x() && left[BlastHoles.RIM_FLOATS] == off.x(), "the rims saved on what broke off go, the rest kept in order");
        Impacts.forget();
        Impacts.take();
        try {
            Impacts.add(under);
            Impacts.add(on);
            Impacts.withdraw(box);
            Impacts.add(off);
            Impacts.add(mark(10.26f, 0.8f));
            Impacts.take();
            check(Impacts.holeCount == 3 && Impacts.holes[0] == under && Impacts.holes[1] == off && Impacts.holes[2].x() == 10.26f,
                    "a mark on what broke off goes, one laid after is kept, and the rest stay in order: " + Impacts.holeCount);
            Impacts.restore(BlastHoles.rimAt(BlastHoles.withRim(new float[0], on), 0));
            Impacts.take();
            check(Impacts.holeCount == 4, "a mark let go of is laid again if a save holds it");
        } finally {
            Impacts.forget();
            Impacts.take();
        }
    }

    private static Impacts.Struck mark(float x, float up) {
        return new Impacts.Struck(x, 20f, up, 0f, -1f, 0f, 0f, 1f, 0f, 0f, Impacts.WOOD, 1f, true, 0f, 0f, 1f);
    }
}
