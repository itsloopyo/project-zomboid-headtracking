package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests of which lamps throw shadows (LampShadows): the strongest and nearest take the slots and
 * keep them, the slots' maps are drawn in turn, a lamp is told to the shader only once its maps
 * are drawn, and the copy the world reads is made again only while it has to be.
 */
final class LampShadowsTests {
    private LampShadowsTests() {}

    static void run() {
        Checks.run("aBiggerCrowdIsDrawnLessOftenInSteps", LampShadowsTests::aBiggerCrowdIsDrawnLessOftenInSteps);
        Checks.run("theStrongestNearestLampsTakeTheSlots", LampShadowsTests::theStrongestNearestLampsTakeTheSlots);
        Checks.run("aLampKeepsItsSlotUntilAnotherFarOutweighsIt", LampShadowsTests::aLampKeepsItsSlotUntilAnotherFarOutweighsIt);
        Checks.run("theSlotsAreDrawnInTurn", LampShadowsTests::theSlotsAreDrawnInTurn);
        Checks.run("aCeilingLightsFloorIsToldOnceItsMapsAreDrawn", LampShadowsTests::aCeilingLightsFloorIsToldOnceItsMapsAreDrawn);
        Checks.run("theCopyIsMadeAgainOnlyWhileSomeoneIsNearOrHasJustGone", LampShadowsTests::theCopyIsMadeAgainOnlyWhileSomeoneIsNearOrHasJustGone);
        Checks.run("aLampLightsThePictureUnlessItsLightIsWhollyPastAnEdge", LampShadowsTests::aLampLightsThePictureUnlessItsLightIsWhollyPastAnEdge);
        Checks.run("theShaderIsWrittenWithPoints", LampShadowsTests::theShaderIsWrittenWithPoints);
    }

    private static final ArrayList<WorldScene.ChunkMeshes> NONE_BUILT = new ArrayList<>();
    /** A frame at 60 a second, in nanoseconds. */
    private static final long FRAME = 16_666_667L;

    /** A lamp of Lamps' list at (x, 0, 3) whose maps see 8.5 tiles. */
    private static void outdoor(LampShadows shadows, int index, float x, float weight) {
        shadows.add(LampShadows.OUTDOOR, index, x, 0f, 3f, 8.5f, weight, null, 0);
    }

    private static void theStrongestNearestLampsTakeTheSlots() {
        check(LampShadows.weight(5f, 4f) > LampShadows.weight(40f, 100f), "a porch light two tiles off counts for more than a street lamp ten off");
        check(LampShadows.weight(5f, 4f) < LampShadows.weight(40f, 4f), "and less than a street lamp as near");
        LampShadows shadows = new LampShadows();
        outdoor(shadows, 0, 0f, 0.2f);
        outdoor(shadows, 1, 4f, 1f);
        outdoor(shadows, 2, 8f, 0.1f);
        outdoor(shadows, 3, 12f, 0.5f);
        shadows.assign(NONE_BUILT, 1, FRAME, 2, 256);
        check(shadows.lampSlot[1] >= 0f && shadows.lampSlot[3] >= 0f && shadows.lampSlot[1] != shadows.lampSlot[3], "the two strongest lamps have a slot each");
        check(shadows.lampSlot[0] == -1f && shadows.lampSlot[2] == -1f, "and the others none");
        check(shadows.from[(int) shadows.lampSlot[3] * 4] == 12f && shadows.from[(int) shadows.lampSlot[3] * 4 + 3] == 8.5f, "a slot is drawn from its lamp, as far as it sees");
        shadows.offers = 0;
        shadows.assign(NONE_BUILT, 2, 2 * FRAME, 0, 256);
        check(shadows.lampSlot[1] == -1f && shadows.lampSlot[3] == -1f && !shadows.any() && shadows.toDraw == 0, "with no slots, no lamp throws a shadow and nothing is drawn");
        LampShadows kinds = new LampShadows();
        outdoor(kinds, 5, 0f, 1f);
        kinds.add(LampShadows.ROOM, 2, 0f, 0f, 3f, 6.5f, 0.9f, null, 0);
        kinds.assign(NONE_BUILT, 1, FRAME, 2, 256);
        check(kinds.lampSlot[5] >= 0f && kinds.roomLampSlot[2] >= 0f && kinds.lampSlot[5] != kinds.roomLampSlot[2],
                "a room's lamp where a lamp out of doors is is not taken for it, and each is told in its own list");
    }

    private static void aLampKeepsItsSlotUntilAnotherFarOutweighsIt() {
        LampShadows shadows = new LampShadows();
        outdoor(shadows, 0, 0f, 1f);
        outdoor(shadows, 1, 5f, 0.5f);
        shadows.assign(NONE_BUILT, 1, FRAME, 1, 256);
        check(shadows.lampSlot[0] == 0f && shadows.lampSlot[1] == -1f, "with one slot the stronger lamp has it");
        // The two swap places in the lists and the other grows stronger, but not by HELD.
        shadows.offers = 0;
        outdoor(shadows, 0, 5f, 1.4f);
        outdoor(shadows, 1, 0f, 1f);
        shadows.assign(NONE_BUILT, 2, 2 * FRAME, 1, 256);
        check(shadows.lampSlot[1] == 0f && shadows.lampSlot[0] == -1f && shadows.toDraw == 0, "a lamp a little stronger than the one holding the slot does not take it");
        shadows.offers = 0;
        outdoor(shadows, 0, 5f, 1.6f);
        outdoor(shadows, 1, 0f, 1f);
        shadows.assign(NONE_BUILT, 3, 3 * FRAME, 1, 256);
        check(shadows.lampSlot[0] == 0f && shadows.lampSlot[1] == -1f && shadows.toDraw == 1, "one more than HELD times as strong does, and its maps are drawn");
        shadows.offers = 0;
        outdoor(shadows, 0, 5f, 1.6f);
        shadows.assign(NONE_BUILT, 4, 4 * FRAME, 1, 512);
        check(shadows.toDraw == 1 && shadows.face == 512, "at another face size every slot is drawn again");
    }

    private static void theSlotsAreDrawnInTurn() {
        LampShadows shadows = new LampShadows();
        float[] weights = {1f, 0.9f, 0.8f, 0.7f};
        WorldScene.ChunkMeshes near = new WorldScene.ChunkMeshes(0);
        near.runs = 1;
        near.minX = 0f;
        near.maxX = 1f;
        near.minY = 0f;
        near.maxY = 1f;
        near.minUp = 0f;
        near.maxUp = 1f;
        ArrayList<WorldScene.ChunkMeshes> drawn = new ArrayList<>(List.of(near));
        long[] at = {FRAME, 2 * FRAME, 3 * FRAME, 3 * FRAME + LampShadows.STALE_NANOS, 4 * FRAME + LampShadows.STALE_NANOS, 5 * FRAME + LampShadows.STALE_NANOS};
        int[] drawnCount = new int[at.length];
        int[] first = new int[at.length];
        for (int frame = 0; frame < at.length; frame++) {
            shadows.offers = 0;
            for (int lamp = 0; lamp < 4; lamp++) {
                outdoor(shadows, lamp, lamp * 20f, weights[lamp]);
            }
            shadows.assign(drawn, frame + 1, at[frame], 4, 256);
            drawnCount[frame] = shadows.toDraw;
            first[frame] = shadows.toDraw > 0 ? shadows.draw[0] : -1;
            if (frame == 0) {
                int shadowed = 0;
                for (int lamp = 0; lamp < 4; lamp++) {
                    shadowed += shadows.lampSlot[lamp] >= 0f ? 1 : 0;
                }
                check(shadowed == LampShadows.DRAWN_A_FRAME, "the first frame draws DRAWN_A_FRAME new slots, and only their lamps throw shadows: " + shadowed);
            }
        }
        check(drawnCount[0] == LampShadows.DRAWN_A_FRAME && drawnCount[1] == 2, "four new lamps are drawn two a frame: " + drawnCount[0] + ", " + drawnCount[1]);
        check(drawnCount[2] == 0, "nothing changed and nothing gone stale, nothing is drawn");
        check(drawnCount[3] == 1 && drawnCount[4] == 1 && first[3] != first[4], "slots gone stale are drawn again one a frame, in turn: " + first[3] + ", " + first[4]);
        check(first[3] == (int) shadows.lampSlot[0] || first[3] == (int) shadows.lampSlot[1], "the oldest first");
        // The lamp at 0 has a build in its reach: the build made again draws its slot again, and no other.
        shadows.offers = 0;
        for (int lamp = 0; lamp < 4; lamp++) {
            outdoor(shadows, lamp, lamp * 20f, weights[lamp]);
        }
        shadows.assign(drawn, 7, 6 * FRAME + LampShadows.STALE_NANOS, 4, 256);
        near.version++;
        shadows.offers = 0;
        for (int lamp = 0; lamp < 4; lamp++) {
            outdoor(shadows, lamp, lamp * 20f, weights[lamp]);
        }
        shadows.assign(drawn, 8, 7 * FRAME + LampShadows.STALE_NANOS, 4, 256);
        check(shadows.toDraw == 1 && shadows.draw[0] == (int) shadows.lampSlot[0], "a build in a lamp's reach made again draws that lamp's slot again");
        // A texture made again holds none of the slots: those this frame does not draw are drawn as new.
        shadows.atlasMade();
        check(shadows.lampSlot[0] >= 0f, "the slot drawn this frame is still told");
        int told = 0;
        for (int lamp = 1; lamp < 4; lamp++) {
            told += shadows.lampSlot[lamp] >= 0f ? 1 : 0;
        }
        check(told == 0, "the others are not, until they are drawn again: " + told);
    }

    private static void aCeilingLightsFloorIsToldOnceItsMapsAreDrawn() {
        LampShadows shadows = new LampShadows();
        float[] plan = {10f, 20f, 14f, 23f};
        outdoor(shadows, 0, 0f, 1f);
        outdoor(shadows, 1, 4f, 0.9f);
        shadows.add(LampShadows.CEILING, 0, 12f, 21.5f, 2.2f, 6f, 0.8f, plan, 0);
        shadows.assign(NONE_BUILT, 1, FRAME, 3, 256);
        int ceiling = -1;
        for (int slot = 0; slot < 3; slot++) {
            ceiling = shadows.ready(slot) || shadows.from[slot * 4] != 12f ? ceiling : slot;
        }
        check(ceiling >= 0, "the ceiling light has a slot, not drawn the first frame: two lamps are stronger");
        check(shadows.rect[ceiling * 4 + 2] == 0f, "and until it is drawn its floor is not told, so nothing is darkened by a map not drawn");
        shadows.offers = 0;
        outdoor(shadows, 0, 0f, 1f);
        outdoor(shadows, 1, 4f, 0.9f);
        shadows.add(LampShadows.CEILING, 0, 12f, 21.5f, 2.2f, 6f, 0.8f, plan, 0);
        shadows.assign(NONE_BUILT, 2, 2 * FRAME, 3, 256);
        check(shadows.ready(ceiling) && shadows.rect[ceiling * 4] == 10f && shadows.rect[ceiling * 4 + 1] == 20f && shadows.rect[ceiling * 4 + 2] == 14f
                && shadows.rect[ceiling * 4 + 3] == 23f, "drawn, its floor is told");
        for (int slot = 0; slot < 3; slot++) {
            check(slot == ceiling || shadows.rect[slot * 4 + 2] == 0f, "no other slot tells a floor");
        }
    }

    private static void theCopyIsMadeAgainOnlyWhileSomeoneIsNearOrHasJustGone() {
        LampShadows shadows = new LampShadows();
        boolean[] people = {false, false, true, true, false, false};
        boolean[] due = new boolean[people.length];
        for (int frame = 0; frame < people.length; frame++) {
            shadows.offers = 0;
            outdoor(shadows, 0, 0f, 1f);
            shadows.assign(NONE_BUILT, frame + 1, (frame + 1) * FRAME, 1, 256);
            shadows.peopled[0] = people[frame];
            due[frame] = shadows.copyDue(0);
            if (due[frame]) {
                shadows.copied(0);
            }
        }
        check(due[0], "the copy is made once the world is drawn");
        check(!due[1], "with nobody near and the world the same, it is kept");
        check(due[2] && due[3], "it is made again every frame someone is near");
        check(due[4], "and once more as they go, so they are not left in it");
        check(!due[5], "then kept again");
        // Three lamps with people near, people drawn into two: the two nearest the eye.
        LampShadows three = new LampShadows();
        outdoor(three, 0, 30f, 1f);
        outdoor(three, 1, 2f, 0.9f);
        outdoor(three, 2, 10f, 0.8f);
        three.assign(NONE_BUILT, 1, FRAME, 3, 256);
        three.assign(NONE_BUILT, 2, 2 * FRAME, 3, 256);
        three.peopleMost = 2;
        java.util.Arrays.fill(three.peopleNear, 0, 3, true);
        three.choosePeople(0f, 0f, 1.6f);
        check(three.peopled[(int) three.lampSlot[1]] && three.peopled[(int) three.lampSlot[2]] && !three.peopled[(int) three.lampSlot[0]],
                "the people are drawn into the maps of the lamps nearest the eye");
        three.peopleNear[(int) three.lampSlot[1]] = false;
        three.choosePeople(0f, 0f, 1.6f);
        check(!three.peopled[(int) three.lampSlot[1]] && three.peopled[(int) three.lampSlot[0]], "and not into one nobody is near");
        check(three.peopleReach(0) == LampShadows.PEOPLE_WITHIN, "nor from further off its lamp than PEOPLE_WITHIN");
    }

    private static void aBiggerCrowdIsDrawnLessOftenInSteps() {
        check(LampShadows.crowdEvery(1, 12) == 1 && LampShadows.crowdEvery(12, 12) == 1, "up to a step's worth of people are drawn every frame");
        check(LampShadows.crowdEvery(13, 12) == 2 && LampShadows.crowdEvery(24, 12) == 2 && LampShadows.crowdEvery(25, 12) == 3,
                "then every second frame up to two steps, every third up to three");
        check(LampShadows.crowdEvery(500, 12) == LampShadows.CROWD_SLOWEST, "and never further apart than CROWD_SLOWEST");
        LampShadows shadows = new LampShadows();
        shadows.peopleMost = 1;
        shadows.crowdStep = 12;
        int drawn = 0;
        for (int frame = 0; frame < 12; frame++) {
            shadows.offers = 0;
            outdoor(shadows, 0, 0f, 1f);
            shadows.assign(NONE_BUILT, frame + 1, (frame + 1) * FRAME, 1, 256);
            shadows.peopleNear[0] = true;
            shadows.crowd[0] = 30;
            shadows.choosePeople(0f, 0f, 1.6f);
            boolean due = shadows.copyDue(0);
            check(due == shadows.peopled[0] || frame == 0, "a held crowd's copy is kept as it is: frame " + frame);
            if (shadows.peopled[0]) {
                drawn++;
            }
            if (due) {
                shadows.copied(0);
            }
        }
        check(drawn == 4, "thirty people round a lamp are drawn every third frame: " + drawn + " of 12");
    }

    private static void aLampLightsThePictureUnlessItsLightIsWhollyPastAnEdge() {
        ViewCamera.Pose eye = new ViewCamera.Pose();
        ViewCamera.clean(eye, 0f, 0f, 1.6f, 0f, 0f);
        float[] view = new float[16], past = new float[5];
        ViewCamera.view(eye, view);
        check(LampShadows.lightsSeen(view, 1f, 0.6f, 10f, 0f, 3f, 2f, past), "a lamp ahead lights the picture");
        check(!LampShadows.lightsSeen(view, 1f, 0.6f, -10f, 0f, 3f, 2f, past), "a lamp behind the eye whose light stops short of it does not");
        check(LampShadows.lightsSeen(view, 1f, 0.6f, -1f, 0f, 3f, 8f, past), "one just behind whose light reaches past the eye does");
        check(!LampShadows.lightsSeen(view, 1f, 0.6f, 10f, 40f, 3f, 2f, past), "nor does one far off to the side");
    }

    private static void theShaderIsWrittenWithPoints() {
        check(!LampShadows.GLSL.matches("(?s).*\\d,\\d.*"), "no number in the shader is written with a comma");
        check(LampShadows.GLSL.contains("uniform vec4 uLampFrom[" + LampShadows.MOST + "];"), "a slot's place for each slot");
        check(Lamps.GLSL.contains("uniform vec2 uLampCover[" + Lamps.LIGHTS + "];"), "a lamp's slot is told beside its cover");
    }
}
